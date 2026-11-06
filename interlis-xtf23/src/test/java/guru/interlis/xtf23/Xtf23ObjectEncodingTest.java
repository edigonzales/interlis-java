package guru.interlis.xtf23;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static guru.interlis.transfer.TransferIdentity.*;
import static guru.interlis.transfer.TransferValue.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

final class Xtf23ObjectEncodingTest {
    private static TransferPlan reference;
    private static TransferPlan values;
    private static final String VALUES = """
            <MODELS><MODEL NAME="Values" VERSION="1" URI="https://example.invalid/values"/></MODELS>
            """;
    private static final String OIDSPACES = """
            <OIDSPACES><OIDSPACE NAME="u" OIDDOMAIN="INTERLIS.UUIDOID"/></OIDSPACES>
            """;

    @BeforeAll
    static void compileModels() throws IOException {
        reference = referencePlan();
        values = compileInline("Values", """
                INTERLIS 2.3;
                MODEL Values (en) AT "https://example.invalid/values" VERSION "1" =
                  TOPIC Data =
                    CLASS Parent = END Parent;
                    STRUCTURE Detail =
                      label: TEXT*32;
                      owner: REFERENCE TO (EXTERNAL) Parent;
                    END Detail;
                    CLASS Item =
                      quantity: -100.000 .. 999.000;
                      flag: BOOLEAN;
                      shade: (dark (red, blue), light);
                      label: TEXT*32;
                      notes: MTEXT*64;
                      one: Detail;
                      details: BAG {0..*} OF Detail;
                      sequence: LIST {0..*} OF Detail;
                    END Item;
                    ASSOCIATION Note =
                      parent (EXTERNAL) -- {0..1} Parent;
                      item -- {0..*} Item;
                      remark: TEXT*32;
                    END Note;
                    ASSOCIATION Peer =
                      left (ORDERED, EXTERNAL) -- {0..*} Parent;
                      right -- {0..*} Item;
                      remark: TEXT*32;
                    END Peer;
                    ASSOCIATION Stable (OID) =
                      first -- {0..1} Parent;
                      second -- {0..*} Item;
                    END Stable;
                  END Data;
                END Values.
                """);
    }

    @Test
    void readsPinnedObjectsAndDoesNotEvaluateUniqueAcrossBaskets() throws IOException {
        for (String fixture : List.of("identity.xtf", "unique-across-baskets.xtf")) {
            try (var source = Files.newInputStream(fixture(fixture));
                 var reader = new Xtf23Reader(reference, source)) {
                var objects = objects(reader);
                assertEquals(3, objects.size());
                var item = objects.get(1);
                assertEquals(new Text("same"), field(reference, item, "code"));
                var details = assertInstanceOf(Repeated.class, field(reference, item, "details"));
                assertEquals(CollectionKind.LIST, details.kind());
                assertEquals(List.of(new Text("zéro & \\"), new Text("alpha")),
                        details.values().stream().map(value -> field(reference, (Structured) value, "description")).toList());
                var parent = assertInstanceOf(Reference.class,
                        item.value(slot(reference, "Reference23.Records.ParentLink", "parent")).orElseThrow());
                assertEquals(new TransferId("parent-1", IdOrigin.SOURCE), parent.target());
                assertEquals(Optional.of(new BasketId("records-1", IdOrigin.SOURCE)), parent.targetBasket());
                assertFalse(parent.external());
                assertEquals(new Text(fixture.equals("identity.xtf") ? "other" : "same"),
                        field(reference, objects.getLast(), "code"));
            }
        }
    }

