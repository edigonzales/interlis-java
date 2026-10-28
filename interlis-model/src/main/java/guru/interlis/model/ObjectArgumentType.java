package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An object or object set accepted or returned by a function or produced by a path.
 *
 * @param id snapshot-local type identity
 * @param origin declaration or expression origin
 * @param base target class, association or view; empty denotes ANYCLASS
 * @param objects true for an object set, false for an individual object
 * @param restrictions allowed specializations in declaration order; empty means no restriction
 */
public record ObjectArgumentType(TypeId id, Origin origin, Optional<SymbolId> base,
                                 List<SymbolId> restrictions, boolean objects) implements TypeDescriptor {
    /** Creates an immutable, null-free descriptor with snapshot-local references. */
    public ObjectArgumentType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(base, "base");
        restrictions = List.copyOf(restrictions);
    }

    @Override
    public TypeKind kind() {
        return objects ? TypeKind.OBJECT_SET : TypeKind.OBJECT_ARGUMENT;
    }
}
