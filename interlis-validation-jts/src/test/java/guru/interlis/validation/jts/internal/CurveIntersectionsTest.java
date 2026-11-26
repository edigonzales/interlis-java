package guru.interlis.validation.jts.internal;

import guru.interlis.transfer.TransferGeometry.ArcRadius;
import guru.interlis.transfer.TransferGeometry.RadiusConvention;
import guru.interlis.transfer.TransferValue.NumberValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.jts.internal.CurveIntersections.Kind.*;
import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.CurveSegment.Straight;
import static org.junit.jupiter.api.Assertions.*;

class CurveIntersectionsTest {
    @Test
    void lineCrossesArcOutsideItsChordEnvelope() {
        var arc = upper(0);
        var line = new Straight(new Point(-2, 0.5), new Point(2, 0.5));
        var result = CurveIntersections.between(line, arc);
        assertFalse(result.undetermined());
        assertEquals(List.of(CROSSING, CROSSING), kinds(result));
        assertEquals(-Math.sqrt(0.75), result.hits().getFirst().point().x(), 1e-12);
        assertEquals(Math.sqrt(0.75), result.hits().getLast().point().x(), 1e-12);
        var root = result.hits().getLast().point();
        assertFalse(root.isExact());
        var enclosure = root.xInterval(50);
        var reference = new BigDecimal("0.75").sqrt(new MathContext(50));
        assertTrue(enclosure.low().compareTo(reference) <= 0 && enclosure.high().compareTo(reference) >= 0);
        assertEquals(result, CurveIntersections.between(arc, line));
    }

    @Test
    void distinguishesTangentFromSecantAndMiss() {
        var arc = upper(0);
        assertEquals(List.of(CONTACT), kinds(CurveIntersections.between(
                new Straight(new Point(-2, 1), new Point(2, 1)), arc)));
        assertTrue(CurveIntersections.between(
                new Straight(new Point(-2, 1.1), new Point(2, 1.1)), arc).hits().isEmpty());
    }

    @Test
    void separatesNearlyTangentRootsBeforeDeliveringTheirCoordinates() {
        var y = BigDecimal.ONE.subtract(new BigDecimal("1e-70"));
        var line = new Straight(point("-2", y.toPlainString()), point("2", y.toPlainString()));
        var result = CurveIntersections.between(line, upper(0));
        assertFalse(result.undetermined());
        assertEquals(List.of(CROSSING, CROSSING), kinds(result));
        assertEquals(-Math.sqrt(2) * 1e-35, result.hits().getFirst().point().x(), 1e-48);
        assertEquals(Math.sqrt(2) * 1e-35, result.hits().getLast().point().x(), 1e-48);
    }

    @Test
    void findsArcCrossingAndSharedArcInterval() {
        var first = upper(0);
        var second = upper(1);
        var result = CurveIntersections.between(first, second);
        assertFalse(result.undetermined());
        assertEquals(List.of(CROSSING), kinds(result));
        assertEquals(0.5, result.hits().getFirst().point().x(), 1e-12);
        assertEquals(Math.sqrt(0.75), result.hits().getFirst().point().y(), 1e-12);
        assertEquals(List.of(OVERLAP), kinds(CurveIntersections.between(first, first.reversed())));
        assertEquals(List.of(CONTACT), kinds(CurveIntersections.between(first, upper(2))));
    }

    @Test
    void complementarySemicirclesOnlyShareEndpoints() {
        var lower = arc(new Point(-1, 0), new Point(0, -1), new Point(1, 0));
        assertEquals(List.of(CONTACT, CONTACT), kinds(CurveIntersections.between(upper(0), lower)));
    }

