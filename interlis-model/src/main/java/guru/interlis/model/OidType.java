package guru.interlis.model;

import java.util.Objects;

/**
 * OID family.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param oidKind identifier family; UUID retains its predefined identity
 * @param valueType numeric or text carrier in the same snapshot; empty for ANY
 */
public record OidType(TypeId id, Origin origin, OidKind oidKind, java.util.Optional<TypeId> valueType) implements TypeDescriptor {
    /** Creates a validated instance. */
    public OidType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(oidKind, "oidKind");
        Objects.requireNonNull(valueType, "valueType");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.OID;
    }
}
