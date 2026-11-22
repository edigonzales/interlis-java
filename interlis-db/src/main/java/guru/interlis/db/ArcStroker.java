package guru.interlis.db;

import guru.interlis.db.DbMapping.Kind;
import guru.interlis.db.GeometryBinary.CoordinateBudget;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.MultiPolyline;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferGeometry.Ring;
import guru.interlis.transfer.TransferGeometry.Surface;
import guru.interlis.transfer.TransferGeometry.Segment;
import guru.interlis.transfer.TransferGeometry.Straight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.GeometryBinary.binaryOrdinate;
import static guru.interlis.db.GeometryBinary.circleCenter;
import static guru.interlis.db.GeometryBinary.cross;
import static guru.interlis.db.GeometryBinary.number;

/** Converts XY arcs to line segments for one stored geometry value. */
final class ArcStroker {
    private final double tolerance;
    private final CoordinateBudget budget;

    ArcStroker(double tolerance, Kind kind) {
        this.tolerance = tolerance;
        budget = new CoordinateBudget(kind);
    }

    TransferGeometry stroke(TransferGeometry value) {
        return switch (value) {
            case Polyline line -> line(line);
            case MultiPolyline multi -> new MultiPolyline(multi.lines().stream().map(this::line).toList());
            case Surface surface -> new Surface(ring(surface.shell()), surface.holes().stream().map(this::ring).toList());
            default -> throw new DbException("Expected a line or surface");
        };
    }

    private Polyline line(Polyline line) {
        return new Polyline(List.of(linearPart(GeometryBinary.linePart(line))));
    }

    private Ring ring(Ring ring) {
        var part = linearPart(GeometryBinary.ringPart(ring, budget));
        return new Ring(List.of(new Polyline(List.of(part))));
    }

    private CurvePart linearPart(CurvePart part) {
        var segments = new ArrayList<Segment>();
        Coordinate start = part.start();
        budget.consume(1);
        for (var segment : part.segments()) {
            if (segment instanceof Arc arc) appendArc(segments, start, arc);
            else {
                budget.consume(1);
                segments.add(segment);
            }
            start = segment.end();
        }
        return new CurvePart(part.start(), segments);
    }

    private void appendArc(List<Segment> segments, Coordinate start, Arc arc) {
        if (arc.radius().isPresent()) throw new DbException("An explicit arc radius is unsupported");
        var end = arc.end();
        var startX = binaryOrdinate(start, 0);
        var startY = binaryOrdinate(start, 1);
        var endX = binaryOrdinate(end, 0);
        var endY = binaryOrdinate(end, 1);
        int xOrder = startX.compareTo(endX);
        boolean reverse = xOrder > 0 || (xOrder == 0 && startY.compareTo(endY) > 0);
        var originX = reverse ? endX : startX;
        var originY = reverse ? endY : startY;
        var chordX = (reverse ? startX : endX).subtract(originX);
        var chordY = (reverse ? startY : endY).subtract(originY);
        var supportX = binaryOrdinate(arc.support(), 0).subtract(originX);
        var supportY = binaryOrdinate(arc.support(), 1).subtract(originY);
        var side = cross(chordX, chordY, supportX, supportY);
        var center = circleCenter(supportX, supportY, chordX, chordY, side);
        if (center == null) {
            if (!withinChord(supportX, chordX) || !withinChord(supportY, chordY)) {
                budget.consume(1);
                segments.add(new Straight(arc.support()));
            }
            budget.consume(1);
            segments.add(new Straight(end));
            return;
        }

        var secondX = supportX.subtract(center.x());
        var secondY = supportY.subtract(center.y());
        double radius = Math.hypot(center.x().doubleValue(), center.y().doubleValue());
        if (!Double.isFinite(radius) || radius == 0) throw new DbException("Arc radius cannot be represented");
        double maxStep = 4 * Math.asin(Math.sqrt(Math.min(1, tolerance / radius / 2)));
        boolean closed = chordX.signum() == 0 && chordY.signum() == 0;
        int direction = closed ? 1 : -side.signum();
        var remainderX = chordX.subtract(supportX);
        var remainderY = chordY.subtract(supportY);
        double first = closed ? Math.PI : sweep(center.x().negate(), center.y().negate(), supportX, supportY, radius, direction);
        double second = closed ? Math.PI : sweep(secondX, secondY, remainderX, remainderY, radius, direction);
        int minimumSteps = closed ? 2 : 1;
        double firstSteps = Math.max(minimumSteps, Math.ceil(first / maxStep));
        double secondSteps = Math.max(minimumSteps, Math.ceil(second / maxStep));
        budget.consume(firstSteps + secondSteps);

        int firstIndex = segments.size();
        appendSteps(segments, originX.doubleValue(), originY.doubleValue(), center.x().negate().doubleValue(),
                center.y().negate().doubleValue(), direction * first, (int) firstSteps, arc.support());
        appendSteps(segments, binaryOrdinate(arc.support(), 0).doubleValue(), binaryOrdinate(arc.support(), 1).doubleValue(),
                secondX.doubleValue(), secondY.doubleValue(), direction * second, (int) secondSteps, reverse ? start : end);
        if (reverse) {
            // Compute identical XY ordinates for a shared arc in either direction.
            var interior = segments.subList(firstIndex, segments.size());
            interior.removeLast();
            java.util.Collections.reverse(interior);
            segments.add(new Straight(end));
        }
    }

    private static boolean withinChord(BigDecimal support, BigDecimal chord) {
        return support.compareTo(chord.min(BigDecimal.ZERO)) >= 0 && support.compareTo(chord.max(BigDecimal.ZERO)) <= 0;
    }

    private static double sweep(BigDecimal radialX, BigDecimal radialY, BigDecimal dx, BigDecimal dy,
                                double radius, int direction) {
        double chord = Math.hypot(dx.doubleValue(), dy.doubleValue());
        double minor = 2 * Math.asin(Math.min(1, chord / radius / 2));
        int side = cross(radialX, radialY, dx, dy).signum();
        return side == 0 || side == direction ? minor : 2 * Math.PI - minor;
    }

    private static void appendSteps(List<Segment> segments, double x, double y, double radialX, double radialY,
                                    double sweep, int steps, Coordinate end) {
        for (int step = 1; step < steps; step++) {
            double angle = sweep * step / steps;
            double sine = Math.sin(angle);
            double halfSine = Math.sin(angle / 2);
            double cosineMinusOne = -2 * halfSine * halfSine;
            // Rotate about the centre as a displacement from the start to retain shallow arcs.
            double px = x + (radialX * cosineMinusOne - radialY * sine);
            double py = y + (radialY * cosineMinusOne + radialX * sine);
            segments.add(new Straight(new Coordinate(List.of(number(px), number(py)))));
        }
        segments.add(new Straight(end));
    }
}
