package guru.interlis.validator.cli;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.LanguageVersion;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.ContentDigest;
import guru.interlis.repository.LoadedSource;
import guru.interlis.repository.SourceLoader;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.HostRepositories;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryCacheRequest;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryProblem;
import guru.interlis.repository.RepositoryResolutionOutcome;
import guru.interlis.repository.RepositoryResolveOptions;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLocator;
import guru.interlis.validation.InputRole;
import guru.interlis.workspace.AnalysisRequest;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.workspace.WorkspaceCompileRequest;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.Locale;

final class ModelPreparation {
    private static final String DEFAULT_REPOSITORY = "https://models.interlis.ch/";

    private ModelPreparation() { }

    record Prepared(CompiledSchema schema, List<String> primaryModels) { }

    static Prepared prepare(ValidatorOptions options, List<ValidationInput> inputs, Path cwd,
                            CancellationToken token) throws ValidatorCommand.Failure {
        var declarations = new LinkedHashMap<String, Optional<String>>();
        var primary = new LinkedHashSet<>(options.models());
        var directories = new LinkedHashSet<String>();
        for (var input : inputs) {
            token.throwIfCancellationRequested();
            directories.add(input.path().getParent().toString());
            for (var entry : input.header().models().entrySet()) {
                var previous = declarations.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && previous.isPresent() && entry.getValue().isPresent()
                        && !previous.equals(entry.getValue())) {
                    throw new ValidatorCommand.Failure(1, "header",
                            "Conflicting model versions for " + entry.getKey() + " at " + input.path());
                }
                if (previous != null && previous.isEmpty()) declarations.put(entry.getKey(), entry.getValue());
                if (options.models().isEmpty() && input.role() == InputRole.PRIMARY) primary.add(entry.getKey());
            }
        }
        var closureNames = new LinkedHashSet<>(primary);
        for (var input : inputs) {
            if (input.role() == InputRole.REFERENCE) closureNames.addAll(input.header().models().keySet());
        }
        List<ModelSelector> roots = closureNames.stream()
                .<ModelSelector>map(name -> new ModelSelector.ByName(name,
                        options.models().contains(name) ? Optional.empty() : declarations.getOrDefault(name, Optional.empty()),
                        Optional.empty()))
                .toList();
        directories.add(DEFAULT_REPOSITORY);
        var locations = options.repositories().isEmpty() ? List.copyOf(directories) : options.repositories();
        var policy = FetchPolicy.defaults();
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            var access = HostRepositories.create(cwd,
                    RepositoryCacheConfiguration.defaults(Path.of(System.getProperty("user.home"))), ignored -> { });
            var offline = new FetchPolicy(true, policy.maxBytes(), policy.connectTimeout(),
                    policy.readTimeout(), policy.requestTimeout());
            var loader = new LocalDirectories(access.loader(), options.has("--offline") ? offline : policy);
            var repositories = configure(locations, cwd, token);
            var service = access.closureService(loader);
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
                case CompilationOutcome.Succeeded compiled -> new Prepared(compiled.schema(), List.copyOf(primary));
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

    private static List<RepositorySource> configure(List<String> locations, Path cwd, CancellationToken token)
            throws ValidatorCommand.Failure {
        var indexed = new ArrayList<RepositorySource>();
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

    /** Presents a loose directory as an in-memory index at its normal lookup position. */
    private static final class LocalDirectories implements SourceLoader {
        private final SourceLoader delegate;
        private final FetchPolicy remotePolicy;
        private final Map<SourceLocator, LoadedSource> indexes = new HashMap<>();
        private final Map<SourceLocator, LoadedSource> files = new HashMap<>();
        private final AnalysisBudget budget = AnalysisBudget.defaults();
        private long scanRemaining = budget.maxSemanticWorkUnits();
        private long sourceUnits;

        LocalDirectories(SourceLoader delegate, FetchPolicy remotePolicy) {
            this.delegate = delegate;
            this.remotePolicy = remotePolicy;
        }

        @Override
        public LoadedSource load(SourceLocator locator, FetchPolicy limits, CancellationToken token) {
            return load(locator, limits, RepositoryCacheRequest.metadata(), token);
        }

        @Override
        public LoadedSource load(SourceLocator locator, FetchPolicy limits,
                                 RepositoryCacheRequest request, CancellationToken token) {
            token.throwIfCancellationRequested();
            if (!locator.location().startsWith("file:")) {
                return remotePolicy.offline() ? delegate.loadCached(locator, remotePolicy, token)
                        : delegate.load(locator, limits, request, token);
            }
            var path = Path.of(URI.create(locator.location()));
            if (path.getFileName() != null && path.getFileName().toString().equals("ilimodels.xml") && Files.notExists(path)
                    && Files.isDirectory(path.getParent())) {
                return indexes.computeIfAbsent(locator, key -> index(key, path.getParent(), limits, token));
            }
            var cached = files.get(locator);
            return cached == null ? delegate.load(locator, limits, request, token) : cached;
        }

        private LoadedSource index(SourceLocator locator, Path directory, FetchPolicy limits, CancellationToken token) {
            var candidates = new ArrayList<Path>();
            try (var entries = Files.list(directory)) {
                for (var it = entries.iterator(); it.hasNext();) {
                    token.throwIfCancellationRequested();
                    if (--scanRemaining < 0) throw tooLarge();
                    var file = it.next();
                    if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ili")
                            && Files.isRegularFile(file)) candidates.add(file);
                }
            } catch (IOException | UncheckedIOException failure) {
                throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE, "Cannot scan local model directory", failure);
            }
            var parser = SyntaxServices.create();
            var xml = new StringBuilder("<RepositoryIndex>");
            var parseOptions = ParseOptions.defaults(ParseMode.STRICT);
            for (var file : candidates.stream().sorted().toList()) {
                token.throwIfCancellationRequested();
                var sourceLocator = new SourceLocator(file.toUri().toString());
                var source = files.computeIfAbsent(sourceLocator, key -> {
                    var loaded = delegate.load(key, limits, token);
                    sourceUnits += loaded.text().length();
                    if (loaded.text().length() > budget.maxSourceUtf16Length()
                            || sourceUnits > budget.maxTotalSourceUtf16Length()) throw tooLarge();
                    return loaded;
                });
                var syntax = parser.parse(new SourceText(source.sourceId(), new SourceRevision(0), source.text()),
                        parseOptions, token);
                for (var model : syntax.root().models()) {
                    if (model.name().isBlank() || model.modelVersion().isBlank()) continue;
                    xml.append("<ModelMetadata><Name>").append(xmlText(model.name()))
                            .append("</Name><SchemaLanguage>")
                            .append(model.languageVersion() == LanguageVersion.ILI23 ? "ili2_3" : "ili2_4")
                            .append("</SchemaLanguage><File>")
                            .append(xmlText(directory.toUri().relativize(file.toUri()).toASCIIString()))
                            .append("</File><Version>").append(xmlText(model.modelVersion()))
                            .append("</Version></ModelMetadata>");
                    if (xml.length() > limits.maxBytes()) throw tooLarge();
                }
            }
            var text = xml.append("</RepositoryIndex>").toString();
            var bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > limits.maxBytes()) throw tooLarge();
            return new LoadedSource(new SourceId(locator.location()), text, ContentDigest.sha256(bytes).value(), "http-no-store");
        }

        private static SourceLoadException tooLarge() {
            return new SourceLoadException(SourceLoadException.Reason.TOO_LARGE,
                    "Local model directory exceeds the source budget", null);
        }

        private static String xmlText(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

}
