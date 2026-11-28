package guru.interlis.validation.jts.internal;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    void indexesMixedRingsAndRetainsArcExtremaAndTangencies() {
        var boundary = roundedRectangle("69");
        var prepared = new CurvePointLocation.PreparedRing(boundary);
        int[] scannedVisits = {0};
        int[] indexedVisits = {0};

        for (var point : List.of(point("5", "1.5"), point("5", "13.5"), point("5", "34.5"), point("5", "45.5"),
                point("-1", "10.5"), point("11", "31.5"), point("5", "68"), point("5", "69"), point("-1", "69"),
                point("5", "69.01"), point("10", "64"))) {
            assertEquals(CurvePointLocation.locate(boundary, point, () -> scannedVisits[0]++),
                    prepared.locate(point, () -> indexedVisits[0]++), () -> "Location at " + point);
        }
        assertTrue(indexedVisits[0] < scannedVisits[0],
                () -> "Indexed visits " + indexedVisits[0] + ", scanned visits " + scannedVisits[0]);
        assertEquals(INSIDE, prepared.locate(point("5", "68"), () -> {}));
        assertEquals(BOUNDARY, prepared.locate(point("5", "69"), () -> {}));
        assertEquals(OUTSIDE, prepared.locate(point("-1", "69"), () -> {}));
        assertEquals(BOUNDARY, prepared.locate(point("10", "64"), () -> {}));

        var nearTangent = Point.enclosed(Interval.exact(BigDecimal.valueOf(5), Interval.INITIAL_DIGITS),
                new Interval(new BigDecimal("68.99"), new BigDecimal("69.01"), Interval.INITIAL_DIGITS));
        assertEquals(UNDETERMINED, prepared.locate(nearTangent, () -> {}));
    }

    @Test
    void retainsRingOrderWhenAnUnresolvedArcPrecedesABoundaryHit() {
        var boundary = roundedRectangle("59");
        // This ray intersects the inward semicircle arbitrarily close to tangency. Its origin is on the left side.
        var nearTangent = Point.exact(BigDecimal.ZERO, BigDecimal.valueOf(59)
                .add(BigDecimal.ONE.scaleByPowerOfTen(-Interval.MAX_DIGITS - 20)));
        assertEquals(UNDETERMINED, CurvePointLocation.locate(boundary, nearTangent, () -> {}));
        assertEquals(UNDETERMINED, new CurvePointLocation.PreparedRing(boundary).locate(nearTangent, () -> {}));

        var leftSideFirst = new ArrayList<>(boundary);
        Collections.rotate(leftSideFirst, -boundary.size() / 2 - 1);
        assertEquals(BOUNDARY, CurvePointLocation.locate(leftSideFirst, nearTangent, () -> {}));
        assertEquals(BOUNDARY, new CurvePointLocation.PreparedRing(leftSideFirst).locate(nearTangent, () -> {}));
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

    private static List<Segment> roundedRectangle(String topSupportY) {
        var segments = new ArrayList<>(rectangle(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE));
        int upperEdgeIndex = segments.size() / 2;
        var upperEdge = segments.get(upperEdgeIndex);
        var start = upperEdge.curve().start();
        var end = upperEdge.curve().end();
        var arc = assertInstanceOf(CircularArc.Construction.Built.class,
                CircularArc.build(start, point("5", topSupportY), end, Optional.empty(), 0)).arc();
        segments.set(upperEdgeIndex, new Segment(arc, upperEdge.path(), upperEdge.edge()));
        return List.copyOf(segments);
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
