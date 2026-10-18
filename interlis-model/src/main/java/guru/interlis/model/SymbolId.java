package guru.interlis.model;

import java.util.Objects;

/**
 * Opaque symbol identity; never parsed as a qualified name.
 *
 * @param value wrapped value
 */
public record SymbolId(String value) {
    /** Creates a validated instance. */
    public SymbolId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SymbolId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
