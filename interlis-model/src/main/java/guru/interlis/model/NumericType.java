package guru.interlis.model;

import java.util.Objects;

/**
 * Exact numeric range including unit, direction and reference system.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param range source range
 */
public record NumericType(TypeId id, Origin origin, NumericRange range)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public NumericType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(range, "range");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.NUMERIC;
    }
}
