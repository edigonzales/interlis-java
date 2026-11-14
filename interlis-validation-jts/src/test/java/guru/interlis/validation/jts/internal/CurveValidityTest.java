package guru.interlis.validation.jts.internal;

import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Origin;
import guru.interlis.model.TypeId;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.jts.JtsGeometryEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.GeometryEngine.Check.*;
import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapterTest.point;
import static org.junit.jupiter.api.Assertions.*;

class CurveValidityTest {
    @Test
    void doesNotBlameAdjacentSegmentsForARepeatedBoundaryVertex() {
        var shell = ring("0,0", "4,0", "4,4", "2,2", "0,4", "0,2", "2,2", "0,0");
        var outcome = check(new Surface(shell, List.of()), GeometryKind.SURFACE, true, false);
        assertEquals(INVALID, outcome.status());
        assertEquals(List.of(GEOMETRY_SELF_INTERSECTION), outcome.codes());
        assertTrue(outcome.findings().getFirst().relatedData().isEmpty());
    }

    @Test
    void rejectsEmptyCompleteLinesAndDoesNotMeasureThemAsZero() {
        for (var empty : List.of(new Polyline(List.of()), line("0,0"))) {
            assertEquals(List.of(GEOMETRY_DEGENERATE_SEGMENT),
                    check(empty, GeometryKind.POLYLINE, false, false).codes());
            assertEquals(MISSING_PARAMETERS, check(empty, GeometryKind.POLYLINE, false, true).status());
            var findings = new ArrayList<GeometryEngine.Finding>();
            assertEquals(new GeometryEngine.Measurement.Unavailable(INVALID),
                    JtsGeometryEngine.standard().measure(GeometryEngine.Measure.LENGTH,
                            input(empty, GeometryKind.POLYLINE, false, false),
                            new GeometryEngine.Control(() -> {}, findings::add)));
        }
        assertEquals(VALID, check(line("0,0", "1,1"), GeometryKind.POLYLINE, false, false).status());
    }

    @Test
    void checksDeliveredHeightsAtRingClosureAndPartJunctions() {
        var first = line("0,0,0", "10,0,0", "10,10,0");
        var closed = line("10.0,10.00,0.0", "0,10,0", "0.0,0,0.00");
        var wrongEnd = line("10,10,0", "0,10,0", "0,0,5");
        var wrongJunction = line("10,10,5", "0,10,0", "0,0,0");
        assertEquals(VALID, check(new Surface(new Ring(List.of(first, closed)), List.of()),
                GeometryKind.SURFACE, true, false).status());
        for (var second : List.of(wrongEnd, wrongJunction)) {
            assertEquals(List.of(GEOMETRY_RING_NOT_CLOSED),
                    check(new Surface(new Ring(List.of(first, second)), List.of()),
                            GeometryKind.SURFACE, true, false).codes());
        }
        assertEquals(VALID, check(wrongEnd, GeometryKind.POLYLINE, true, false).status());
    }

    @Test
    void allowsOpenLinesAndChecksCrossingsOnlyWhenRequired() {
        var line = line("0,0", "4,4", "0,4", "4,0");
        assertEquals(VALID, check(line, GeometryKind.POLYLINE, false, false).status());
        assertEquals(List.of(GEOMETRY_SELF_INTERSECTION),
                check(line, GeometryKind.POLYLINE, true, false).codes());
        assertEquals(List.of(GEOMETRY_RING_NOT_CLOSED),
                check(new Surface(new Ring(List.of(line)), List.of()), GeometryKind.SURFACE, true, false).codes());
        assertEquals(MISSING_PARAMETERS,
                check(new Surface(new Ring(List.of(line)), List.of()), GeometryKind.SURFACE, true, true).status());
    }

