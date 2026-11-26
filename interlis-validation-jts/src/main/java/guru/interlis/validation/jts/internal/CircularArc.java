package guru.interlis.validation.jts.internal;

import guru.interlis.transfer.TransferGeometry.ArcRadius;
import guru.interlis.transfer.TransferGeometry.RadiusConvention;
import guru.interlis.validation.jts.internal.ExactCircle.Rational;
import org.locationtech.jts.geom.Envelope;

import java.math.BigDecimal;
import java.util.Optional;

record CircularArc(Point start, Point end, Point center, double radius, double sweep,
                   ExactCircle definition, Envelope bounds) implements CurveSegment {
    private static final double TURN = 2 * Math.PI;

    private CircularArc(Point start, Point end, Point center, double radius, double sweep, ExactCircle definition) {
        this(start, end, center, radius, sweep, definition,
                envelope(start, end, definition, sweep < 0 ? 1 : -1));
    }

    sealed interface Construction {
        record Built(CircularArc arc) implements Construction {}
        record Invalid(String detail, boolean chordTooLong) implements Construction {
            Invalid(String detail) {
                this(detail, false);
            }
        }
        record Undetermined(String detail) implements Construction {}
    }

    static Construction build(Point start, Point support, Point end, Optional<ArcRadius> specified,
                              double supportTolerance) {
        return build(start, support, end, specified, BigDecimal.valueOf(supportTolerance), false, () -> {});
    }

    static Construction build(Point start, Point support, Point end, Optional<ArcRadius> specified,
                              BigDecimal supportQuantum, boolean modelQuantum, Runnable visit) {
        if (start.equals(end) || start.equals(support) || end.equals(support)) {
            return new Construction.Invalid("Coincident arc positions");
        }
        int supportDirection = Point.orientation(start, support, end);
        if (specified.isEmpty()) {
            if (supportDirection == 0) {
                return new Construction.Invalid("Collinear arc positions");
            }
            return candidate(start, end, ExactCircle.through(start, support, end), supportDirection);
        }

        var input = specified.orElseThrow();
        var value = input.sourceValue().exactValue();
        int sign = value.signum();
        boolean signed = input.convention() == RadiusConvention.SIGNED_CLOCKWISE;
        if (sign == 0 || !signed && sign < 0) {
            return new Construction.Invalid("Radius does not satisfy its convention");
        }
        var squaredRadius = value.pow(2);
        var chordSquared = squaredDistance(start, end);
        var diameterSquared = squaredRadius.multiply(BigDecimal.valueOf(4));
        int feasible = diameterSquared.compareTo(chordSquared);
        if (feasible < 0) {
            return new Construction.Invalid("Radius is smaller than half the chord (squared chord "
                    + chordSquared.toPlainString() + ", squared diameter " + diameterSquared.toPlainString() + ")", true);
        }

        // In XTF 2.3 the signed radius fixes the direction. The support selects between the two arcs
        // in that direction, including when its allowed displacement takes it across the chord.
        int direction = signed ? -sign : supportDirection;
        if (direction == 0) {
            return new Construction.Undetermined("Collinear positions cannot select the prescribed arc");
        }
        var radiusSquared = Rational.of(squaredRadius);
        var a = ExactCircle.fromRadius(start, end, radiusSquared, 1);
        var b = feasible == 0 ? a : ExactCircle.fromRadius(start, end, radiusSquared, -1);
        if (supportDirection == direction && a.pointSide(support) == 0) {
            return candidate(start, end, a, direction);
        }
        if (supportDirection == direction && b.pointSide(support) == 0) {
            return candidate(start, end, b, direction);
        }

        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            var da = supportDistanceSquared(a, start, end, support, direction, digits);
            var db = a == b ? da : supportDistanceSquared(b, start, end, support, direction, digits);
            var tolerance = Interval.exact(supportQuantum.pow(2), digits);
            if (modelQuantum) {
                tolerance = signed ? tolerance.multiply(Interval.exact(BigDecimal.valueOf(4), digits))
                        : tolerance.multiply(Interval.exact(new BigDecimal("0.5"), digits));
            }
            if (da.low().min(db.low()).compareTo(tolerance.high()) > 0) {
                return new Construction.Invalid("Intermediate position is outside the prescribed arc");
            }
            if ((a == b || da.high().compareTo(db.low()) < 0) && da.high().compareTo(tolerance.low()) <= 0) {
                return candidate(start, end, a, direction);
            }
            if (db.high().compareTo(da.low()) < 0 && db.high().compareTo(tolerance.low()) <= 0) {
                return candidate(start, end, b, direction);
            }
        }
        return new Construction.Undetermined("Intermediate position does not resolve the prescribed arc");
    }

    private static Interval supportDistanceSquared(ExactCircle circle, Point start, Point end, Point support,
                                                   int direction, int digits) {
        var cx = circle.centreX(digits);
        var cy = circle.centreY(digits);
        var dx = Interval.exact(support.exactX(), digits).subtract(cx);
        var dy = Interval.exact(support.exactY(), digits).subtract(cy);
        var distance = dx.square().add(dy.square()).sqrt();
        var radius = circle.radiusSquared().interval(digits).sqrt();
        var radial = distance.subtract(radius).square();
        if (distance.high().signum() == 0) {
            return radial;
        }
        var endpoint = Interval.exact(squaredDistance(start, support).min(squaredDistance(end, support)), digits);
        if (distance.low().signum() > 0) {
            var ratio = radius.divide(distance);
            int side = side(start, end, cx.add(dx.multiply(ratio)), cy.add(dy.multiply(ratio))).sign();
            if (side == -direction || side == 0) {
                return radial;
            }
            if (side == direction) {
                return endpoint;
            }
        }
        return new Interval(radial.low().min(endpoint.low()), endpoint.high(), digits);
    }

    private static BigDecimal squaredDistance(Point first, Point second) {
        return first.exactX().subtract(second.exactX()).pow(2)
                .add(first.exactY().subtract(second.exactY()).pow(2));
    }

    private static Construction candidate(Point start, Point end, ExactCircle circle, int direction) {
        var cx = circle.centreX(Interval.INITIAL_DIGITS).middle();
        var cy = circle.centreY(Interval.INITIAL_DIGITS).middle();
        var center = new Point(cx, cy);
        double radius = circle.radiusSquared().interval(Interval.INITIAL_DIGITS).sqrt().middle().doubleValue();
        double sweep = sweep(start, end, circle, direction);
        if (!(radius > 0) || !Double.isFinite(radius * sweep) || sweep == 0
                || !Double.isFinite(center.x()) || !Double.isFinite(center.y())) {
            return new Construction.Undetermined("Circle angle or radius exceeds numerical resolution");
        }
        return new Construction.Built(new CircularArc(start, end, center, radius, sweep, circle));
    }

    private static double sweep(Point start, Point end, ExactCircle circle, int direction) {
        var ratio = ExactCircle.chordSquared(start, end)
                .divide(circle.radiusSquared().multiply(Rational.of(4)));
        double halfChordOverRadius = ratio.interval(Interval.INITIAL_DIGITS).sqrt().middle().doubleValue();
        double minor = 2 * Math.asin(Math.min(1, halfChordOverRadius));
        return direction * (circle.centreSide(start, end) == -direction ? TURN - minor : minor);
    }

    double angle(Point point) {
        return Math.atan2(point.exactY().subtract(center.exactY()).doubleValue(),
                point.exactX().subtract(center.exactX()).doubleValue());
    }

    int arcSide() {
        return sweep < 0 ? 1 : -1;
    }

    boolean containsOnCircle(Point point) {
        return start.equals(point) || end.equals(point) || Point.orientation(start, end, point) == arcSide();
    }

    static Interval side(Point start, Point end, Interval x, Interval y) {
        int digits = x.digits();
        var startX = start.xInterval(digits);
        var startY = start.yInterval(digits);
        return end.xInterval(digits).subtract(startX).multiply(y.subtract(startY))
                .subtract(end.yInterval(digits).subtract(startY).multiply(x.subtract(startX)));
    }

    @Override public Envelope envelope() {
        return new Envelope(bounds);
    }

    @Override public Envelope bounds() {
        return new Envelope(bounds);
    }

    private static Envelope envelope(Point start, Point end, ExactCircle definition, int arcSide) {
        var bounds = new Straight(start, end).envelope();
        int digits = Interval.INITIAL_DIGITS;
        var cx = definition.centreX(digits);
        var cy = definition.centreY(digits);
        var r = definition.radiusSquared().interval(digits).sqrt();
        includeExtremum(bounds, cx.add(r), cy, start, end, arcSide);
        includeExtremum(bounds, cx.subtract(r), cy, start, end, arcSide);
        includeExtremum(bounds, cx, cy.add(r), start, end, arcSide);
        includeExtremum(bounds, cx, cy.subtract(r), start, end, arcSide);
        return bounds;
    }

    private static void includeExtremum(Envelope bounds, Interval x, Interval y, Point start, Point end, int arcSide) {
        if (side(start, end, x, y).sign() != -arcSide) {
            bounds.expandToInclude(CurveSegment.lower(x.low()), CurveSegment.lower(y.low()));
            bounds.expandToInclude(CurveSegment.upper(x.high()), CurveSegment.upper(y.high()));
        }
    }

    @Override public double length() {
        return radius * Math.abs(sweep);
    }

    CircularArc subArc(Point from, Point to) {
        if (from.equals(start) && to.equals(end)) {
            return this;
        }
        return new CircularArc(from, to, center, radius, sweep(from, to, definition, -arcSide()), definition);
    }

    Point pointAt(double fraction) {
        if (fraction == 0) return start;
        if (fraction == 1) return end;
        var dx = start.exactX().subtract(center.exactX()).doubleValue();
        var dy = start.exactY().subtract(center.exactY()).doubleValue();
        double angle = sweep * fraction;
        double halfSine = Math.sin(angle / 2);
        double cosineMinusOne = -2 * halfSine * halfSine;
        double sine = Math.sin(angle);
        return new Point(start.exactX().add(BigDecimal.valueOf(Math.fma(cosineMinusOne, dx, -sine * dy))),
                start.exactY().add(BigDecimal.valueOf(Math.fma(cosineMinusOne, dy, sine * dx))));
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
        return new CircularArc(end, start, center, radius, -sweep, definition, bounds);
    }
}
