package guru.interlis.repository;

import java.util.List;
import java.util.Objects;

/**
 * Immutable repository index with safely resolved model locations.
 *
 * @param base repository base
 * @param models model entries in document order
 */
public record RepositoryIndex(RepositoryBase base, List<RepositoryModelMetadata> models) {
    /** Creates a validated instance. */
    public RepositoryIndex {
        Objects.requireNonNull(base, "base");
        models = List.copyOf(Objects.requireNonNull(models, "models"));
    }
}
