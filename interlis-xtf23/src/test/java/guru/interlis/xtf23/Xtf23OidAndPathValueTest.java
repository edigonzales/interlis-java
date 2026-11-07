package guru.interlis.xtf23;

import guru.interlis.model.QualifiedName;
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

import static guru.interlis.transfer.TransferEvent.ObjectEvent;
import static guru.interlis.transfer.TransferValue.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class Xtf23OidAndPathValueTest {
    private static final String HEADER = """
            <MODELS><MODEL NAME="Paths" VERSION="1" URI="https://example.invalid/paths"/></MODELS>
            <OIDSPACES><OIDSPACE NAME="num" OIDDOMAIN="Paths.NumericId"/>
              <OIDSPACE NAME="tx" OIDDOMAIN="Paths.TextId"/></OIDSPACES>
            """;
    private static final List<Xtf23OidSpace> SPACES = List.of(new Xtf23OidSpace("num", "Paths.NumericId"),
            new Xtf23OidSpace("tx", "Paths.TextId"));
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = compileInline("Paths", """
                INTERLIS 2.3;
                MODEL Paths (en) AT "https://example.invalid/paths" VERSION "1" =
                  DOMAIN
                    TextId = OID TEXT*24;
                    NumericId = OID 0..1000;
                    AnyId = OID ANY;
                  STRUCTURE Detail =
                    title : TEXT*20;
                  END Detail;
                  TOPIC Data =
                    CLASS Item =
                      textId : TextId;
                      numericId : NumericId;
                      anyId : AnyId;
                      uuid : INTERLIS.UUIDOID;
                      className : CLASS;
                      structureName : STRUCTURE;
                      attributeName : ATTRIBUTE;
                    END Item;
                  END Data;
                END Paths.
                """);
    }

    @Test
    void roundTripsOidFamiliesIndependentlyOfBasketAndObjectIdsAndRetainsModelPaths() {
        String uuid = "00112233-4455-6677-8899-aabbccddeeff";
        var events = read(transfer("""
                <textId OID="obj"/><numericId OID="num:1.20e-2"/><anyId OID="tx:basket"/><uuid OID="%s"/>
                <className>Paths.Data.Item</className><structureName>Paths.Detail</structureName>
                <attributeName>Paths.Data.Item.numericId</attributeName>
                """.formatted(uuid)));
        var object = ((ObjectEvent) events.get(2)).object();
        assertEquals("obj", object.identity().tid().orElseThrow().value());
        assertTrue(object.identity().oid().isEmpty());
        assertEquals(new Oid("obj"), field(object, "textId"));
        assertEquals(new Oid("num:1.20e-2"), field(object, "numericId"));
        assertEquals(new Oid("tx:basket"), field(object, "anyId"));
        assertEquals(new Oid(uuid), field(object, "uuid"));
        assertEquals(new ClassName(QualifiedName.of("Paths", "Data", "Item")), field(object, "className"));
        assertEquals(new ClassName(QualifiedName.of("Paths", "Detail")), field(object, "structureName"));
        assertEquals(new AttributePath(QualifiedName.of("Paths", "Data", "Item", "numericId")),
                field(object, "attributeName"));
        String wire = write(events);
        assertTrue(wire.contains("<numericId OID=\"num:1.20e-2\""));
        assertEquals(events, read(wire));
    }

    @Test
    void leavesDomainBoundsAndModelPathTargetResolutionToDataValidation() {
        var events = read(transfer("""
                <numericId OID="1001"/><className>AnotherModel.Data.Other</className>
                <attributeName>AnotherModel.Record.field</attributeName>
                """));
        assertEquals(events, read(write(events)));
    }

    @Test
    void rejectsInvalidWireValuesOnRead() {
        for (String value : List.of("<textId/>", "<textId REF='obj'/>", "<textId TID='obj'/>",
                "<textId BID='basket'/>", "<textId OID='obj'>body</textId>", "<textId OID='obj'><x/></textId>",
                "<textId OID='missing:obj'/>", "<textId OID='tx:123'/>", "<anyId OID='obj'/>",
                "<numericId OID='num:abc'/>", "<numericId OID='007'/>",
                "<numericId OID='num:-1'/>", "<className>Item</className>", "<className>Paths..Item</className>",
                "<className>Paths.Data.Item.extra</className>", "<structureName> Paths.Detail</structureName>",
                "<structureName>Paths.&#xA;Detail</structureName>", "<attributeName>Paths.Item.</attributeName>",
                "<attributeName>Paths.Item</attributeName>", "<attributeName><x/></attributeName>")) {
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class, () -> read(transfer(value)), value).reason());
        }
    }

    @Test
    void rejectsWrongNeutralKindsAndInvalidLexemesOnWrite() {
        var events = read(transfer(""));
        for (var field : Map.<String, TransferValue>of(
                "numericId", new Oid("num:07"),
                "textId", new Oid("missing:key"),
                "anyId", new Oid("object"),
                "className", new ClassName(QualifiedName.of("Paths.Data", "Item")),
                "structureName", new AttributePath(QualifiedName.of("Paths", "Detail", "title")),
                "attributeName", new AttributePath(QualifiedName.of("Paths", "Detail"))).entrySet()) {
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class,
                    () -> write(withField(events, field.getKey(), field.getValue())), field.toString()).reason());
        }
        assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class,
                () -> write(withField(events, "anyId", new Text("tx:object")))).reason());
        String longPath = "P".repeat(Xtf23Lexemes.MAX_MODEL_PATH_LENGTH) + ".Item";
        assertEquals(LIMIT_EXCEEDED, assertThrows(Xtf23Exception.class,
                () -> read(transfer("<className>" + longPath + "</className>"))).reason());
        assertEquals(LIMIT_EXCEEDED, assertThrows(Xtf23Exception.class, () -> write(withField(events, "className",
                new ClassName(QualifiedName.of("P".repeat(Xtf23Lexemes.MAX_MODEL_PATH_LENGTH), "Item"))))).reason());
    }

    private static String transfer(String fields) {
        return document(HEADER, "<Paths.Data BID=\"basket\"><Paths.Data.Item TID=\"obj\">"
                + fields + "</Paths.Data.Item></Paths.Data>");
    }

    private static List<TransferEvent> read(String source) {
        try (var reader = new Xtf23Reader(plan, input(source))) {
            return readAll(reader);
        }
    }

    private static String write(List<TransferEvent> events) {
        var output = new ByteArrayOutputStream();
        try (var writer = new Xtf23Writer(plan, output, SPACES)) {
            for (var event : events) {
                writer.write(event, TOKEN);
            }
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static TransferValue field(TransferObject object, String name) {
        return object.value(slot(object, name)).orElseThrow();
    }

    private static List<TransferEvent> withField(List<TransferEvent> events, String name, TransferValue value) {
        var replacement = new ArrayList<>(events);
        var event = (ObjectEvent) events.get(2);
        var object = event.object();
        replacement.set(2, new ObjectEvent(new TransferObject(object.identity(),
                new Structured(object.type(), Map.of(slot(object, name), value))),
                event.operation(), event.consistency()));
        return replacement;
    }

    private static guru.interlis.model.SymbolRef slot(TransferObject object, String name) {
        return plan.object(object.type()).orElseThrow().slots().stream()
                .filter(slot -> slot.name().declared().last().equals(name)).findFirst().orElseThrow().symbol();
    }
}
