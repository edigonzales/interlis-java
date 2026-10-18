package guru.interlis.model;

import java.util.Objects;

/**
 * Legal generic domain reference before a context binds it.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param name declared name
 */
public record GenericType(TypeId id, Origin origin, String name) implements TypeDescriptor {
    /** Creates a validated instance. */
    public GenericType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.GENERIC;
    }
}
