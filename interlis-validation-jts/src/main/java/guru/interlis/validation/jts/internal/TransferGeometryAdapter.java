package guru.interlis.validation.jts.internal;

import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferReader.XtfEncoding;
import guru.interlis.validation.DataLocation.GeometryComponent;
import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.GeometryWitness;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationLimitException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static guru.interlis.validation.DataLocation.GeometryComponent.Kind.*;
import static guru.interlis.validation.GeometryEngine.Check.*;
import static java.util.stream.Collectors.joining;

final class TransferGeometryAdapter {
    record Position(BigDecimal x, BigDecimal y) {
        Position {
            x = x.stripTrailingZeros();
            y = y.stripTrailingZeros();
        }

        Position(TransferGeometry.Coordinate coordinate) {
            this(requireNumericBudget(coordinate.ordinates().get(0).exactValue()),
                    requireNumericBudget(coordinate.ordinates().get(1).exactValue()));
        }

        Position minimum(Position other) {
            return new Position(x.min(other.x), y.min(other.y));
        }
    }

    record Edge(Position start, Position end, ExactCircle circle, int direction) {
        Edge reversed() {
            return new Edge(end, start, circle, -direction);
        }

        @Override public boolean equals(Object object) {
            return object instanceof Edge other && start.equals(other.start) && end.equals(other.end)
                    && direction == other.direction
                    && (circle == null ? other.circle == null : other.circle != null && circle.sameCircle(other.circle));
        }

        @Override public int hashCode() {
            return Objects.hash(start, end, direction);
        }
    }

    record TransferMismatch(String detail, CurveSegment.Point witness) {}

    private record XtfBoundary(XtfEncoding encoding, List<BigDecimal> start, List<BigDecimal> end,
                               List<BigDecimal> intermediate, Optional<BigDecimal> radius) {
        XtfBoundary(XtfEncoding encoding, TransferGeometry.Coordinate start, TransferGeometry.Segment segment) {
            this(encoding, ordinates(start), ordinates(segment.end()),
                    segment instanceof TransferGeometry.Arc arc ? ordinates(arc.support()) : List.of(),
                    segment instanceof TransferGeometry.Arc arc ? arc.radius().map(r ->
                            requireNumericBudget(r.sourceValue().exactValue()).stripTrailingZeros()) : Optional.empty());
        }

        private static List<BigDecimal> ordinates(TransferGeometry.Coordinate coordinate) {
            return coordinate.ordinates().stream()
                    .map(value -> requireNumericBudget(value.exactValue()).stripTrailingZeros()).toList();
        }

        XtfBoundary reversed() {
            return new XtfBoundary(encoding, end, start, intermediate,
                    encoding == XtfEncoding.XTF23 ? radius.map(BigDecimal::negate) : radius);
        }
    }

    record Segment(CurveSegment curve, List<GeometryComponent> path, Edge edge, Optional<XtfBoundary> xtfBoundary) {
        Segment(CurveSegment curve, List<GeometryComponent> path, Edge edge) {
            this(curve, path, edge, Optional.empty());
        }

        Segment reversed() {
            return new Segment(curve.reversed(), path, edge.reversed(), xtfBoundary.map(XtfBoundary::reversed));
        }

        /** Compares the transferred values of an already matched geometric edge. */
        Optional<TransferMismatch> transferIdentityMismatch(Segment other, boolean sameDirection) {
            if (xtfBoundary.isEmpty() || other.xtfBoundary().isEmpty()) {
                return Optional.empty();
            }
            var boundary = xtfBoundary.orElseThrow();
            var adjacent = other.xtfBoundary().orElseThrow();
            var aligned = sameDirection ? adjacent : adjacent.reversed();
            if (!boundary.start().equals(aligned.start())) {
                return Optional.of(new TransferMismatch("Shared XTF AREA edge has different support point ordinates "
                        + display(boundary.start()) + " and " + display(aligned.start()), curve.start()));
            }
            if (!boundary.end().equals(aligned.end())) {
                return Optional.of(new TransferMismatch("Shared XTF AREA edge has different support point ordinates "
                        + display(boundary.end()) + " and " + display(aligned.end()), curve.end()));
            }
            if (!boundary.intermediate().equals(aligned.intermediate())) {
                return Optional.of(new TransferMismatch("Shared XTF AREA arc has different intermediate ordinates "
                        + display(boundary.intermediate()) + " and " + display(aligned.intermediate()), curve.start()));
            }
            boolean mixedEncodings = boundary.encoding() != adjacent.encoding();
            var radius = mixedEncodings ? boundary.radius().map(BigDecimal::abs) : boundary.radius();
            var adjacentRadius = mixedEncodings ? aligned.radius().map(BigDecimal::abs) : aligned.radius();
            if (!radius.equals(adjacentRadius)) {
                return Optional.of(new TransferMismatch("Shared XTF AREA arc has different transferred radii "
                        + boundary.radius().map(BigDecimal::toPlainString).orElse("(absent)") + " and "
                        + adjacent.radius().map(BigDecimal::toPlainString).orElse("(absent)"), curve.start()));
            }
            if (sameDirection && boundary.encoding() == XtfEncoding.XTF24 && !mixedEncodings) {
                return Optional.of(new TransferMismatch("Shared XTF 2.4 AREA edges run in the same direction", curve.start()));
            }
            return Optional.empty();
        }

        private static String display(List<BigDecimal> ordinates) {
            return ordinates.stream().map(BigDecimal::toPlainString).collect(joining(", ", "[", "]"));
        }
    }

