package guru.interlis.transfer;

import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.MultiCoordinate;
import guru.interlis.transfer.TransferGeometry.MultiPolyline;
import guru.interlis.transfer.TransferGeometry.MultiSurface;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferGeometry.Ring;
import guru.interlis.transfer.TransferGeometry.Segment;
import guru.interlis.transfer.TransferGeometry.Straight;
import guru.interlis.transfer.TransferGeometry.Surface;
import guru.interlis.transfer.TransferValue.NumberValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferGeometryValueTest {
    @Test
    void retainsExactOrdinatesLexemesAndAxisOrder() {
        var ordinates = new ArrayList<>(List.of(number("100000000000000000.001"), number("0.000000000000001")));
        var coordinate = new Coordinate(ordinates);
        ordinates.clear();
        assertEquals(List.of("100000000000000000.001", "0.000000000000001"),
                coordinate.ordinates().stream().map(NumberValue::lexeme).toList());
        assertEquals(List.of(new BigDecimal("100000000000000000.001"), new BigDecimal("0.000000000000001")),
                coordinate.ordinates().stream().map(NumberValue::exactValue).toList());
        assertThrows(UnsupportedOperationException.class, () -> coordinate.ordinates().clear());
        assertEquals(List.of(number("1.00"), number("2.00"), number("3.00")),
                coordinate("1.00", "2.00", "3.00").ordinates());
    }

    @Test
    void retainsCurvePartsStraightSegmentsAndArcPointsWithSignedRadius() {
        var start = coordinate("0", "0", "1.00");
        var straightEnd = coordinate("2", "0", "2.00");
        var support = coordinate("3.00000000001", "1.00000000001");
        var arcEnd = coordinate("2", "2", "3.00");
        var segments = new ArrayList<Segment>(List.of(new Straight(straightEnd),
                new Arc(support, arcEnd, Optional.of(number("-1.000")))));
        var part = new CurvePart(start, segments);
        segments.clear();
        var next = new CurvePart(coordinate("5", "5", "6.00"), List.of());
        var parts = new ArrayList<>(List.of(part, next));
        var line = new Polyline(parts);
        parts.clear();

        assertEquals(List.of(part, next), line.parts());
        assertEquals(start, part.start());
        assertEquals(List.of(new Straight(straightEnd), new Arc(support, arcEnd, Optional.of(number("-1.000")))),
                part.segments());
        var arc = (Arc) part.segments().get(1);
        assertEquals(support, arc.support());
        assertEquals(arcEnd, arc.end());
        assertEquals("-1.000", arc.radius().orElseThrow().lexeme());
        assertEquals(new BigDecimal("-1.000"), arc.radius().orElseThrow().exactValue());
        assertEquals(Optional.of(number("1.000")), new Arc(support, arcEnd, Optional.of(number("1.000"))).radius());
        assertEquals(Optional.empty(), new Arc(support, arcEnd, Optional.empty()).radius());
        assertEquals(List.of(), next.segments());
        assertThrows(UnsupportedOperationException.class, () -> part.segments().clear());
        assertThrows(UnsupportedOperationException.class, () -> line.parts().clear());
        assertEquals(line, new Polyline(List.of(new CurvePart(start, List.copyOf(part.segments())), next)));
    }

    @Test
    void retainsRingPartitionsHoleOrderAndMultiGeometryComponents() {
        var origin = coordinate("0", "0");
        var corner = coordinate("4", "4");
        var lower = line(origin, coordinate("4", "0"), corner);
        var upper = line(corner, coordinate("0", "4"), origin);
        var parts = new ArrayList<>(List.of(lower, upper));
        var shell = new Ring(parts);
        parts.clear();
        var hole = new Ring(List.of(line(coordinate("1", "1"), coordinate("1", "2"),
                coordinate("2", "2"), coordinate("1", "1"))));
        var secondHole = new Ring(List.of(line(coordinate("3", "3"), coordinate("3", "3.5"),
                coordinate("3.5", "3.5"), coordinate("3", "3"))));
        var holes = new ArrayList<>(List.of(hole, secondHole));
        var surface = new Surface(shell, holes);
        holes.clear();
        var secondSurface = new Surface(hole, List.of());
        var surfaces = new ArrayList<>(List.of(surface, secondSurface));
        var multi = new MultiSurface(surfaces);
        surfaces.clear();

        assertEquals(List.of(lower, upper), multi.surfaces().getFirst().shell().lines());
        assertEquals(List.of(hole, secondHole), surface.holes());
        assertEquals(new MultiSurface(List.of(new Surface(new Ring(List.of(lower, upper)),
                List.of(hole, secondHole)), secondSurface)), multi);
        assertEquals(List.of(origin, corner, origin), new MultiCoordinate(List.of(origin, corner, origin)).coordinates());
        assertEquals(List.of(upper, lower), new MultiPolyline(List.of(upper, lower)).lines());
        assertEquals(List.of(), new MultiCoordinate(List.of()).coordinates());
        assertEquals(List.of(), new MultiPolyline(List.of()).lines());
        assertEquals(List.of(), new MultiSurface(List.of()).surfaces());
        assertThrows(UnsupportedOperationException.class, () -> shell.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> surface.holes().clear());
        assertThrows(UnsupportedOperationException.class, () -> multi.surfaces().clear());
    }

    @Test
    void keepsAnEmptyExtractLineWithoutAssigningADimension() {
        var empty = new Polyline(List.of());
        var line2 = line(coordinate("0", "0"));
        var line3 = line(coordinate("0", "0", "1"));

        assertEquals(List.of(), empty.parts());
        assertEquals(List.of(empty, line2, empty), new MultiPolyline(List.of(empty, line2, empty)).lines());
        assertEquals(List.of(line3, empty), new MultiPolyline(List.of(line3, empty)).lines());
        assertEquals(List.of(line2, empty, line3), new MultiPolyline(List.of(line2, empty, line3)).lines());
        assertThrows(IllegalArgumentException.class, () -> new Ring(List.of(empty)));
    }

    @Test
    void rejectsMissingGeometryComponents() {
        var point = coordinate("0", "0");
        assertThrows(NullPointerException.class, () -> new Coordinate(null));
        assertThrows(NullPointerException.class, () -> new MultiCoordinate(Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> new Straight(null));
        assertThrows(NullPointerException.class, () -> new Arc(null, point, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new Arc(point, point, null));
        assertThrows(NullPointerException.class, () -> new CurvePart(point, Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> new Ring(List.of()));
        assertThrows(NullPointerException.class, () -> new Surface(null, List.of()));
    }

    @Test
    void retainsDecodableDimensionDifferencesAndZeroRadius() {
        var xy = coordinate("0", "0");
        var xyz = coordinate("0", "0", "1");
        var line2 = line(xy);
        var line3 = line(xyz);
        var ring2 = new Ring(List.of(line2));
        var ring3 = new Ring(List.of(line3));

        assertThrows(IllegalArgumentException.class, () -> coordinate("0"));
        assertThrows(IllegalArgumentException.class, () -> coordinate("0", "0", "1", "1"));
        assertThrows(IllegalArgumentException.class, () -> new Arc(xyz, xy, Optional.empty()));
        assertEquals(Optional.of(number("0.00")), new Arc(xy, xyz, Optional.of(number("0.00"))).radius());
        var straight = new Straight(xyz);
        var arc = new Arc(xy, xyz, Optional.empty());
        assertEquals(List.of(straight, arc), new CurvePart(xy, List.of(straight, arc)).segments());
        assertEquals(List.of(line2.parts().getFirst(), line3.parts().getFirst()),
                new Polyline(List.of(line2.parts().getFirst(), line3.parts().getFirst())).parts());
        assertEquals(List.of(xy, xyz), new MultiCoordinate(List.of(xy, xyz)).coordinates());
        assertEquals(List.of(line2, line3), new MultiPolyline(List.of(line2, line3)).lines());
        assertEquals(List.of(line2, line3), new Ring(List.of(line2, line3)).lines());
        assertEquals(List.of(ring3), new Surface(ring2, List.of(ring3)).holes());
        var surfaces = List.of(new Surface(ring2, List.of()), new Surface(ring3, List.of()));
        assertEquals(surfaces, new MultiSurface(surfaces).surfaces());
    }

    private static Polyline line(Coordinate start, Coordinate... ends) {
        List<Segment> segments = new ArrayList<>();
        for (Coordinate end : ends) {
            segments.add(new Straight(end));
        }
        return new Polyline(List.of(new CurvePart(start, segments)));
    }

    private static Coordinate coordinate(String... ordinates) {
        return new Coordinate(List.of(ordinates).stream().map(TransferGeometryValueTest::number).toList());
    }

    private static NumberValue number(String lexeme) {
        return new NumberValue(lexeme, new BigDecimal(lexeme));
    }
}