    @Test
    void keepsScalarLexemesEmptyTextAndOrderedStructureValues() {
        String detail = "<Values.Data.Detail><label>again</label></Values.Data.Detail>";
        String data = """
                <quantity>+1.2300e+2</quantity><flag>true</flag><shade>dark.red</shade>
                <label/><notes>line&#9;1&#13;&#10;line 2</notes>
                <one><Values.Data.Detail><owner REF="unresolved" BID="elsewhere"/></Values.Data.Detail></one>
                <details>%s%s</details><sequence>%s<Values.Data.Detail/></sequence>
                """.formatted(detail, detail, detail);
        try (var reader = new Xtf23Reader(values, input(valuesDocument(VALUES, item("item", data))))) {
            var value = objects(reader).getFirst();
            assertEquals(new NumberValue("+1.2300e+2", new BigDecimal("+1.2300e+2")), field(values, value, "quantity"));
            assertEquals(new BooleanValue(true), field(values, value, "flag"));
            assertEquals(new Enumeration(List.of("dark", "red")), field(values, value, "shade"));
            assertEquals(new Text(""), field(values, value, "label"));
            assertEquals(new Text("line\t1\r\nline 2"), field(values, value, "notes"));
            var structure = assertInstanceOf(Structured.class, field(values, value, "one"));
            assertTrue(structure.value(slot(values, "Values.Data.Detail", "label")).isEmpty());
            var reference = assertInstanceOf(Reference.class, field(values, structure, "owner"));
            assertTrue(reference.external());
            assertEquals("elsewhere", reference.targetBasket().orElseThrow().value());
            assertEquals("unresolved", reference.target().value());
            var bag = assertInstanceOf(Repeated.class, field(values, value, "details"));
            assertEquals(CollectionKind.BAG, bag.kind());
            assertEquals(2, bag.values().size());
            assertEquals(bag.values().getFirst(), bag.values().getLast());
            var sequence = assertInstanceOf(Repeated.class, field(values, value, "sequence"));
            assertEquals(CollectionKind.LIST, sequence.kind());
            assertEquals(bag.values().getFirst(), sequence.values().getFirst());
            assertTrue(((Structured) sequence.values().getLast()).values().isEmpty());
        }
    }

    @Test
    void preservesEmbeddedPayloadStandaloneRolesAndSeparateIdentityKinds() {
        String stable = "u:123e4567-e89b-12d3-a456-426614174000";
        String data = item("item", """
                <parent REF="%s" BID="foreign">
                  <Values.Data.Note><remark>embedded</remark></Values.Data.Note>
                </parent>
                """.formatted(stable)) + """
                <Values.Data.Peer>
                  <left REF="%s" BID="foreign" ORDER_POS="0003"/><right REF="item"/>
                  <remark>standalone</remark>
                </Values.Data.Peer>
                <Values.Data.Stable TID="%s" BID="origin"><first REF="absent"/><second REF="item"/></Values.Data.Stable>
                """.formatted(stable, stable);
        try (var reader = new Xtf23Reader(values, input(valuesDocument(VALUES + OIDSPACES, data)))) {
            var objects = objects(reader);
            var parentRole = slot(values, "Values.Data.Note", "parent");
            var embedded = assertInstanceOf(Structured.class, objects.getFirst().value(parentRole).orElseThrow());
            assertEquals("Values.Data.Note", values.object(embedded.type()).orElseThrow().name().declared().toString());
            assertEquals(new Text("embedded"), field(values, embedded, "remark"));
            var parent = assertInstanceOf(Reference.class, embedded.value(parentRole).orElseThrow());
            assertEquals(new ObjectOid(stable), parent.target());
            assertEquals("foreign", parent.targetBasket().orElseThrow().value());
            assertTrue(parent.external());
            var peer = objects.get(1);
            assertTrue(peer.identity().tid().isEmpty());
            var left = assertInstanceOf(Reference.class, field(values, peer, "left"));
            assertEquals(new ObjectOid(stable), left.target());
            assertEquals(Optional.of(BigInteger.valueOf(3)), left.orderPosition());
            assertEquals(Optional.of(new BasketId("foreign", IdOrigin.SOURCE)), left.targetBasket());
            assertEquals(new Text("standalone"), field(values, peer, "remark"));
            var link = objects.getLast();
            assertEquals(Optional.of(new TransferId(stable, IdOrigin.SOURCE)), link.identity().tid());
            assertEquals(Optional.of(new ObjectOid(stable)), link.identity().oid());
            assertEquals("origin", link.identity().originalBasket().orElseThrow().value());
            assertEquals("data", link.identity().currentBasket().value());
        }
    }

