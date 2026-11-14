package guru.interlis.xtf23;

import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;

import static guru.interlis.transfer.TransferGeometry.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

final class Xtf23GeometryTest {
    private static TransferPlan curves;
    private static TransferPlan reference;
    private static final String MODELS_CURVES = """
            <MODELS><MODEL NAME="Curves" VERSION="1" URI="https://example.invalid/curves"/></MODELS>
            """;
    private static final String START_3D = "<COORD><C1>0.00</C1><C2>0.0</C2><C3>+1.2e0</C3></COORD>";

    @BeforeAll
    static void compileModels() throws IOException {
        reference = referencePlan();
        curves = compileInline("Curves", """
                INTERLIS 2.3;
                MODEL Curves (en) AT "https://example.invalid/curves" VERSION "1" =
                  TOPIC Data =
                    DOMAIN
                      Point2 = COORD 0.00 .. 100.00, 0.00 .. 100.00;
                      Point3 = COORD 0.00 .. 100.00, 0.00 .. 100.00, 0.00 .. 100.00;
                    CLASS Shape =
                      point: Point3;
                      trace: POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Point3;
                      surface: SURFACE WITH (STRAIGHTS, ARCS) VERTEX Point2 WITHOUT OVERLAPS > 0.01;
                      straight: POLYLINE WITH (STRAIGHTS) VERTEX Point2;
                    END Shape;
                  END Data;
                END Curves.
                """);
    }

    @Test
    void preservesThreeDimensionalEndpointsAndSignedExactArcRadii() {
        String arc = """
                <ARC><C1>2.0100</C1><C2>3.002</C2><C3>5.4321</C3><A1>1.11000</A1><A2>2.000</A2><R>-9.12345678901234567890</R></ARC>
                <ARC><C1>3.0</C1><C2>4.0</C2><C3>6.0</C3><A1>2.22</A1><A2>3.33</A2><R>+8.5e-1</R></ARC>
                """;
        String straight = "<COORD><C1>4.0</C1><C2>5.0</C2><C3>7.0</C3></COORD>";
        try (var reader = new Xtf23Reader(curves, input(shape("<point>" + START_3D + "</point><trace><POLYLINE>"
                + START_3D + arc + straight + "</POLYLINE></trace>")))) {
            var object = objects(reader).getFirst();
            var point = assertInstanceOf(Coordinate.class, field(curves, object, "point"));
            assertEquals(List.of("0.00", "0.0", "+1.2e0"), lexemes(point));
            var line = assertInstanceOf(Polyline.class, field(curves, object, "trace"));
            assertEquals(1, line.parts().size());
            var part = line.parts().getFirst();
            assertEquals(point, part.start());
            assertEquals(3, part.segments().size());
            var negative = assertInstanceOf(Arc.class, part.segments().get(0));
            assertEquals(List.of("2.0100", "3.002", "5.4321"), lexemes(negative.end()));
            assertEquals(List.of("1.11000", "2.000"), lexemes(negative.support()));
            assertEquals(new TransferValue.NumberValue("-9.12345678901234567890", new BigDecimal("-9.12345678901234567890")),
                    negative.radius().orElseThrow().sourceValue());
            assertEquals(RadiusConvention.SIGNED_CLOCKWISE, negative.radius().orElseThrow().convention());
            var positive = assertInstanceOf(Arc.class, part.segments().get(1));
            assertEquals("+8.5e-1", positive.radius().orElseThrow().sourceValue().lexeme());
            assertEquals(new BigDecimal("0.85"), positive.radius().orElseThrow().sourceValue().exactValue());
            assertEquals(List.of("4.0", "5.0", "7.0"), lexemes(assertInstanceOf(Straight.class,
                    part.segments().getLast()).end()));
        }
    }

