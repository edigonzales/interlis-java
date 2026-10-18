package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Coordinate/MULTICOORD type with exact per-axis ranges.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param dimensions the {@code dimensions} component
 * @param axes the {@code axes} component
 * @param multiCoordinate the {@code multiCoordinate} component
 */
public record CoordinateType(TypeId id, Origin origin, int dimensions,
                             List<NumericRange> axes, boolean multiCoordinate)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public CoordinateType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        if (dimensions < 1 || dimensions > 3) {
            throw new IllegalArgumentException("coordinate dimensions must be 1..3");
        }
        axes = List.copyOf(Objects.requireNonNull(axes, "axes"));
        if (axes.size() != dimensions) {
            throw new IllegalArgumentException("axis count must match dimensions");
        }
    }

    @Override
    public TypeKind kind() {
        return TypeKind.COORDINATE;
    }
}
