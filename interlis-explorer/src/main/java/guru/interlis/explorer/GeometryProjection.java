package guru.interlis.explorer;

import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.MultiCoordinate;
import guru.interlis.transfer.TransferGeometry.MultiPolyline;
import guru.interlis.transfer.TransferGeometry.MultiSurface;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferGeometry.Ring;
import guru.interlis.transfer.TransferGeometry.Surface;
import guru.interlis.transfer.TransferValue.NumberValue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Writes the native query geometry as standard WKB, without changing the transfer value. */
final class GeometryProjection {
    private static final int MAX_VERTICES = 1_000_000;
    private static final int POINT = 1;
    private static final int LINE = 2;
    private static final int POLYGON = 3;
    private static final int MULTIPOINT = 4;
    private static final int MULTILINE = 5;
    private static final int MULTIPOLYGON = 6;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final int dimensions;
    private final double tolerance;
    private int vertices;

    private GeometryProjection(int dimensions, double tolerance) {
        this.dimensions = dimensions;
        this.tolerance = tolerance;
    }

    static byte[] encode(TransferGeometry value, double tolerance) {
        Coordinate first = firstCoordinate(value);
        var writer = new GeometryProjection(first == null ? 2 : first.ordinates().size(), tolerance);
        writer.geometry(value);
        return writer.output.toByteArray();
    }

    private static Coordinate firstCoordinate(TransferGeometry geometry) {
        return switch (geometry) {
            case Coordinate point -> point;
            case MultiCoordinate multi -> multi.coordinates().isEmpty() ? null : multi.coordinates().getFirst();
            case Polyline line -> line.parts().isEmpty() ? null : line.parts().getFirst().start();
            case MultiPolyline multi -> multi.lines().stream().flatMap(line -> line.parts().stream())
                    .findFirst().map(CurvePart::start).orElse(null);
            case Surface surface -> surface.shell().lines().getFirst().parts().getFirst().start();
            case MultiSurface multi -> multi.surfaces().isEmpty() ? null : firstCoordinate(multi.surfaces().getFirst());
        };
    }

    private void geometry(TransferGeometry geometry) {
        switch (geometry) {
            case Coordinate point -> point(point);
            case MultiCoordinate multi -> {
                header(MULTIPOINT);
                integer(multi.coordinates().size());
                multi.coordinates().forEach(this::point);
            }
            case Polyline line -> {
                if (line.parts().size() < 2) {
                    line(line.parts().isEmpty() ? List.of() : coordinates(line.parts().getFirst()));
                } else {
                    multiline(line.parts());
                }
            }
            case MultiPolyline multi -> multiline(multi.lines().stream().flatMap(line -> line.parts().stream()).toList());
            case Surface surface -> polygon(surface);
            case MultiSurface multi -> {
                header(MULTIPOLYGON);
                integer(multi.surfaces().size());
                multi.surfaces().forEach(this::polygon);
            }
        }
    }

    private void point(Coordinate point) {
        header(POINT);
        checkBudget(1);
        writeCoordinate(point);
    }

    private void multiline(List<CurvePart> parts) {
        header(MULTILINE);
        integer(parts.size());
        for (var part : parts) {
            line(coordinates(part));
        }
    }

    private void line(List<Coordinate> points) {
        if (points.size() == 1) {
            throw new IllegalArgumentException("A line needs at least two coordinates");
        }
        header(LINE);
        writeCoordinates(points);
    }

    private void polygon(Surface surface) {
        header(POLYGON);
        integer(1 + surface.holes().size());
        ring(surface.shell());
        surface.holes().forEach(this::ring);
    }

