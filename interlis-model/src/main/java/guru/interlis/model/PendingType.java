package guru.interlis.model;

import java.util.Objects;

/**
 * Explicitly not-yet-analyzed type; distinct from a missing name.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param reason the {@code reason} component
 */
public record PendingType(TypeId id, Origin origin, PendingReason reason)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public PendingType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.PENDING;
    }
}
