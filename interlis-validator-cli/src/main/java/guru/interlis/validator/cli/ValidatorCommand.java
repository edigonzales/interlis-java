package guru.interlis.validator.cli;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.SemanticGraph;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.validation.*;
import guru.interlis.validation.jts.JtsGeometryEngine;
import guru.interlis.xtf23.Xtf23Exception;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Exception;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class ValidatorCommand {
    private ValidatorCommand() { }

    static int run(List<String> args, Path cwd, PrintWriter out, PrintWriter err, CancellationToken token) {
        int code;
        try {
            code = execute(args, cwd, out, err, token);
        } catch (Failure failure) {
            for (var message : failure.messages) err.println(failure.phase + ": " + message);
            code = failure.code;
        } catch (OperationCancelledException cancelled) {
            err.println("Validation cancelled.");
            code = 4;
        } catch (XtfHeaderProbe.InvalidHeader invalid) {
            err.println("header: INVALID_WIRE " + invalid.getMessage());
            code = 1;
        } catch (Xtf23Exception invalid) {
            code = wireFailure(invalid.reason().name(), invalid, err);
        } catch (Xtf24Exception invalid) {
            code = wireFailure(invalid.reason().name(), invalid, err);
        } catch (IOException | RuntimeException failure) {
            err.println("Operational failure: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            code = 3;
        }
        out.flush();
        err.flush();
        return out.checkError() || err.checkError() ? 3 : code;
    }

    private static int execute(List<String> args, Path cwd, PrintWriter out, PrintWriter err, CancellationToken token)
            throws Failure, IOException, XtfHeaderProbe.InvalidHeader {
        ValidatorOptions options;
        try {
            options = ValidatorOptions.parse(args);
        } catch (IllegalArgumentException invalid) {
            err.print(ValidatorOptions.usage());
            throw new Failure(2, "options", invalid.getMessage());
        }
        if (options.has("--help")) {
            err.print(ValidatorOptions.usage());
            return 0;
        }
        if (options.has("--version")) {
            var module = Main.class.getModule().getDescriptor();
            err.println("interlis-validator " + (module == null ? "development"
                    : module.rawVersion().orElse("development")));
            return 0;
        }
        token.throwIfCancellationRequested();
        var input = cwd.resolve(options.input()).normalize().toAbsolutePath();
        if (!Files.isRegularFile(input)) throw new Failure(3, "input", "Not a regular file: " + input);
        var header = XtfHeaderProbe.inspect(input, token);
        if (options.has("--verbose")) err.println("Preparing " + input + " as XTF " + (header.xtf23() ? "2.3" : "2.4"));
        var schema = ModelPreparation.prepare(options, input, cwd, header, token);
        var transfer = switch (TransferPlanner.plan(schema)) {
            case TransferPlanningResult.Ready ready -> ready.plan();
            case TransferPlanningResult.Unsupported unsupported ->
                    throw new Failure(5, "transfer planning", unsupported.issues().toString());
        };
        var bindings = RuntimeBindings.empty().withGeometry(JtsGeometryEngine.standard());
        var plan = switch (ValidationPlanner.plan(transfer, ValidationProfile.DATASET, bindings)) {
            case ValidationPlanningResult.Ready ready -> ready.plan();
            case ValidationPlanningResult.Rejected rejected ->
                    throw new Failure(5, "validation planning", rejected.problems().toString());
        };
        var source = Optional.of(new SourceId(input.toUri().toString()));
        var coverage = new ActiveCoverage(plan.uncheckedRules());
        ValidationReport report;
        try (var stream = Files.newInputStream(input);
             var reader = header.xtf23() ? new Xtf23Reader(transfer, stream, source) : new Xtf24Reader(transfer, stream, source);
             var storage = new InMemoryValidationStorage(1_000_000, 256L * 1024 * 1024);
             var run = DatasetValidator.session(plan, DatasetOptions.defaults(), storage);
             var observed = coverage.observe(reader, transfer)) {
            run.readInput(new InputDescriptor(new InputId(source.orElseThrow().value()), InputRole.PRIMARY), observed, token);
            report = run.finish(token);
        }
        report.examples().forEach(diagnostic -> render(diagnostic, schema.semanticGraph(), err));
        var gaps = coverage.gaps(report.coverage());
        gaps.forEach(gap -> err.println("Unchecked: " + gap));
        err.println("Objects checked: " + report.inspectedObjects() + ", violations: " + report.detectedViolations()
                + ", diagnostic examples: " + report.examples().size());
        if (report.detectedViolations() > 0) {
            err.println(gaps.isEmpty() ? "Rejected supplied dataset." : "Rejected supplied dataset with incomplete checks.");
            return 1;
        }
        if (!gaps.isEmpty()) {
            err.println("Validation is incomplete.");
            return 5;
        }
        err.println("No violations found in the supplied dataset.");
        return 0;
    }

    private static void render(ValidationDiagnostic diagnostic, SemanticGraph graph, PrintWriter err) {
        var data = diagnostic.data();
        var rule = diagnostic.rule();
        err.println(diagnostic.code() + " " + diagnostic.message() + location(data, graph)
                + " | model rule " + name(rule.declaration(), graph)
                + rule.range().map(range -> " at " + range.source().value() + ":" + range.startOffset()).orElse("")
                + diagnostic.relatedData().map(other -> " | related:" + location(other, graph)).orElse(""));
    }

    private static String location(DataLocation location, SemanticGraph graph) {
        var value = new StringBuilder();
        location.source().ifPresent(source -> value.append(" ").append(source.value()));
        location.basket().ifPresent(basket -> value.append(" BID=").append(basket.value()));
        location.tid().ifPresent(tid -> value.append(" TID=").append(tid.value()));
        location.oid().ifPresent(oid -> value.append(" OID=").append(oid.value()));
        location.objectType().ifPresent(type -> value.append(" ").append(name(type, graph)));
        for (var step : location.path()) {
            switch (step) {
                case DataLocation.Slot slot -> value.append("/").append(name(slot.symbol(), graph));
                case DataLocation.Index index -> value.append("[").append(index.position()).append("]");
                case DataLocation.GeometryComponent geometry ->
                        value.append("/").append(geometry.kind()).append("[").append(geometry.position()).append("]");
            }
        }
        return value.toString();
    }

    private static String name(guru.interlis.model.SymbolRef ref, SemanticGraph graph) {
        return graph.symbol(ref.symbol()).map(symbol -> symbol.name().value()).orElse(ref.symbol().value());
    }

    private static int wireFailure(String reason, RuntimeException failure, PrintWriter err) {
        err.println("transfer: " + reason + " " + failure.getMessage());
        if (failure.getSuppressed().length > 0) return 3;
        return switch (reason) {
            case "MALFORMED_XML", "UNSAFE_XML", "INVALID_WIRE", "DUPLICATE_ID" -> 1;
            case "UNSUPPORTED" -> 5;
            default -> 3;
        };
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
