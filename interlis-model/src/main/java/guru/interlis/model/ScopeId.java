package guru.interlis.model;

import java.util.Objects;

/**
 * Identity of one lexical scope in a snapshot.
 *
 * @param value wrapped value
 */
public record ScopeId(String value) {
    /** Creates a validated instance. */
    public ScopeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ScopeId must not be blank");
        }
    }
}
