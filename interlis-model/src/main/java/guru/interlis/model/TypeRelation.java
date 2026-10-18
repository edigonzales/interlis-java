package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Typed type relation with a stable reason and related types.
 *
 * @param decision the {@code decision} component
 * @param reason the {@code reason} component
 * @param related the {@code related} component
 */
public record TypeRelation(RelationDecision decision, String reason, List<TypeId> related) {
    /** Creates a validated instance. */
    public TypeRelation {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        related = List.copyOf(Objects.requireNonNull(related, "related"));
    }
}
