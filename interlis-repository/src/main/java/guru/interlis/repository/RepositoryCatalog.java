package guru.interlis.repository;

import java.util.List;
import java.util.Objects;

/**
 * Immutable repository catalog with a content-derived revision.
 *
 * @param config configuration used for discovery
 * @param revision opaque catalog revision
 * @param candidates all discovered candidates in deterministic order
 * @param failures repository-local failures
 */
public record RepositoryCatalog(RepositoryConfig config, String revision,
                                List<ModelCandidate> candidates,
                                List<RepositoryFailure> failures) {
    /** Creates a validated instance. */
    public RepositoryCatalog {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(revision, "revision");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }
}
