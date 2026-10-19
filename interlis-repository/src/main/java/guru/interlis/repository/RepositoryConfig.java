package guru.interlis.repository;

import java.util.List;
import java.util.Objects;

/**
 * Ordered repository configuration; earlier entries have higher priority for
 * equal candidates.
 *
 * @param sources configured repositories
 * @param fetchPolicy host fetch policy
 */
public record RepositoryConfig(List<RepositorySource> sources, FetchPolicy fetchPolicy) {
    /** Creates a validated instance. */
    public RepositoryConfig {
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(fetchPolicy, "fetchPolicy");
    }
}
