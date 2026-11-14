package guru.interlis.validation.jts.internal;

import guru.interlis.transfer.TransferGeometry;
import guru.interlis.validation.DataLocation.GeometryComponent;
import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.ValidationCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.validation.DataLocation.GeometryComponent.Kind.*;
import static guru.interlis.validation.GeometryEngine.Check.*;

final class TransferGeometryAdapter {
    record Position(BigDecimal x, BigDecimal y) {
        Position {
            x = x.stripTrailingZeros();
            y = y.stripTrailingZeros();
        }

        Position(TransferGeometry.Coordinate coordinate) {
            this(coordinate.ordinates().get(0).exactValue(), coordinate.ordinates().get(1).exactValue());
        }

        Position minimum(Position other) {
            return new Position(x.min(other.x), y.min(other.y));
        }
    }

    record Edge(Position start, Position end, Position support, BigDecimal radius) {
        Edge reversed() {
            return new Edge(end, start, support, radius);
        }
    }

    record Segment(CurveSegment curve, List<GeometryComponent> path, Edge edge) {
        Segment reversed() {
            return new Segment(curve.reversed(), path, edge.reversed());
        }
    }

    record Part(Position start, TransferGeometry.Coordinate first, TransferGeometry.Coordinate last,
                List<Segment> segments, List<GeometryComponent> path) {}
    record Ring(List<Part> parts, List<GeometryComponent> path) {}
    record Surface(Ring shell, List<Ring> holes) {}
    record Result(GeometryEngine.Check check, List<Part> lines, List<Surface> surfaces, boolean threeDimensional) {}

    private final GeometryEngine.Input input;
    private final GeometryEngine.Control control;
    private final boolean coverage;
    private Position origin;
    private boolean threeDimensional;
    private GeometryEngine.Check check = VALID;

    TransferGeometryAdapter(GeometryEngine.Input input, GeometryEngine.Control control) {
        this(input, control, null);
    }

    TransferGeometryAdapter(GeometryEngine.Input input, GeometryEngine.Control control, Position coverageOrigin) {
        this.input = input;
        this.control = control;
        this.origin = coverageOrigin;
        this.coverage = coverageOrigin != null;
    }

    static Position coverageOrigin(TransferGeometry geometry, Position minimum, Runnable visit) {
        var surfaces = switch (geometry) {
            case TransferGeometry.Surface surface -> List.of(surface);
            case TransferGeometry.MultiSurface multi -> multi.surfaces();
            default -> List.<TransferGeometry.Surface>of();
        };
        for (var surface : surfaces) {
            minimum = ringOrigin(surface.shell(), minimum, visit);
            for (var hole : surface.holes()) {
                minimum = ringOrigin(hole, minimum, visit);
            }
        }
        return minimum;
    }

    private static Position ringOrigin(TransferGeometry.Ring ring, Position minimum, Runnable visit) {
        for (var line : ring.lines()) {
            for (var part : line.parts()) {
                minimum = include(minimum, part.start(), visit);
                for (var segment : part.segments()) {
                    minimum = include(minimum, segment.end(), visit);
                    if (segment instanceof TransferGeometry.Arc arc) {
                        minimum = include(minimum, arc.support(), visit);
                    }
                }
            }
        }
        return minimum;
    }

    private static Position include(Position minimum, TransferGeometry.Coordinate coordinate, Runnable visit) {
        visit.run();
        var position = new Position(coordinate);
        return minimum == null ? position : minimum.minimum(position);
    }

    Result read() {
        var lines = new ArrayList<Part>();
        var surfaces = new ArrayList<Surface>();
        switch (input.value()) {
            case TransferGeometry.Polyline line -> line(line, List.of(), lines);
            case TransferGeometry.MultiPolyline multi -> {
                for (int i = 0; i < multi.lines().size(); i++) {
                    line(multi.lines().get(i), at(List.of(), LINE, i), lines);
                }
            }
            case TransferGeometry.Surface surface -> surfaces.add(surface(surface, List.of()));
            case TransferGeometry.MultiSurface multi -> {
                for (int i = 0; i < multi.surfaces().size(); i++) {
                    surfaces.add(surface(multi.surfaces().get(i), at(List.of(), SURFACE, i)));
                }
            }
            default -> check = UNSUPPORTED;
        }
        return new Result(check, List.copyOf(lines), List.copyOf(surfaces), threeDimensional);
    }

    private Surface surface(TransferGeometry.Surface value, List<GeometryComponent> path) {
        var shell = ring(value.shell(), at(path, RING, 0));
        var holes = new ArrayList<Ring>();
        for (int i = 0; i < value.holes().size(); i++) {
            holes.add(ring(value.holes().get(i), at(path, RING, i + 1)));
        }
        return new Surface(shell, List.copyOf(holes));
    }

    private Ring ring(TransferGeometry.Ring value, List<GeometryComponent> path) {
        var parts = new ArrayList<Part>();
        for (int i = 0; i < value.lines().size(); i++) {
            line(value.lines().get(i), at(path, LINE, i), parts);
        }
        return new Ring(List.copyOf(parts), path);
    }