    @Test
    void twoKnownEndpointsExhaustDifferentCirclesWithEqualRadii() {
        // Two radius-2 circles through the common chord with centres (0, ±sqrt(3)).
        var left = point("-1", "0");
        var right = point("1", "0");
        var upper = radiusTwo(left, new Point(0, 2 - Math.sqrt(3)), right);
        var lower = radiusTwo(left, new Point(0, Math.sqrt(3) - 2), right);
        assertFalse(upper.definition().sameCircle(lower.definition()));
        for (var pair : List.of(List.of(upper, lower), List.of(lower.reversed(), upper),
                List.of(upper.reversed(), lower.reversed()))) {
            var result = CurveIntersections.between(pair.getFirst(), pair.getLast());
            assertFalse(result.undetermined());
            assertEquals(List.of(CONTACT, CONTACT), kinds(result));
            assertTrue(result.hits().stream().anyMatch(hit -> hit.point().equals(left)));
            assertTrue(result.hits().stream().anyMatch(hit -> hit.point().equals(right)));
        }
    }

    @Test
    void recognizesExactTangencyBetweenIrrationalCircleCentres() {
        // The centres (0, sqrt(3)) and (2, -sqrt(3)) are four units apart, exactly 2 + 2.
        var shared = point("1", "0");
        var first = radiusTwo(point("-1", "0"), new Point(0, Math.sqrt(3) + 2), shared);
        var second = radiusTwo(shared, new Point(2, 2 - Math.sqrt(3)), point("3", "0"));
        var result = CurveIntersections.between(first, second);
        assertFalse(result.undetermined());
        assertEquals(List.of(CONTACT), kinds(result));
        assertEquals(shared, result.hits().getFirst().point());
    }

    @Test
    void rendersExactTerminatingCoordinatesForTheFactoredSecondRoot() {
        // The y=3 chord of a radius-5 circle centred at (4,0) meets it at exactly x=0 and x=8.
        var shared = point("0", "3");
        var boundary = arc(shared, point("4", "5"), point("9", "0"));
        var result = CurveIntersections.between(new Straight(shared, point("12", "3")), boundary);
        assertFalse(result.undetermined());
        assertEquals(List.of(CONTACT, CROSSING), kinds(result));
        assertEquals(point("8", "3"), result.hits().getLast().point());
    }

    @Test
    void treatsImplicitHalfDiagonalLimitAsExactAndInclusive() {
        // INTERLIS 2.4 §3.8.12.2. At e=0 the circle with centre (1/2, (1+e)/2)
        // meets the diagonal at (0,0) and (1+e/2,1+e/2), with sagitta equal to sqrt(2)/2.
        var shared = point("0", "0");
        var line = new Straight(shared, point("2", "2"));
        var limit = new LensHeight.Tolerance(BigDecimal.ONE, true);
        for (int side : new int[]{-1, 0, 1}) {
            var elevation = BigDecimal.ONE.add(BigDecimal.valueOf(side).scaleByPowerOfTen(-35));
            var boundary = arc(point("1", "0"), point("0", elevation.toPlainString()), shared);
            var assessment = LensHeight.compare(boundary, line, shared, limit, () -> {});
            assertNotNull(assessment);
            assertEquals(side <= 0, assessment.permitted());
        }
    }

    @Test
    void acceptsExactImplicitHeightBetweenTwoCircularSegments() {
        // The two same-radius circles have centres (1/4,-3/4) and (3/4,-1/4). Signed sagittas
        // therefore differ by the centre distance sqrt(2)/2 along their common chord normal.
        var shared = point("0", "0");
        var crossing = point("1", "-1");
        var first = arc(shared, crossing, point("-0.5", "-1"));
        var second = arc(shared, crossing, point("0.5", "-1"));
        assertEquals(List.of(CONTACT, CROSSING), kinds(CurveIntersections.between(first, second)));
        var assessment = LensHeight.compare(first, second, shared,
                new LensHeight.Tolerance(BigDecimal.ONE, true), () -> {});
        assertNotNull(assessment);
        assertTrue(assessment.permitted());
    }

