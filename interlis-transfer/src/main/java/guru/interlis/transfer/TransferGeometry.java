package guru.interlis.transfer;

import guru.interlis.transfer.TransferValue.NumberValue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Exact geometry values with immutable component lists in source order. Ordinates
 * and radii retain their original numeric lexemes. An enclosing transfer object
 * can supply the source identity, and the component lists retain each part's
 * position in that object. Constructors check shape and dimensional consistency.
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

    /** A possibly empty sequence of positions of one dimension. */
    record MultiCoordinate(List<Coordinate> coordinates) implements TransferGeometry {
        public MultiCoordinate {
            coordinates = List.copyOf(coordinates);
            requireHomogeneousDimensions(coordinates, Coordinate::dimensions);
        }
    }

    /**
     * A polyline with its parts in source order. All parts have the same dimension.
     * An empty list represents a supplied line with no parts, as in an empty extract.
     *
     * @param parts nonnull immutable sequence of curve parts
     * @param lineAttributes nonnull optional model-bound structure for this line
     */
    record Polyline(List<CurvePart> parts, Optional<TransferValue.Structured> lineAttributes) implements TransferGeometry {
        public Polyline {
            parts = List.copyOf(parts);
            Objects.requireNonNull(lineAttributes, "lineAttributes");
            requireHomogeneousDimensions(parts, CurvePart::dimensions);
        }

        /** Creates a line without a line-attribute structure. */
        public Polyline(List<CurvePart> parts) {
            this(parts, Optional.empty());
        }

        private int dimensions() {
            return parts.isEmpty() ? 0 : parts.getFirst().dimensions();
        }
    }

    /** A possibly empty sequence of polylines of one dimension. */
    record MultiPolyline(List<Polyline> lines) implements TransferGeometry {
        public MultiPolyline {
            lines = List.copyOf(lines);
            requireHomogeneousDimensions(lines, Polyline::dimensions);
        }
    }

    /**
     * One outer boundary and its inner boundaries, of the same dimension.
     * The order of holes is retained.
     */
    record Surface(Ring shell, List<Ring> holes) implements TransferGeometry {
        public Surface {
            Objects.requireNonNull(shell, "shell");
            holes = List.copyOf(holes);
            requireDimensions(holes, shell.dimensions(), Ring::dimensions);
        }

        private int dimensions() {
            return shell.dimensions();
        }
    }

    /** A possibly empty sequence of surfaces of one dimension. */
    record MultiSurface(List<Surface> surfaces) implements TransferGeometry {
        public MultiSurface {
            surfaces = List.copyOf(surfaces);
            requireHomogeneousDimensions(surfaces, Surface::dimensions);
        }
    }

    /**
     * One boundary with its original partition into one or more nonempty polylines
     * of the same dimension. Closure and continuity are geometric properties
     * of the supplied boundary.
     */
    record Ring(List<Polyline> lines) {
        public Ring {
            lines = List.copyOf(lines);
            if (lines.isEmpty() || lines.stream().anyMatch(line -> line.parts().isEmpty())) {
                throw new IllegalArgumentException("A ring requires one or more nonempty polylines");
            }
            requireHomogeneousDimensions(lines, Polyline::dimensions);
        }

        private int dimensions() {
            return lines.getFirst().dimensions();
        }
    }

    /**
     * A start position followed by zero or more segments in traversal order.
     * Every segment starts at the preceding end position, or at {@code start}
     * for the first segment. All end positions have the start's dimension.
     */
    record CurvePart(Coordinate start, List<Segment> segments) {
        public CurvePart {
            Objects.requireNonNull(start, "start");
            segments = List.copyOf(segments);
            requireDimensions(segments, start.dimensions(), segment -> segment.end().dimensions());
        }

        private int dimensions() {
            return start.dimensions();
        }
    }

    /** A straight or circular segment within a curve part. */
    sealed interface Segment permits Straight, Arc {
        /** Returns the exact end position, of the same dimension as the part. */
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
     * two- or three-dimensional end. An optional nonzero radius retains its
     * supplied sign, including the direction encoded by an INTERLIS 2.3 radius.
     */
    record Arc(Coordinate support, Coordinate end, Optional<NumberValue> radius) implements Segment {
        public Arc {
            Objects.requireNonNull(support, "support");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(radius, "radius");
            if (support.dimensions() != 2) {
                throw new IllegalArgumentException("An arc support position requires two ordinates");
            }
            if (radius.isPresent() && radius.orElseThrow().exactValue().signum() == 0) {
                throw new IllegalArgumentException("An arc radius must be nonzero");
            }
        }
    }

    private static <T> void requireHomogeneousDimensions(List<T> values, ToIntFunction<T> dimensions) {
        // Zero means the component has no positions from which to derive a dimension.
        int expected = 0;
        for (T value : values) {
            int actual = dimensions.applyAsInt(value);
            if (expected == 0) {
                expected = actual;
            } else if (actual != 0) {
                requireSameDimension(expected, actual);
            }
        }
    }

    private static <T> void requireDimensions(List<T> values, int expected, ToIntFunction<T> dimensions) {
        for (T value : values) {
            requireSameDimension(expected, dimensions.applyAsInt(value));
        }
    }

    private static void requireSameDimension(int expected, int actual) {
        if (expected != actual) {
            throw new IllegalArgumentException("Geometry component dimensions must agree");
        }
    }
}
