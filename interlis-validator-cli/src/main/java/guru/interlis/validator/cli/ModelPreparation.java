package guru.interlis.validator.cli;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CompiledSchema;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.FileSourceLoader;
import guru.interlis.repository.HostRepositories;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryClosureService;
import guru.interlis.repository.RepositoryProblem;
import guru.interlis.repository.RepositoryResolutionOutcome;
import guru.interlis.repository.RepositoryResolveOptions;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLocator;
import guru.interlis.workspace.AnalysisRequest;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceCompileRequest;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class ModelPreparation {
    private static final String DEFAULT_REPOSITORY = "https://models.interlis.ch/";

    private ModelPreparation() { }

    static CompiledSchema prepare(ValidatorOptions options, Path input, Path cwd,
                                  XtfHeaderProbe.Header header, CancellationToken token)
            throws IOException, ValidatorCommand.Failure {
        var models = options.models().isEmpty() ? header.models().keySet() : options.models();
        List<ModelSelector> roots = models.stream().distinct()
                .<ModelSelector>map(name -> new ModelSelector.ByName(name,
                        header.models().getOrDefault(name, java.util.Optional.empty()), java.util.Optional.empty()))
                .toList();
        var locations = options.repositories().isEmpty()
                ? List.of(input.getParent().toString(), DEFAULT_REPOSITORY) : options.repositories();
        var policy = FetchPolicy.defaults();
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            var repositories = configure(locations, cwd, workspace, policy, token);
            var access = HostRepositories.create(cwd, RepositoryCacheConfiguration.disabled(), ignored -> { });
            var service = access.closureService();
            if (options.has("--offline")) {
                var offline = new FetchPolicy(true, policy.maxBytes(), policy.connectTimeout(),
                        policy.readTimeout(), policy.requestTimeout());
                // Offline in the CLI prohibits HTTP transfers, while local model files remain readable.
                service = new RepositoryClosureService((locator, limits, cancellation) ->
                        locator.location().startsWith("file:")
                                ? access.loader().load(locator, limits, cancellation)
                                : access.loader().loadCached(locator, offline, cancellation), access.contentStore());
            }
            var result = service.resolve(workspace, new AnalysisRequest(workspace.currentRevision(), roots),
                    RepositoryResolveOptions.defaults(new RepositoryConfig(repositories, policy)), token);
            switch (result) {
                case RepositoryResolutionOutcome.Prepared prepared -> prepared.update().apply(workspace);
                case RepositoryResolutionOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
                case RepositoryResolutionOutcome.Rejected rejected -> {
                    int code = rejected.problems().stream().allMatch(problem ->
                            problem.reason() == RepositoryProblem.Reason.INVALID_SOURCE) ? 1 : 3;
                    throw new ValidatorCommand.Failure(code, "model resolution",
                            rejected.problems().stream().map(p -> p.reason() + " " + p.modelName()).toList());
                }
                case RepositoryResolutionOutcome.Stale ignored ->
                        throw new ValidatorCommand.Failure(3, "model resolution", "Model sources changed during resolution");
            }
            return switch (workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots), token)) {
                case CompilationOutcome.Succeeded compiled -> compiled.schema();
                case CompilationOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
                case CompilationOutcome.Failed failed -> throw new ValidatorCommand.Failure(3, "compilation",
                        failed.error().code().value() + " " + failed.failureId().value());
                case CompilationOutcome.Rejected rejected -> {
                    var reasons = rejected.rejection().reasons();
                    int code = reasons.contains(RejectionReason.SOURCE_ERRORS)
                            && !reasons.contains(RejectionReason.MISSING_INPUT)
                            && !reasons.contains(RejectionReason.ENVIRONMENT_FAILURE) ? 1 : 3;
                    throw new ValidatorCommand.Failure(code, "compilation",
                            rejected.analysis().diagnostics().stream()
                                    .map(d -> d.code().value() + " " + d.messageKey() + " " + d.arguments()
                                            + d.primaryRange().map(r -> " at " + r.source().value() + ":" + r.startOffset()).orElse(""))
                                    .toList());
                }
            };
        }
    }

    private static List<RepositorySource> configure(List<String> locations, Path cwd, Workspace workspace,
                                                     FetchPolicy policy, CancellationToken token)
            throws IOException, ValidatorCommand.Failure {
        var indexed = new ArrayList<RepositorySource>();
        var loaded = new HashSet<guru.interlis.source.SourceId>();
        var loader = new FileSourceLoader(cwd);
        var budget = workspace.configuration().compilerOptions().budget();
        long entriesRemaining = budget.maxSemanticWorkUnits();
        long sourceUnits = 0;
        for (var location : locations) {
            token.throwIfCancellationRequested();
            URI uri;
            try {
                uri = location.matches("(?i)(?:https?|file):.*") ? URI.create(location.replace(" ", "%20"))
                        : cwd.resolve(location).toUri();
                if (uri.isOpaque() || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
                    throw new IllegalArgumentException();
                }
                if ("file".equalsIgnoreCase(uri.getScheme())) uri = Path.of(uri).toAbsolutePath().normalize().toUri();
            } catch (IllegalArgumentException invalid) {
                throw new ValidatorCommand.Failure(2, "options", "Invalid repository location");
            }
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                var path = Path.of(uri);
                if (Files.isDirectory(path) && Files.notExists(path.resolve("ilimodels.xml"))) {
                    try (var entries = Files.list(path)) {
                        var candidates = new ArrayList<Path>();
                        for (var iterator = entries.iterator(); iterator.hasNext();) {
                            token.throwIfCancellationRequested();
                            if (--entriesRemaining < 0) throw new IOException("Model directory scan exceeds the work limit");
                            var candidate = iterator.next();
                            if (candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ili")
                                    && Files.isRegularFile(candidate)) candidates.add(candidate);
                        }
                        for (var candidate : candidates.stream().sorted().toList()) {
                            token.throwIfCancellationRequested();
                            var source = loader.load(new SourceLocator(candidate.toUri().toString()), policy, token);
                            if (loaded.add(source.sourceId())) {
                                sourceUnits += source.text().length();
                                if (source.text().length() > budget.maxSourceUtf16Length()
                                        || sourceUnits > budget.maxTotalSourceUtf16Length()) {
                                    throw new IOException("Local model sources exceed the compiler source limit");
                                }
                                workspace.update(new SourceUpdate(source.sourceId(), new SourceExpectation.Absent(),
                                        new SourceChange.Replace(source.text())));
                            }
                        }
                    }
                    continue;
                }
            }
            if (uri.getPath().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                indexed.add(new RepositorySource(new RepositoryBase(uri.resolve(".")), new SourceLocator(uri.toString())));
            } else {
                var directory = uri.toString().endsWith("/") ? uri : URI.create(uri + "/");
                indexed.add(new RepositorySource(new RepositoryBase(directory),
                        new SourceLocator(directory.resolve("ilimodels.xml").toString())));
            }
        }
        return List.copyOf(indexed);
    }
}
