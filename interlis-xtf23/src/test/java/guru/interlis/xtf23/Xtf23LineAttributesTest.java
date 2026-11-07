package guru.interlis.xtf23;

import guru.interlis.model.SnapshotId;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.transfer.TransferEvent.ObjectEvent;
import static guru.interlis.transfer.TransferGeometry.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class Xtf23LineAttributesTest {
    private static final String HEADER = """
            <MODELS><MODEL NAME="Lines" VERSION="1" URI="https://example.invalid/lines"/></MODELS>
            """;
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = compileInline("Lines", """
                INTERLIS 2.3;
                MODEL Lines (en) AT "https://example.invalid/lines" VERSION "1" =
                  DOMAIN Point = COORD 0.00..100.00, 0.00..100.00;
                  TOPIC Data =
                    STRUCTURE Details =
                      note : TEXT*20;
                    END Details;
                    STRUCTURE Marks =
                      label : MANDATORY TEXT*20;
                      details : BAG {0..*} OF Details;
                    END Marks;
                    STRUCTURE Other =
                      label : MANDATORY TEXT*20;
                    END Other;
                    CLASS Item =
                      trace : POLYLINE WITH (STRAIGHTS) VERTEX Point;
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks;
                      parcel : AREA WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks;
                    END Item;
                  END Data;
                END Lines.
                """);
    }

    @Test
    void roundTripsAttributedSurfaceAndAreaPartitionsAndPlainPolyline() {
        String east = line(marks("east").replace("</Lines.Data.Marks>", """
                <details><Lines.Data.Details><note>inside</note></Lines.Data.Details></details></Lines.Data.Marks>
                """), point(0, 0), point(10, 0), point(10, 10));
        String west = line(marks("west"), point(10, 10), point(0, 10), point(0, 0));
        String inner = line(marks("hole"), point(2, 2), point(2, 4), point(4, 4), point(2, 2));
        String shape = "<shape><SURFACE><BOUNDARY>" + east + west + "</BOUNDARY><BOUNDARY>" + inner
                + "</BOUNDARY></SURFACE></shape>";
        String area = "<parcel><SURFACE><BOUNDARY>" + east + west + "</BOUNDARY></SURFACE></parcel>";
        String trace = "<trace>" + line("", point(0, 0), point(1, 1)) + "</trace>";
        var events = read(transfer(trace + shape + area));
        var object = ((ObjectEvent) events.get(2)).object();
        assertTrue(assertInstanceOf(Polyline.class, field(object, "trace")).lineAttributes().isEmpty());
        for (String field : List.of("shape", "parcel")) {
            var surface = assertInstanceOf(Surface.class, field(object, field));
            assertEquals(List.of("east", "west"), surface.shell().lines().stream()
                    .map(Xtf23LineAttributesTest::label).toList());
        }
        var surface = assertInstanceOf(Surface.class, field(object, "shape"));
        var attributes = surface.shell().lines().getFirst().lineAttributes().orElseThrow();
        var details = assertInstanceOf(TransferValue.Repeated.class,
                attributes.value(slot(type("Marks"), "details")).orElseThrow());
        var detail = assertInstanceOf(TransferValue.Structured.class, details.values().getFirst());
        assertEquals(type("Details"), detail.type());
        assertEquals(new TransferValue.Text("inside"), detail.value(slot(type("Details"), "note")).orElseThrow());
        assertEquals("hole", label(surface.holes().getFirst().lines().getFirst()));
        String wire = write(events);
        assertTrue(wire.contains("<POLYLINE><LINEATTR><Lines.Data.Marks><label>east</label>"));
        assertTrue(wire.contains("</Lines.Data.Marks></LINEATTR><COORD>"));
        assertEquals(events, read(wire));
    }

    @Test
    void rejectsMissingUnexpectedMisorderedDuplicateAndUnrelatedLineStructures() {
        String first = point(0, 0);
        String end = point(1, 1);
        String mark = marks("first");
        String complete = line(mark, first, end, first);
        for (String lines : List.of(
                line("", first, end),
                complete + line("", first, end),
                line(mark.replace(".Marks", ".Other"), first, end),
                line(mark.replace(".Marks", ".Item"), first, end),
                line("<LINEATTR/>", first, end),
                line(mark + mark, first, end),
                line(mark.replace("</LINEATTR>", "<Lines.Data.Marks/></LINEATTR>"), first, end),
                "<POLYLINE>" + first + mark + end + "</POLYLINE>",
                "<POLYLINE>" + mark + first + end + mark + "</POLYLINE>")) {
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class,
                    () -> read(transfer("<shape><SURFACE><BOUNDARY>" + lines + "</BOUNDARY></SURFACE></shape>")),
                    lines).reason());
        }
        assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class,
                () -> read(transfer("<trace>" + complete + "</trace>"))).reason());
        assertEquals(UNSUPPORTED, assertThrows(Xtf23Exception.class, () -> read(transfer(
                "<shape><SURFACE><BOUNDARY><POLYLINE>" + mark + "<CLIPPED>" + first + end
                        + "</CLIPPED></POLYLINE></BOUNDARY></SURFACE></shape>"))).reason());
    }

    @Test
    void validatesWriterLineStructureAndRejectsForeignSnapshotBeforeWriting() {
        String line = line(marks("valid"), point(0, 0), point(1, 1), point(0, 0));
        var events = read(transfer("<shape><SURFACE><BOUNDARY>" + line + "</BOUNDARY></SURFACE></shape>"));
        var object = ((ObjectEvent) events.get(2)).object();
        var original = assertInstanceOf(Surface.class, field(object, "shape")).shell().lines().getFirst();
        var absent = new Polyline(original.parts());
        var wrong = new Polyline(original.parts(),
                Optional.of(new TransferValue.Structured(type("Other"), Map.of())));
        for (var bad : List.of(absent, wrong)) {
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class, () -> write(withField(events, "shape",
                    new Surface(new Ring(List.of(bad)), List.of())))).reason());
        }
        assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class,
                () -> write(withField(events, "trace", original))).reason());

        var foreign = new SymbolRef(new SnapshotId("foreign-line-model"), type("Marks").symbol());
        var foreignLine = new Polyline(original.parts(),
                Optional.of(new TransferValue.Structured(foreign, Map.of())));
        assertThrows(SnapshotMismatchException.class, () -> withField(events, "shape",
                new Surface(new Ring(List.of(original)), List.of(new Ring(List.of(foreignLine))))));
    }

    private static String label(Polyline line) {
        var structure = line.lineAttributes().orElseThrow();
        assertEquals(type("Marks"), structure.type());
        var label = (TransferValue.Text) structure.value(slot(structure.type(), "label")).orElseThrow();
        return label.value();
    }

    private static String marks(String value) {
        return "<LINEATTR><Lines.Data.Marks><label>" + value + "</label></Lines.Data.Marks></LINEATTR>";
    }

    private static String point(int x, int y) {
        return "<COORD><C1>" + x + ".00</C1><C2>" + y + ".00</C2></COORD>";
    }

    private static String line(String attrs, String... points) {
        return "<POLYLINE>" + attrs + String.join("", points) + "</POLYLINE>";
    }

    private static String transfer(String fields) {
        return document(HEADER, "<Lines.Data BID=\"b\"><Lines.Data.Item TID=\"obj\">"
                + fields + "</Lines.Data.Item></Lines.Data>");
    }

    private static List<TransferEvent> read(String source) {
        try (var reader = new Xtf23Reader(plan, input(source))) {
            return readAll(reader);
        }
    }

    private static String write(List<TransferEvent> events) {
        var output = new ByteArrayOutputStream();
        try (var writer = new Xtf23Writer(plan, output)) {
            for (var event : events) {
                writer.write(event, TOKEN);
            }
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static TransferValue field(TransferObject object, String name) {
        return object.value(slot(object.type(), name)).orElseThrow();
    }

    private static List<TransferEvent> withField(List<TransferEvent> events, String name, TransferValue value) {
        var replacement = new ArrayList<>(events);
        var event = (ObjectEvent) events.get(2);
        var object = event.object();
        replacement.set(2, new ObjectEvent(new TransferObject(object.identity(),
                new TransferValue.Structured(object.type(), Map.of(slot(object.type(), name), value))),
                event.operation(), event.consistency()));
        return replacement;
    }

    private static SymbolRef type(String name) {
        return plan.objects().stream().filter(type -> type.name().declared().last().equals(name))
                .findFirst().orElseThrow().symbol();
    }

    private static SymbolRef slot(SymbolRef type, String name) {
        return plan.object(type).orElseThrow().slots().stream()
                .filter(slot -> slot.name().declared().last().equals(name)).findFirst().orElseThrow().symbol();
    }
}
