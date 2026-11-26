package guru.interlis.validation.jts.internal;

import guru.interlis.validation.jts.internal.CurveSegment.Point;
import guru.interlis.validation.jts.internal.ExactCircle.Rational;
import guru.interlis.validation.jts.internal.LensHeight.Radicals;

import java.util.Comparator;

import static guru.interlis.validation.jts.internal.Interval.OVERLAPS_ZERO;

/** Order known positions from the start of a directed segment, including through a major arc. */
final class CurveOrder implements Comparator<Point> {
    static final class Unknown extends RuntimeException {}

    private record Radius(Radicals x, Radicals y) {
        static Radius at(Point point, ExactCircle circle) {
            return new Radius(new Radicals(Rational.of(point.exactX()).subtract(circle.x()),
                    circle.offsetX().negate(), circle.radicand()),
                    new Radicals(Rational.of(point.exactY()).subtract(circle.y()),
                            circle.offsetY().negate(), circle.radicand()));
        }

        int cross(Radius other, Runnable visit) {
            return x.times(other.y).minus(y.times(other.x)).sign(visit);
        }

        int dot(Radius other, Runnable visit) {
            return x.times(other.x).plus(y.times(other.y)).sign(visit);
        }
    }

    private record EnclosedRadius(Interval x, Interval y) {
        static EnclosedRadius at(Point point, ExactCircle circle, int digits) {
            return new EnclosedRadius(point.xInterval(digits).subtract(circle.centreX(digits)),
                    point.yInterval(digits).subtract(circle.centreY(digits)));
        }

        int cross(EnclosedRadius other) {
            return x.multiply(other.y).subtract(y.multiply(other.x)).sign();
        }

        int dot(EnclosedRadius other) {
            return x.multiply(other.x).add(y.multiply(other.y)).sign();
        }
    }

    private final CurveSegment curve;
    private final Runnable visit;
    private final Radius start;

    CurveOrder(CurveSegment curve, Runnable visit) {
        this.curve = curve;
        this.visit = visit;
        start = curve instanceof CircularArc arc ? Radius.at(curve.start(), arc.definition()) : null;
    }

    @Override public int compare(Point first, Point second) {
        visit.run();
        if (first.equals(second)) {
            return 0;
        }
        if (first.equals(curve.start()) || second.equals(curve.end())) {
            return -1;
        }
        if (second.equals(curve.start()) || first.equals(curve.end())) {
            return 1;
        }
        if (!(curve instanceof CircularArc arc)) {
            int direction = curve.end().exactX().compareTo(curve.start().exactX());
            if (first.isExact() && second.isExact()) {
                return direction == 0
                        ? curve.end().exactY().compareTo(curve.start().exactY()) * first.exactY().compareTo(second.exactY())
                        : direction * first.exactX().compareTo(second.exactX());
            }
            return compareEnclosedLine(first, second, direction);
        }
        if (!first.isExact() || !second.isExact()) {
            return compareEnclosedArc(first, second, arc);
        }
        var a = Radius.at(first, arc.definition());
        var b = Radius.at(second, arc.definition());
        int direction = -arc.arcSide();
        int order = Integer.compare(sector(a, direction), sector(b, direction));
        if (order != 0) {
            return order;
        }
        int cross = a.cross(b, visit);
        if (cross == OVERLAPS_ZERO || cross == 0) {
            throw new Unknown();
        }
        return -direction * cross;
    }

    private int sector(Radius ray, int direction) {
        int turn = start.cross(ray, visit);
        if (turn == OVERLAPS_ZERO) {
            throw new Unknown();
        }
        return sector(turn, turn == 0 ? start.dot(ray, visit) : 0, direction);
    }

    private int compareEnclosedLine(Point first, Point second, int direction) {
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            int order = direction == 0 ? first.yInterval(digits).subtract(second.yInterval(digits)).sign()
                    : first.xInterval(digits).subtract(second.xInterval(digits)).sign();
            if (Math.abs(order) == 1) {
                return order * (direction == 0
                        ? curve.end().exactY().compareTo(curve.start().exactY()) : direction);
            }
        }
        throw new Unknown();
    }

    private int compareEnclosedArc(Point first, Point second, CircularArc arc) {
        int direction = -arc.arcSide();
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            var origin = EnclosedRadius.at(arc.start(), arc.definition(), digits);
            var a = EnclosedRadius.at(first, arc.definition(), digits);
            var b = EnclosedRadius.at(second, arc.definition(), digits);
            int aSector = intervalSector(origin, a, direction);
            int bSector = intervalSector(origin, b, direction);
            if (aSector < 0 || bSector < 0) {
                continue;
            }
            if (aSector != bSector) {
                return Integer.compare(aSector, bSector);
            }
            int order = a.cross(b);
            if (Math.abs(order) == 1) {
                return -direction * order;
            }
        }
        throw new Unknown();
    }

    private static int intervalSector(EnclosedRadius origin, EnclosedRadius ray, int direction) {
        int turn = origin.cross(ray);
        if (turn == OVERLAPS_ZERO) {
            return -1;
        }
        int dot = turn == 0 ? origin.dot(ray) : 0;
        return turn == 0 && Math.abs(dot) != 1 ? -1 : sector(turn, dot, direction);
    }

    private static int sector(int turn, int dot, int direction) {
        if (turn != 0) {
            return direction * turn > 0 ? 1 : 3;
        }
        if (Math.abs(dot) != 1) {
            throw new Unknown();
        }
        return dot > 0 ? 0 : 2;
    }
}
