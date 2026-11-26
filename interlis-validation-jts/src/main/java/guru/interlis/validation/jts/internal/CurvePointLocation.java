package guru.interlis.validation.jts.internal;

import java.math.BigDecimal;
import java.util.List;

import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.Interval.OVERLAPS_ZERO;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapter.Segment;

/** Even–odd point location on the original curves and on edges cut at enclosed intersections. */
final class CurvePointLocation {
    enum Position { INSIDE, OUTSIDE, BOUNDARY, UNDETERMINED }

    private static final int ON_BOUNDARY = -1;
    private static final int UNDETERMINED = -2;

    private CurvePointLocation() {}

    static Position locate(List<Segment> boundary, Point point, Runnable visit) {
        var query = point.isExact() ? point : Point.exact(point.exactX(), point.exactY());
        int crossings = 0;
        for (var segment : boundary) {
            visit.run();
            var curve = segment.curve();
            if (!point.isExact() && !separated(curve, point)) {
                return Position.UNDETERMINED;
            }
            if (curve.start().equals(query) || curve.end().equals(query)) {
                return Position.BOUNDARY;
            }
            int count = switch (curve) {
                case CircularArc arc -> arcCrossings(arc, query, visit);
                case CurveSegment.Straight line -> lineCrossings(line, query, visit);
            };
            if (count == ON_BOUNDARY) {
                return Position.BOUNDARY;
            }
            if (count == UNDETERMINED) {
                return Position.UNDETERMINED;
            }
            crossings += count;
        }
        return crossings % 2 == 0 ? Position.OUTSIDE : Position.INSIDE;
    }

    /** Encloses a point in the interior of the segment or returns null for an unresolved chord. */
    static Point midpoint(CurveSegment curve) {
        int digits = Interval.MAX_DIGITS;
        var sx = curve.start().xInterval(digits);
        var sy = curve.start().yInterval(digits);
        var ex = curve.end().xInterval(digits);
        var ey = curve.end().yInterval(digits);
        if (curve instanceof CircularArc arc) {
            var dx = ex.subtract(sx);
            var dy = ey.subtract(sy);
            var chord = dx.square().add(dy.square()).sqrt();
            if (chord.low().signum() <= 0) {
                return null;
            }
            var circle = arc.definition();
            var normal = curve.start().isExact() && curve.end().isExact()
                    ? circle.radiusSquared().divide(ExactCircle.chordSquared(curve.start(), curve.end())).interval(digits).sqrt()
                    : circle.radiusSquared().interval(digits).sqrt().divide(chord);
            if (arc.arcSide() < 0) {
                normal = normal.negate();
            }
            return Point.enclosed(circle.centreX(digits).subtract(dy.multiply(normal)),
                    circle.centreY(digits).add(dx.multiply(normal)));
        }
        var half = Interval.exact(new BigDecimal("0.5"), digits);
        return Point.enclosed(sx.add(ex).multiply(half), sy.add(ey).multiply(half));
    }

    private static boolean separated(CurveSegment curve, Point point) {
        int digits = Interval.MAX_DIGITS;
        var x = point.xInterval(digits);
        var y = point.yInterval(digits);
        var bounds = curve.envelope();
        if (CurveSegment.upper(x.high()) < bounds.getMinX() || CurveSegment.lower(x.low()) > bounds.getMaxX()
                || CurveSegment.upper(y.high()) < bounds.getMinY() || CurveSegment.lower(y.low()) > bounds.getMaxY()) {
            return true;
        }
        var side = CircularArc.side(curve.start(), curve.end(), x, y).sign();
        if (curve instanceof CircularArc arc) {
            var circle = arc.definition();
            var radial = x.subtract(circle.centreX(digits)).square().add(y.subtract(circle.centreY(digits)).square())
                    .subtract(circle.radiusSquared().interval(digits)).sign();
            return side == -arc.arcSide() || Math.abs(radial) == 1;
        }
        return Math.abs(side) == 1;
    }