    @Test
    void keepsOneContactAndOneCrossingWhenOriginChanges() {
        // ilivalidator#65, iox-ili c7b866a. The second point is an independently verified crossing.
        var from = point("2625269.247", "1238678.723");
        var shared = point("2625285.589", "1238688.269");
        var support = point("2625280.733", "1238687.664");
        var end = point("2625277.091", "1238690.932");
        for (Point origin : List.of(point("0", "0"), from, shared, support)) {
            var relativeFrom = relative(from, origin);
            var relativeShared = relative(shared, origin);
            var line = new Straight(relativeFrom, relativeShared);
            var boundary = arc(relativeShared, relative(support, origin), relative(end, origin));
            for (var pair : List.of(List.of(line, boundary), List.of(line.reversed(), boundary.reversed()))) {
                var result = CurveIntersections.between(pair.getFirst(), pair.getLast());
                assertFalse(result.undetermined());
                assertEquals(List.of(CONTACT, CROSSING), kinds(result));
                assertEquals(relativeShared, result.hits().getFirst().point());
                var crossing = result.hits().getLast().point();
                assertEquals(2625285.3554505454, crossing.exactX().add(origin.exactX()).doubleValue(), 1e-8);
                assertEquals(1238688.1325746486, crossing.exactY().add(origin.exactY()).doubleValue(), 1e-8);
            }
        }
    }

    @Test
    void preservesAnActualSecondRootCloseToTheKnownEndpoint() {
        // ilivalidator#476, the first arc and its competing straight from the published coordinates.
        var shared = point("2754777.422", "1260634.983");
        var from = point("2754781.612", "1260649.621");
        var support = point("2754777.506", "1260635.783");
        var end = point("2754777.312", "1260636.563");
        var zero = relative(shared, shared);
        var line = new Straight(relative(from, shared), zero);
        var boundary = arc(zero, relative(support, shared), relative(end, shared));
        var result = CurveIntersections.between(line, boundary);
        assertFalse(result.undetermined());
        assertEquals(List.of(CONTACT, CROSSING), kinds(result));
        assertEquals(0.000433336, result.hits().getLast().point().distance(zero), 1e-9);
    }

    @Test
    void detectsRetracingWithDifferentExactDefinitionsOfTheSameCircle() {
        var first = arc(point("0", "0"), point("0.04", "-0.08"), point("0.24", "-0.18"));
        var second = arc(point("0.24", "-0.18"), point("0.17", "-0.17"), point("0.09", "-0.13"));
        var result = CurveIntersections.between(first, second);
        assertFalse(result.undetermined());
        assertEquals(List.of(OVERLAP), kinds(result));
        assertTrue(first.containsOnCircle(result.hits().getFirst().point()));
        assertTrue(second.containsOnCircle(result.hits().getFirst().point()));
    }

    private static Point point(String x, String y) {
        return Point.exact(new BigDecimal(x), new BigDecimal(y));
    }

    private static Point relative(Point point, Point origin) {
        return Point.exact(point.exactX().subtract(origin.exactX()), point.exactY().subtract(origin.exactY()));
    }

    private static List<CurveIntersections.Kind> kinds(CurveIntersections.Result result) {
        return result.hits().stream().map(CurveIntersections.Hit::kind).toList();
    }

    private static CircularArc upper(double x) {
        return arc(new Point(x - 1, 0), new Point(x, 1), new Point(x + 1, 0));
    }

    private static CircularArc arc(Point start, Point middle, Point end) {
        return assertInstanceOf(CircularArc.Construction.Built.class,
                CircularArc.build(start, middle, end, Optional.empty(), 0)).arc();
    }

    private static CircularArc radiusTwo(Point start, Point middle, Point end) {
        var radius = new ArcRadius(new NumberValue("2", BigDecimal.TWO), RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT);
        return assertInstanceOf(CircularArc.Construction.Built.class,
                CircularArc.build(start, middle, end, Optional.of(radius), 1e-12)).arc();
    }
}
