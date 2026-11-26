package guru.interlis.validation.jts.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.validation.jts.internal.ExactCircle.Rational;
import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.CurveSegment.Straight;
import static guru.interlis.validation.jts.internal.Interval.INITIAL_DIGITS;
import static guru.interlis.validation.jts.internal.Interval.MAX_DIGITS;
import static guru.interlis.validation.jts.internal.Interval.OVERLAPS_ZERO;

final class CurveIntersections {
    enum Kind { CONTACT, CROSSING, OVERLAP }
    record Hit(Point point, Kind kind) {}
    record Result(List<Hit> hits, boolean undetermined) {
        Result {
            hits = List.copyOf(hits);
        }
    }

    private record DecimalVector(BigDecimal x, BigDecimal y) {
        static DecimalVector delta(Point start, Point end) {
            return new DecimalVector(end.exactX().subtract(start.exactX()), end.exactY().subtract(start.exactY()));
        }

        BigDecimal cross(DecimalVector other) {
            return x.multiply(other.y).subtract(y.multiply(other.x));
        }

        IntervalVector onLine(Point anchor, BigDecimal numerator, BigDecimal denominator, int digits) {
            return new IntervalVector(
                    intersectionCoordinate(anchor.exactX().multiply(denominator).add(x.multiply(numerator)), denominator, digits),
                    intersectionCoordinate(anchor.exactY().multiply(denominator).add(y.multiply(numerator)), denominator, digits));
        }

        IntervalVector interval(int digits) {
            return new IntervalVector(Interval.exact(x, digits), Interval.exact(y, digits));
        }
    }

    private record RationalPoint(Rational x, Rational y) {
        static RationalPoint secondRoot(Point anchor, ExactCircle circle, Rational dx, Rational dy) {
            var startX = Rational.of(anchor.exactX());
            var startY = Rational.of(anchor.exactY());
            var offsetX = startX.subtract(circle.x());
            var offsetY = startY.subtract(circle.y());
            var parameter = offsetX.multiply(dx).add(offsetY.multiply(dy)).multiply(Rational.of(-2))
                    .divide(dx.square().add(dy.square()));
            return new RationalPoint(startX.add(dx.multiply(parameter)), startY.add(dy.multiply(parameter)));
        }

        IntervalVector interval(int digits) {
            return new IntervalVector(intersectionCoordinate(new BigDecimal(x.numerator()), new BigDecimal(x.denominator()), digits),
                    intersectionCoordinate(new BigDecimal(y.numerator()), new BigDecimal(y.denominator()), digits));
        }
    }

    private static Interval intersectionCoordinate(BigDecimal numerator, BigDecimal denominator, int digits) {
        try {
            return Interval.exact(numerator.divide(denominator), digits);
        } catch (ArithmeticException nonterminating) {
            return Interval.ratio(numerator, denominator, digits);
        }
    }

    private record IntervalVector(Interval x, Interval y) {
        static IntervalVector at(Point point, int digits) {
            return new IntervalVector(Interval.exact(point.exactX(), digits), Interval.exact(point.exactY(), digits));
        }

        static IntervalVector centre(ExactCircle circle, int digits) {
            return new IntervalVector(circle.centreX(digits), circle.centreY(digits));
        }

        IntervalVector add(IntervalVector other) {
            return new IntervalVector(x.add(other.x), y.add(other.y));
        }

        IntervalVector subtract(IntervalVector other) {
            return new IntervalVector(x.subtract(other.x), y.subtract(other.y));
        }

        IntervalVector multiply(Interval factor) {
            return new IntervalVector(x.multiply(factor), y.multiply(factor));
        }

        Interval dot(IntervalVector other) {
            return x.multiply(other.x).add(y.multiply(other.y));
        }

        Interval cross(IntervalVector other) {
            return x.multiply(other.y).subtract(y.multiply(other.x));
        }

        Interval squaredLength() {
            return x.square().add(y.square());
        }

        IntervalVector normal() {
            return new IntervalVector(y.negate(), x);
        }

        Point point() {
            return Point.enclosed(x, y);
        }
    }

    static Result between(CurveSegment first, CurveSegment second) {
        return between(first, second, () -> {});
    }

    static Result between(CurveSegment first, CurveSegment second, Runnable visit) {
        if (!first.envelope().intersects(second.envelope())) {
            return result();
        }
        return switch (first) {
            case Straight line -> switch (second) {
                case Straight other -> lines(line, other);
                case CircularArc arc -> lineArc(line, arc, visit);
            };
            case CircularArc arc -> switch (second) {
                case Straight line -> lineArc(line, arc, visit);
                case CircularArc other -> arcs(arc, other, visit);
            };
        };
    }

