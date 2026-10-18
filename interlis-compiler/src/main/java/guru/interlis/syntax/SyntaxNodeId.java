package guru.interlis.syntax;

import java.util.Objects;

/**
 * Snapshot-local syntax-node identity.
 *
 * @param value wrapped value
 */
public record SyntaxNodeId(String value) {
    /** Creates a validated instance. */
    public SyntaxNodeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SyntaxNodeId must not be blank");
        }
    }
}
