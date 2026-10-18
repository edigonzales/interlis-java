package guru.interlis.model;

import java.util.Objects;

/**
 * Embedded structure value.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param target target symbol
 */
public record StructureValueType(TypeId id, Origin origin, SymbolId target)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public StructureValueType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.STRUCTURE_VALUE;
    }
}
