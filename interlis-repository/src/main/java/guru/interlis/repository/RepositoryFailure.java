package guru.interlis.repository;

import java.net.URI;
import java.util.Objects;

/**
 * Repository-local discovery failure; does not hide other repositories'
 * candidates.
 *
 * @param indexLocation index location
 * @param reason typed failure class
 * @param safeMessage credential-free message
 */
public record RepositoryFailure(URI indexLocation, Reason reason, String safeMessage) {
    /** Creates a validated instance. */
    public RepositoryFailure {
        Objects.requireNonNull(indexLocation, "indexLocation");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(safeMessage, "safeMessage");
    }

    /** Failure classes. */
    public enum Reason {
        /** Repository could not be loaded. */
        LOAD,
        /** Index could not be read. */
        INDEX
    }
}
