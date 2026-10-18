package guru.interlis.model;

import java.util.Objects;

/**
 * Result of an exact unit-relation check.
 *
 * @param decision the {@code decision} component
 * @param reason the {@code reason} component
 */
public record UnitRelation(RelationDecision decision, String reason) {
    /** Creates a validated instance. */
    public UnitRelation {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
    }
}