    private void line(TransferGeometry.Polyline value, List<GeometryComponent> path, List<Part> parts) {
        control.visit().run();
        if (value.parts().isEmpty() && !input.partial()) {
            invalid(path, ValidationCode.GEOMETRY_DEGENERATE_SEGMENT, "Line has no parts");
        }
        for (int i = 0; i < value.parts().size(); i++) {
            var part = value.parts().get(i);
            var partPath = at(path, PART, i);
            var start = new Position(part.start());
            var previous = project(start, part.start());
            var previousExact = start;
            var segments = new ArrayList<Segment>();
            for (int j = 0; j < part.segments().size(); j++) {
                control.visit().run();
                var segment = part.segments().get(j);
                var endExact = new Position(segment.end());
                var end = project(endExact, segment.end());
                var segmentPath = at(partPath, SEGMENT, j);
                if (previousExact.equals(endExact)) {
                    invalid(segmentPath, ValidationCode.GEOMETRY_DEGENERATE_SEGMENT,
                            "Coincident projected endpoints");
                } else if (previous != null && end != null && previous.equals(end)) {
                    unavailable(NUMERICALLY_UNDETERMINED);
                } else if (previous != null && end != null) {
                    switch (segment) {
                        case TransferGeometry.Straight ignored ->
                            segments.add(new Segment(new CurveSegment.Straight(previous, end), segmentPath,
                                    new Edge(previousExact, endExact, null, null)));
                        case TransferGeometry.Arc arc ->
                            arc(previousExact, endExact, previous, end, arc, segmentPath, segments);
                    }
                }
                previous = end;
                previousExact = endExact;
            }
            var end = part.segments().isEmpty() ? part.start() : part.segments().getLast().end();
            parts.add(new Part(start, part.start(), end, List.copyOf(segments), partPath));
        }
    }

    private void arc(Position startExact, Position endExact, CurveSegment.Point start, CurveSegment.Point end,
                     TransferGeometry.Arc value, List<GeometryComponent> path, List<Segment> segments) {
        var supportExact = new Position(value.support());
        var support = project(supportExact, value.support());
        if (support == null) {
            return;
        }
        if (support.equals(start) && !supportExact.equals(startExact)
                || support.equals(end) && !supportExact.equals(endExact)) {
            unavailable(NUMERICALLY_UNDETERMINED);
            return;
        }
        double tolerance = 0;
        if (value.radius().isPresent()) {
            if (input.quantum().isEmpty()) {
                unavailable(MISSING_PARAMETERS);
                return;
            }
            tolerance = input.quantum().orElseThrow().doubleValue()
                    * (value.radius().orElseThrow().convention() == TransferGeometry.RadiusConvention.SIGNED_CLOCKWISE ? 2 : 1 / Math.sqrt(2));
            if (!Double.isFinite(tolerance) || tolerance <= 0) {
                unavailable(NUMERICALLY_UNDETERMINED);
                return;
            }
        }
        switch (CircularArc.build(start, support, end, value.radius(), tolerance)) {
            case CircularArc.Construction.Built built -> {
                var radius = value.radius().map(supplied -> supplied.sourceValue().exactValue().abs()).orElse(null);
                if (radius != null && inferredRadiusEquals(startExact, supportExact, endExact, radius)) {
                    radius = null;
                }
                segments.add(new Segment(built.arc(), path, new Edge(startExact, endExact, supportExact,
                        radius == null ? null : radius.stripTrailingZeros())));
            }
            case CircularArc.Construction.Invalid error -> invalid(path, ValidationCode.GEOMETRY_INVALID_ARC, error.detail());
            case CircularArc.Construction.Undetermined ignored -> unavailable(NUMERICALLY_UNDETERMINED);
        }
    }

    private static boolean inferredRadiusEquals(Position start, Position support, Position end, BigDecimal radius) {
        var sx = support.x().subtract(start.x());
        var sy = support.y().subtract(start.y());
        var ex = end.x().subtract(start.x());
        var ey = end.y().subtract(start.y());
        var twiceArea = sx.multiply(ey).subtract(sy.multiply(ex));
        // The square of abc = 4A times the circumradius avoids a square root or a division.
        return twiceArea.signum() != 0
                && squaredLength(sx, sy).multiply(squaredLength(ex, ey))
                .multiply(squaredLength(ex.subtract(sx), ey.subtract(sy)))
                .compareTo(radius.pow(2).multiply(twiceArea.pow(2)).multiply(BigDecimal.valueOf(4))) == 0;
    }

    private static BigDecimal squaredLength(BigDecimal x, BigDecimal y) {
        return x.pow(2).add(y.pow(2));
    }

    private CurveSegment.Point project(Position position, TransferGeometry.Coordinate coordinate) {
        control.visit().run();
        threeDimensional |= coordinate.ordinates().size() == 3;
        if (origin == null) {
            origin = position;
        }
        var dx = position.x().subtract(origin.x());
        var dy = position.y().subtract(origin.y());
        var point = new CurveSegment.Point(dx.doubleValue(), dy.doubleValue());
        if (!finite(point) || coverage && (BigDecimal.valueOf(point.x()).compareTo(dx) != 0
                || BigDecimal.valueOf(point.y()).compareTo(dy) != 0)) {
            unavailable(NUMERICALLY_UNDETERMINED);
            return null;
        }
        return point;
    }

    private static boolean finite(CurveSegment.Point point) {
        return Double.isFinite(point.x()) && Double.isFinite(point.y());
    }

    private void unavailable(GeometryEngine.Check reason) {
        if (check == VALID) {
            check = reason;
        }
    }

    private void invalid(List<GeometryComponent> path, ValidationCode code, String detail) {
        check = INVALID;
        control.findings().accept(new GeometryEngine.Finding(input.location(), path, code, detail));
    }

    private static List<GeometryComponent> at(List<GeometryComponent> path, GeometryComponent.Kind kind, int offset) {
        var next = new ArrayList<>(path);
        next.add(new GeometryComponent(kind, offset + 1));
        return List.copyOf(next);
    }
}