    @Test
    void distinguishesInteriorContactFromDisconnectedInteriorAndOutsideHole() {
        var shell = ring("0,0", "8,0", "8,8", "0,8", "0,0");
        var tangent = ring("0,4", "2,3", "2,5", "0,4");
        assertEquals(VALID, check(new Surface(shell, List.of(tangent)), GeometryKind.SURFACE, true, false).status());
        var cut = ring("0,4", "4,3", "8,4", "4,5", "0,4");
        assertEquals(List.of(GEOMETRY_INTERIOR_DISCONNECTED),
                check(new Surface(shell, List.of(cut)), GeometryKind.SURFACE, true, false).codes());
        var outside = ring("9,0", "10,0", "10,2", "9,2", "9,0");
        var outcome = check(new Surface(shell, List.of(outside)), GeometryKind.SURFACE, true, false);
        assertEquals(List.of(GEOMETRY_HOLE_OUTSIDE_SHELL), outcome.codes());
        assertEquals(1, ((DataLocation.GeometryComponent) outcome.findings().getFirst()
                .relatedData().orElseThrow().path().getLast()).position());
    }

    @Test
    void locatesHoleAgainstCircularBoundaryAndRejectsSharedMultiEdge() {
        var left = point("-4", "0");
        var right = point("4", "0");
        var circle = new Ring(List.of(new Polyline(List.of(new CurvePart(left, List.of(
                new Arc(point("0", "4"), right, Optional.empty()),
                new Arc(point("0", "-4"), left, Optional.empty())))))));
        var hole = ring("-1,-1", "1,-1", "1,1", "-1,1", "-1,-1");
        assertEquals(VALID, check(new Surface(circle, List.of(hole)), GeometryKind.SURFACE, true, false).status());
        var first = new Surface(ring("0,0", "2,0", "2,2", "0,2", "0,0"), List.of());
        var second = new Surface(ring("2,0", "4,0", "4,2", "2,2", "2,0"), List.of());
        var adjacent = check(new MultiSurface(List.of(first, second)), GeometryKind.MULTISURFACE, true, false);
        assertEquals(INVALID, adjacent.status());
        assertEquals(List.of(GEOMETRY_BOUNDARY_MISMATCH), adjacent.codes());
        assertTrue(adjacent.findings().getFirst().relatedData().isPresent());
        var crossing = new Surface(ring("1,-1", "3,-1", "3,1", "1,1", "1,-1"), List.of());
        var intersecting = check(new MultiSurface(List.of(first, crossing)), GeometryKind.MULTISURFACE, true, false);
        assertEquals(List.of(GEOMETRY_AREA_OVERLAP, GEOMETRY_AREA_OVERLAP), intersecting.codes());
        assertTrue(intersecting.findings().stream().allMatch(f -> f.relatedData().isPresent()));
    }

    @Test
    void acceptsOnlyOverlapWithSharedVertexWithinSagittaTolerance() {
        var start = point("-1", "0");
        var end = point("1", "0");
        var arcThenLine = new Polyline(List.of(new CurvePart(start, List.of(
                new Arc(point("0", "1"), end, Optional.empty()),
                new Straight(point("0.99", "1"))))));
        assertEquals(VALID, check(arcThenLine, GeometryKind.POLYLINE, true, false).status());
        var outside = new Polyline(List.of(new CurvePart(start, List.of(
                new Arc(point("0", "1"), end, Optional.empty()),
                new Straight(point("0.8", "1"))))));
        assertEquals(List.of(GEOMETRY_SELF_INTERSECTION),
                check(outside, GeometryKind.POLYLINE, true, false).codes());
    }

    @Test
    void separatesCoverageFromGapsAndPartialDelivery() {
        var shell = ring("0,0", "8,0", "8,8", "0,8", "0,0");
        var hole = ring("2,2", "4,2", "4,4", "2,4", "2,2");
        var donut = new Surface(shell, List.of(hole));
        var filler = new Surface(hole, List.of());
        var separate = new Surface(ring("10,0", "12,0", "12,2", "10,2", "10,0"), List.of());
        assertEquals(VALID, coverage(true, false, donut).status());
        assertEquals(List.of(GEOMETRY_GAP), coverage(true, true, donut).codes());
        assertEquals(VALID, coverage(true, true, donut, filler, separate).status());
        assertEquals(VALID, coverage(true, true).status());
        assertEquals(MISSING_PARAMETERS, coverage(false, true, donut).status());
        assertEquals(INVALID, coverage(false, true, donut, donut).status());
        var open = new Surface(ring("0,0", "2,0", "2,2"), List.of());
        assertEquals(INVALID, coverage(true, true, open, separate).status());
    }

