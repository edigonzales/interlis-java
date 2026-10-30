package guru.interlis.repository;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import guru.interlis.workspace.resolution.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Explicit JVM discovery loop over the shared syntax service and pure resolver.
 * IO happens outside the user's workspace monitor. All downloaded materials are
 * staged privately; only PreparedRepositoryUpdate.apply can install them, with
 * one atomic revision check. No analysis or schema is certified by this service.
 * Instances have no run-local mutable state and may be used concurrently.
 */
public final class RepositoryClosureService {
    private final SourceLoader loader;
    private final ContentStore store;

    public RepositoryClosureService(SourceLoader loader, ContentStore store) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Prepares one requested closure. Does not mutate workspace sources,
     * configuration or accepted analysis. Loaded bytes, identities, actual parsed
     * headers and published MD5 (when supplied) must agree. Unqualified versions
     * in one priority group remain ambiguous. Online inputs are reread once at
     * the end to detect changes during discovery; this is an optimistic read
     * window, not a remote filesystem transaction. Later workspace edits reject
     * application of the entire batch. Cancellation never returns a partial batch.
     */
    public RepositoryResolutionOutcome resolve(Workspace workspace, AnalysisRequest request,
                                               RepositoryResolveOptions options, CancellationToken token) {
        Objects.requireNonNull(workspace, "workspace"); Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options"); Objects.requireNonNull(token, "token");
        var expected = request.expectedWorkspaceRevision();
        try {
            token.throwIfCancellationRequested();
            var originalConfig = workspace.configuration();
            var initial = workspace.capture(request, token);
            if (!workspace.currentRevision().equals(expected)) return stale(workspace, expected);
            var selections = new LinkedHashMap<>(originalConfig.resolutionPolicy().modelSelections());
            var ranks = new LinkedHashMap<>(originalConfig.resolutionPolicy().sourcePriorities());
            var pins = new LinkedHashMap<>(options.modelPins());
            options.lock().ifPresent(lock -> lock.models().forEach((name, group) -> {
                var explicit = pins.get(name);
                if (explicit != null && !group.contains(explicit)) reject(RepositoryProblem.Reason.LOCK_MISMATCH, name);
                if (group.size() == 1) pins.putIfAbsent(name, group.getFirst());
            }));
            pins.forEach((name, pin) -> {
                var previous = selections.putIfAbsent(name, pin.selector());
                if (previous != null && !compatible(previous, pin.selector())) reject(options.modelPins().containsKey(name) ? RepositoryProblem.Reason.PIN_MISMATCH : RepositoryProblem.Reason.LOCK_MISMATCH, name);
                selections.put(name, pin.selector());
            });
            var bytePins = new LinkedHashMap<>(options.sourcePins());
            pins.values().forEach(pin -> addPin(bytePins, pin.source(), pin.digest()));
            options.lock().ifPresent(lock -> lock.models().values().forEach(group -> group.forEach(pin -> addPin(bytePins, pin.source(), pin.digest()))));
            options.lock().ifPresent(lock -> lock.indexes().forEach((id, digest) -> addPin(bytePins, id, digest)));
            var verified = new VerifiedLoader(bytePins, options.lock(), options.repositories().fetchPolicy(), token);
            RepositoryCatalog catalog = null;
            var indexDigests = new LinkedHashMap<SourceId, ContentDigest>();
            options.lock().ifPresent(lock -> indexDigests.putAll(lock.indexes()));
            if (options.lock().isEmpty()) originalConfig.resolverCatalog().entries().forEach((key, digest) -> {
                if (key.startsWith("index:") && digest.matches("[0-9a-f]{64}"))
                    indexDigests.put(new SourceId(key.substring(6)), new ContentDigest("sha256", digest));
            });
            var resolverCatalog = originalConfig.resolverCatalog();
            var added = new LinkedHashMap<SourceId, LoadedSource>();
            var selectedPins = new LinkedHashMap<String, List<ModelPin>>();
            var trace = new ArrayList<String>();
            var configuration = configuration(originalConfig, selections, ranks, resolverCatalog);
            try (var staging = Workspaces.create(CompilerEngines.create(), configuration)) {
                for (var source : initial.request().sources().sources().values()) {
                    staging.update(update(source.id(), source.text()));
                }
                long steps = 0;
                while (true) {
                    token.throwIfCancellationRequested();
                    if (!workspace.currentRevision().equals(expected)) return stale(workspace, expected);
                    if (++steps > originalConfig.compilerOptions().budget().maxSemanticWorkUnits())
                        throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
                    var capture = staging.capture(new AnalysisRequest(staging.currentRevision(), request.roots()), token);
                    if (capture.resolution().complete()) {
                        recordSelected(capture, ranks.keySet(), pins, options.sourcePins(), options.lock(), selectedPins);
                        verified.verifyUnchanged();
                        if (!workspace.currentRevision().equals(expected)) return stale(workspace, expected);
                        trace.addAll(capture.resolution().trace().decisions());
                        var lock = new ResolutionLock(selectedPins, indexDigests);
                        var updates = added.values().stream().map(s -> update(s.sourceId(), s.text())).toList();
                        return new RepositoryResolutionOutcome.Prepared(new PreparedRepositoryUpdate(workspace, expected,
                                updates, configuration, originalConfig.fullFingerprint(), lock, trace));
                    }
                    if (capture.resolution().pendingFetches().isEmpty()) reject(RepositoryProblem.Reason.INVALID_SOURCE, "");
                    if (catalog == null) {
                        catalog = new RepositoryClient(verified, new RepositoryIndexReader()).catalog(options.repositories(), token);
                        if (!catalog.failures().isEmpty()) reject(RepositoryProblem.Reason.INDEX_FAILURE, "");
                        indexDigests.clear();
                        for (var source : options.repositories().sources()) {
                            var id = new SourceId(source.indexLocator().location());
                            var loaded = verified.seen.get(id);
                            if (loaded == null) reject(RepositoryProblem.Reason.INDEX_FAILURE, "");
                            if (options.lock().isPresent() && !options.lock().orElseThrow().indexes().containsKey(id))
                                reject(RepositoryProblem.Reason.LOCK_MISMATCH, "");
                            indexDigests.put(id, new ContentDigest("sha256", loaded.byteSha256()));
                        }
                        var entries = new TreeMap<String, String>();
                        entries.put("repository-catalog", catalog.revision());
                        indexDigests.forEach((id, digest) -> entries.put("index:" + id.value(), digest.value()));
                        resolverCatalog = new ResolverCatalog(entries);
                        configuration = configuration(originalConfig, selections, ranks, resolverCatalog);
                        staging.configure(configuration, staging.currentRevision());
                        continue;
                    }
                    RepositoryCatalog available = catalog;
                    boolean progress = false;
                    for (var pending : capture.resolution().pendingFetches()) {
                        token.throwIfCancellationRequested();
                        if (pending.modelName().isEmpty()) reject(RepositoryProblem.Reason.NOT_FOUND, "");
                        var query = capture.resolution().negativeDependencies().stream()
                                .map(ResolutionDependency::selector).filter(q -> q instanceof ModelSelector.ByName)
                                .map(q -> (ModelSelector.ByName) q).filter(q -> q.modelName().equals(pending.modelName())
                                        && q.modelVersion().equals(pending.modelVersion())).findFirst()
                                .orElse(new ModelSelector.ByName(pending.modelName(), pending.modelVersion(), Optional.empty()));
                        var pin = pins.get(query.modelName());
                        if (pin != null && !compatible(query, pin.selector())) reject(options.modelPins().containsKey(query.modelName()) ? RepositoryProblem.Reason.PIN_MISMATCH : RepositoryProblem.Reason.LOCK_MISMATCH, query.modelName());
                        if (capture.resolution().negativeDependencies().stream().anyMatch(d ->
                                d.selector().equals(query) && d.kind() == ResolutionDependency.Kind.AMBIGUOUS))
                            reject(RepositoryProblem.Reason.AMBIGUOUS, query.modelName());
                        steps += available.candidates().size();
                        if (steps > originalConfig.compilerOptions().budget().maxSemanticWorkUnits())
                            throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
                        if (options.lock().isPresent() && !options.lock().orElseThrow().models().containsKey(query.modelName()))
                            reject(RepositoryProblem.Reason.LOCK_MISMATCH, query.modelName());
                        var matching = available.candidates().stream()
                                .filter(c -> c.metadata().name().equals(query.modelName()))
                                .filter(c -> query.modelVersion().filter(v -> !v.equals(c.metadata().version())).isEmpty())
                                .filter(c -> query.sourceHint().filter(id -> !id.value().equals(c.metadata().location().toString())).isEmpty())
                                .filter(c -> Set.of("ili2_3", "ili2_4").contains(c.metadata().schemaLanguage())).toList();
                        if (matching.isEmpty()) {
                            boolean otherVersions = catalog.candidates().stream().anyMatch(c -> c.metadata().name().equals(query.modelName())
                                    && query.sourceHint().filter(id -> !id.value().equals(c.metadata().location().toString())).isEmpty());
                            reject(otherVersions ? RepositoryProblem.Reason.VERSION_MISMATCH : RepositoryProblem.Reason.NOT_FOUND, query.modelName());
                        }
                        if (options.lock().isPresent()) {
                            var allowed = options.lock().orElseThrow().models().get(query.modelName());
                            matching = matching.stream().filter(c -> allowed.stream().anyMatch(p -> p.modelVersion().equals(c.metadata().version())
                                    && p.source().value().equals(c.metadata().location().toString()))).toList();
                            if (matching.isEmpty()) reject(RepositoryProblem.Reason.LOCK_MISMATCH, query.modelName());
                        }
                        int rank = matching.stream().mapToInt(ModelCandidate::priority).min().orElseThrow();
                        var winners = matching.stream().filter(c -> c.priority() == rank)
                                .map(ModelCandidate::metadata).distinct().toList();
                        if (winners.size() != 1) throw new Rejection(new RepositoryProblem(RepositoryProblem.Reason.AMBIGUOUS,
                                query.modelName(), winners.stream().map(m -> new SourceId(m.location().toString())).distinct().toList(), Optional.empty()));
                        var metadata = winners.getFirst();
                        var id = new SourceId(metadata.location().toString());
                        if (options.lock().isPresent() && !options.lock().orElseThrow().models().containsKey(query.modelName()))
                            reject(RepositoryProblem.Reason.LOCK_MISMATCH, query.modelName());
                        if (staging.source(id).isPresent()) {
                            // A saved download must never overwrite an open text at the same identity.
                            reject(RepositoryProblem.Reason.INVALID_SOURCE, query.modelName());
                        }
                        var loaded = verified.load(new SourceLocator(id.value()), options.repositories().fetchPolicy(), token);
                        metadata.md5().ifPresent(md5 -> {
                            try {
                                String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
                                        .digest(loaded.text().getBytes(StandardCharsets.UTF_8)));
                                if (!md5.equalsIgnoreCase(actual)) reject(RepositoryProblem.Reason.PIN_MISMATCH, metadata.name());
                            } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
                        });
                        staging.update(update(id, loaded.text()));
                        var checked = staging.capture(new AnalysisRequest(staging.currentRevision(), request.roots()), token);
                        var syntax = checked.reuse().previousSyntax().get(id);
                        var actual = syntax.root().models().stream().filter(m -> m.name().equals(metadata.name())
                                && m.modelVersion().equals(metadata.version()) && schemaLanguage(m.languageVersion()).equals(metadata.schemaLanguage())).toList();
                        if (actual.isEmpty() && syntax.root().models().stream().anyMatch(m -> m.name().equals(metadata.name())
                                && !m.modelVersion().equals(metadata.version()))) reject(RepositoryProblem.Reason.VERSION_MISMATCH, metadata.name());
                        if (actual.size() != 1 || !syntax.coverage().parseComplete() || !syntax.consumedEntireInput())
                            reject(RepositoryProblem.Reason.INVALID_SOURCE, metadata.name());
                        added.put(id, loaded);
                        ranks.put(id, rank);
                        configuration = configuration(originalConfig, selections, ranks, resolverCatalog);
                        staging.configure(configuration, staging.currentRevision());
                        trace.add("selected " + metadata.name() + " version " + metadata.version() + " priority " + rank);
                        progress = true;
                        // Reprepare immediately: one file may satisfy several pending models.
                        break;
                    }
                    if (!progress) reject(RepositoryProblem.Reason.NOT_FOUND, "");
                }
            }
        } catch (OperationCancelledException cancelled) {
            return new RepositoryResolutionOutcome.Cancelled(cancelled.reason());
        } catch (UpdateRejectedException stale) {
            if (!workspace.currentRevision().equals(expected)) return stale(workspace, expected);
            throw stale;
        } catch (Rejection rejected) {
            return new RepositoryResolutionOutcome.Rejected(List.of(rejected.problem));
        }
    }

    private static boolean compatible(ModelSelector.ByName a, ModelSelector.ByName b) {
        return a.modelName().equals(b.modelName())
                && (a.modelVersion().isEmpty() || b.modelVersion().isEmpty() || a.modelVersion().equals(b.modelVersion()))
                && (a.sourceHint().isEmpty() || b.sourceHint().isEmpty() || a.sourceHint().equals(b.sourceHint()));
    }
    private static WorkspaceConfiguration configuration(WorkspaceConfiguration original,
            Map<String, ModelSelector.ByName> selections, Map<SourceId, Integer> ranks, ResolverCatalog catalog) {
        return new WorkspaceConfiguration(original.compilerOptions(),
                new ResolutionPolicy(original.resolutionPolicy().preferWorkspaceSource(), selections, ranks), catalog, original.cacheLimits());
    }
    private static SourceUpdate update(SourceId id, String text) {
        return new SourceUpdate(id, new SourceExpectation.Absent(), new SourceChange.Replace(text));
    }
    private static RepositoryResolutionOutcome stale(Workspace workspace, WorkspaceRevision expected) {
        return new RepositoryResolutionOutcome.Stale(expected, workspace.currentRevision());
    }
    private static String schemaLanguage(LanguageVersion language) { return language == LanguageVersion.ILI23 ? "ili2_3" : "ili2_4"; }

    private static void recordSelected(WorkspaceCapture capture, Set<SourceId> remoteSources,
            Map<String, ModelPin> pins, Map<SourceId, ContentDigest> sourcePins, Optional<ResolutionLock> lock, Map<String, List<ModelPin>> selected) {
        var locations = new ArrayList<ModelLocation>();
        capture.resolution().dependencies().forEach(d -> locations.addAll(d.candidates()));
        for (var root : capture.request().roots()) {
            if (root instanceof ModelSelector.BySource bySource) {
                var syntax = capture.reuse().previousSyntax().get(bySource.source());
                if (syntax != null) for (var model : syntax.root().models())
                    locations.add(new ModelLocation(syntax.source().id(), syntax.source().revision(), model.name(), Optional.of(model.modelVersion()), model.range().startOffset()));
                continue;
            }
            var byName = (ModelSelector.ByName) root;
            for (var syntax : capture.reuse().previousSyntax().values()) for (var model : syntax.root().models()) {
                if (model.name().equals(byName.modelName()) && byName.sourceHint().filter(id -> !id.equals(syntax.source().id())).isEmpty()
                        && byName.modelVersion().filter(v -> !v.equals(model.modelVersion())).isEmpty())
                    locations.add(new ModelLocation(syntax.source().id(), syntax.source().revision(), model.name(), Optional.of(model.modelVersion()), model.range().startOffset()));
            }
        }
        for (var location : locations) {
            boolean remote = remoteSources.contains(location.source()) || lock.map(l -> l.models().containsKey(location.modelName())).orElse(false);
            var text = capture.request().sources().sources().get(location.source());
            var pin = new ModelPin(location.modelName(), location.modelVersion().orElse(""), location.source(),
                    ContentDigest.sha256(text.text().getBytes(StandardCharsets.UTF_8)));
            var sourcePin = sourcePins.get(location.source());
            if (sourcePin != null && !sourcePin.equals(pin.digest())) reject(RepositoryProblem.Reason.PIN_MISMATCH, pin.modelName());
            if (!remote && !pins.containsKey(location.modelName())) continue;
            var expected = pins.get(pin.modelName());
            if (expected != null && !expected.equals(pin)) reject(RepositoryProblem.Reason.PIN_MISMATCH, pin.modelName());
            if (remote && lock.isPresent() && !lock.orElseThrow().models().getOrDefault(pin.modelName(), List.of()).contains(pin))
                reject(RepositoryProblem.Reason.LOCK_MISMATCH, pin.modelName());
            var group = selected.computeIfAbsent(pin.modelName(), ignored -> new ArrayList<>());
            if (!group.contains(pin)) group.add(pin);
        }
    }
    private static void addPin(Map<SourceId, ContentDigest> pins, SourceId id, ContentDigest digest) {
        var previous = pins.putIfAbsent(id, digest);
        if (previous != null && !previous.equals(digest)) reject(RepositoryProblem.Reason.PIN_MISMATCH, "");
    }
    private static void reject(RepositoryProblem.Reason reason, String model) {
        throw new Rejection(new RepositoryProblem(reason, model, List.of(), Optional.empty()));
    }
    private static final class Rejection extends RuntimeException {
        final RepositoryProblem problem;
        Rejection(RepositoryProblem problem) { super(problem.reason().name()); this.problem = problem; }
    }

    private final class VerifiedLoader implements SourceLoader {
        final Map<SourceId, ContentDigest> pins;
        final Optional<ResolutionLock> lock;
        final FetchPolicy policy;
        final CancellationToken token;
        final Map<SourceId, LoadedSource> seen = new LinkedHashMap<>();
        VerifiedLoader(Map<SourceId, ContentDigest> pins, Optional<ResolutionLock> lock, FetchPolicy policy, CancellationToken token) {
            this.pins = Map.copyOf(pins); this.lock = lock; this.policy = policy; this.token = token;
        }
        @Override public LoadedSource load(SourceLocator locator, FetchPolicy ignored, CancellationToken ignoredToken) {
            token.throwIfCancellationRequested();
            var id = new SourceId(locator.location());
            ContentDigest expected = pins.get(id);
            LoadedSource result;
            try {
                if (policy.offline()) {
                    if (expected == null) throw new SourceLoadException(SourceLoadException.Reason.OFFLINE, "offline source has no digest", null);
                    var cached = store.lookup(expected).orElseThrow(() -> new SourceLoadException(SourceLoadException.Reason.OFFLINE, "verified content unavailable offline", null));
                    byte[] bytes = cached.bytes();
                    if (!expected.matches(bytes)) reject(RepositoryProblem.Reason.PIN_MISMATCH, "");
                    if (bytes.length > policy.maxBytes()) throw new SourceLoadException(SourceLoadException.Reason.TOO_LARGE, "cached source exceeds byte limit", null);
                    String text;
                    try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
                    catch (java.nio.charset.CharacterCodingException invalid) { throw new SourceLoadException(SourceLoadException.Reason.INVALID_ENCODING, "invalid cached UTF-8", invalid); }
                    result = new LoadedSource(id, text, expected.value(), "verified-cache");
                } else result = loader.load(locator, policy, token);
            } catch (SourceLoadException failure) {
                throw new Rejection(new RepositoryProblem(RepositoryProblem.Reason.LOAD_FAILURE, "", List.of(), Optional.of(failure.reason())));
            }
            token.throwIfCancellationRequested();
            byte[] bytes = result.text().getBytes(StandardCharsets.UTF_8);
            var digest = ContentDigest.sha256(bytes);
            if (!id.equals(result.sourceId()) || !digest.value().equals(result.byteSha256())) reject(RepositoryProblem.Reason.INVALID_SOURCE, "");
            if (bytes.length > policy.maxBytes()) throw new Rejection(new RepositoryProblem(RepositoryProblem.Reason.LOAD_FAILURE, "", List.of(), Optional.of(SourceLoadException.Reason.TOO_LARGE)));
            if (expected != null && !expected.equals(digest)) reject(lock.isPresent() ? RepositoryProblem.Reason.LOCK_MISMATCH : RepositoryProblem.Reason.PIN_MISMATCH, "");
            var previous = seen.putIfAbsent(id, result);
            if (previous != null && !previous.byteSha256().equals(result.byteSha256())) reject(RepositoryProblem.Reason.CHANGED_SOURCE, "");
            store.put(new VerifiedContent(id, digest, bytes));
            return result;
        }
        void verifyUnchanged() {
            if (!policy.offline()) for (var id : List.copyOf(seen.keySet())) load(new SourceLocator(id.value()), policy, token);
        }
    }
}
