package guru.interlis.repository;

import java.net.URI;
import java.util.Objects;

/**
 * Absolute repository root used to resolve index entries.
 *
 * @param uri repository root URI
 */
public record RepositoryBase(URI uri) {
    /** Creates a validated instance. */
    public RepositoryBase {
        Objects.requireNonNull(uri, "uri");
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("repository base must be absolute");
        }
    }
}
