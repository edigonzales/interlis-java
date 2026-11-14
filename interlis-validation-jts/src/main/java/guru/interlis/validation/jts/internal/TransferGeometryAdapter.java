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
    record Segment(CurveSegment curve, List<GeometryComponent> path) {}
    record Part(CurveSegment.Point start, List<Segment> segments, List<GeometryComponent> path) {}
    record Ring(List<Part> parts, List<GeometryComponent> path) {}
    record Surface(Ring shell, List<Ring> holes) {}
    record Result(GeometryEngine.Check check, List<Part> lines, List<Surface> surfaces, boolean threeDimensional,
                  BigDecimal originX, BigDecimal originY) {}

    private final GeometryEngine.Input input;
    private final GeometryEngine.Control control;
    private BigDecimal originX;
    private BigDecimal originY;
    private boolean threeDimensional;
    private GeometryEngine.Check check = VALID;

    TransferGeometryAdapter(GeometryEngine.Input input, GeometryEngine.Control control) {
        this.input = input;
        this.control = control;
    }

    TransferGeometryAdapter(GeometryEngine.Input input, GeometryEngine.Control control,
                            BigDecimal originX, BigDecimal originY) {
        this(input, control);
        this.originX = originX;
        this.originY = originY;
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
        return new Result(check, List.copyOf(lines), List.copyOf(surfaces), threeDimensional, originX == null ? BigDecimal.ZERO : originX,
                originY == null ? BigDecimal.ZERO : originY);
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
        for (int i = 0; i < value.parts().size(); i++) {
            var part = value.parts().get(i);
            var partPath = at(path, PART, i);
            var start = point(part.start());
            var previous = start;
            var previousExact = part.start();
            var segments = new ArrayList<Segment>();
            for (int j = 0; j < part.segments().size(); j++) {
                control.visit().run();
                var segment = part.segments().get(j);
                var end = point(segment.end());
                var segmentPath = at(partPath, SEGMENT, j);
                if (previous.equals(end)) {
                    if (sameXY(previousExact, segment.end())) {
                        invalid(segmentPath, ValidationCode.GEOMETRY_DEGENERATE_SEGMENT,
                                "Coincident projected endpoints");
                    } else {
                        unavailable(NUMERICALLY_UNDETERMINED);
                    }
                } else if (finite(previous) && finite(end)) {
                    switch (segment) {
                        case TransferGeometry.Straight ignored ->
                            segments.add(new Segment(new CurveSegment.Straight(previous, end), segmentPath));
                        case TransferGeometry.Arc arc ->
                            arc(previousExact, previous, arc, end, segmentPath, segments);
                    }
                }
                previous = end;
                previousExact = segment.end();
            }
            parts.add(new Part(start, List.copyOf(segments), partPath));
        }
    }

    private void arc(TransferGeometry.Coordinate previousExact, CurveSegment.Point start, TransferGeometry.Arc value,
                     CurveSegment.Point end, List<GeometryComponent> path, List<Segment> segments) {
        var support = point(value.support());
        if (!finite(support)) {
            return;
        }
        if (support.equals(start) && !sameXY(value.support(), previousExact)
                || support.equals(end) && !sameXY(value.support(), value.end())) {
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
            case CircularArc.Construction.Built built -> segments.add(new Segment(built.arc(), path));
            case CircularArc.Construction.Invalid error -> invalid(path, ValidationCode.GEOMETRY_INVALID_ARC, error.detail());
            case CircularArc.Construction.Undetermined ignored -> unavailable(NUMERICALLY_UNDETERMINED);
        }
    }

    private CurveSegment.Point point(TransferGeometry.Coordinate coordinate) {
        control.visit().run();
        var x = coordinate.ordinates().get(0).exactValue();
        var y = coordinate.ordinates().get(1).exactValue();
        threeDimensional |= coordinate.ordinates().size() == 3;
        if (originX == null) {
            originX = x;
            originY = y;
        }
        var point = new CurveSegment.Point(x.subtract(originX).doubleValue(), y.subtract(originY).doubleValue());
        if (!finite(point)) {
            unavailable(NUMERICALLY_UNDETERMINED);
        }
        return point;
    }

    private static boolean sameXY(TransferGeometry.Coordinate a, TransferGeometry.Coordinate b) {
        return a.ordinates().get(0).exactValue().compareTo(b.ordinates().get(0).exactValue()) == 0
                && a.ordinates().get(1).exactValue().compareTo(b.ordinates().get(1).exactValue()) == 0;
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
