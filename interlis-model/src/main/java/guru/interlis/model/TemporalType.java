package guru.interlis.model;

import java.util.Objects;

/**
 * DATE, TIMEOFDAY or DATETIME.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param temporalKind the {@code temporalKind} component
 */
public record TemporalType(TypeId id, Origin origin, TemporalKind temporalKind)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public TemporalType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(temporalKind, "temporalKind");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.TEMPORAL;
    }
}
