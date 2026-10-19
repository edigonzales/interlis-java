package guru.interlis.repository;

import java.util.List;
import java.util.Objects;

/**
 * Discovery result; candidates and technical repository failures stay separate.
 *
 * @param candidates deterministic candidate list
 * @param failures repository-local failures
 */
public record RepositoryDiscovery(List<ModelCandidate> candidates,
                                  List<RepositoryFailure> failures) {
    /** Creates a validated instance. */
    public RepositoryDiscovery {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }

    /**
     * Returns whether at least one repository failed technically.
     *
     * @return true if failures are present
     */
    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
