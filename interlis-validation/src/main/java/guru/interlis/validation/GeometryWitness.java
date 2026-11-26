package guru.interlis.validation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A point supporting a geometry finding in the first two axes of the model's coordinate system.
 * Both ordinates use the model's units. Calculated ordinates may be rounded.
 *
 * @param kind the relation of the point to the geometry finding
 * @param x the first ordinate
 * @param y the second ordinate
 */
public record GeometryWitness(Kind kind, BigDecimal x, BigDecimal y) {
    public GeometryWitness {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
    }

    /** The geometric role of the reported point. */
    public enum Kind {
        /** An intersection or contact between the participating segments. */
        INTERSECTION,
        /** A point on the common part of the participating boundaries. */
        SHARED_BOUNDARY,
        /** A point on one boundary that lies strictly inside the other geometry. */
        CONTAINED_BOUNDARY,
        /** A point on an interior ring that lies strictly outside its intended shell. */
        OUTSIDE_BOUNDARY,
        /** A point on the boundary of a bounded uncovered region. */
        GAP_BOUNDARY,
        /** A position supplied by the data at the reported geometry component. */
        VERTEX
    }
}
