package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.*;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferProtocolException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24EnvelopeTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String SOURCE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.org" VERSION "1" =
              TOPIC T = CLASS Item = END Item; END T;
            END Base.
            MODEL Main AT "https://example.org" VERSION "1" =
              IMPORTS Base;
              TOPIC U EXTENDS Base.T = END U;
            END Main.
            MODEL Other AT "https://example.org" VERSION "1" =
              TOPIC O = END O;
            END Other.
            """;

    @Test
    void roundTripsEmptyTransferWithPresentEmptyComment() {
        var plan = plan(SOURCE, "Main");
        var xml = document("<i:comment/><i:extensions/>", "");
        var events = read(plan, xml);
        var start = assertInstanceOf(StartTransfer.class, events.getFirst());
        assertEquals(Optional.empty(), start.sender());
        assertEquals(Optional.of(""), start.comment());
        assertEquals(1, start.models().size());
        assertEquals(Optional.empty(), start.models().getFirst().modelVersion());
        assertEquals(Optional.empty(), start.models().getFirst().issuer());
        assertEquals(List.of(start, new EndTransfer()), events);
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void coversImportedObjectModelWithoutAddingHeaderEntries() {
        var plan = plan(SOURCE, "Main", "Other");
        var xml = document("<i:sender>author</i:sender>", """
                <m:U i:bid="1" i:consistency="INCOMPLETE">
                  <b:Item i:tid="ns:23"/>
                </m:U>
                <b:T i:bid="2"/>
                """);
        var events = read(plan, xml);
        var start = assertInstanceOf(StartTransfer.class, events.getFirst());
        assertEquals(1, start.models().size());
        assertEquals("Main", plan.model(start.models().getFirst().model()).orElseThrow().name().declared().last());
        var basket = assertInstanceOf(StartBasket.class, events.get(1));
        var object = assertInstanceOf(ObjectEvent.class, events.get(2)).object();
        assertEquals(Consistency.INCOMPLETE, basket.consistency());
        assertEquals("Base", plan.object(object.type()).orElseThrow().name().declared().segments().getFirst());
        assertEquals("ns:23", object.identity().tid().orElseThrow().value());
        assertEquals(events, read(plan, write(plan, events)));
        assertEquals(Xtf24Exception.Reason.INVALID_WIRE, assertThrows(Xtf24Exception.class,
                () -> read(plan, document("", "<o:O i:bid=\"unused\"/>"))).reason());
    }

    @Test
    void rejectsCollisionsInBothBasketAndObjectDirectionsOnReadAndWrite() {
        var plan = plan(SOURCE, "Main");
        var previousBasket = "<m:U i:bid=\"clash\"><b:Item i:tid=\"clash\"/></m:U>";
        var previousObject = """
                <m:U i:bid="first"><b:Item i:tid="clash"/></m:U>
                <m:U i:bid="clash"/>
                """;
        for (String data : List.of(previousBasket, previousObject)) {
            assertEquals(Xtf24Exception.Reason.DUPLICATE_ID,
                    assertThrows(Xtf24Exception.class, () -> read(plan, document("", data))).reason());
        }
        var events = read(plan, document("", """
                <m:U i:bid="first"><b:Item i:tid="clash"/></m:U>
                <m:U i:bid="other"/>
                """));
        var first = assertInstanceOf(StartBasket.class, events.get(1));
        var object = assertInstanceOf(ObjectEvent.class, events.get(2));
        var identity = object.object().identity();
        var tid = new guru.interlis.transfer.TransferIdentity.TransferId(
                first.id().value(), identity.tid().orElseThrow().origin());
        var changed = new guru.interlis.transfer.TransferIdentity(identity.currentBasket(), Optional.empty(),
                Optional.of(tid), Optional.empty(), Optional.empty());
        var occurrence = new ObjectEvent(new guru.interlis.transfer.TransferObject(changed, object.object().data()),
                Optional.empty(), Consistency.COMPLETE);
        try (var writer = new Xtf24Writer(plan, new ByteArrayOutputStream())) {
            writer.write(events.getFirst());
            writer.write(first);
            assertEquals(Xtf24Exception.Reason.DUPLICATE_ID,
                    assertThrows(Xtf24Exception.class, () -> writer.write(occurrence)).reason());
        }
        var second = assertInstanceOf(StartBasket.class, events.get(4));
        var collidingId = new guru.interlis.transfer.TransferIdentity.BasketId("clash", second.id().origin());
        var colliding = new StartBasket(collidingId, second.topic(), List.of(), BasketKind.FULL, Consistency.COMPLETE,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        try (var writer = new Xtf24Writer(plan, new ByteArrayOutputStream())) {
            events.subList(0, 4).forEach(writer::write);
            assertEquals(Xtf24Exception.Reason.DUPLICATE_ID,
                    assertThrows(Xtf24Exception.class, () -> writer.write(colliding)).reason());
        }
    }

    @Test
    void rejectsUnsupportedBasketsAndIllegalIdentifiers() {
        var plan = plan(SOURCE, "Main");
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED, assertThrows(Xtf24Exception.class,
                () -> read(plan, document("", "<m:U i:bid=\"one\" i:kind=\"UPDATE\"/>"))).reason());
        for (String bid : List.of("-dash", "a/b", "space id", "one:two:three", "")) {
            var xml = document("", "<m:U i:bid=\"" + bid + "\"/>");
            assertEquals(Xtf24Exception.Reason.INVALID_WIRE,
                    assertThrows(Xtf24Exception.class, () -> read(plan, xml)).reason());
        }
    }

    @Test
    void checksTrailingXmlBeforeEndAndKeepsCallerStreamsOpen() {
        var plan = plan(SOURCE, "Main");
        var valid = document("", "");
        class Input extends ByteArrayInputStream {
            boolean closed;
            Input() { super((valid + "<extra/>").getBytes(StandardCharsets.UTF_8)); }
            @Override public void close() { closed = true; }
            int consumed() { return pos; }
        }
        var input = new Input();
        try (var reader = new Xtf24Reader(plan, input)) {
            assertEquals(0, input.consumed());
            assertThrows(OperationCancelledException.class, () -> reader.readNext(() -> true));
            assertEquals(0, input.consumed());
            assertInstanceOf(StartTransfer.class, reader.readNext(TOKEN).orElseThrow());
            assertEquals(Xtf24Exception.Reason.MALFORMED_XML,
                    assertThrows(Xtf24Exception.class, () -> reader.readNext(TOKEN)).reason());
            assertEquals(TransferProtocolException.Reason.FAILED,
                    assertThrows(TransferProtocolException.class, () -> reader.readNext(TOKEN)).reason());
        }
        assertFalse(input.closed);

        class Output extends ByteArrayOutputStream {
            boolean closed;
            @Override public void close() { closed = true; }
        }
        var output = new Output();
        try (var writer = new Xtf24Writer(plan, output)) {
            assertEquals(0, output.size());
            read(plan, valid).forEach(writer::write);
        }
        assertFalse(output.closed);
        assertEquals(read(plan, valid), read(plan, output.toString(StandardCharsets.UTF_8)));
    }

    private static String document(String header, String data) {
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                  xmlns:m="http://www.interlis.ch/xtf/2.4/Main" xmlns:b="http://www.interlis.ch/xtf/2.4/Base"
                  xmlns:o="http://www.interlis.ch/xtf/2.4/Other">
                  <i:headersection><i:models><i:model>Main</i:model></i:models>%s</i:headersection>
                  <i:datasection>%s</i:datasection>
                </i:transfer>
                """.formatted(header, data);
    }

    static List<TransferEvent> read(TransferPlan plan, String xml) {
        var events = new ArrayList<TransferEvent>();
        try (var reader = new Xtf24Reader(plan, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            for (var event = reader.readNext(TOKEN); event.isPresent(); event = reader.readNext(TOKEN)) {
                events.add(event.orElseThrow());
            }
        }
        return List.copyOf(events);
    }

    static String write(TransferPlan plan, List<TransferEvent> events) {
        var output = new ByteArrayOutputStream();
        try (var writer = new Xtf24Writer(plan, output)) {
            events.forEach(writer::write);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
