package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bound ATTRIBUTE type with an optional exact owner (subclasses are not substituted).
 * Restrictions retain source order and reference types in the same immutable graph.
 * Values are null-free. No path or function argument is evaluated.
 */
public record AttributePathType(TypeId id, Origin origin, Optional<Owner> owner,
        List<TypeId> restrictions) implements TypeDescriptor {
    public AttributePathType {
        Objects.requireNonNull(id); Objects.requireNonNull(origin); Objects.requireNonNull(owner);
        restrictions = List.copyOf(restrictions);
    }
    /** Closed owner choices: class-valued expression, function argument or constant's class. */
    public sealed interface Owner permits ClassAttribute, Argument, Viewable { }
    /** A bound class-valued expression in this snapshot: a declared OF path or
     * the actual argument of a call with a dependent ATTRIBUTE result. No
     * expression is evaluated, and the function's declared type is unchanged. */
    public record ClassAttribute(ExpressionId path) implements Owner {
        public ClassAttribute { Objects.requireNonNull(path); }
    }
    /** Zero-based argument index in the bound function's ordered parameter list. */
    public record Argument(SymbolId function, int index) implements Owner {
        public Argument { Objects.requireNonNull(function); if (index < 0) throw new IllegalArgumentException("negative argument index"); }
    }
    public record Viewable(SymbolId target) implements Owner {
        public Viewable { Objects.requireNonNull(target); }
    }
    @Override public TypeKind kind() { return TypeKind.ATTRIBUTE_PATH; }
}
