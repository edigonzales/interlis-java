package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24AssociationTest {
    private static final String NS = "http://www.interlis.ch/xtf/2.4/Relations";
    private static final String ILI = Xtf24Wire.NAMESPACE;
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = plan("""
                INTERLIS 2.4;
                MODEL Relations AT "https://example.org" VERSION "1" =
                  TOPIC Data =
                    CLASS Parent = END Parent;
                    STRUCTURE Detail = owner : REFERENCE TO (EXTERNAL) Parent; END Detail;
                    CLASS Item = title : TEXT; details : Detail; END Item;
                    ASSOCIATION Note =
                      parent (EXTERNAL) -- {0..1} Parent;
                      item -- {0..*} Item;
                      remark : TEXT;
                    END Note;
                    ASSOCIATION Peer =
                      left (ORDERED, EXTERNAL) -- {0..*} Parent;
                      right -- {0..*} Item;
                      remark : TEXT;
                    END Peer;
                    ASSOCIATION Triple =
                      one -- {0..1} Parent;
                      two -- {0..1} Item;
                      three -- {0..1} Item;
                      label : TEXT;
                    END Triple;
                    ASSOCIATION Stable (OID) =
                      first -- {0..1} Parent;
                      second -- {0..*} Item;
                    END Stable;
                  END Data;
                END Relations.
                """, "Relations");
    }

    @Test
    void preservesEmbeddedPayloadStandaloneAndIdentifiedAssociations() throws Exception {
        String position = "1234567890123456789012345678901234567890";
        var events = read(plan, transfer("""
                <Item ili:tid="item">
                  <title>title</title><details><Detail><owner ili:ref="outside"/></Detail></details>
                  <parent ili:ref="forward"><Note><remark>payload</remark></Note></parent>
                </Item>
                <Peer><left ili:ref="missing" ili:order_pos="%s"/><right ili:ref="item"/><remark>link</remark></Peer>
                <Triple><one ili:ref="forward"/><two ili:ref="item"/><three ili:ref="absent"/><label>three</label></Triple>
                <Stable ili:tid="stable"><first ili:ref="forward"/><second ili:ref="item"/></Stable>
                <Parent ili:tid="forward"/>
                """.formatted(position)));
        var objects = events.stream().filter(ObjectEvent.class::isInstance).map(ObjectEvent.class::cast)
                .map(ObjectEvent::object).toList();
        var item = objects.getFirst();
        var parent = slot("Note", "parent");
        var payload = assertInstanceOf(Structured.class, item.value(parent).orElseThrow());
        assertEquals(type("Note"), payload.type());
        assertEquals(new Text("payload"), payload.value(slot("Note", "remark")).orElseThrow());
        assertEquals(new Reference(new TransferIdentity.TransferId("forward", TransferIdentity.IdOrigin.SOURCE),
                Optional.empty(), true, Optional.empty()), payload.value(parent).orElseThrow());
        var nested = (Structured) item.value(slot("Item", "details")).orElseThrow();
        assertEquals(new Reference(new TransferIdentity.TransferId("outside", TransferIdentity.IdOrigin.SOURCE),
                Optional.empty(), true, Optional.empty()), nested.value(slot("Detail", "owner")).orElseThrow());
        var peer = objects.get(1);
        assertTrue(peer.identity().tid().isEmpty());
        var left = assertInstanceOf(Reference.class, peer.value(slot("Peer", "left")).orElseThrow());
        assertEquals(new BigInteger(position), left.orderPosition().orElseThrow());
        assertEquals("missing", left.target().value());
        assertTrue(left.external());
        var triple = objects.get(2);
        assertEquals(4, triple.data().values().size());
        assertEquals("absent", ((Reference) triple.value(slot("Triple", "three")).orElseThrow()).target().value());
        var stable = objects.get(3);
        assertEquals("stable", stable.identity().tid().orElseThrow().value());
        assertEquals("stable", stable.identity().oid().orElseThrow().value());

        var wire = write(plan, events);
        var parsed = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)));
        var itemXml = (Element) parsed.getElementsByTagNameNS(NS, "Item").item(0);
        assertEquals(List.of("title", "details", "parent"), children(itemXml).stream().map(Element::getLocalName).toList());
        var parentXml = children(itemXml).getLast();
        assertEquals("forward", parentXml.getAttributeNS(ILI, "ref"));
        assertEquals("Note", children(parentXml).getFirst().getLocalName());
        assertEquals("payload", children(children(parentXml).getFirst()).getFirst().getTextContent());
        var peerXml = (Element) parsed.getElementsByTagNameNS(NS, "Peer").item(0);
        assertFalse(peerXml.hasAttributeNS(ILI, "tid"));
        assertEquals(List.of("left", "right", "remark"), children(peerXml).stream().map(Element::getLocalName).toList());
        assertEquals(position, children(peerXml).getFirst().getAttributeNS(ILI, "order_pos"));
        assertEquals(events, read(plan, wire));

        var reordered = new LinkedHashMap<SymbolRef, TransferValue>();
        peer.data().values().entrySet().stream().toList().reversed()
                .forEach(entry -> reordered.put(entry.getKey(), entry.getValue()));
        var copy = new ArrayList<>(events);
        copy.set(3, new ObjectEvent(new TransferObject(peer.identity(), new Structured(peer.type(), reordered)),
                Optional.empty(), TransferEvent.Consistency.COMPLETE));
        assertEquals(wire, write(plan, copy));
    }

    @Test
    void ordersEmbeddedFieldsAlphabeticallyWithinTheirInheritanceLevel() {
        var fixture = plan("""
                INTERLIS 2.4;
                MODEL Relations AT "https://example.org" VERSION "1" =
                  TOPIC Data =
                    CLASS Parent = END Parent;
                    CLASS Item = title : TEXT; END Item;
                    ASSOCIATION Last =
                      zParent -- {0..1} Parent; zItem -- {0..*} Item;
                    END Last;
                    ASSOCIATION First =
                      aParent -- {0..1} Parent; aItem -- {0..*} Item;
                    END First;
                  END Data;
                END Relations.
                """, "Relations");
        var item = fixture.objects().stream().filter(type -> type.name().declared().last().equals("Item"))
                .findFirst().orElseThrow();
        var topic = fixture.topic(item.topic().orElseThrow()).orElseThrow();
        var layout = new Xtf24Layout(fixture);
        assertEquals(List.of("title", "aParent", "zParent"),
                layout.fields(item, java.util.Set.copyOf(topic.objectTypes()), false).stream()
                        .map(field -> field.name().getLocalPart()).toList());
        var events = read(fixture, transfer("<Item ili:tid=\"item\"><title>x</title><aParent ili:ref=\"a\"/><zParent ili:ref=\"z\"/></Item>"));
        assertEquals(events, read(fixture, write(fixture, events)));
        assertEquals(Xtf24Exception.Reason.INVALID_WIRE, assertThrows(Xtf24Exception.class,
                () -> read(fixture, transfer("<Item ili:tid=\"item\"><zParent ili:ref=\"z\"/><aParent ili:ref=\"a\"/></Item>"))).reason());
    }

    @Test
    void readsPlainEmbeddedReferencesAndRejectsUnrepresentableTargetBasket() {
        var events = read(plan, transfer("<Item ili:tid=\"item\"><parent ili:ref=\"missing\"/></Item>"));
        var object = ((ObjectEvent) events.get(2)).object();
        var reference = assertInstanceOf(Reference.class, object.value(slot("Note", "parent")).orElseThrow());
        assertEquals("missing", reference.target().value());
        assertEquals(events, read(plan, write(plan, events)));
        var changed = new Reference(reference.target(),
                Optional.of(new TransferIdentity.BasketId("other", TransferIdentity.IdOrigin.SOURCE)),
                reference.external(), reference.orderPosition());
        var data = new Structured(object.type(), java.util.Map.of(slot("Note", "parent"), changed));
        var copy = new ArrayList<>(events);
        copy.set(2, new ObjectEvent(new TransferObject(object.identity(), data), Optional.empty(),
                TransferEvent.Consistency.COMPLETE));
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                assertThrows(Xtf24Exception.class, () -> write(plan, copy)).reason());
    }

    @Test
    void rejectsRepeatedEmbeddedLinksBeforePublishingTheObject() {
        var xml = transfer("""
                <Item ili:tid="item">
                  <parent ili:ref="first"><Note><remark>one</remark></Note></parent>
                  <parent ili:ref="second"><Note><remark>two</remark></Note></parent>
                </Item>
                """);
        try (var reader = new Xtf24Reader(plan,
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            var cancellation = CancellationToken.none();
            assertInstanceOf(TransferEvent.StartTransfer.class, reader.readNext(cancellation).orElseThrow());
            assertInstanceOf(TransferEvent.StartBasket.class, reader.readNext(cancellation).orElseThrow());
            assertEquals(Xtf24Exception.Reason.INVALID_WIRE,
                    assertThrows(Xtf24Exception.class, () -> reader.readNext(cancellation)).reason());
            assertThrows(guru.interlis.transfer.TransferProtocolException.class,
                    () -> reader.readNext(cancellation));
        }
    }

    @Test
    void rejectsStandaloneEmbeddedTypesAndInvalidOrderPositions() {
        for (String data : List.of("<Note><parent ili:ref=\"one\"/><item ili:ref=\"two\"/></Note>",
                "<Peer><left ili:ref=\"one\" ili:order_pos=\"0\"/></Peer>",
                "<Peer><right ili:ref=\"one\" ili:order_pos=\"1\"/></Peer>")) {
            assertEquals(Xtf24Exception.Reason.INVALID_WIRE,
                    assertThrows(Xtf24Exception.class, () -> read(plan, transfer(data))).reason());
        }
    }

    private static SymbolRef type(String name) {
        return plan.objects().stream().filter(object -> object.name().declared().last().equals(name))
                .findFirst().orElseThrow().symbol();
    }

    private static SymbolRef slot(String owner, String name) {
        return plan.object(type(owner)).orElseThrow().slots().stream()
                .filter(slot -> slot.name().declared().last().equals(name)).findFirst().orElseThrow().symbol();
    }

    private static String transfer(String objects) {
        return """
                <ili:transfer xmlns:ili="http://www.interlis.ch/xtf/2.4/INTERLIS"
                    xmlns="http://www.interlis.ch/xtf/2.4/Relations">
                  <ili:headersection><ili:models><ili:model>Relations</ili:model></ili:models></ili:headersection>
                  <ili:datasection><Data ili:bid="b">%s</Data></ili:datasection>
                </ili:transfer>
                """.formatted(objects);
    }

    private static List<Element> children(Element element) {
        var children = new ArrayList<Element>();
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element field) {
                children.add(field);
            }
        }
        return children;
    }
}
