package guru.interlis.validation.jts.internal;

import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Origin;
import guru.interlis.model.TypeId;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.*;
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

    private record Outcome(GeometryEngine.Check status, List<GeometryEngine.Finding> findings) {
        List<ValidationCode> codes() {
            return findings.stream().map(GeometryEngine.Finding::code).toList();
        }
    }

    private static Outcome check(TransferGeometry value, GeometryKind kind, boolean simple, boolean partial) {
        var type = new GeometryType(new TypeId("test"), new Origin.PredefinedOrigin(LanguageVersion.ILI24, "test"),
                kind, List.of(), Optional.empty(), Optional.empty(), false, simple, Optional.empty());
        var location = new DataLocation(Optional.empty(), Optional.<BasketId>empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of());
        var input = new GeometryEngine.Input(value, type, location, LanguageVersion.ILI24,
                Optional.of(new BigDecimal("0.001")), Optional.of(new BigDecimal("0.001")), partial);
        var findings = new ArrayList<GeometryEngine.Finding>();
        var control = new GeometryEngine.Control(() -> {}, findings::add);
        return new Outcome(JtsGeometryEngine.standard().checkValue(input, control), List.copyOf(findings));
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
