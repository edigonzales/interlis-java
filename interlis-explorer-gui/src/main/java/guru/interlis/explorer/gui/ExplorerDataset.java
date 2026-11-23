package guru.interlis.explorer.gui;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.explorer.DuckDbStore;
import guru.interlis.explorer.DuckDbStore.StoredObject;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.HostRepositories;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryResolutionOutcome;
import guru.interlis.repository.RepositoryResolveOptions;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.SourceLocator;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.workspace.AnalysisRequest;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceCompileRequest;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;
import guru.interlis.xtf24.Xtf24Limits;
import guru.interlis.xtf24.Xtf24ReadProfile;
import guru.interlis.xtf24.Xtf24Reader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** Owns the transfer plan and temporary store for one opened dataset. */
record ExplorerDataset(TransferPlan plan, DuckDbStore store, Path directory) implements AutoCloseable {
    static final int OBJECT_LIMIT = 100;

    static ExplorerDataset open(List<Path> models, Path xtf, CancellationToken cancellation)
            throws IOException, SQLException {
        return open(models, xtf, cancellation, false);
    }

    static ExplorerDataset openWithSpatial(List<Path> models, Path xtf, CancellationToken cancellation)
            throws IOException, SQLException {
        return open(models, xtf, cancellation, true);
    }

    private static ExplorerDataset open(List<Path> models, Path xtf, CancellationToken cancellation, boolean spatial)
            throws IOException, SQLException {
        var plan = compile(models, xtf, cancellation);
        cancellation.throwIfCancellationRequested();
        var directory = Files.createTempDirectory("interlis-explorer-");
        var database = directory.resolve("transfer.duckdb");
        try {
            try (var store = spatial ? DuckDbStore.openWithSpatial(database, plan) : DuckDbStore.open(database, plan);
                 var input = Files.newInputStream(xtf);
                 var reader = new Xtf24Reader(plan, input, Optional.of(sourceId(xtf)), Xtf24Limits.defaults(),
                         Xtf24ReadProfile.DMAV_1_1)) {
                store.importTransfer(reader, cancellation);
            }
            cancellation.throwIfCancellationRequested();
            return new ExplorerDataset(plan, DuckDbStore.openReadOnly(database, plan), directory);
        } catch (IOException | SQLException | RuntimeException | Error failure) {
            try {
                release(null, directory);
            } catch (IOException | SQLException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static TransferPlan compile(List<Path> models, Path xtf, CancellationToken cancellation) throws IOException {
        List<String> headerModels;
        try (var input = Files.newInputStream(xtf)) {
            headerModels = Xtf24Reader.readModelNames(input, cancellation);
        }
        var location = Path.of("").toAbsolutePath();
        var repositories = HostRepositories.createObserved(location,
                RepositoryCacheConfiguration.defaults(Path.of(System.getProperty("user.home"))), ignored -> {});
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            var roots = new ArrayList<ModelSelector>();
            var directories = new LinkedHashSet<Path>();
            for (var model : models) {
                cancellation.throwIfCancellationRequested();
                var path = model.toRealPath();
                var source = addModelSource(path, workspace, repositories, cancellation);
                var selector = new ModelSelector.BySource(source);
                if (!roots.contains(selector)) {
                    roots.add(selector);
                }
                directories.add(path.getParent());
            }
            boolean discoveredNeighbours = missingImports(workspace, roots, cancellation);
            if (discoveredNeighbours) {
                addNeighbouringSources(directories, workspace, repositories, cancellation);
            }
            resolveModelImports(workspace, roots, repositories, cancellation);
            var plan = compile(workspace, roots, cancellation);
            var namedModels = new LinkedHashSet<String>();
            for (var model : plan.models()) {
                namedModels.add(model.name().declared().last());
                var canonical = plan.schema().semanticGraph().symbol(model.name().canonical().symbol()).orElseThrow();
                namedModels.add(canonical.name().value());
            }
            var additionalRoots = headerModels.stream().filter(name -> !namedModels.contains(name))
                    .map(name -> new ModelSelector.ByName(name, Optional.empty(), Optional.empty())).toList();
            if (additionalRoots.isEmpty()) {
                return plan;
            }
            if (!discoveredNeighbours) {
                addNeighbouringSources(directories, workspace, repositories, cancellation);
            }
            roots.addAll(additionalRoots);
            resolveModelImports(workspace, roots, repositories, cancellation);
            return compile(workspace, roots, cancellation);
        } catch (SourceLoadException failure) {
            throw new IOException("Cannot read a model source: " + failure.getMessage(), failure);
        }
    }

    private static TransferPlan compile(Workspace workspace, List<ModelSelector> roots, CancellationToken cancellation)
            throws IOException {
        return plan(workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots), cancellation));
    }

