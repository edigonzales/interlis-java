package guru.interlis.gui.application;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.execution.RunEvent;
import guru.interlis.repository.ContentDigest;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.FileSourceLoader;
import guru.interlis.repository.HostRepositories;
import guru.interlis.repository.LoadedSource;
import guru.interlis.repository.RepositoryResolutionOutcome;
import guru.interlis.repository.RepositoryResolveOptions;
import guru.interlis.repository.RepositorySearchStep;
import guru.interlis.repository.ResolutionLock;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.SourceLocator;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.workspace.AnalysisRequest;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.UpdateRejectedException;
import guru.interlis.workspace.UpdateRejectionCode;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceCompileRequest;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;
import guru.interlis.workspace.WorkspaceCapture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Synchronous background adapter. Each compile owns a fresh workspace and immutable result. */
public final class CompileService {
    private static final int MAX_EVENTS = 256;

    public Optional<LocalInput> inspect(Path path, Path workingDirectory, CancellationToken token) {
        token.throwIfCancellationRequested();
        if (Files.isDirectory(path)) {
            return Optional.empty();
        }
        try {
            LoadedSource loaded = load(new FileSourceLoader(workingDirectory), path, token);
            try (Workspace workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
                add(workspace, loaded);
                var syntax = SyntaxServices.create().parse(workspace.source(loaded.sourceId()).orElseThrow(),
                        ParseOptions.defaults(ParseMode.EDITOR), token);
                var models = syntax.root().models().stream().filter(model -> !model.name().isBlank())
                        .map(model -> new RootChoice.Local(model.name(), version(model.modelVersion()), loaded.sourceId()))
                        .distinct().toList();
                return Optional.of(new LocalInput(path, Optional.of(loaded.sourceId()), models, Optional.empty()));
            }
        } catch (SourceLoadException failure) {
            return Optional.of(new LocalInput(path, Optional.empty(), List.of(), Optional.of(problem(path, failure))));
        }
    }

    private static Optional<String> version(String version) {
        return version.isEmpty() ? Optional.empty() : Optional.of(version);
    }

    public CompileResult compile(CompileRequest request, CancellationToken token) {
        var state = new RunState(request);
        var defaults = WorkspaceConfiguration.defaults();
        var options = defaults.compilerOptions();
        var configuration = new WorkspaceConfiguration(new CompilerOptions(options.semanticProfile(),
                options.allowedLanguages(), request.options().warnings(), options.budget(), options.overlays()),
                defaults.resolutionPolicy(), defaults.resolverCatalog(), defaults.cacheLimits());
        var services = CompilerServices.defaults();
        var engine = CompilerEngines.create(new CompilerServices(state::observe, services.clock(), services.runContexts()));
        try (Workspace workspace = Workspaces.create(engine, configuration)) {
            try {
                return state.complete(execute(workspace, state, token));
            } catch (OperationCancelledException cancelled) {
                return state.complete(new CompileResult.Cancelled(cancelled.reason()));
            }
        }
    }

