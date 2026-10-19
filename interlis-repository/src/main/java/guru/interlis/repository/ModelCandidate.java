package guru.interlis.repository;

import java.net.URI;
import java.util.Objects;

/**
 * Candidate model from one repository index.
 *
 * @param metadata model metadata
 * @param repository repository that supplied the entry
 * @param indexLocation exact index location used as provenance
 * @param priority zero-based repository priority
 */
public record ModelCandidate(RepositoryModelMetadata metadata, RepositoryBase repository,
                             URI indexLocation, int priority) {
    /** Creates a validated instance. */
    public ModelCandidate {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(indexLocation, "indexLocation");
        if (priority < 0) {
            throw new IllegalArgumentException("priority must not be negative");
        }
    }
}
