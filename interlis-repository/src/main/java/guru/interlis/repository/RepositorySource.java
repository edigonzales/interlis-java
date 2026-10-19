package guru.interlis.repository;

import java.util.Objects;

/**
 * One configured repository and its exact index location.
 *
 * @param base repository base
 * @param indexLocator index document location
 */
public record RepositorySource(RepositoryBase base, SourceLocator indexLocator) {
    /** Creates a validated instance. */
    public RepositorySource {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(indexLocator, "indexLocator");
    }
}