    private CompileResult.Outcome execute(Workspace workspace, RunState state, CancellationToken token) {
        var request = state.request;
        var loader = new FileSourceLoader(request.workingDirectory());
        var local = new LinkedHashSet<SourceId>();
        var problems = new ArrayList<LocalInput.Problem>();
        for (Path file : request.inputs().files()) {
            try {
                var loaded = load(loader, file, token);
                if (local.add(loaded.sourceId())) {
                    var digest = new ContentDigest("sha256", loaded.byteSha256());
                    add(workspace, loaded);
                    state.sources.put(loaded.sourceId(), new CompileResult.SourceSnapshot(
                            workspace.source(loaded.sourceId()).orElseThrow(), Optional.of(digest),
                            CompileResult.Origin.LOCAL_INPUT));
                }
            } catch (SourceLoadException failure) {
                problems.add(problem(file, failure));
            }
        }
        token.throwIfCancellationRequested();
        if (!problems.isEmpty()) {
            return new CompileResult.InputFailed(problems);
        }

        List<ModelSelector> roots = request.inputs().roots().isEmpty()
                ? local.stream().<ModelSelector>map(ModelSelector.BySource::new).toList()
                : request.inputs().roots().stream().<ModelSelector>map(RootChoice::selector).toList();
        var repositories = request.options().repositories();
        if (!repositories.sources().isEmpty()) {
            var host = HostRepositories.createObserved(request.workingDirectory(), request.options().cache(),
                    ignored -> { }, request.options().proxy());
            var resolved = host.closureService().resolve(workspace, new AnalysisRequest(workspace.currentRevision(), roots),
                    RepositoryResolveOptions.defaults(repositories), token);
            switch (resolved) {
                case RepositoryResolutionOutcome.Prepared(var update) -> {
                    state.lock = Optional.of(update.lock());
                    state.steps = update.lock().steps();
                    try {
                        update.apply(workspace);
                    } catch (UpdateRejectedException conflict) {
                        if (conflict.code() == UpdateRejectionCode.VERSION_CONFLICT) {
                            return new CompileResult.ResolutionStale();
                        }
                        throw conflict;
                    }
                    var digests = new LinkedHashMap<SourceId, ContentDigest>();
                    update.lock().models().values().forEach(pins ->
                            pins.forEach(pin -> digests.put(pin.source(), pin.digest())));
                    workspace.state().sourceRevisions().keySet().forEach(id -> {
                        if (!local.contains(id)) {
                            state.repositorySource(workspace.source(id).orElseThrow(), Optional.ofNullable(digests.get(id)));
                        }
                    });
                }
                case RepositoryResolutionOutcome.Rejected rejected -> {
                    state.steps = rejected.steps();
                    rejected.sources().values().forEach(source -> {
                        if (!local.contains(source.id())) {
                            state.repositorySource(source, Optional.empty());
                        }
                    });
                    return new CompileResult.RepositoryRejected(rejected);
                }
                case RepositoryResolutionOutcome.Cancelled(var reason) -> {
                    return new CompileResult.Cancelled(reason);
                }
                case RepositoryResolutionOutcome.Stale ignored -> {
                    return new CompileResult.ResolutionStale();
                }
            }
        }

        var revision = workspace.currentRevision();
        var compilation = workspace.compileCaptured(new WorkspaceCompileRequest(revision, roots), request.context(), token);
        state.capture = compilation.capture();
        return new CompileResult.Compiled(compilation.outcome());
    }

    private static LoadedSource load(FileSourceLoader loader, Path path, CancellationToken token) {
        token.throwIfCancellationRequested();
        if (Files.exists(path) && !Files.isRegularFile(path)) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE, "Input is not a regular file", null);
        }
        return loader.load(new SourceLocator(path.toString()), FetchPolicy.defaults(), token);
    }

    private static LocalInput.Problem problem(Path path, SourceLoadException failure) {
        return new LocalInput.Problem(path, failure.reason(), failure.getMessage());
    }

    private static void add(Workspace workspace, LoadedSource loaded) {
        workspace.update(new SourceUpdate(loaded.sourceId(), new SourceExpectation.Absent(), new SourceChange.Replace(loaded.text())));
    }

    private static final class RunState {
        private final CompileRequest request;
        private final long started = System.nanoTime();
        private final Map<SourceId, CompileResult.SourceSnapshot> sources = new LinkedHashMap<>();
        private final ArrayDeque<RunEvent> events = new ArrayDeque<>();
        private Optional<WorkspaceCapture> capture = Optional.empty();
        private Optional<ResolutionLock> lock = Optional.empty();
        private List<RepositorySearchStep> steps = List.of();

        private RunState(CompileRequest request) {
            this.request = request;
        }

        private void observe(RunEvent event) {
            if (events.size() == MAX_EVENTS) {
                events.removeFirst();
            }
            events.addLast(event);
        }

        private void repositorySource(SourceText text, Optional<ContentDigest> digest) {
            sources.put(text.id(), new CompileResult.SourceSnapshot(text, digest, CompileResult.Origin.REPOSITORY));
        }

        private CompileResult complete(CompileResult.Outcome outcome) {
            return new CompileResult(request, Instant.now(), Duration.ofNanos(System.nanoTime() - started),
                    outcome, sources, capture, lock, steps, List.copyOf(events));
        }
    }
}
