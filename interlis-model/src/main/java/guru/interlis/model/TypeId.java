package guru.interlis.model;

import java.util.Objects;

/**
 * Identity of one type descriptor in a snapshot.
 *
 * @param value wrapped value
 */
public record TypeId(String value) {
    /** Creates a validated instance. */
    public TypeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TypeId must not be blank");
        }
    }
}
