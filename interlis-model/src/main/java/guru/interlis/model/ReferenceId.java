package guru.interlis.model;

import java.util.Objects;

/**
 * Identity of one name-reference occurrence in a snapshot.
 *
 * @param value wrapped value
 */
public record ReferenceId(String value) {
    /** Creates a validated instance. */
    public ReferenceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ReferenceId must not be blank");
        }
    }
}
