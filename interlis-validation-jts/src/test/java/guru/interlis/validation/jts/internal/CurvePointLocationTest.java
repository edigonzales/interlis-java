package guru.interlis.validation.jts.internal;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.validation.jts.internal.CurvePointLocation.Position.*;
import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapter.Edge;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapter.Segment;
import static org.junit.jupiter.api.Assertions.*;

class CurvePointLocationTest {
    @Test
    void testsFewerSegmentsAcrossRepeatedRays() {
        var segments = rectangle(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE);
        var prepared = new CurvePointLocation.PreparedRing(segments);
        int[] scannedVisits = {0};
        int[] indexedVisits = {0};
        for (var point : List.of(point("5", "1.5"), point("5", "13.5"), point("5", "34.5"),
                point("5", "45.5"), point("-1", "10.5"), point("11", "31.5"), point("10", "53.5"))) {
            assertEquals(CurvePointLocation.locate(segments, point, () -> scannedVisits[0]++),
                    prepared.locate(point, () -> indexedVisits[0]++));
        }
        assertTrue(indexedVisits[0] < scannedVisits[0],
                () -> "Indexed visits " + indexedVisits[0] + ", scanned visits " + scannedVisits[0]);
    }

    @Test
    void preservesExactPointLocationsWhenCoordinatesShareTheirDoubleProjection() {
        var base = new BigDecimal("10000000000000000");
        var right = base.add(new BigDecimal("0.5"));
        var centre = base.add(new BigDecimal("0.25"));
        var step = new BigDecimal("0.01");
        var top = base.add(step.multiply(BigDecimal.valueOf(64)));
        var prepared = new CurvePointLocation.PreparedRing(rectangle(base, right, base, step));

        assertEquals(base.doubleValue(), right.doubleValue());
        assertEquals(base.doubleValue(), top.doubleValue());
        assertEquals(INSIDE, prepared.locate(Point.exact(centre, base.add(new BigDecimal("0.315"))), () -> {}));
        assertEquals(BOUNDARY, prepared.locate(Point.exact(right, base.add(new BigDecimal("0.32"))), () -> {}));
        assertEquals(BOUNDARY, prepared.locate(Point.exact(centre, top), () -> {}));
        assertEquals(OUTSIDE, prepared.locate(Point.exact(centre, top.add(step)), () -> {}));
    }

    @Test
    void retainsUncertaintyAndPollsCancellationOnAnEmptyRay() {
        var segments = rectangle(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE);
        var prepared = new CurvePointLocation.PreparedRing(segments);
        var offRay = point("11", "31.5");
        assertEquals(OUTSIDE, prepared.locate(offRay, () -> {}));
        var cancelled = CancellationToken.requested(CancellationReason.USER_REQUESTED);
        assertThrows(OperationCancelledException.class, () -> prepared.locate(offRay, cancelled::throwIfCancellationRequested));

        var enclosed = Point.enclosed(new Interval(new BigDecimal("9.9"), new BigDecimal("10.1"), Interval.INITIAL_DIGITS),
                new Interval(new BigDecimal("22.4"), new BigDecimal("22.6"), Interval.INITIAL_DIGITS));
        assertEquals(UNDETERMINED, prepared.locate(enclosed, () -> {}));
    }

    private static List<Segment> rectangle(BigDecimal minX, BigDecimal maxX, BigDecimal minY, BigDecimal step) {
        int height = 64;
        var segments = new ArrayList<Segment>();
        var first = Point.exact(minX, minY);
        var previous = Point.exact(maxX, minY);
        segments.add(straight(first, previous));
        for (int i = 1; i <= height; i++) {
            var next = Point.exact(maxX, minY.add(step.multiply(BigDecimal.valueOf(i))));
            segments.add(straight(previous, next));
            previous = next;
        }
        var left = Point.exact(minX, previous.exactY());
        segments.add(straight(previous, left));
        previous = left;
        for (int i = height - 1; i >= 0; i--) {
            var next = Point.exact(minX, minY.add(step.multiply(BigDecimal.valueOf(i))));
            segments.add(straight(previous, next));
            previous = next;
        }
        return List.copyOf(segments);
    }

    private static Segment straight(Point start, Point end) {
        var edge = new Edge(new TransferGeometryAdapter.Position(start.exactX(), start.exactY()),
                new TransferGeometryAdapter.Position(end.exactX(), end.exactY()), null, 0);
        return new Segment(new CurveSegment.Straight(start, end), List.of(), edge);
    }

    private static Point point(String x, String y) {
        return Point.exact(new BigDecimal(x), new BigDecimal(y));
    }
}
