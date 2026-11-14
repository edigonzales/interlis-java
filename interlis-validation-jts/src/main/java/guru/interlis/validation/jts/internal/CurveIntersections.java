package guru.interlis.validation.jts.internal;

import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.math.DD;

import java.util.ArrayList;
import java.util.List;

import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.CurveSegment.Straight;

final class CurveIntersections {
    enum Kind { CONTACT, CROSSING, OVERLAP }
    record Hit(Point point, Kind kind) {}
    record Result(List<Hit> hits, boolean undetermined) {
        Result {
            hits = List.copyOf(hits);
        }
    }

    static Result between(CurveSegment first, CurveSegment second) {
        if (!first.envelope().intersects(second.envelope())) {
            return result();
        }
        return switch (first) {
            case Straight line -> switch (second) {
                case Straight other -> lines(line, other);
                case CircularArc arc -> lineArc(line, arc);
            };
            case CircularArc arc -> switch (second) {
                case Straight line -> lineArc(line, arc);
                case CircularArc other -> arcs(arc, other);
            };
        };
    }

    private static Result lines(Straight a, Straight b) {
        var intersector = new RobustLineIntersector();
        intersector.computeIntersection(a.start().coordinate(), a.end().coordinate(),
                b.start().coordinate(), b.end().coordinate());
        var hits = new ArrayList<Hit>();
        for (int i = 0; i < intersector.getIntersectionNum(); i++) {
            var p = intersector.getIntersection(i);
            var kind = intersector.getIntersectionNum() == 2 ? Kind.OVERLAP
                    : intersector.isProper() ? Kind.CROSSING : Kind.CONTACT;
            hits.add(new Hit(new Point(p.x, p.y), kind));
        }
        return new Result(hits, false);
    }

    private static Result lineArc(Straight line, CircularArc arc) {
        double dx = line.end().x() - line.start().x();
        double dy = line.end().y() - line.start().y();
        double ox = line.start().x() - arc.center().x();
        double oy = line.start().y() - arc.center().y();
        DD squaredLength = DD.sqr(dx).add(DD.sqr(dy));
        DD dot = DD.valueOf(dx).multiply(ox).add(DD.valueOf(dy).multiply(oy));
        DD cross = DD.valueOf(dx).multiply(oy).subtract(DD.valueOf(dy).multiply(ox));
        DD discriminant = DD.sqr(arc.radius()).multiply(squaredLength).subtract(cross.sqr());
        if (!(squaredLength.doubleValue() > 0) || !Double.isFinite(discriminant.doubleValue())) {
            return new Result(List.of(), true);
        }
        var hits = sharedEndpoints(line, arc);
        if (discriminant.signum() >= 0) {
            double middle = dot.negate().divide(squaredLength).doubleValue();
            double delta = discriminant.sqrt().divide(squaredLength).doubleValue();
            addLineArc(hits, line, arc, dx, dy, middle - delta, delta == 0);
            if (delta != 0) {
                addLineArc(hits, line, arc, dx, dy, middle + delta, false);
            }
        }
        return new Result(hits, false);
    }

    private static void addLineArc(List<Hit> hits, Straight line, CircularArc arc,
                                   double dx, double dy, double position, boolean tangent) {
        if (position < 0 || position > 1) {
            return;
        }
        var point = new Point(Math.fma(dx, position, line.start().x()),
                Math.fma(dy, position, line.start().y()));
        if (includes(arc, point)) {
            add(hits, point, tangent || endpoint(line, point) || endpoint(arc, point)
                    ? Kind.CONTACT : Kind.CROSSING);
        }
    }

    private static Result arcs(CircularArc a, CircularArc b) {
        var hits = sharedEndpoints(a, b);
        double dx = b.center().x() - a.center().x();
        double dy = b.center().y() - a.center().y();
        if (dx == 0 && dy == 0) {
            if (a.radius() == b.radius()) {
                return coincident(a, b, hits);
            }
            return result();
        }

        double scale = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.max(a.radius(), b.radius()));
        if (!Double.isFinite(scale) || scale == 0) {
            return new Result(hits, true);
        }
        double x = dx / scale;
        double y = dy / scale;
        double ra = a.radius() / scale;
        double rb = b.radius() / scale;
        DD squaredDistance = DD.sqr(x).add(DD.sqr(y));
        DD radiusA = DD.sqr(ra);
        DD radiusB = DD.sqr(rb);
        DD numerator = squaredDistance.add(radiusA).subtract(radiusB);
        DD discriminant = squaredDistance.multiply(radiusA).multiply(4).subtract(numerator.sqr());
        if (squaredDistance.isZero() || !Double.isFinite(discriminant.doubleValue())) {
            return new Result(hits, true);
        }
        if (discriminant.signum() < 0) {
            return new Result(hits, false);
        }
        double distance = squaredDistance.sqrt().doubleValue();
        double along = numerator.doubleValue() / (2 * distance) * scale;
        double height = discriminant.sqrt().doubleValue() / (2 * distance) * scale;
        double ux = x / distance;
        double uy = y / distance;
        var middle = new Point(Math.fma(along, ux, a.center().x()), Math.fma(along, uy, a.center().y()));
        addArcArc(hits, a, b, new Point(Math.fma(-height, uy, middle.x()),
                Math.fma(height, ux, middle.y())), height == 0);
        if (height != 0) {
            addArcArc(hits, a, b, new Point(Math.fma(height, uy, middle.x()),
                    Math.fma(-height, ux, middle.y())), false);
        }
        return new Result(hits, false);
    }

    private static Result coincident(CircularArc a, CircularArc b, List<Hit> hits) {
        if (interior(a, b.start()) || interior(a, b.end()) || interior(b, a.start()) || interior(b, a.end())
                || a.containsAngle(b.angle(b.start()) + b.sweep() / 2)
                || b.containsAngle(a.angle(a.start()) + a.sweep() / 2)) {
            return new Result(List.of(new Hit(a.start(), Kind.OVERLAP)), false);
        }
        return new Result(hits, false);
    }

    private static void addArcArc(List<Hit> hits, CircularArc a, CircularArc b, Point point, boolean tangent) {
        if (includes(a, point) && includes(b, point)) {
            add(hits, point, tangent || endpoint(a, point) || endpoint(b, point) ? Kind.CONTACT : Kind.CROSSING);
        }
    }

    private static boolean includes(CircularArc arc, Point point) {
        return endpoint(arc, point) || arc.containsAngle(arc.angle(point));
    }

    private static boolean interior(CircularArc arc, Point point) {
        return !endpoint(arc, point) && arc.containsAngle(arc.angle(point));
    }

    private static boolean endpoint(CurveSegment curve, Point point) {
        return curve.start().equals(point) || curve.end().equals(point);
    }

    private static List<Hit> sharedEndpoints(CurveSegment a, CurveSegment b) {
        var hits = new ArrayList<Hit>();
        if (endpoint(b, a.start())) {
            hits.add(new Hit(a.start(), Kind.CONTACT));
        }
        if (endpoint(b, a.end())) {
            hits.add(new Hit(a.end(), Kind.CONTACT));
        }
        return hits;
    }

    private static void add(List<Hit> hits, Point point, Kind kind) {
        if (hits.stream().noneMatch(hit -> hit.point().equals(point))) {
            hits.add(new Hit(point, kind));
        }
    }

    private static Result result() {
        return new Result(List.of(), false);
    }
}