    @Test
    void requiresMatchingCoverageEdgesAndPreservesBothOccurrences() {
        var first = new Surface(ring("0,0", "2,0", "2,2", "0,2", "0,0"), List.of());
        var adjacent = new Surface(ring("2,0", "4,0", "4,2", "2,2", "2,0"), List.of());
        var split = new Surface(ring("2,0", "4,0", "4,2", "2,2", "2,1", "2,0"), List.of());
        assertEquals(VALID, coverage(true, true, first, adjacent).status());
        var mismatch = coverage(true, false, first, split);
        assertEquals(INVALID, mismatch.status());
        assertTrue(mismatch.codes().contains(GEOMETRY_BOUNDARY_MISMATCH));
        assertTrue(mismatch.findings().stream().allMatch(finding -> !finding.location().tid()
                .equals(finding.relatedData().orElseThrow().tid())));
    }

    @Test
    void matchesDecimalSharedBoundariesIndependentlyOfObjectAndRingStartOrder() {
        var first = new Surface(ring("2600000,0", "2600000.1,0", "2600000.1,1", "2600000,1", "2600000,0"), List.of());
        var second = new Surface(ring("2600000.5,0", "2600000.5,1", "2600000.1,1",
                "2600000.1,0", "2600000.5,0"), List.of());
        assertEquals(VALID, coverage(true, true, first, second).status());
        assertEquals(VALID, coverage(true, true, second, first).status());
    }

    @Test
    void matchesReversedSharedArcsInEitherOrderAndCancelsTheirGapEdges() {
        var first = curvedFace("4,5", "1,4", "1,6", "4,5");
        var adjacent = curvedFace("1.0,6.0", "1,4", "4,5", "5,5", "5,1", "-1,1", "-1,6", "1,6");
        var overlapping = curvedFace("1,6", "1,4", "4,5", "5,0", "-2,0", "-2,10", "0,10", "1,6");
        for (boolean reversed : List.of(false, true)) {
            var pair = reversed ? new Surface[]{adjacent, first} : new Surface[]{first, adjacent};
            var joined = coverage(true, true, pair);
            assertEquals(VALID, joined.status(), joined.findings().toString());
            assertTrue(joined.findings().isEmpty());
            pair = reversed ? new Surface[]{overlapping, first} : new Surface[]{first, overlapping};
            assertTrue(coverage(true, false, pair).codes().contains(GEOMETRY_AREA_OVERLAP));
        }
    }

    @Test
    void matchesAnExplicitRadiusThatExactlyFitsTheOpposingImplicitArc() {
        var cap = curvedFace("-2,0", "0,2", "2,0", "-2,0");
        var radius = new ArcRadius(new TransferValue.NumberValue("2.0", new BigDecimal("2.0")),
                RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT);
        var surrounding = curvedFace(Optional.of(radius), "2,0", "0,2", "-2,0", "-2,3", "2,3", "2,0");
        for (var pair : List.of(new Surface[]{cap, surrounding}, new Surface[]{surrounding, cap})) {
            var result = coverage(true, true, pair);
            assertEquals(VALID, result.status(), result.findings().toString());
            assertTrue(result.findings().isEmpty());
        }
    }