    record Part(Position start, TransferGeometry.Coordinate first, TransferGeometry.Coordinate last,
                List<Segment> segments, List<GeometryComponent> path) {}
    record Ring(List<Part> parts, List<GeometryComponent> path, List<Segment> segments) {}
    record Surface(Ring shell, List<Ring> holes) {}
    record Result(GeometryEngine.Check check, List<Part> lines, List<Surface> surfaces, boolean threeDimensional,
                  Position origin) {}

    private final GeometryEngine.Input input;
    private final GeometryEngine.Control control;
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
        return new Result(check, List.copyOf(lines), List.copyOf(surfaces), threeDimensional,
                origin == null ? new Position(BigDecimal.ZERO, BigDecimal.ZERO) : origin);
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
        return new Ring(List.copyOf(parts), path, parts.stream().flatMap(part -> part.segments().stream()).toList());
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
            var previousCoordinate = part.start();
            var segments = new ArrayList<Segment>();
            for (int j = 0; j < part.segments().size(); j++) {
                control.visit().run();
                var segment = part.segments().get(j);
                var endExact = new Position(segment.end());
                var end = project(endExact, segment.end());
                var segmentPath = at(partPath, SEGMENT, j);
                if (previousExact.equals(endExact)) {
                    invalid(segmentPath, ValidationCode.GEOMETRY_DEGENERATE_SEGMENT,
                            "Coincident projected endpoints", endExact);
                } else if (previous != null && end != null) {
                    var startCoordinate = previousCoordinate;
                    var xtfBoundary = input.xtfAreaEncoding().map(encoding -> new XtfBoundary(encoding, startCoordinate, segment));
                    switch (segment) {
                        case TransferGeometry.Straight ignored ->
                            segments.add(new Segment(new CurveSegment.Straight(previous, end), segmentPath,
                                    new Edge(previousExact, endExact, null, 0), xtfBoundary));
                        case TransferGeometry.Arc arc ->
                            arc(previousExact, endExact, previous, end, arc, segmentPath, xtfBoundary, segments);
                    }
                }
                previous = end;
                previousExact = endExact;
                previousCoordinate = segment.end();
            }
            var end = part.segments().isEmpty() ? part.start() : part.segments().getLast().end();
            parts.add(new Part(start, part.start(), end, List.copyOf(segments), partPath));
        }
    }

    private void arc(Position startExact, Position endExact, CurveSegment.Point start, CurveSegment.Point end,
                     TransferGeometry.Arc value, List<GeometryComponent> path, Optional<XtfBoundary> xtfBoundary,
                     List<Segment> segments) {
        var supportExact = new Position(value.support());
        var support = project(supportExact, value.support());
        if (support == null) {
            return;
        }
        var quantum = BigDecimal.ZERO;
        if (value.radius().isPresent()) {
            requireNumericBudget(value.radius().orElseThrow().sourceValue().exactValue());
            if (input.quantum().isEmpty()) {
                unavailable(MISSING_PARAMETERS);
                return;
            }
            quantum = requireNumericBudget(input.quantum().orElseThrow());
            if (quantum.signum() <= 0) {
                unavailable(NUMERICALLY_UNDETERMINED);
                return;
            }
        }
        switch (CircularArc.build(start, support, end, value.radius(), quantum, true, control.visit())) {
            case CircularArc.Construction.Built built -> {
                var arc = built.arc();
                segments.add(new Segment(arc, path, new Edge(startExact, endExact, arc.definition(), -arc.arcSide()),
                        xtfBoundary));
            }
            case CircularArc.Construction.Invalid error ->
                invalid(path, ValidationCode.GEOMETRY_INVALID_ARC, error.detail(),
                        error.chordTooLong() ? endExact : supportExact);
            case CircularArc.Construction.Undetermined ignored -> unavailable(NUMERICALLY_UNDETERMINED);
        }
    }

    static Position absolute(CurveSegment.Point point, Position origin) {
        return new Position(origin.x().add(point.exactX()), origin.y().add(point.exactY()));
    }

    private CurveSegment.Point project(Position position, TransferGeometry.Coordinate coordinate) {
        control.visit().run();
        threeDimensional |= coordinate.ordinates().size() == 3;
        if (origin == null) {
            origin = position;
        }
        var dx = position.x().subtract(origin.x());
        var dy = position.y().subtract(origin.y());
        var point = new CurveSegment.Point(dx, dy);
        if (!finite(point)) {
            unavailable(NUMERICALLY_UNDETERMINED);
            return null;
        }
        return point;
    }

    private static boolean finite(CurveSegment.Point point) {
        return Double.isFinite(point.x()) && Double.isFinite(point.y());
    }

    private static BigDecimal requireNumericBudget(BigDecimal value) {
        // Match the scalar validator's input budget before converting source decimals to rational numbers.
        if (value.unscaledValue().bitLength() > 16_384 || Math.abs((long) value.scale()) > 10_000) {
            throw new ValidationLimitException("Numeric magnitude or scale exceeds the local comparison budget");
        }
        return value;
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

    private void invalid(List<GeometryComponent> path, ValidationCode code, String detail, Position witness) {
        check = INVALID;
        var coordinate = new GeometryWitness(GeometryWitness.Kind.VERTEX, witness.x(), witness.y());
        control.findings().accept(new GeometryEngine.Finding(input.location(), path, code, detail,
                Optional.empty(), Optional.of(coordinate)));
    }

    private static List<GeometryComponent> at(List<GeometryComponent> path, GeometryComponent.Kind kind, int offset) {
        var next = new ArrayList<>(path);
        next.add(new GeometryComponent(kind, offset + 1));
        return List.copyOf(next);
    }
}
