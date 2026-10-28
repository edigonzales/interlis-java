package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A bound REFERENCE TO class/association target set, immutable and snapshot-local.
 * An empty target means ANYCLASS. Restrictions are an ordered disjunction of
 * allowed subhierarchies; an empty list retains the entire primary hierarchy.
 * EXTERNAL permits cross-basket values and is independent of target identity. */
public record ReferenceType(TypeId id, Origin origin, Optional<SymbolId> target,
                            List<SymbolId> restrictions, boolean external) implements TypeDescriptor {
    public ReferenceType {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target"); restrictions = List.copyOf(restrictions);
    }

    /** Allowed roots, without enumerating subclasses. Empty means unrestricted ANYCLASS. */
    public List<SymbolId> targetRoots() {
        return restrictions.isEmpty() ? target.stream().toList() : restrictions;
    }
    @Override public TypeKind kind() { return TypeKind.REFERENCE; }
}
