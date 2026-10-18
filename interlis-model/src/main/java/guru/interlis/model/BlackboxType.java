package guru.interlis.model;

import java.util.Objects;

/**
 * XML or binary blackbox.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param blackboxKind the {@code blackboxKind} component
 */
public record BlackboxType(TypeId id, Origin origin, BlackboxKind blackboxKind)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public BlackboxType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(blackboxKind, "blackboxKind");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.BLACKBOX;
    }
}