    @Test
    void reportsUnresolvedWhenDistinctBoundariesCollapseInTheCommonProjection() {
        var unit = new Surface(ring("0,0", "1,0", "1,1", "0,1", "0,0"), List.of());
        var east = new Surface(ring("2,0", "3,0", "3,1", "2,1", "2,0"), List.of());
        var distant = new Surface(ring("-100000000000000000000,0", "-100000000000000000000,2",
                "-99999999999999999990,2", "-99999999999999999990,0", "-100000000000000000000,0"), List.of());
        for (var sequence : List.of(new Surface[]{unit, east, distant}, new Surface[]{distant, east, unit})) {
            var result = coverage(true, false, sequence);
            assertEquals(NUMERICALLY_UNDETERMINED, result.status());
            assertTrue(result.findings().isEmpty());
        }
        assertEquals(VALID, coverage(true, false, unit, east).status());
    }

    @Test
    void drawsNoInteriorConclusionsFromPartialFaces() {
        var lower = new Surface(ring("0,-1", "2,-1", "2,0", "0,0", "0,-1"), List.of());
        var upper = new Surface(ring("0,0", "2,0", "2,1", "0,1", "0,0"), List.of());
        var partial = new Surface(ring("0,0", "2,0"), List.of());
        assertEquals(VALID, coverage(true, false, lower, upper).status());
        for (var sequence : List.of(List.of(lower, partial), List.of(partial, lower))) {
            var result = coverage(false, false, sequence, Optional.of(partial));
            assertEquals(MISSING_PARAMETERS, result.status());
            assertTrue(result.findings().isEmpty());
        }
        assertTrue(coverage(false, false, List.of(lower, partial, lower), Optional.of(partial))
                .codes().contains(GEOMETRY_AREA_OVERLAP));
    }

    private static Surface curvedFace(String start, String support, String end, String... vertices) {
        return curvedFace(Optional.empty(), start, support, end, vertices);
    }

    private static Surface curvedFace(Optional<ArcRadius> radius, String start, String support, String end,
                                      String... vertices) {
        var segments = new ArrayList<Segment>();
        segments.add(new Arc(point(support.split(",")), point(end.split(",")), radius));
        for (var vertex : vertices) {
            segments.add(new Straight(point(vertex.split(","))));
        }
        return new Surface(new Ring(List.of(new Polyline(List.of(
                new CurvePart(point(start.split(",")), segments))))), List.of());
    }

    private static Outcome coverage(boolean complete, boolean gaps, Surface... surfaces) {
        return coverage(complete, gaps, List.of(surfaces), Optional.empty());
    }

    private static Outcome coverage(boolean complete, boolean gaps, List<Surface> surfaces, Optional<Surface> partial) {
        var type = new GeometryType(new TypeId("test-area"), new Origin.PredefinedOrigin(LanguageVersion.ILI24, "area"),
                GeometryKind.AREA, List.of(), Optional.empty(), Optional.empty(), false, true, Optional.empty());
        var source = new GeometryEngine.Source() {
            public boolean complete() { return complete; }
            public GeometryEngine.Cursor open() {
                return new GeometryEngine.Cursor() {
                    private int index;
                    public void close() {}
                    public Optional<GeometryEngine.Input> next() {
                        if (index == surfaces.size()) {
                            return Optional.empty();
                        }
                        var tid = new guru.interlis.transfer.TransferIdentity.TransferId(Integer.toString(index),
                                guru.interlis.transfer.TransferIdentity.IdOrigin.SOURCE);
                        var location = new DataLocation(Optional.empty(),
                                new BasketId("basket", guru.interlis.transfer.TransferIdentity.IdOrigin.SOURCE),
                                Optional.of(tid), Optional.empty(), Optional.empty(), List.of());
                        var surface = surfaces.get(index++);
                        return Optional.of(new GeometryEngine.Input(surface, type, location, LanguageVersion.ILI24,
                                Optional.of(new BigDecimal("0.001")), Optional.of(new BigDecimal("0.001")),
                                partial.filter(surface::equals).isPresent()));
                    }
                };
            }
        };
        var findings = new ArrayList<GeometryEngine.Finding>();
        var status = JtsGeometryEngine.standard().checkCoverage(source,
                gaps ? GeometryEngine.CoveragePurpose.AREA_WITHOUT_GAPS : GeometryEngine.CoveragePurpose.AREA,
                new GeometryEngine.Control(() -> {}, findings::add));
        return new Outcome(status, List.copyOf(findings));
    }