    private static Result lines(Straight a, Straight b) {
        if (onSameSide(a, b) || onSameSide(b, a)) {
            return result();
        }
        var directionA = DecimalVector.delta(a.start(), a.end());
        var directionB = DecimalVector.delta(b.start(), b.end());
        var offset = DecimalVector.delta(a.start(), b.start());
        var denominator = directionA.cross(directionB);
        var positionB = offset.cross(directionA);
        if (denominator.signum() == 0) {
            return positionB.signum() == 0 ? collinear(a, b) : result();
        }
        var positionA = offset.cross(directionB);
        if (!unitInterval(positionA, denominator) || !unitInterval(positionB, denominator)) {
            return result();
        }
        if (positionA.signum() == 0) return contact(a.start());
        if (positionA.compareTo(denominator) == 0) return contact(a.end());
        if (positionB.signum() == 0) return contact(b.start());
        if (positionB.compareTo(denominator) == 0) return contact(b.end());

        var point = directionA.onLine(a.start(), positionA, denominator, INITIAL_DIGITS).point();
        return new Result(List.of(new Hit(point, Kind.CROSSING)), false);
    }

    private static boolean onSameSide(Straight axis, Straight candidate) {
        int side = Point.filteredOrientation(axis.start(), axis.end(), candidate.start());
        return side != OVERLAPS_ZERO && side == Point.filteredOrientation(axis.start(), axis.end(), candidate.end());
    }

    private static boolean unitInterval(BigDecimal value, BigDecimal denominator) {
        int sign = denominator.signum();
        return sign * value.signum() >= 0 && sign * value.compareTo(denominator) <= 0;
    }

    private static Result collinear(Straight a, Straight b) {
        var loA = compare(a.start(), a.end()) <= 0 ? a.start() : a.end();
        var hiA = loA.equals(a.start()) ? a.end() : a.start();
        var loB = compare(b.start(), b.end()) <= 0 ? b.start() : b.end();
        var hiB = loB.equals(b.start()) ? b.end() : b.start();
        var low = compare(loA, loB) >= 0 ? loA : loB;
        var high = compare(hiA, hiB) <= 0 ? hiA : hiB;
        return switch (Integer.signum(compare(low, high))) {
            case -1 -> new Result(List.of(new Hit(low, Kind.OVERLAP), new Hit(high, Kind.OVERLAP)), false);
            case 0 -> contact(low);
            default -> result();
        };
    }

    private static int compare(Point a, Point b) {
        int x = a.exactX().compareTo(b.exactX());
        return x == 0 ? a.exactY().compareTo(b.exactY()) : x;
    }

    private static boolean onLine(Straight line, Point point) {
        return between(line.start().exactX(), line.end().exactX(), point.exactX())
                && between(line.start().exactY(), line.end().exactY(), point.exactY())
                && Point.orientation(line.start(), line.end(), point) == 0;
    }

    private static boolean between(BigDecimal start, BigDecimal end, BigDecimal value) {
        return value.compareTo(start.min(end)) >= 0 && value.compareTo(start.max(end)) <= 0;
    }

    private static Result lineArc(Straight line, CircularArc arc, Runnable visit) {
        var known = new ArrayList<Hit>();
        for (Point endpoint : List.of(line.start(), line.end())) {
            if (arc.containsOnCircle(endpoint) && arc.definition().pointSide(endpoint) == 0) {
                addContact(known, endpoint);
            }
        }
        for (Point endpoint : List.of(arc.start(), arc.end())) {
            if (onLine(line, endpoint)) {
                addContact(known, endpoint);
            }
        }
        if (known.size() == 2) {
            return new Result(known, false);
        }

        // Anchor at the contact when one is already known. Factoring t=0 protects the
        // contact identity and preserves a distinct second root even if it is very close.
        var definition = arc.definition();
        int sign = definition.lineDiscriminant(line.start(), line.end());
        if (sign < 0 || sign == 0 && !known.isEmpty()) {
            return new Result(known, false);
        }
        var direction = DecimalVector.delta(line.start(), line.end());
        var rational = known.isEmpty() || definition.radicand().signum() != 0 ? null
                : RationalPoint.secondRoot(known.getFirst().point(), definition, Rational.of(direction.x), Rational.of(direction.y));
        for (int digits = INITIAL_DIGITS; digits <= MAX_DIGITS; digits *= 2) {
            visit.run();
            var hits = new ArrayList<>(known);
            var vector = direction.interval(digits);
            var centre = IntervalVector.centre(definition, digits);
            if (!known.isEmpty()) {
                var point = known.getFirst().point();
                var anchor = IntervalVector.at(point, digits);
                var root = rational == null ? anchor.add(vector.multiply(factor(anchor.subtract(centre), vector, digits)))
                        : rational.interval(digits);
                if (lineArcRoot(line, arc, root, Kind.CROSSING, hits) && separated(hits, digits)) {
                    return new Result(hits, false);
                }
            } else {
                var start = IntervalVector.at(line.start(), digits);
                var offset = start.subtract(centre);
                var lengthSquared = vector.squaredLength();
                var middle = offset.dot(vector).negate().divide(lengthSquared);
                if (sign == 0) {
                    if (lineArcRoot(line, arc, start.add(vector.multiply(middle)), Kind.CONTACT, hits)) {
                        return new Result(hits, false);
                    }
                } else {
                    var discriminant = definition.radiusSquared().interval(digits).multiply(lengthSquared)
                            .subtract(vector.cross(offset).square());
                    if (discriminant.low().signum() <= 0) {
                        continue;
                    }
                    var delta = discriminant.sqrt().divide(lengthSquared);
                    if (lineArcRoot(line, arc, start.add(vector.multiply(middle.subtract(delta))), Kind.CROSSING, hits)
                            && lineArcRoot(line, arc, start.add(vector.multiply(middle.add(delta))), Kind.CROSSING, hits)
                            && separated(hits, digits)) {
                        return new Result(hits, false);
                    }
                }
            }
        }
        return new Result(known, true);
    }

