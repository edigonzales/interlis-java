package guru.interlis.validation.jts.internal;

import org.locationtech.jts.geom.Envelope;

import java.math.BigDecimal;

sealed interface CurveSegment permits CurveSegment.Straight, CircularArc {
    Point start();
    Point end();
    Envelope envelope();
    double length();
    CurveSegment reversed();

    record Point(double x, double y, BigDecimal exactX, BigDecimal exactY, Bounds bounds) {
        private record Bounds(Interval x, Interval y) {}

        Point(double x, double y) {
            this(BigDecimal.valueOf(x), BigDecimal.valueOf(y));
        }

        Point(BigDecimal x, BigDecimal y) {
            this(x, y, null);
        }

        private Point(BigDecimal x, BigDecimal y, Bounds bounds) {
            this(x.doubleValue(), y.doubleValue(), x.stripTrailingZeros(), y.stripTrailingZeros(), bounds);
        }

        static Point exact(BigDecimal x, BigDecimal y) {
            return new Point(x, y);
        }

        static Point enclosed(Interval x, Interval y) {
            if (x.low().compareTo(x.high()) == 0 && y.low().compareTo(y.high()) == 0) {
                return exact(x.low(), y.low());
            }
            return new Point(x.middle(), y.middle(), new Bounds(x, y));
        }

        boolean isExact() {
            return bounds == null;
        }

        Interval xInterval(int digits) {
            return isExact() ? Interval.exact(exactX, digits) : new Interval(bounds.x.low(), bounds.x.high(), digits);
        }

        Interval yInterval(int digits) {
            return isExact() ? Interval.exact(exactY, digits) : new Interval(bounds.y.low(), bounds.y.high(), digits);
        }

        @Override public boolean equals(Object object) {
            return this == object || object instanceof Point other
                    && exactX.equals(other.exactX) && exactY.equals(other.exactY);
        }

        @Override public int hashCode() {
            return 31 * exactX.hashCode() + exactY.hashCode();
        }

        org.locationtech.jts.geom.Coordinate coordinate() {
            return new org.locationtech.jts.geom.Coordinate(x, y);
        }

        double distance(Point other) {
            return Math.hypot(exactX.subtract(other.exactX).doubleValue(), exactY.subtract(other.exactY).doubleValue());
        }

        static int orientation(Point a, Point b, Point p) {
            int filtered = filteredOrientation(a, b, p);
            if (filtered != Interval.OVERLAPS_ZERO) {
                return filtered;
            }
            return b.exactX.subtract(a.exactX).multiply(p.exactY.subtract(a.exactY))
                    .subtract(b.exactY.subtract(a.exactY).multiply(p.exactX.subtract(a.exactX))).signum();
        }

        static int filteredOrientation(Point a, Point b, Point p) {
            var ax = DoubleInterval.around(a.x);
            var ay = DoubleInterval.around(a.y);
            var dx = DoubleInterval.around(b.x).subtract(ax);
            var dy = DoubleInterval.around(b.y).subtract(ay);
            var px = DoubleInterval.around(p.x).subtract(ax);
            var py = DoubleInterval.around(p.y).subtract(ay);
            return dx.multiply(py).subtract(dy.multiply(px)).sign();
        }
    }

    record Straight(Point start, Point end) implements CurveSegment {
        @Override public Envelope envelope() {
            if (!start.isExact() || !end.isExact()) {
                int digits = Interval.MAX_DIGITS;
                var startX = start.xInterval(digits);
                var endX = end.xInterval(digits);
                var startY = start.yInterval(digits);
                var endY = end.yInterval(digits);
                return new Envelope(lower(startX.low().min(endX.low())), upper(startX.high().max(endX.high())),
                        lower(startY.low().min(endY.low())), upper(startY.high().max(endY.high())));
            }
            return new Envelope(Math.nextDown(Math.min(start.x, end.x)), Math.nextUp(Math.max(start.x, end.x)),
                    Math.nextDown(Math.min(start.y, end.y)), Math.nextUp(Math.max(start.y, end.y)));
        }

        @Override public double length() {
            return start.distance(end);
        }

        @Override public Straight reversed() {
            return new Straight(end, start);
        }
    }

    static double lower(BigDecimal value) {
        double nearest = value.doubleValue();
        return Double.isFinite(nearest) && new BigDecimal(nearest).compareTo(value) > 0
                ? Math.nextDown(nearest) : nearest;
    }

    static double upper(BigDecimal value) {
        double nearest = value.doubleValue();
        return Double.isFinite(nearest) && new BigDecimal(nearest).compareTo(value) < 0
                ? Math.nextUp(nearest) : nearest;
    }
}
