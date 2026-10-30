package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Host-side discovery over an explicit ordered repository configuration. */
public final class RepositoryClient {
    private final SourceLoader loader;
    private final RepositoryIndexReader indexReader;

    /**
     * Creates a client.
     *
     * @param loader source loader used for index documents
     * @param indexReader index parser
     */
    public RepositoryClient(SourceLoader loader, RepositoryIndexReader indexReader) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.indexReader = Objects.requireNonNull(indexReader, "indexReader");
    }

    /**
     * Discovers exact model candidates. Load/index failures are returned as
     * data and do not suppress candidates from other repositories.
     *
     * @param query exact model query
     * @param config ordered repositories
     * @param token cancellation signal
     * @return candidates plus repository-local failures
     */
    public RepositoryCatalog catalog(RepositoryConfig config, CancellationToken token) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(token, "token");
        java.util.List<ModelCandidate> candidates = new java.util.ArrayList<>();
        java.util.List<RepositoryFailure> failures = new java.util.ArrayList<>();
        for (int priority = 0; priority < config.sources().size(); priority++) {
            token.throwIfCancellationRequested();
            RepositorySource source = config.sources().get(priority);
            java.net.URI indexLocation = java.net.URI.create(source.indexLocator().location());
            readSource(source, priority, indexLocation, candidates, failures, config.fetchPolicy(), token);
        }
        sort(candidates);
        String revision = revision(config, candidates, failures);
        return new RepositoryCatalog(config, revision, candidates, failures);
    }

    public RepositoryDiscovery discover(ModelQuery query, RepositoryConfig config,
                                        CancellationToken token) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(token, "token");
        List<ModelCandidate> candidates = new ArrayList<>();
        List<RepositoryFailure> failures = new ArrayList<>();
        for (int priority = 0; priority < config.sources().size(); priority++) {
            token.throwIfCancellationRequested();
            RepositorySource source = config.sources().get(priority);
            URI indexLocation = URI.create(source.indexLocator().location());
            try {
                LoadedSource loaded = loader.load(source.indexLocator(),
                        config.fetchPolicy(), token);
                RepositoryIndex index = indexReader.read(new IndexPayload(loaded.text()),
                        source.base());
                for (RepositoryModelMetadata metadata : index.models()) {
                    token.throwIfCancellationRequested();
                    if (!metadata.name().equals(query.modelName())) {
                        continue;
                    }
                    if (query.modelVersion().isPresent()
                            && !query.modelVersion().orElseThrow().equals(metadata.version())) {
                        continue;
                    }
                    if (query.sourceHint().isPresent()
                            && !query.sourceHint().orElseThrow().equals(metadata.location())) {
                        continue;
                    }
                    candidates.add(new ModelCandidate(metadata, source.base(), indexLocation,
                            priority));
                }
            } catch (SourceLoadException failure) {
                failures.add(new RepositoryFailure(indexLocation,
                        RepositoryFailure.Reason.LOAD, failure.getMessage()));
            } catch (RepositoryIndexException failure) {
                failures.add(new RepositoryFailure(indexLocation,
                        RepositoryFailure.Reason.INDEX, failure.getMessage()));
            }
        }
        candidates.sort(Comparator.comparing((ModelCandidate candidate) ->
                        candidate.metadata().name())
                .thenComparing(candidate -> candidate.metadata().version())
                .thenComparing(candidate -> candidate.priority())
                .thenComparing(candidate -> candidate.metadata().location().toString()));
        return new RepositoryDiscovery(candidates, failures);
    }

    private void readSource(RepositorySource source, int priority, URI indexLocation,
                            List<ModelCandidate> candidates, List<RepositoryFailure> failures,
                            FetchPolicy fetchPolicy, CancellationToken token) {
        try {
            LoadedSource loaded = loader.load(source.indexLocator(), fetchPolicy, token);
            RepositoryIndex index = indexReader.read(new IndexPayload(loaded.text()),
                    source.base());
            for (RepositoryModelMetadata metadata : index.models()) {
                candidates.add(new ModelCandidate(metadata, source.base(), indexLocation, priority));
            }
        } catch (SourceLoadException failure) {
            failures.add(new RepositoryFailure(indexLocation,
                    RepositoryFailure.Reason.LOAD, failure.getMessage()));
        } catch (RepositoryIndexException failure) {
            failures.add(new RepositoryFailure(indexLocation,
                    RepositoryFailure.Reason.INDEX, failure.getMessage()));
        }
    }

    private static void sort(List<ModelCandidate> candidates) {
        candidates.sort(Comparator.comparing((ModelCandidate candidate) ->
                        candidate.metadata().name())
                .thenComparing(candidate -> candidate.metadata().version())
                .thenComparing(candidate -> candidate.priority())
                .thenComparing(candidate -> candidate.metadata().location().toString()));
    }

    private static String revision(RepositoryConfig config, List<ModelCandidate> candidates,
                                   List<RepositoryFailure> failures) {
        StringBuilder canonical = new StringBuilder("repository-catalog-v2");
        field(canonical, Boolean.toString(config.fetchPolicy().offline()));
        field(canonical, Integer.toString(config.fetchPolicy().maxBytes()));
        field(canonical, config.fetchPolicy().timeout().toString());
        field(canonical, Integer.toString(config.sources().size()));
        config.sources().forEach(source -> {
            field(canonical, source.base().uri().toString());
            field(canonical, source.indexLocator().location());
        });
        field(canonical, Integer.toString(candidates.size()));
        candidates.forEach(candidate -> {
            var metadata = candidate.metadata();
            field(canonical, metadata.name()); field(canonical, metadata.version());
            field(canonical, metadata.schemaLanguage()); field(canonical, metadata.location().toString());
            optional(canonical, metadata.precursorVersion()); optional(canonical, metadata.md5());
            field(canonical, candidate.repository().uri().toString());
            field(canonical, candidate.indexLocation().toString());
            field(canonical, Integer.toString(candidate.priority()));
        });
        field(canonical, Integer.toString(failures.size()));
        failures.forEach(failure -> {
            field(canonical, failure.indexLocation().toString()); field(canonical, failure.reason().name());
            field(canonical, failure.safeMessage());
        });
        return "sha256:" + guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digestUtf16(canonical.toString()));
    }
    private static void field(StringBuilder b, String value) { b.append(value.length()).append(':').append(value); }
    private static void optional(StringBuilder b, java.util.Optional<String> value) {
        field(b, value.isPresent() ? "present" : "absent"); value.ifPresent(v -> field(b, v));
    }
}
