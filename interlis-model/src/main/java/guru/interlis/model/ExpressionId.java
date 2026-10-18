package guru.interlis.model;

import java.util.Objects;

/**
 * Identity of one typed expression in a snapshot.
 *
 * @param value wrapped value
 */
public record ExpressionId(String value) {
    /** Creates a validated instance. */
    public ExpressionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExpressionId must not be blank");
        }
    }
}