    private void ring(Ring ring) {
        var points = new ArrayList<Coordinate>();
        for (var line : ring.lines()) {
            for (var part : line.parts()) {
                var component = coordinates(part);
                if (points.isEmpty()) {
                    points.addAll(component);
                } else if (samePosition(points.getLast(), component.getFirst())) {
                    points.addAll(component.subList(1, component.size()));
                } else {
                    throw new IllegalArgumentException("A ring has disconnected segments");
                }
            }
        }
        if (points.size() < 4 || !samePosition(points.getFirst(), points.getLast())) {
            throw new IllegalArgumentException("A ring needs at least four coordinates and must be closed");
        }
        writeCoordinates(points);
    }

    private List<Coordinate> coordinates(CurvePart part) {
        var points = new ArrayList<Coordinate>();
        checkBudget(1);
        points.add(part.start());
        for (var segment : part.segments()) {
            if (segment instanceof Arc arc) {
                appendArc(points, points.getLast(), arc);
            } else {
                checkBudget(1);
                points.add(segment.end());
            }
        }
        return points;
    }

    // The XY stroker follows ili2db-transfer's ArcStroker. Standardize direction
    // before computing vertices so both uses of a shared arc get identical ordinates.
    private void appendArc(List<Coordinate> points, Coordinate start, Arc arc) {
        if (dimensions != 2 || start.ordinates().size() != 2 || arc.end().ordinates().size() != 2
                || arc.radius().isPresent()) {
            throw new IllegalArgumentException("Only XY arcs without an explicit radius can be projected");
        }
        var end = arc.end();
        var startX = binary(start, 0);
        var startY = binary(start, 1);
        var endX = binary(end, 0);
        var endY = binary(end, 1);
        int xOrder = startX.compareTo(endX);
        boolean reverse = xOrder > 0 || (xOrder == 0 && startY.compareTo(endY) > 0);
        var originX = reverse ? endX : startX;
        var originY = reverse ? endY : startY;
        var chordX = (reverse ? startX : endX).subtract(originX);
        var chordY = (reverse ? startY : endY).subtract(originY);
        var supportX = binary(arc.support(), 0).subtract(originX);
        var supportY = binary(arc.support(), 1).subtract(originY);
        var side = cross(chordX, chordY, supportX, supportY);
        var center = circleCenter(supportX, supportY, chordX, chordY, side);
        if (center == null) {
            if (!withinChord(supportX, chordX) || !withinChord(supportY, chordY)) {
                checkBudget(1);
                points.add(arc.support());
            }
            checkBudget(1);
            points.add(end);
            return;
        }

        var secondX = supportX.subtract(center.x());
        var secondY = supportY.subtract(center.y());
        double radius = Math.hypot(center.x().doubleValue(), center.y().doubleValue());
        if (!Double.isFinite(radius) || radius == 0) {
            throw new IllegalArgumentException("Arc radius cannot be represented");
        }
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
        checkBudget(firstSteps + secondSteps);

        int firstIndex = points.size();
        appendSteps(points, originX.doubleValue(), originY.doubleValue(), center.x().negate().doubleValue(),
                center.y().negate().doubleValue(), direction * first, (int) firstSteps, arc.support());
        appendSteps(points, binary(arc.support(), 0).doubleValue(), binary(arc.support(), 1).doubleValue(),
                secondX.doubleValue(), secondY.doubleValue(), direction * second, (int) secondSteps,
                reverse ? start : end);
        if (reverse) {
            var interior = points.subList(firstIndex, points.size());
            interior.removeLast();
            Collections.reverse(interior);
            points.add(end);
        }
    }

    private static boolean withinChord(BigDecimal support, BigDecimal chord) {
        return support.compareTo(chord.min(BigDecimal.ZERO)) >= 0
                && support.compareTo(chord.max(BigDecimal.ZERO)) <= 0;
    }

    private record Center(BigDecimal x, BigDecimal y) {}