    @Test
    void preservesShellHolesAndPartitionIntoBoundaryLines() {
        String first = line(point("0", "0"), point("10", "0"), point("10", "10"));
        String second = line(point("10", "10"), point("0", "10"), point("0", "0"));
        String hole = line(point("2", "2"), point("3", "2"), point("3", "3"), point("2", "2"));
        String field = "<surface><SURFACE><BOUNDARY>" + first + second + "</BOUNDARY><BOUNDARY>" + hole
                + "</BOUNDARY></SURFACE></surface>";
        try (var reader = new Xtf23Reader(curves, input(shape(field)))) {
            var surface = assertInstanceOf(Surface.class, field(curves, objects(reader).getFirst(), "surface"));
            assertEquals(2, surface.shell().lines().size());
            var lines = surface.shell().lines();
            assertEquals(List.of("0", "0"), lexemes(lines.get(0).parts().getFirst().start()));
            assertEquals(List.of("10", "10"), lexemes(lines.get(1).parts().getFirst().start()));
            assertEquals(1, surface.holes().size());
            assertEquals(List.of("2", "2"), lexemes(surface.holes().getFirst().lines().getFirst().parts().getFirst().start()));
        }
    }

    @Test
    void decodesAreaFixturesAndPreservesTheSelfCrossingG0Boundary() throws IOException {
        for (String name : List.of("area-shared-edge.xtf", "area-overlap.xtf")) {
            try (var source = Files.newInputStream(fixture(name)); var reader = new Xtf23Reader(reference, source)) {
                var objects = objects(reader);
                assertEquals(2, objects.size());
                for (var object : objects) {
                    var area = assertInstanceOf(Surface.class, field(reference, object, "shape"));
                    assertEquals(4, area.shell().lines().getFirst().parts().getFirst().segments().size());
                }
            }
        }
        // Same wire points as the pinned G0-B area-self-crossing-ring case.
        String crossing = line(point("10.00", "10.00"), point("20.00", "20.00"), point("20.00", "10.00"),
                point("10.00", "20.00"), point("10.00", "10.00"));
        String xml = document(MODELS, """
                <Reference23.Records BID="b"><Reference23.Records.Parcel TID="p">
                  <shape><SURFACE><BOUNDARY>%s</BOUNDARY></SURFACE></shape>
                </Reference23.Records.Parcel></Reference23.Records>
                """.formatted(crossing));
        try (var reader = new Xtf23Reader(reference, input(xml))) {
            var area = assertInstanceOf(Surface.class, field(reference, objects(reader).getFirst(), "shape"));
            var vertices = area.shell().lines().getFirst().parts().getFirst().segments().stream()
                    .map(segment -> lexemes(segment.end())).toList();
            assertEquals(List.of(List.of("20.00", "20.00"), List.of("20.00", "10.00"),
                    List.of("10.00", "20.00"), List.of("10.00", "10.00")), vertices);
        }
    }

    @Test
    void roundTripsAnArcEvenWhenTheModelAllowsOnlyStraightSegments() {
        String arc = "<ARC><C1>3</C1><C2>4</C2><A1>2</A1><A2>4</A2></ARC>";
        String fields = "<straight>" + line(point("1", "2"), arc) + "</straight>";
        try (var reader = new Xtf23Reader(curves, input(shape(fields)))) {
            var events = readAll(reader);
            var object = assertInstanceOf(ObjectEvent.class, events.get(2)).object();
            var polyline = assertInstanceOf(Polyline.class, field(curves, object, "straight"));
            var decoded = assertInstanceOf(Arc.class, polyline.parts().getFirst().segments().getFirst());
            assertEquals(List.of("2", "4"), lexemes(decoded.support()));

            var output = new ByteArrayOutputStream();
            try (var writer = new Xtf23Writer(curves, output)) {
                for (var event : events) {
                    writer.write(event, TOKEN);
                }
            }
            try (var rereader = new Xtf23Reader(curves, new ByteArrayInputStream(output.toByteArray()))) {
                assertEquals(events, readAll(rereader));
            }
        }
    }