    private static Interval factor(IntervalVector offset, IntervalVector direction, int digits) {
        return offset.dot(direction).multiply(Interval.exact(BigDecimal.valueOf(-2), digits))
                .divide(direction.squaredLength());
    }

    private static boolean lineArcRoot(Straight line, CircularArc arc, IntervalVector point, Kind kind, List<Hit> hits) {
        int digits = point.x.digits();
        if (line.start().exactX().equals(line.end().exactX())) {
            point = new IntervalVector(Interval.exact(line.start().exactX(), digits), point.y);
        } else if (line.start().exactY().equals(line.end().exactY())) {
            point = new IntervalVector(point.x, Interval.exact(line.start().exactY(), digits));
        }
        int onArc = arcMembership(arc, point);
        if (onArc == 0) {
            return true;
        }
        var along = DecimalVector.delta(line.start(), line.end()).interval(digits);
        var fromStart = point.subtract(IntervalVector.at(line.start(), digits)).dot(along).sign();
        var fromEnd = point.subtract(IntervalVector.at(line.end(), digits)).dot(along).sign();
        if (fromStart < 0 || fromEnd == 1) {
            return true;
        }
        if (onArc == OVERLAPS_ZERO || fromStart == OVERLAPS_ZERO || fromEnd == OVERLAPS_ZERO) {
            return false;
        }
        hits.add(new Hit(point.point(), kind));
        return true;
    }

    private static Result arcs(CircularArc a, CircularArc b, Runnable visit) {
        if (a.definition().sameCircle(b.definition())) {
            return coincident(a, b);
        }
        if (a.definition().sameCentre(b.definition())) {
            return result();
        }
        var known = new ArrayList<Hit>();
        for (Point endpoint : List.of(a.start(), a.end())) {
            if (b.containsOnCircle(endpoint) && b.definition().pointSide(endpoint) == 0) {
                addContact(known, endpoint);
            }
        }
        for (Point endpoint : List.of(b.start(), b.end())) {
            if (a.containsOnCircle(endpoint) && a.definition().pointSide(endpoint) == 0) {
                addContact(known, endpoint);
            }
        }
        if (known.size() == 2) {
            return new Result(known, false);
        }
        int exactSign = a.definition().circleDiscriminant(b.definition());
        if (exactSign < 0 || exactSign == 0 && !known.isEmpty()) {
            return new Result(known, false);
        }
        var definitionA = a.definition();
        var definitionB = b.definition();
        var rational = known.isEmpty() || definitionA.radicand().signum() != 0 || definitionB.radicand().signum() != 0
                ? null : RationalPoint.secondRoot(known.getFirst().point(), definitionA,
                        definitionA.y().subtract(definitionB.y()), definitionB.x().subtract(definitionA.x()));
        for (int digits = INITIAL_DIGITS; digits <= MAX_DIGITS; digits *= 2) {
            visit.run();
            var hits = new ArrayList<>(known);
            if (rational != null) {
                if (arcRoot(a, b, rational.interval(digits), Kind.CROSSING, hits) && separated(hits, digits)) {
                    return new Result(hits, false);
                }
                continue;
            }
            var ca = IntervalVector.centre(definitionA, digits);
            var cb = IntervalVector.centre(definitionB, digits);
            // Subtracting the two circle equations leaves their radical axis.
            var difference = cb.subtract(ca);
            var direction = difference.normal();
            var distanceSquared = difference.squaredLength();
            if (distanceSquared.low().signum() <= 0) {
                continue;
            }
            if (!known.isEmpty()) {
                var anchor = IntervalVector.at(known.getFirst().point(), digits);
                var parameter = factor(anchor.subtract(ca), direction, digits);
                if (parameter.sign() != OVERLAPS_ZERO
                        && arcRoot(a, b, anchor.add(direction.multiply(parameter)), Kind.CROSSING, hits)
                        && separated(hits, digits)) {
                    return new Result(hits, false);
                }
            } else {
                var ra2 = a.definition().radiusSquared().interval(digits);
                var rb2 = b.definition().radiusSquared().interval(digits);
                var numerator = distanceSquared.add(ra2).subtract(rb2);
                var twoDistanceSquared = distanceSquared.multiply(Interval.exact(BigDecimal.valueOf(2), digits));
                var discriminant = ra2.multiply(distanceSquared).multiply(Interval.exact(BigDecimal.valueOf(4), digits))
                        .subtract(numerator.square());
                var middle = ca.add(difference.multiply(numerator.divide(twoDistanceSquared)));
                if (exactSign == 0) {
                    if (arcRoot(a, b, middle, Kind.CONTACT, hits)) {
                        return new Result(hits, false);
                    }
                } else if (discriminant.low().signum() > 0) {
                    var delta = direction.multiply(discriminant.sqrt().divide(twoDistanceSquared));
                    if (arcRoot(a, b, middle.add(delta), Kind.CROSSING, hits)
                            && arcRoot(a, b, middle.subtract(delta), Kind.CROSSING, hits)
                            && separated(hits, digits)) {
                        return new Result(hits, false);
                    }
                }
            }
        }
        return new Result(known, true);
    }

