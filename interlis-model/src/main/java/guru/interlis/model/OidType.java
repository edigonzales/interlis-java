package guru.interlis.model;

import java.util.Objects;

/**
 * OID family.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param oidKind the {@code oidKind} component
 */
public record OidType(TypeId id, Origin origin, OidKind oidKind) implements TypeDescriptor {
    /** Creates a validated instance. */
    public OidType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(oidKind, "oidKind");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.OID;
    }
}
