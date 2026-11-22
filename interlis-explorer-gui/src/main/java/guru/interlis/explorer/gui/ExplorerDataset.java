package guru.interlis.explorer.gui;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.explorer.DuckDbStore;
import guru.interlis.explorer.DuckDbStore.StoredObject;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Owns the transfer plan and temporary store for one opened dataset. */
record ExplorerDataset(TransferPlan plan, DuckDbStore store, Path directory) implements AutoCloseable {
    static final int OBJECT_LIMIT = 100;

    static ExplorerDataset open(List<Path> models, Path xtf, CancellationToken cancellation)
            throws IOException, SQLException {
        var plan = compile(models, cancellation);
        cancellation.throwIfCancellationRequested();
        var directory = Files.createTempDirectory("interlis-explorer-");
        DuckDbStore store = null;
        try {
            store = DuckDbStore.open(directory.resolve("transfer.duckdb"), plan);
            try (var input = Files.newInputStream(xtf);
                 var reader = new Xtf24Reader(plan, input, Optional.of(sourceId(xtf)))) {
                store.importTransfer(reader, cancellation);
            }
            return new ExplorerDataset(plan, store, directory);
        } catch (IOException | SQLException | RuntimeException | Error failure) {
            try {
                release(store, directory);
            } catch (IOException | SQLException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static TransferPlan compile(List<Path> models, CancellationToken cancellation) throws IOException {
        var sources = new LinkedHashMap<SourceId, SourceText>();
        for (var file : models) {
            cancellation.throwIfCancellationRequested();
            var path = file.toRealPath();
            var id = sourceId(path);
            sources.put(id, new SourceText(id, new SourceRevision(1), Files.readString(path)));
        }
        var roots = sources.keySet().stream()
                .<ModelSelector>map(ModelSelector.BySource::new).toList();
        var request = new CompilationRequest(roots, SourceSet.of(sources), CompilerOptions.defaults());
        var schema = switch (CompilerEngines.create().compile(request, cancellation)) {
            case CompilationOutcome.Succeeded success -> success.schema();
            case CompilationOutcome.Rejected rejected -> {
                var formatter = new DiagnosticMessageFormatter();
                var diagnostics = rejected.analysis().diagnostics().stream()
                        .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                        .map(diagnostic -> formatter.format(diagnostic, DiagnosticLanguage.EN).text())
                        .limit(10).collect(Collectors.joining("\n"));
                throw new IOException("Model compilation rejected. " + rejected.rejection().reasons()
                        + "\n" + diagnostics);
            }
            case CompilationOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
            case CompilationOutcome.Failed failed -> throw new IOException("Internal compiler error "
                    + failed.failureId() + ". "
                    + new DiagnosticMessageFormatter().format(failed.error(), DiagnosticLanguage.EN).text());
        };
        return switch (TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)) {
            case TransferPlanningResult.Ready ready -> ready.plan();
            case TransferPlanningResult.Unsupported unsupported -> throw new IOException(
                    unsupported.issues().stream().limit(10)
                            .map(issue -> issue.path() + " (" + issue.feature() + ")")
                            .collect(Collectors.joining("\n", "Unsupported transfer model.\n", "")));
        };
    }

    private static SourceId sourceId(Path file) {
        return new SourceId(file.toUri().toString());
    }

    List<StoredObject> objects(ObjectDescriptor type) throws SQLException {
        return store.objects(type.name().declared(), OBJECT_LIMIT);
    }

    List<StoredObject> objectsIncluding(ObjectDescriptor type, StoredObject selected) throws SQLException {
        var objects = objects(type);
        if (objects.stream().anyMatch(object -> object.position() == selected.position())) {
            return objects;
        }
        var visible = new ArrayList<StoredObject>();
        visible.add(selected);
        visible.addAll(objects.subList(0, Math.min(objects.size(), OBJECT_LIMIT - 1)));
        return List.copyOf(visible);
    }

    @Override
    public void close() throws IOException, SQLException {
        release(store, directory);
    }

    private static void release(DuckDbStore store, Path directory) throws IOException, SQLException {
        try (store) {
            // Close the database before removing its files.
        } finally {
            List<Path> paths;
            try (var files = Files.walk(directory)) {
                paths = files.sorted(Comparator.reverseOrder()).toList();
            }
            for (var path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
