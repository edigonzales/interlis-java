package guru.interlis.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ordered display/index name; not an identity.
 *
 * @param segments the {@code segments} component
 */
public record QualifiedName(List<String> segments) {
    /** Creates a validated instance. */
    public QualifiedName {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.isEmpty() || segments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("qualified name needs non-blank segments");
        }
    }

    /**
     * Executes {@code of}.
     *
     * @param segments the {@code segments} argument
     *
     * @return the result
     */
    public static QualifiedName of(String... segments) {
        return new QualifiedName(List.of(segments));
    }

    /**
     * Executes {@code child}.
     *
     * @param segment the {@code segment} argument
     *
     * @return the result
     */
    public QualifiedName child(String segment) {
        List<String> copy = new ArrayList<>(segments);
        copy.add(segment);
        return new QualifiedName(copy);
    }

    /**
     * Returns the {@code last} value.
     *
     * @return the value
     */
    public String last() {
        return segments.getLast();
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}
