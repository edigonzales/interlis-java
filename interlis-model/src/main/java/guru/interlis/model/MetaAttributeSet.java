package guru.interlis.model;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable meta-attribute values; distinct from transfer data.
 *
 * @param values the {@code values} component
 */
public record MetaAttributeSet(Map<String, String> values) {
    /** Creates a validated instance. */
    public MetaAttributeSet {
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static MetaAttributeSet empty() {
        return new MetaAttributeSet(Map.of());
    }
}
