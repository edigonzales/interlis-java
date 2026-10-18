package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * Ordered enumeration tree value with declaration origin.
 *
 * @param path name path
 * @param order the {@code order} component
 * @param range source range
 */
public record EnumerationValue(String path, int order, Optional<SourceRange> range) {
    /** Creates a validated instance. */
    public EnumerationValue {
        Objects.requireNonNull(path, "path");
        if (path.isBlank() || order < 0) {
            throw new IllegalArgumentException("enum value needs path and non-negative order");
        }
        range = Objects.requireNonNull(range, "range");
    }
}