    private static int lineCrossings(CurveSegment.Straight curve, Point point, Runnable visit) {
        var a = curve.start();
        var b = curve.end();
        if (a.isExact() && b.isExact()) {
            int orientation = Point.orientation(a, b, point);
            if (orientation == 0 && between(point.exactX(), a.exactX(), b.exactX())
                    && between(point.exactY(), a.exactY(), b.exactY())) {
                return ON_BOUNDARY;
            }
            return crossing(a.exactY().compareTo(point.exactY()), b.exactY().compareTo(point.exactY()), orientation);
        }
        int digits = Interval.MAX_DIGITS;
        var x = point.xInterval(digits);
        var y = point.yInterval(digits);
        int start = a.yInterval(digits).subtract(y).sign();
        int end = b.yInterval(digits).subtract(y).sign();
        if (start == end && Math.abs(start) == 1) {
            return 0;
        }
        var bounds = curve.envelope();
        if (point.x() > bounds.getMaxX()) {
            return 0;
        }
        if (start == OVERLAPS_ZERO || end == OVERLAPS_ZERO) {
            return UNDETERMINED;
        }
        visit.run();
        var orientation = CircularArc.side(a, b, x, y).sign();
        if (orientation == OVERLAPS_ZERO || orientation == 0) {
            return UNDETERMINED;
        }
        return crossing(start, end, orientation);
    }

    private static int crossing(int startY, int endY, int orientation) {
        // Count the lower endpoint of a crossing and omit its upper endpoint.
        return (startY > 0) != (endY > 0) && (endY > startY ? orientation > 0 : orientation < 0) ? 1 : 0;
    }

    private static int arcCrossings(CircularArc arc, Point point, Runnable visit) {
        var bounds = arc.envelope();
        if (point.y() < bounds.getMinY() || point.y() > bounds.getMaxY() || point.x() > bounds.getMaxX()) {
            return 0;
        }
        for (var endpoint : List.of(arc.start(), arc.end())) {
            if (!endpoint.isExact()) {
                // The root on a clipped circle remains enclosed. Pick a different query ordinate
                // when this ray might run through that endpoint.
                var y = endpoint.yInterval(Interval.MAX_DIGITS);
                if (between(point.exactY(), y.low(), y.high())) {
                    return UNDETERMINED;
                }
            }
        }
        double endX = Math.nextUp(bounds.getMaxX());
        if (!Double.isFinite(endX)) {
            return UNDETERMINED;
        }
        var end = Point.exact(new BigDecimal(endX), point.exactY());
        var result = CurveIntersections.between(new CurveSegment.Straight(point, end), arc, visit);
        if (result.undetermined()) {
            return UNDETERMINED;
        }
        int crossings = 0;
        for (var hit : result.hits()) {
            var contact = hit.point();
            if (contact.equals(point)) {
                return ON_BOUNDARY;
            }
            if (hit.kind() == CurveIntersections.Kind.CROSSING) {
                crossings++;
                continue;
            }
            if (contact.equals(arc.start()) || contact.equals(arc.end())) {
                int rises = departureAbove(arc, contact, visit);
                if (rises == 0) {
                    return UNDETERMINED;
                }
                if (rises > 0) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    private static int departureAbove(CircularArc arc, Point point, Runnable visit) {
        var circle = arc.definition();
        var x = ExactCircle.Rational.of(point.exactX());
        boolean extremum = circle.offsetX().signum() == 0 && circle.x().equals(x);
        int direction = arc.start().equals(point) ? -arc.arcSide() : arc.arcSide();
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            // At a vertical extremum both tangents lead towards the centre's y.
            int sign = extremum ? circle.centreY(digits).subtract(point.yInterval(digits)).sign()
                    : x.interval(digits).subtract(circle.centreX(digits)).sign();
            if (Math.abs(sign) == 1) {
                return extremum ? sign : direction * sign;
            }
        }
        return 0;
    }

    private static boolean between(BigDecimal point, BigDecimal a, BigDecimal b) {
        return point.compareTo(a.min(b)) >= 0 && point.compareTo(a.max(b)) <= 0;
    }
}