    @Test
    void measuresTrueArcLengthAndAreaAndRejectsThreeDimensionalLength() {
        var left = point("-4", "0");
        var right = point("4", "0");
        var top = new Arc(point("0", "4"), right, Optional.empty());
        var arc = new Polyline(List.of(new CurvePart(left, List.of(top))));
        var half = new Surface(new Ring(List.of(new Polyline(List.of(new CurvePart(left,
                List.of(top, new Straight(left))))))), List.of());
        var location = new DataLocation(Optional.empty(), Optional.<BasketId>empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of());
        var control = new GeometryEngine.Control(() -> {}, ignored -> {});
        var engine = JtsGeometryEngine.standard();
        var type = new GeometryType(new TypeId("test"), new Origin.PredefinedOrigin(LanguageVersion.ILI24, "test"),
                GeometryKind.POLYLINE, List.of(), Optional.empty(), Optional.empty(), false, true, Optional.empty());
        var length = engine.measure(GeometryEngine.Measure.LENGTH, new GeometryEngine.Input(
                new MultiPolyline(List.of(arc, arc)), type, location, LanguageVersion.ILI24,
                Optional.empty(), Optional.empty(), false), control);
        assertEquals(8 * Math.PI, assertInstanceOf(GeometryEngine.Measurement.Value.class, length).value(), 1e-12);
        var area = engine.measure(GeometryEngine.Measure.AREA, new GeometryEngine.Input(
                half, type, location, LanguageVersion.ILI24, Optional.empty(), Optional.empty(), false), control);
        assertEquals(8 * Math.PI, assertInstanceOf(GeometryEngine.Measurement.Value.class, area).value(), 1e-12);
        var start = point("0", "0", "0");
        var end = point("3", "4", "12");
        var spatial = new Polyline(List.of(new CurvePart(start, List.of(new Straight(end)))));
        assertEquals(new GeometryEngine.Measurement.Unavailable(UNSUPPORTED),
                engine.measure(GeometryEngine.Measure.LENGTH, new GeometryEngine.Input(spatial, type, location,
                        LanguageVersion.ILI24, Optional.empty(), Optional.empty(), false), control));
    }

    private record Outcome(GeometryEngine.Check status, List<GeometryEngine.Finding> findings) {
        List<ValidationCode> codes() {
            return findings.stream().map(GeometryEngine.Finding::code).toList();
        }
    }

    private static Outcome check(TransferGeometry value, GeometryKind kind, boolean simple, boolean partial) {
        var input = input(value, kind, simple, partial);
        var findings = new ArrayList<GeometryEngine.Finding>();
        var control = new GeometryEngine.Control(() -> {}, findings::add);
        return new Outcome(JtsGeometryEngine.standard().checkValue(input, control), List.copyOf(findings));
    }

    private static GeometryEngine.Input input(TransferGeometry value, GeometryKind kind, boolean simple, boolean partial) {
        var type = new GeometryType(new TypeId("test"), new Origin.PredefinedOrigin(LanguageVersion.ILI24, "test"),
                kind, List.of(), Optional.empty(), Optional.empty(), false, simple, Optional.empty());
        var location = new DataLocation(Optional.empty(), Optional.<BasketId>empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of());
        return new GeometryEngine.Input(value, type, location, LanguageVersion.ILI24,
                Optional.of(new BigDecimal("0.001")), Optional.of(new BigDecimal("0.001")), partial);
    }

    private static Ring ring(String... points) {
        return new Ring(List.of(line(points)));
    }

    private static Polyline line(String... points) {
        var positions = Arrays.stream(points).map(xy -> point(xy.split(","))).toList();
        List<Segment> segments = positions.stream().skip(1).<Segment>map(Straight::new).toList();
        return new Polyline(List.of(new CurvePart(positions.getFirst(), segments)));
    }
}
