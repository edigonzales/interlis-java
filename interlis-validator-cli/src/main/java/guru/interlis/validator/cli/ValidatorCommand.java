package guru.interlis.validator.cli;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.validation.*;
import guru.interlis.validation.jts.JtsGeometryEngine;
import guru.interlis.validation.mvstore.MvStoreOptions;
import guru.interlis.validation.mvstore.MvStoreValidationStorage;
import guru.interlis.xtf23.Xtf23Exception;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Exception;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ValidatorCommand {
    private static final long MEMORY_BYTES = 256L * 1024 * 1024;
    private static final long DISK_BYTES = 4L * 1024 * 1024 * 1024;

    private ValidatorCommand() { }

    static int run(List<String> args, Path cwd, PrintWriter out, PrintWriter err, CancellationToken token) {
        boolean json = args.stream().takeWhile(arg -> !arg.equals("--")).anyMatch("--json"::equals);
        var output = new ValidationOutput(json, out, err);
        try {
            ValidatorOptions options;
            try {
                options = ValidatorOptions.parse(args);
            } catch (IllegalArgumentException invalid) {
                if (!json) err.print(ValidatorOptions.usage());
                throw new Failure(2, "options", invalid.getMessage());
            }
            if (options.has("--help")) {
                err.print(ValidatorOptions.usage());
                return output.flush(0);
            }
            if (options.has("--version")) return output.version();
            output.maxDiagnostics = options.maxDiagnostics();
            output.scope = options.has("--partial") ? DatasetScope.PARTIAL_DATASET : DatasetScope.SUPPLIED_DATASET;
            return execute(options, cwd, output, err, token);
        } catch (Failure failure) {
            return output.failure(failure.code, failure.phase, failure.messages);
        } catch (OperationCancelledException cancelled) {
            int code = cancelled.reason() == CancellationReason.RESOURCE_LIMIT ? 3 : 4;
            return output.failure(code, output.phase, List.of(cancelled.reason().name()));
        } catch (XtfHeaderProbe.InvalidHeader invalid) {
            return output.failure(1, "header", List.of(invalid.getMessage()), "INVALID_WIRE");
        } catch (ActiveCoverage.PrimaryModelMismatch mismatch) {
            return output.failure(mismatch.getSuppressed().length == 0 ? 1 : 3,
                    "transfer", List.of(mismatch.getMessage()), "PRIMARY_MODEL_MISMATCH");
        } catch (Xtf23Exception invalid) {
            return wireFailure(invalid.reason().name(), invalid, output);
        } catch (Xtf24Exception invalid) {
            return wireFailure(invalid.reason().name(), invalid, output);
        } catch (IOException | RuntimeException failure) {
            return output.failure(3, output.phase,
                    List.of(failure.getClass().getSimpleName() + ": " + failure.getMessage()));
        }
    }

    private static int execute(ValidatorOptions options, Path cwd, ValidationOutput output,
                               PrintWriter err, CancellationToken token)
            throws Failure, IOException, XtfHeaderProbe.InvalidHeader {
        output.phase = "input";
        token.throwIfCancellationRequested();
        var inputs = output.inputs;
        var paths = new HashSet<Path>();
        probe(options.primary(), InputRole.PRIMARY, cwd, paths, output, token);
        probe(options.reference(), InputRole.REFERENCE, cwd, paths, output, token);
        if (options.has("--verbose")) {
            inputs.forEach(input -> err.println("Preparing " + input.path() + " as " + input.format()));
        }
        output.phase = "model resolution";
        var models = ModelPreparation.prepare(options, inputs, cwd, token);
        var schema = models.schema();
        output.phase = "transfer planning";
        var transfer = switch (TransferPlanner.plan(schema)) {
            case TransferPlanningResult.Ready ready -> ready.plan();
            case TransferPlanningResult.Unsupported unsupported ->
                    throw new Failure(5, output.phase, unsupported.issues().toString());
        };
        output.phase = "validation planning";
        var primary = transfer.models().stream()
                .filter(model -> models.primaryModels().contains(model.name().declared().toString()))
                .map(model -> model.symbol()).toList();
        var bindings = RuntimeBindings.empty().withGeometry(JtsGeometryEngine.standard());
        var plan = switch (ValidationPlanner.plan(transfer, ValidationProfile.DATASET, bindings, primary)) {
            case ValidationPlanningResult.Ready ready -> ready.plan();
            case ValidationPlanningResult.Rejected rejected ->
                    throw new Failure(5, output.phase, rejected.problems().toString());
        };
        var defaults = ValidationOptions.defaults();
        var local = new ValidationOptions(options.maxDiagnostics(), defaults.maxNestedDepth(),
                defaults.maxValuesPerObject(), defaults.numericRounding());
        var dataset = new DatasetOptions(local, DatasetLimits.defaults(), output.scope);
        var coverage = new ActiveCoverage(plan);
        ValidationReport report;
        output.phase = "storage";
        try (var storage = openStorage(options, cwd);
             var run = DatasetValidator.session(plan, dataset, storage)) {
            output.graph = schema.semanticGraph();
            try {
                output.phase = "transfer";
                for (var input : inputs) {
                    output.currentInput = input.path();
                    read(input, transfer, run, coverage, !options.models().isEmpty(), token);
                }
                output.currentInput = null;
                output.phase = "validation";
                report = run.finish(token);
            } finally {
                output.provisional = run.findings();
            }
            output.phase = "cleanup";
        }
        token.throwIfCancellationRequested();
        output.phase = "validation";
        return output.report(report, coverage.gaps(report.coverage(), output.scope), schema.semanticGraph());
    }

    private static void probe(List<Path> supplied, InputRole role, Path cwd, Set<Path> paths,
                              ValidationOutput output, CancellationToken token)
            throws Failure, IOException, XtfHeaderProbe.InvalidHeader {
        for (var file : supplied) {
            token.throwIfCancellationRequested();
            output.phase = "input";
            var path = cwd.resolve(file).normalize().toAbsolutePath();
            output.currentInput = path;
            if (!Files.isRegularFile(path)) throw new Failure(3, "input", "Not a regular file: " + path);
            if (!paths.add(path.toRealPath())) throw new Failure(2, "options", "Repeated input file: " + path);
            if (paths.size() > DatasetLimits.defaults().maxInputs()) {
                throw new Failure(3, "input", "Dataset input budget exceeded");
            }
            output.phase = "header";
            var input = new ValidationInput(path, role, XtfHeaderProbe.inspect(path, token));
            output.inputs.add(input);
            output.currentInput = null;
        }
    }

    private static ValidationStorage openStorage(ValidatorOptions options, Path cwd) {
        return switch (options.storage()) {
            case MEMORY -> new InMemoryValidationStorage(1_000_000, MEMORY_BYTES);
            case MVSTORE -> MvStoreValidationStorage.open(options.tempDir().map(cwd::resolve)
                    .orElseGet(() -> Path.of(System.getProperty("java.io.tmpdir"))), MvStoreOptions.defaults(DISK_BYTES));
        };
    }

    private static void read(ValidationInput input, TransferPlan transfer, DatasetValidationSession run,
                             ActiveCoverage coverage, boolean explicitPrimary, CancellationToken token) throws IOException {
        var source = new SourceId(input.path().toUri().toString());
        try (var stream = Files.newInputStream(input.path());
             var reader = input.header().xtf23()
                     ? new Xtf23Reader(transfer, stream, Optional.of(source))
                     : new Xtf24Reader(transfer, stream, Optional.of(source));
             var observed = coverage.observe(reader, transfer, input.role(), explicitPrimary)) {
            run.readInput(new InputDescriptor(new InputId(source.value()), input.role()), observed, token);
        }
    }

    private static int wireFailure(String reason, RuntimeException failure, ValidationOutput output) {
        int code = failure.getSuppressed().length > 0 ? 3 : switch (reason) {
            case "MALFORMED_XML", "UNSAFE_XML", "INVALID_WIRE", "DUPLICATE_ID" -> 1;
            case "UNSUPPORTED" -> 5;
            default -> 3;
        };
        return output.failure(code, "transfer", List.of(failure.getMessage()), reason);
    }

    static final class Failure extends Exception {
        final int code;
        final String phase;
        final List<String> messages;

        Failure(int code, String phase, String message) {
            this(code, phase, List.of(message));
        }

        Failure(int code, String phase, List<String> messages) {
            this.code = code;
            this.phase = phase;
            this.messages = List.copyOf(messages);
        }
    }
}
