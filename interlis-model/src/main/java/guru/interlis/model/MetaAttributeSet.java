package guru.interlis.model;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable meta-attribute values; distinct from transfer data.
 *
 * @param values the {@code values} component
 */
public record MetaAttributeSet(Map<String, Value> values) {
    /** Creates a validated instance. */
    public MetaAttributeSet {
        var copy = new java.util.LinkedHashMap<String, Value>();
        Objects.requireNonNull(values, "values").forEach((key,value) ->
                copy.put(Objects.requireNonNull(key,"key"),Objects.requireNonNull(value,"value")));
        values = java.util.Collections.unmodifiableMap(copy);
    }

    /** Immutable value with its original declared, derived or predefined provenance. */
    public record Value(String text,Origin origin) {
        public Value { Objects.requireNonNull(text);Objects.requireNonNull(origin); }
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
