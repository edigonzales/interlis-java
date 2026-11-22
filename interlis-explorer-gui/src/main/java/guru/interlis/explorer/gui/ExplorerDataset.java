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
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.transfer.TransferValue;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    List<Map.Entry<String, String>> attributes(TransferObject object) {
        return plan.object(object.type()).orElseThrow().slots().stream()
                .filter(AttributeSlot.class::isInstance)
                .map(AttributeSlot.class::cast)
                .map(slot -> Map.entry(slot.name().declared().last(),
                        object.value(slot.symbol()).map(value -> displayValue(slot, value)).orElse("Absent")))
                .toList();
    }

    private String displayValue(AttributeSlot slot, TransferValue value) {
        return switch (value) {
            case TransferValue.Undefined ignored -> "Undefined";
            case TransferValue.Text text -> text.value();
            case TransferValue.BooleanValue flag -> flag.value() ? "TRUE" : "FALSE";
            case TransferValue.Enumeration enumeration -> String.join(".", enumeration.path());
            case TransferValue.NumberValue number -> number.lexeme();
            case TransferValue.Oid oid -> oid.lexeme();
            case TransferValue.ClassName name -> name.name().toString();
            case TransferValue.AttributePath path -> path.name().toString();
            case TransferValue.Binary binary -> "BINARY (" + binary.size() + " bytes)";
            case TransferValue.XmlFragment ignored -> "XML";
            case TransferValue.Structured structure -> plan.object(structure.type()).orElseThrow().name().declared().toString();
            case TransferValue.Repeated repeated -> {
                var collection = (CollectionType) plan.type(slot.effectiveType()).known().orElseThrow();
                var ref = new TypeRef(plan.snapshotId(), collection.elementType());
                var element = new TypeRelations(plan.schema().semanticGraph()).resolveAliases(ref).known()
                        .map(type -> new TypeRef(plan.snapshotId(), type.effectiveType())).orElse(ref);
                yield repeated.kind() + " OF " + typeName(plan.type(element).known().orElseThrow())
                        + " (" + repeated.values().size() + ")";
            }
            case TransferValue.Reference reference -> {
                var target = reference.target();
                String label = (target instanceof TransferIdentity.TransferId ? "TID " : "OID ") + target.value();
                yield reference.targetBasket().map(basket -> label + " (BID " + basket.value() + ")").orElse(label);
            }
            case TransferGeometry.Coordinate ignored -> "COORD";
            case TransferGeometry.MultiCoordinate ignored -> "MULTICOORD";
            case TransferGeometry ignored -> typeName(plan.type(slot.effectiveType()).known().orElseThrow());
        };
    }

    private String typeName(TypeDescriptor type) {
        return switch (type) {
            case CoordinateType coordinate -> coordinate.multiCoordinate() ? "MULTICOORD" : "COORD";
            case GeometryType geometry -> geometry.geometryKind().name();
            case StructureValueType structure -> structure.target()
                    .map(id -> plan.object(new SymbolRef(plan.snapshotId(), id)).orElseThrow())
                    .map(descriptor -> descriptor.name().declared().toString()).orElse("ANYSTRUCTURE");
            default -> type.kind().name();
        };
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
