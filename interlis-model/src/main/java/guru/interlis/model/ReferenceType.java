package guru.interlis.model;

import java.util.Objects;

/**
 * REFERENCE TO a restricted class or association.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param target target symbol
 * @param external external-role marker
 */
public record ReferenceType(TypeId id, Origin origin, SymbolId target, boolean external)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public ReferenceType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.REFERENCE;
    }
}