    private static Center circleCenter(BigDecimal supportX, BigDecimal supportY, BigDecimal chordX,
                                       BigDecimal chordY, BigDecimal side) {
        if (chordX.signum() == 0 && chordY.signum() == 0) {
            return supportX.signum() == 0 && supportY.signum() == 0 ? null
                    : new Center(supportX.divide(BigDecimal.TWO), supportY.divide(BigDecimal.TWO));
        }
        if (side.signum() == 0) {
            return null;
        }
        var denominator = side.negate().multiply(BigDecimal.TWO);
        var supportSquared = supportX.multiply(supportX).add(supportY.multiply(supportY));
        var chordSquared = chordX.multiply(chordX).add(chordY.multiply(chordY));
        return new Center(supportSquared.multiply(chordY).subtract(chordSquared.multiply(supportY))
                .divide(denominator, MathContext.DECIMAL128),
                supportX.multiply(chordSquared).subtract(chordX.multiply(supportSquared))
                        .divide(denominator, MathContext.DECIMAL128));
    }

    private static BigDecimal cross(BigDecimal ax, BigDecimal ay, BigDecimal bx, BigDecimal by) {
        return ax.multiply(by).subtract(ay.multiply(bx));
    }

    private static double sweep(BigDecimal radialX, BigDecimal radialY, BigDecimal dx, BigDecimal dy,
                                double radius, int direction) {
        double chord = Math.hypot(dx.doubleValue(), dy.doubleValue());
        double minor = 2 * Math.asin(Math.min(1, chord / radius / 2));
        int side = cross(radialX, radialY, dx, dy).signum();
        return side == 0 || side == direction ? minor : 2 * Math.PI - minor;
    }

    private static void appendSteps(List<Coordinate> points, double x, double y, double radialX,
                                    double radialY, double sweep, int steps, Coordinate end) {
        for (int step = 1; step < steps; step++) {
            double angle = sweep * step / steps;
            double sine = Math.sin(angle);
            double halfSine = Math.sin(angle / 2);
            double cosineMinusOne = -2 * halfSine * halfSine;
            var px = number(x + (radialX * cosineMinusOne - radialY * sine));
            var py = number(y + (radialX * sine + radialY * cosineMinusOne));
            points.add(new Coordinate(List.of(px, py)));
        }
        points.add(end);
    }

    private static NumberValue number(double value) {
        var decimal = BigDecimal.valueOf(finite(value));
        return new NumberValue(decimal.toPlainString(), decimal);
    }

    private static BigDecimal binary(Coordinate point, int axis) {
        return new BigDecimal(finite(point.ordinates().get(axis).exactValue().doubleValue()));
    }

    private static boolean samePosition(Coordinate a, Coordinate b) {
        if (a.ordinates().size() != b.ordinates().size()) {
            return false;
        }
        for (int axis = 0; axis < a.ordinates().size(); axis++) {
            if (a.ordinates().get(axis).exactValue().compareTo(b.ordinates().get(axis).exactValue()) != 0) {
                return false;
            }
        }
        return true;
    }

    private void checkBudget(double count) {
        if (!Double.isFinite(count) || count > MAX_VERTICES - vertices) {
            throw new IllegalArgumentException("Geometry projection exceeds the vertex budget");
        }
        vertices += (int) count;
    }

    private void header(int type) {
        output.write(1);
        integer(type + (dimensions == 3 ? 1000 : 0));
    }

    private void writeCoordinates(List<Coordinate> points) {
        integer(points.size());
        points.forEach(this::writeCoordinate);
    }

    private void writeCoordinate(Coordinate point) {
        if (point.ordinates().size() != dimensions) {
            throw new IllegalArgumentException("Geometry contains mixed coordinate dimensions");
        }
        for (var ordinate : point.ordinates()) {
            long bits = Double.doubleToLongBits(finite(ordinate.exactValue().doubleValue()));
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
                output.write((int) (bits >>> shift));
            }
        }
    }

    private void integer(int value) {
        for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
            output.write(value >>> shift);
        }
    }

    private static double finite(double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Geometry ordinates must be finite");
        }
        return number;
    }
}