    private static void addNeighbouringSources(Iterable<Path> directories, Workspace workspace,
            HostRepositories.Access repositories, CancellationToken cancellation) throws IOException {
        for (var directory : directories) {
            cancellation.throwIfCancellationRequested();
            try (var entries = Files.list(directory)) {
                var neighbours = entries.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith(".ili") && Files.isRegularFile(path)).sorted().iterator();
                while (neighbours.hasNext()) {
                    cancellation.throwIfCancellationRequested();
                    addModelSource(neighbours.next().toRealPath(), workspace, repositories, cancellation);
                }
            }
        }
    }

    private static SourceId addModelSource(Path path, Workspace workspace, HostRepositories.Access repositories,
            CancellationToken cancellation) {
        var id = sourceId(path);
        if (workspace.source(id).isEmpty()) {
            var source = repositories.loader().load(new SourceLocator(id.value()), FetchPolicy.defaults(), cancellation);
            workspace.update(new SourceUpdate(id, new SourceExpectation.Absent(), new SourceChange.Replace(source.text())));
        }
        return id;
    }

    private static boolean missingImports(Workspace workspace, List<ModelSelector> roots, CancellationToken cancellation) {
        var request = new AnalysisRequest(workspace.currentRevision(), roots);
        return workspace.capture(request, cancellation).resolution().pendingFetches().stream()
                .anyMatch(fetch -> !fetch.modelName().isEmpty());
    }

    private static void resolveModelImports(Workspace workspace, List<ModelSelector> roots,
            HostRepositories.Access repositories, CancellationToken cancellation) throws IOException {
        if (!missingImports(workspace, roots, cancellation)) {
            return;
        }
        var configured = new RepositoryConfig(List.of(modelRepository("https://models.geo.admin.ch/"),
                modelRepository("https://models.interlis.ch/")), FetchPolicy.defaults());
        var request = new AnalysisRequest(workspace.currentRevision(), roots);
        switch (repositories.closureService().resolve(workspace, request, RepositoryResolveOptions.defaults(configured),
                cancellation)) {
            case RepositoryResolutionOutcome.Prepared prepared -> prepared.update().apply(workspace);
            case RepositoryResolutionOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
            case RepositoryResolutionOutcome.Rejected rejected -> throw new IOException(rejected.problems().stream()
                    .limit(10)
                    .map(problem -> problem.reason() + " " + problem.modelName())
                    .collect(Collectors.joining("\n", "Model resolution rejected.\n", "")));
            case RepositoryResolutionOutcome.Stale ignored -> throw new IOException("Model sources changed during resolution.");
        }
    }

    private static RepositorySource modelRepository(String url) {
        var base = URI.create(url);
        return new RepositorySource(new RepositoryBase(base), new SourceLocator(base.resolve("ilimodels.xml").toString()));
    }

    private static TransferPlan plan(CompilationOutcome outcome) throws IOException {
        var schema = switch (outcome) {
            case CompilationOutcome.Succeeded success -> success.schema();
            case CompilationOutcome.Rejected rejected -> {
                var formatter = new DiagnosticMessageFormatter();
                var diagnostics = rejected.analysis().diagnostics().stream()
                        .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                        .map(diagnostic -> diagnostic.primaryRange().map(range -> range.source().value() + "\n").orElse("")
                                + formatter.format(diagnostic, DiagnosticLanguage.EN).text())
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
