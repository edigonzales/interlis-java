package guru.interlis.model;

import java.util.Objects;

/**
 * Legal deferred 2.4 binding with an explicit open condition.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param condition condition expression
 */
public record DeferredTypeBinding(TypeId id, Origin origin, String condition)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public DeferredTypeBinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(condition, "condition");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.DEFERRED;
    }
}
