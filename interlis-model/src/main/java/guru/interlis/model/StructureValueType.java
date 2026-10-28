package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Embedded structure value. Empty target denotes ANYSTRUCTURE; restrictions
 * retain explicit bound targets in declaration order. IDs belong to the owning
 * snapshot. Unresolved source targets are represented by ErrorType, never by an
 * empty wildcard. All components and returned collections are immutable/null-free.
 */
public record StructureValueType(TypeId id, Origin origin, Optional<SymbolId> target,
                                 List<SymbolId> restrictions) implements TypeDescriptor {
    public StructureValueType {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(origin, "origin");
        target=Objects.requireNonNull(target, "target");restrictions=List.copyOf(restrictions);
    }
    /** Effective allowed roots: explicit restrictions, otherwise the primary target. */
    public List<SymbolId> targetRoots() { return restrictions.isEmpty()?target.stream().toList():restrictions; }
    @Override public TypeKind kind() { return TypeKind.STRUCTURE_VALUE; }
}
