package guru.interlis.validation.jts.internal;

import guru.interlis.transfer.TransferGeometry.ArcRadius;
import guru.interlis.transfer.TransferGeometry.RadiusConvention;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Triangle;

import java.util.Optional;

record CircularArc(Point start, Point end, Point center, double radius, double sweep) implements CurveSegment {
    private static final double TURN = 2 * Math.PI;

    sealed interface Construction {
        record Built(CircularArc arc) implements Construction {}
        record Invalid(String detail) implements Construction {}
        record Undetermined(String detail) implements Construction {}
    }

    static Construction build(Point start, Point support, Point end, Optional<ArcRadius> specified,
                              double supportTolerance) {
        if (start.equals(end) || start.equals(support) || end.equals(support)) {
            return new Construction.Invalid("Coincident arc positions");
        }
        int side = Orientation.index(start.coordinate(), support.coordinate(), end.coordinate());
        if (specified.isEmpty()) {
            if (side == Orientation.COLLINEAR) {
                return new Construction.Invalid("Collinear arc positions");
            }
            var center = Triangle.circumcentreDD(start.coordinate(), support.coordinate(), end.coordinate());
            var point = new Point(center.x, center.y);
            return candidate(start, end, point, point.distance(start), side);
        }

        var input = specified.orElseThrow();
        int sign = input.sourceValue().exactValue().signum();
        boolean signed = input.convention() == RadiusConvention.SIGNED_CLOCKWISE;
        if (sign == 0 || !signed && sign < 0) {
            return new Construction.Invalid("Radius does not satisfy its convention");
        }
        if (side == Orientation.COLLINEAR) {
            return new Construction.Undetermined("Collinear positions cannot select the prescribed arc");
        }
        if (signed && -sign != side) {
            return new Construction.Invalid("Radius direction contradicts intermediate position");
        }

        double radius = input.sourceValue().exactValue().abs().doubleValue();
        double halfChord = start.distance(end) / 2;
        if (!Double.isFinite(radius) || !Double.isFinite(halfChord) || radius == 0 || halfChord == 0) {
            return new Construction.Undetermined("Radius or chord exceeds numerical resolution");
        }
        if (radius < halfChord) {
            return new Construction.Invalid("Radius is smaller than half the chord");
        }
        double ratio = halfChord / radius;
        double offset = radius * Math.sqrt((1 - ratio) * (1 + ratio));
        double dx = (end.x() - start.x()) / 2;
        double dy = (end.y() - start.y()) / 2;
        var middle = new Point(start.x() + dx, start.y() + dy);
        var first = new Point(middle.x() - dy / halfChord * offset, middle.y() + dx / halfChord * offset);
        var second = new Point(middle.x() + dy / halfChord * offset, middle.y() - dx / halfChord * offset);
        var a = candidate(start, end, first, radius, side);
        var b = offset == 0 ? a : candidate(start, end, second, radius, side);
        double da = supportDistance(a, support);
        double db = supportDistance(b, support);
        if (!Double.isFinite(Math.min(da, db))) {
            return new Construction.Undetermined("Cannot resolve prescribed circle");
        }
        if (Math.min(da, db) > supportTolerance) {
            return new Construction.Invalid("Intermediate position is outside the prescribed arc");
        }
        if (offset != 0 && da == db) {
            return new Construction.Undetermined("Intermediate position does not distinguish the two circles");
        }
        return da <= db ? a : b;
    }

    private static Construction candidate(Point start, Point end, Point center, double radius, int direction) {
        double ax = (start.x() - center.x()) / radius;
        double ay = (start.y() - center.y()) / radius;
        double bx = (end.x() - center.x()) / radius;
        double by = (end.y() - center.y()) / radius;
        double minorAngle = Math.atan2(Math.fma(ax, by, -ay * bx), Math.fma(ax, bx, ay * by));
        double sweep = direction * positive(direction * minorAngle);
        if (!Double.isFinite(sweep) || !Double.isFinite(radius * sweep) || sweep == 0) {
            return new Construction.Undetermined("Circle angle or radius exceeds numerical resolution");
        }
        return new Construction.Built(new CircularArc(start, end, center, radius, sweep));
    }

    private static double supportDistance(Construction construction, Point support) {
        if (!(construction instanceof Construction.Built built)) {
            return Double.POSITIVE_INFINITY;
        }
        var arc = built.arc();
        return arc.containsAngle(arc.angle(support))
                ? Math.abs(arc.center.distance(support) - arc.radius)
                : Math.min(arc.start.distance(support), arc.end.distance(support));
    }

    private static double positive(double angle) {
        return angle < 0 ? angle + TURN : angle;
    }

    double angle(Point point) {
        return Math.atan2(point.y() - center.y(), point.x() - center.x());
    }

    boolean containsAngle(double angle) {
        double delta = angle - angle(start);
        double directed = Math.copySign(1, sweep) * delta;
        if (directed >= TURN) {
            directed -= TURN;
        }
        return positive(directed) <= Math.abs(sweep);
    }

    @Override public Envelope envelope() {
        var bounds = new Envelope(start.coordinate(), end.coordinate());
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI / 2);
            if (containsAngle(angle)) {
                switch (i) {
                    case 0 -> bounds.expandToInclude(center.x() + radius, center.y());
                    case 1 -> bounds.expandToInclude(center.x(), center.y() + radius);
                    case 2 -> bounds.expandToInclude(center.x() - radius, center.y());
                    case 3 -> bounds.expandToInclude(center.x(), center.y() - radius);
                    default -> throw new AssertionError(i);
                }
            }
        }
        return bounds;
    }

    @Override public double length() {
        return radius * Math.abs(sweep);
    }

    double signedArea(Point origin) {
        double x = start.x() - origin.x();
        double y = start.y() - origin.y();
        double chord = Math.fma(x, end.y() - origin.y(), -y * (end.x() - origin.x()));
        double angleSquared = sweep * sweep;
        double correction = Math.abs(sweep) < 0.1
                ? sweep * angleSquared * (1.0 / 6 - angleSquared * (1.0 / 120 - angleSquared / 5040))
                : sweep - Math.sin(sweep);
        return (chord + radius * (radius * correction)) / 2;
    }

    @Override public CircularArc reversed() {
        return new CircularArc(end, start, center, radius, -sweep);
    }
}
