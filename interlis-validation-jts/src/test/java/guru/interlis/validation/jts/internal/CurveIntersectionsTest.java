package guru.interlis.validation.jts.internal;

import org.junit.jupiter.api.Test;

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
}