    @Test
    void preservesTheConcreteAssociationWhenAllOfItsAttributesAreAbsent() {
        String data = item("item", "<parent REF=\"absent\"><Values.Data.Note/></parent>");
        try (var reader = new Xtf23Reader(values, input(valuesDocument(VALUES, data)))) {
            var parentRole = slot(values, "Values.Data.Note", "parent");
            var link = assertInstanceOf(Structured.class, objects(reader).getFirst().value(parentRole).orElseThrow());
            assertEquals("Values.Data.Note", values.object(link.type()).orElseThrow().name().declared().toString());
            assertEquals(List.of(parentRole), List.copyOf(link.values().keySet()));
            assertEquals("absent", assertInstanceOf(Reference.class, link.value(parentRole).orElseThrow()).target().value());
        }
    }

    @Test
    void rejectsMalformedValuesAndFieldsWithoutDroppingThem() {
        for (String malformed : List.of(
                "<quantity>01.20</quantity>", "<quantity>١٢</quantity>", "<flag>True</flag>",
                "<shade>dark..red</shade>", "<label>a&#10;b</label>", "<details/>", "<sequence/>",
                "<one><Values.Data.Parent/></one>", "<flag>true</flag><flag>false</flag>",
                "<label>before</label><flag>false</flag>", "<unknown/>",
                "<one><Values.Data.Detail><owner REF=\"x\" ORDER_POS=\"1\"/></Values.Data.Detail></one>")) {
            assertEquals(INVALID_WIRE, failed(item("item", malformed)), malformed);
        }
        assertEquals(UNSUPPORTED, failed(item("item", "<quantity>1:20</quantity>")));
        assertEquals(INVALID_WIRE, failed("<Values.Data.Note/>"));
        assertEquals(INVALID_WIRE, failed("<Values.Data.Peer TID=\"invalid\"/>"));
        assertEquals(INVALID_WIRE, failed("<Values.Data.Stable/>"));
        assertEquals(INVALID_WIRE, failed("<Values.Data.Peer><left REF=\"x\" ORDER_POS=\"0\"/></Values.Data.Peer>"));
        assertEquals(INVALID_WIRE, failed("<Values.Data.Peer><right REF=\"x\" ORDER_POS=\"1\"/></Values.Data.Peer>"));
    }

    private static Xtf23Exception.Reason failed(String xml) {
        try (var reader = new Xtf23Reader(values, input(valuesDocument(VALUES, xml)))) {
            return assertThrows(Xtf23Exception.class, () -> readAll(reader)).reason();
        }
    }

    private static String item(String tid, String fields) {
        return "<Values.Data.Item TID=\"" + tid + "\">" + fields + "</Values.Data.Item>";
    }

    private static String valuesDocument(String models, String objects) {
        return document(models, "<Values.Data BID=\"data\">" + objects + "</Values.Data>");
    }

    private static List<guru.interlis.transfer.TransferObject> objects(Xtf23Reader reader) {
        return readAll(reader).stream().filter(ObjectEvent.class::isInstance).map(ObjectEvent.class::cast)
                .map(ObjectEvent::object).toList();
    }

    private static TransferValue field(TransferPlan plan, guru.interlis.transfer.TransferObject value, String name) {
        return field(plan, value.data(), name);
    }

    private static TransferValue field(TransferPlan plan, Structured value, String name) {
        String type = plan.object(value.type()).orElseThrow().name().declared().toString();
        return value.value(slot(plan, type, name)).orElseThrow();
    }

    private static SymbolRef slot(TransferPlan plan, String type, String name) {
        return plan.objects().stream().filter(value -> value.name().declared().toString().equals(type)).findFirst()
                .orElseThrow().slots().stream().filter(value -> value.name().declared().last().equals(name))
                .findFirst().orElseThrow().symbol();
    }
}
