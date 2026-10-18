package guru.interlis.model;

import java.util.Objects;

/**
 * Name prefix for scoped visibility queries.
 *
 * @param value wrapped value
 */
public record NamePrefix(String value) {
    /** Creates a validated instance. */
    public NamePrefix {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static NamePrefix empty() {
        return new NamePrefix("");
    }
}
