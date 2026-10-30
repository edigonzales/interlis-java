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

    /** Canonical lexical lookup anchor owned by a semantic declaration. */
    public static ScopeId forOwner(SymbolId owner) {
        return new ScopeId(Objects.requireNonNull(owner, "owner").value());
    }
}