    private static boolean separated(List<Hit> hits, int digits) {
        if (hits.size() < 2) {
            return true;
        }
        var a = hits.getFirst().point();
        var b = hits.getLast().point();
        return disjoint(a.xInterval(digits), b.xInterval(digits)) || disjoint(a.yInterval(digits), b.yInterval(digits));
    }

    private static boolean disjoint(Interval a, Interval b) {
        return a.high().compareTo(b.low()) < 0 || b.high().compareTo(a.low()) < 0;
    }

    private static Result coincident(CircularArc a, CircularArc b) {
        if (a.start().equals(b.start()) && a.end().equals(b.end()) && a.arcSide() == b.arcSide()
                || a.start().equals(b.end()) && a.end().equals(b.start()) && a.arcSide() == -b.arcSide()) {
            return new Result(List.of(new Hit(a.start(), Kind.OVERLAP)), false);
        }
        for (Point endpoint : List.of(a.start(), a.end())) {
            if (Point.orientation(b.start(), b.end(), endpoint) == b.arcSide()) {
                return new Result(List.of(new Hit(endpoint, Kind.OVERLAP)), false);
            }
        }
        for (Point endpoint : List.of(b.start(), b.end())) {
            if (Point.orientation(a.start(), a.end(), endpoint) == a.arcSide()) {
                return new Result(List.of(new Hit(endpoint, Kind.OVERLAP)), false);
            }
        }
        var hits = new ArrayList<Hit>();
        for (Point endpoint : List.of(a.start(), a.end())) {
            if (b.start().equals(endpoint) || b.end().equals(endpoint)) {
                addContact(hits, endpoint);
            }
        }
        return new Result(hits, false);
    }

    private static int arcMembership(CircularArc arc, IntervalVector point) {
        int side = CircularArc.side(arc.start(), arc.end(), point.x, point.y).sign();
        if (side == OVERLAPS_ZERO) {
            return OVERLAPS_ZERO;
        }
        return side == arc.arcSide() || side == 0 ? 1 : 0;
    }

    private static boolean arcRoot(CircularArc a, CircularArc b, IntervalVector point, Kind kind, List<Hit> hits) {
        int onA = arcMembership(a, point);
        int onB = arcMembership(b, point);
        if (onA == 0 || onB == 0) {
            return true;
        }
        if (onA == OVERLAPS_ZERO || onB == OVERLAPS_ZERO) {
            return false;
        }
        hits.add(new Hit(point.point(), kind));
        return true;
    }

    private static void addContact(List<Hit> hits, Point point) {
        if (hits.stream().noneMatch(hit -> hit.point().equals(point))) {
            hits.add(new Hit(point, Kind.CONTACT));
        }
    }

    private static Result contact(Point point) {
        return new Result(List.of(new Hit(point, Kind.CONTACT)), false);
    }

    private static Result result() {
        return new Result(List.of(), false);
    }
}
