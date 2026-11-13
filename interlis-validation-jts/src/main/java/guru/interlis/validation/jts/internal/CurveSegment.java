package guru.interlis.validation.jts.internal;

import org.locationtech.jts.geom.Envelope;

sealed interface CurveSegment permits CurveSegment.Straight, CircularArc {
    Point start();
    Point end();
    Envelope envelope();
    double length();
    CurveSegment reversed();

    record Point(double x, double y) {
        org.locationtech.jts.geom.Coordinate coordinate() {
            return new org.locationtech.jts.geom.Coordinate(x, y);
        }

        double distance(Point other) {
            return Math.hypot(x - other.x, y - other.y);
        }
    }

    record Straight(Point start, Point end) implements CurveSegment {
        @Override public Envelope envelope() {
            return new Envelope(start.coordinate(), end.coordinate());
        }

        @Override public double length() {
            return start.distance(end);
        }

        @Override public Straight reversed() {
            return new Straight(end, start);
        }
    }
}