    @Test
    void retainsTheXtf23ZeroRadiusWriterRejectionAfterNeutralRelaxation() {
        String arc = "<ARC><C1>3</C1><C2>4</C2><A1>2</A1><A2>4</A2><R>1</R></ARC>";
        try (var reader = new Xtf23Reader(curves, input(shape("<straight>" + line(point("1", "2"), arc) + "</straight>")))) {
            var events = new java.util.ArrayList<>(readAll(reader));
            var occurrence = (ObjectEvent) events.get(2);
            var object = occurrence.object();
            var original = (Polyline) field(curves, object, "straight");
            var part = original.parts().getFirst();
            var originalArc = (Arc) part.segments().getFirst();
            var zero = new Arc(originalArc.support(), originalArc.end(),
                    java.util.Optional.of(new ArcRadius(new TransferValue.NumberValue("0", BigDecimal.ZERO), RadiusConvention.SIGNED_CLOCKWISE)));
            var changed = new Polyline(List.of(new CurvePart(part.start(), List.of(zero))));
            var values = new java.util.LinkedHashMap<>(object.data().values());
            values.replaceAll((slot, value) -> changed);
            events.set(2, new ObjectEvent(new TransferObject(object.identity(),
                    new TransferValue.Structured(object.type(), values)), occurrence.operation(), occurrence.consistency()));
            try (var writer = new Xtf23Writer(curves, new ByteArrayOutputStream())) {
                writer.write(events.getFirst());
                writer.write(events.get(1));
                assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class, () -> writer.write(events.get(2))).reason());
            }
        }
    }

    @Test
    void rejectsMalformedGeometryAndReportsClippedAsUnsupported() {
        String two = point("1", "2");
        String validArc = "<ARC><C1>3</C1><C2>4</C2><A1>2</A1><A2>3</A2></ARC>";
        for (String value : List.of(
                "<point>" + two + "</point>",
                "<trace>" + line(START_3D) + "</trace>",
                "<trace>" + line(START_3D, validArc) + "</trace>",
                "<trace>" + line(START_3D, two) + "</trace>",
                "<surface><SURFACE><BOUNDARY/></SURFACE></surface>",
                "<surface><SURFACE/></surface>",
                "<surface><AREA/></surface>",
                "<surface><SURFACE><BOUNDARY>" + line(two, validArc.replace("</ARC>", "<R>0.0</R></ARC>"))
                        + "</BOUNDARY></SURFACE></surface>",
                "<surface><SURFACE><BOUNDARY>" + line(two, validArc.replace("</ARC>", "<RADIUS>1</RADIUS></ARC>"))
                        + "</BOUNDARY></SURFACE></surface>")) {
            assertEquals(INVALID_WIRE, failed(value), value);
        }
        assertEquals(UNSUPPORTED, failed("<trace><POLYLINE><CLIPPED>"
                + START_3D + START_3D + "</CLIPPED></POLYLINE></trace>"));
        assertEquals(LIMIT_EXCEEDED, failed("<point>" + START_3D.replace("0.00", "1e2147483649") + "</point>"));
    }

    private static Xtf23Exception.Reason failed(String fields) {
        try (var reader = new Xtf23Reader(curves, input(shape(fields)))) {
            return assertThrows(Xtf23Exception.class, () -> readAll(reader)).reason();
        }
    }

    private static String shape(String fields) {
        return document(MODELS_CURVES, "<Curves.Data BID=\"basket\"><Curves.Data.Shape TID=\"shape\">"
                + fields + "</Curves.Data.Shape></Curves.Data>");
    }

    private static String point(String x, String y) {
        return "<COORD><C1>" + x + "</C1><C2>" + y + "</C2></COORD>";
    }

    private static String line(String... entries) {
        return "<POLYLINE>" + String.join("", entries) + "</POLYLINE>";
    }

    private static List<TransferObject> objects(Xtf23Reader reader) {
        return readAll(reader).stream().filter(ObjectEvent.class::isInstance).map(ObjectEvent.class::cast)
                .map(ObjectEvent::object).toList();
    }

    private static TransferValue field(TransferPlan plan, TransferObject value, String name) {
        var slot = plan.object(value.type()).orElseThrow().slots().stream()
                .filter(candidate -> candidate.name().declared().last().equals(name)).findFirst().orElseThrow();
        return value.value(slot.symbol()).orElseThrow();
    }

    private static List<String> lexemes(Coordinate point) {
        return point.ordinates().stream().map(TransferValue.NumberValue::lexeme).toList();
    }
}
