package guru.interlis.xtf23;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.transfer.TransferEvent.*;
import static guru.interlis.transfer.TransferIdentity.*;
import static guru.interlis.transfer.TransferValue.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

final class Xtf23WriterRoundTripTest {
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() throws IOException {
        plan = referencePlan();
    }

    @Test
    void roundTripsClassWithPredefinedBaseOutsideTheTransferPlan() {
        var transfer = compileInline("Checks", """
                INTERLIS 2.3;
                REFSYSTEM MODEL Checks AT "https://example.org" VERSION "1" =
                  TOPIC T =
                    CLASS Datum EXTENDS INTERLIS.REFSYSTEM = label : TEXT; END Datum;
                  END T;
                END Checks.
                """);
        assertTrue(transfer.objects().stream().noneMatch(type -> type.name().declared().last().equals("REFSYSTEM")));
        var xml = document("<MODELS><MODEL NAME=\"Checks\" VERSION=\"1\" URI=\"https://example.org\"/></MODELS>", """
                <Checks.T BID="b">
                  <Checks.T.Datum TID="datum"><label>example</label></Checks.T.Datum>
                </Checks.T>
                """);
        List<TransferEvent> events;
        try (var reader = new Xtf23Reader(transfer, input(xml))) {
            events = readAll(reader);
        }
        var datum = assertInstanceOf(ObjectEvent.class, events.get(2)).object();
        assertEquals(List.of(new Text("example")), List.copyOf(datum.data().values().values()));
        var bytes = new ByteArrayOutputStream();
        try (var writer = new Xtf23Writer(transfer, bytes)) {
            events.forEach(writer::write);
        }
        try (var reader = new Xtf23Reader(transfer, new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(events, readAll(reader));
        }
    }

    @Test
    void writesNeutralEventsInModelOrderAndEscapesXml() {
        var item = type("Item");
        var detail = type("Detail");
        var basket = basket("basket", IdOrigin.SOURCE);
        var fields = new LinkedHashMap<SymbolRef, TransferValue>();
        fields.put(slot(item, "details"), new Repeated(CollectionKind.LIST, List.of(
                new Structured(detail, Map.of(slot(detail, "description"), new Text("zéro <& β"))),
                new Structured(detail, Map.of(slot(detail, "description"), new Text("]]> 🚀"))))));
        fields.put(slot(item, "code"), new Text(""));
        var data = object(basket.id(), "obj", IdOrigin.SOURCE, item, fields);
        var start = start(plan, "sender \" & < >");
        start = new StartTransfer(start.source(), start.sender(), Optional.of("line\r\nnext\tλ"), start.models());
        var expected = List.of(start, basket, data, new EndBasket(basket.id()), new EndTransfer());

        byte[] bytes = write(expected, List.of());
        String xml = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(xml.indexOf("<code>") < xml.indexOf("<details>"));
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&lt;"));
        try (var reader = new Xtf23Reader(plan, new ByteArrayInputStream(bytes))) {
            var actual = readAll(reader);
            assertEquals(expected, actual);
            var decoded = assertInstanceOf(ObjectEvent.class, actual.get(2));
            assertEquals(List.of(slot(item, "code"), slot(item, "details")),
                    List.copyOf(decoded.object().data().values().keySet()));
        }
    }

    @Test
    void roundTripsOriginalObjectsAndAnAreaWithSignedArcAndPartitionedBoundary() throws IOException {
        String original = Files.readString(fixture("identity.xtf"));
        String surface = """
                <Reference23.Records.Parcel TID="parcel">
                  <shape><SURFACE><BOUNDARY>
                    <POLYLINE><COORD><C1>0.00</C1><C2>0.00</C2></COORD>
                      <ARC><C1>8.00</C1><C2>0.00</C2><A1>4.00</A1><A2>1.23400</A2><R>-5.234500</R></ARC>
                    </POLYLINE>
                    <POLYLINE><COORD><C1>8.00</C1><C2>0.00</C2></COORD><COORD><C1>0.00</C1><C2>0.00</C2></COORD></POLYLINE>
                  </BOUNDARY></SURFACE></shape>
                </Reference23.Records.Parcel>
                """;
        int end = original.lastIndexOf("</Reference23.Records>");
        var events = roundTrip(original.substring(0, end) + surface + original.substring(end));
        assertEquals(2, events.stream().filter(StartBasket.class::isInstance).count());
        assertEquals(4, events.stream().filter(ObjectEvent.class::isInstance).count());
        roundTrip(Files.readString(fixture("area-shared-edge.xtf")));
    }

    @Test
    void roundTripsStableIdsAndOriginalAndTargetBasketAttributes() {
        String stable = "u:123e4567-e89b-12d3-a456-426614174000";
        String xml = document(MODELS + "<OIDSPACES><OIDSPACE NAME=\"u\" OIDDOMAIN=\"INTERLIS.UUIDOID\"/></OIDSPACES>", """
                <Reference23.Records BID="origin"><Reference23.Records.Parent TID="%s"/></Reference23.Records>
                <Reference23.Records BID="current" CONSISTENCY="ADAPTED">
                  <Reference23.Records.Item TID="item" BID="origin" CONSISTENCY="INCOMPLETE">
                    <parent REF="%s" BID="origin"/>
                  </Reference23.Records.Item>
                </Reference23.Records>
                """.formatted(stable, stable));
        var events = roundTrip(xml);
        var item = (ObjectEvent) events.get(5);
        assertEquals("origin", item.object().identity().originalBasket().orElseThrow().value());
    }

    @Test
    void rejectsNeutralValuesWithoutAnEquivalentWireEncoding() {
        var item = type("Item");
        var basket = basket("basket", IdOrigin.SOURCE);
        for (var fields : List.<Map<SymbolRef, TransferValue>>of(
                Map.of(slot(item, "code"), Undefined.INSTANCE),
                Map.of(slot(item, "details"), new Repeated(CollectionKind.LIST, List.of())))) {
            try (var writer = new Xtf23Writer(plan, new ByteArrayOutputStream())) {
                writer.write(start(plan, "test"));
                writer.write(basket);
                var event = object(basket.id(), "item", IdOrigin.SOURCE, item, fields);
                assertEquals(UNSUPPORTED, assertThrows(Xtf23Exception.class, () -> writer.write(event)).reason());
            }
        }
    }

    @Test
    void checksBothBidTidOrdersEvenWithSyntheticIds() {
        var parent = type("Parent");
        for (boolean bidFirst : List.of(true, false)) {
            var first = basket("first", IdOrigin.SYNTHETIC);
            try (var writer = new Xtf23Writer(plan, new ByteArrayOutputStream())) {
                writer.write(start(plan, "test"));
                writer.write(first);
                var event = object(first.id(), bidFirst ? "first" : "second", IdOrigin.SOURCE, parent, Map.of());
                if (bidFirst) {
                    assertEquals(DUPLICATE_ID, assertThrows(Xtf23Exception.class, () -> writer.write(event)).reason());
                } else {
                    writer.write(event);
                    writer.write(new EndBasket(first.id()));
                    assertEquals(DUPLICATE_ID, assertThrows(Xtf23Exception.class,
                            () -> writer.write(basket("second", IdOrigin.SYNTHETIC))).reason());
                }
            }
        }
    }

    static StartTransfer start(TransferPlan plan, String sender) {
        var model = plan.models().getFirst();
        var symbol = (ModelSymbol) plan.symbol(model.symbol()).known().orElseThrow();
        return new StartTransfer(Optional.empty(), Optional.of(sender), Optional.empty(), List.of(
                new ModelDeclaration(model.symbol(), model.languageVersion(), Optional.of(model.modelVersion()),
                        Optional.of(symbol.issuer()))));
    }

    private static StartBasket basket(String id, IdOrigin origin) {
        return new StartBasket(new BasketId(id, origin), plan.topics().getFirst().symbol(), List.of(), BasketKind.FULL,
                Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static ObjectEvent object(BasketId basket, String tid, IdOrigin origin, SymbolRef type,
                                      Map<SymbolRef, TransferValue> fields) {
        return new ObjectEvent(new TransferObject(new guru.interlis.transfer.TransferIdentity(basket, Optional.empty(),
                Optional.of(new TransferId(tid, origin)), Optional.empty(), Optional.empty()),
                new Structured(type, fields)), Optional.empty(), Consistency.COMPLETE);
    }

    private static SymbolRef type(String name) {
        return plan.objects().stream().filter(candidate -> candidate.name().declared().last().equals(name))
                .findFirst().orElseThrow().symbol();
    }

    private static SymbolRef slot(SymbolRef type, String name) {
        return plan.object(type).orElseThrow().slots().stream()
                .filter(candidate -> candidate.name().declared().last().equals(name)).findFirst().orElseThrow().symbol();
    }

    private static List<TransferEvent> roundTrip(String original) {
        List<TransferEvent> before;
        List<Xtf23OidSpace> oidSpaces;
        try (var reader = new Xtf23Reader(plan, input(original))) {
            before = readAll(reader);
            oidSpaces = reader.oidSpaces();
        }
        byte[] result = write(before, oidSpaces);
        try (var reader = new Xtf23Reader(plan, new ByteArrayInputStream(result))) {
            assertEquals(before, readAll(reader));
            assertEquals(oidSpaces, reader.oidSpaces());
        }
        return before;
    }

    private static byte[] write(List<? extends TransferEvent> events, List<Xtf23OidSpace> oidSpaces) {
        var result = new ByteArrayOutputStream();
        try (var writer = new Xtf23Writer(plan, result, oidSpaces)) {
            events.forEach(writer::write);
        }
        return result.toByteArray();
    }
}
