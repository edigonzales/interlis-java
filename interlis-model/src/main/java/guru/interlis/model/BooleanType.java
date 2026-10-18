package guru.interlis.model;

import java.util.Objects;

/**
 * BOOLEAN value.
 *
 * @param id stable identifier
 * @param origin origin information
 */
public record BooleanType(TypeId id, Origin origin) implements TypeDescriptor {
    /** Creates a validated instance. */
    public BooleanType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.BOOLEAN;
    }
}
