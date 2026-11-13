package guru.interlis.transfer;

import guru.interlis.transfer.TransferValue.NumberValue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact geometry values with immutable component lists in source order. Ordinates
 * and radii retain their original numeric lexemes. An enclosing transfer object
 * can supply the source identity, and the component lists retain each part's
 * position in that object. Constructors check structural shape. Coordinate dimensions
 * and numeric radius values are retained without model or geometry validation.
 */
public sealed interface TransferGeometry extends TransferValue
        permits TransferGeometry.Coordinate, TransferGeometry.MultiCoordinate,
                TransferGeometry.Polyline, TransferGeometry.MultiPolyline,
                TransferGeometry.Surface, TransferGeometry.MultiSurface {

    /** A position with exactly two or three ordinates in model axis order. */
    record Coordinate(List<NumberValue> ordinates) implements TransferGeometry {
        public Coordinate {
            ordinates = List.copyOf(ordinates);
            if (ordinates.size() != 2 && ordinates.size() != 3) {
                throw new IllegalArgumentException("A coordinate requires two or three ordinates");
            }
        }

        private int dimensions() {
            return ordinates.size();
        }
    }

    /** A possibly empty sequence of positions, retaining each component's dimension. */
    record MultiCoordinate(List<Coordinate> coordinates) implements TransferGeometry {
        public MultiCoordinate {
            coordinates = List.copyOf(coordinates);
        }
    }

    /**
     * A polyline with its parts and each position's dimension in source order.
     * An empty list represents a supplied line with no parts, as in an empty extract.
     *
     * @param parts nonnull immutable sequence of curve parts
     * @param lineAttributes nonnull optional model-bound structure for this line
     */
    record Polyline(List<CurvePart> parts, Optional<TransferValue.Structured> lineAttributes) implements TransferGeometry {
        public Polyline {
            parts = List.copyOf(parts);
            Objects.requireNonNull(lineAttributes, "lineAttributes");
        }

        /** Creates a line without a line-attribute structure. */
        public Polyline(List<CurvePart> parts) {
            this(parts, Optional.empty());
        }
    }

    /** A possibly empty sequence of polylines with their original dimensions. */
    record MultiPolyline(List<Polyline> lines) implements TransferGeometry {
        public MultiPolyline {
            lines = List.copyOf(lines);
        }
    }

    /**
     * One outer boundary and its inner boundaries, retaining each position's dimension.
     * The order of holes is retained.
     */
    record Surface(Ring shell, List<Ring> holes) implements TransferGeometry {
        public Surface {
            Objects.requireNonNull(shell, "shell");
            holes = List.copyOf(holes);
        }
    }

    /** A possibly empty sequence of surfaces with their original dimensions. */
    record MultiSurface(List<Surface> surfaces) implements TransferGeometry {
        public MultiSurface {
            surfaces = List.copyOf(surfaces);
        }
    }

    /**
     * One boundary with its original partition into one or more nonempty polylines
     * with original position dimensions. Closure and continuity are geometric properties
     * of the supplied boundary.
     */
    record Ring(List<Polyline> lines) {
        public Ring {
            lines = List.copyOf(lines);
            if (lines.isEmpty() || lines.stream().anyMatch(line -> line.parts().isEmpty())) {
                throw new IllegalArgumentException("A ring requires one or more nonempty polylines");
            }
        }
    }

    /**
     * A start position followed by zero or more segments in traversal order.
     * Every segment starts at the preceding end position, or at {@code start}
     * for the first segment. The dimension of each end position is retained.
     */
    record CurvePart(Coordinate start, List<Segment> segments) {
        public CurvePart {
            Objects.requireNonNull(start, "start");
            segments = List.copyOf(segments);
        }
    }

    /** The interpretation of a supplied radius, independent of its validity. */
    enum RadiusConvention {
        /** Positive denotes clockwise traversal, negative denotes counterclockwise traversal. */
        SIGNED_CLOCKWISE,
        /** A positive magnitude with the arc selected by its intermediate position. */
        POSITIVE_SELECTED_BY_SUPPORT
    }

    /** Exact supplied radius in coordinate units and its non-null interpretation. */
    record ArcRadius(NumberValue sourceValue, RadiusConvention convention) {
        public ArcRadius {
            Objects.requireNonNull(sourceValue, "sourceValue");
            Objects.requireNonNull(convention, "convention");
        }
    }

    /** A straight or circular segment within a curve part. */
    sealed interface Segment permits Straight, Arc {
        /** Returns the exact end position with its supplied dimension. */
        Coordinate end();
    }

    /** A straight segment ending at the supplied position. */
    record Straight(Coordinate end) implements Segment {
        public Straight {
            Objects.requireNonNull(end, "end");
        }
    }

    /**
     * A circular segment with a two-dimensional intermediate position and a
     * two- or three-dimensional end. The optional radius preserves the supplied
     * number and the convention of its source encoding.
     */
    record Arc(Coordinate support, Coordinate end, Optional<ArcRadius> radius) implements Segment {
        public Arc {
            Objects.requireNonNull(support, "support");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(radius, "radius");
            if (support.dimensions() != 2) {
                throw new IllegalArgumentException("An arc support position requires two ordinates");
            }
        }
    }

}
