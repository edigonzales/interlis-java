package guru.interlis.model;

import java.util.Objects;

/** Polymorphic ENUMVAL/ENUMTREEVAL function formal, without an invented enum
 * identity. ENUMVAL accepts leaves; ENUMTREEVAL also accepts nodes. Actual
 * arguments retain their concrete bound enumeration in the same snapshot. */
public record EnumArgumentType(TypeId id, Origin origin, boolean treeValues) implements TypeDescriptor {
    public EnumArgumentType { Objects.requireNonNull(id); Objects.requireNonNull(origin); }
    @Override public TypeKind kind() { return TypeKind.ENUM_ARGUMENT; }
}
