package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.MultiCoordinate;
import guru.interlis.transfer.TransferGeometry.MultiPolyline;
import guru.interlis.transfer.TransferGeometry.MultiSurface;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24LibraryConsumerTest {
    private record Result(List<TransferEvent> events, byte[] output) {}

    private static TransferPlan reference;
    private static TransferPlan generic;

    @BeforeAll
    static void compileHandwrittenModelsThroughThePublicCompilerAndPlanner() throws Exception {
        reference = plan(Files.readString(fixture("Reference24.ili")), "Reference24");
        generic = plan(Files.readString(fixture("Generic24.ili")), "Generic24");
    }

    @Test
    void consumesHandwrittenScalarStructureAndRelationshipFixtures() throws Exception {
        var simple = copy(reference, "simple");
        var simpleObjects = objects(simple);
        assertEquals(2, simpleObjects.size());
        assertEquals(new Text("Parent"), field(reference, simpleObjects.getFirst(), "title"));
        assertEquals(new Text("yes"), field(reference, simpleObjects.getLast(), "required"));
        assertEquals(new NumberValue("3", BigDecimal.valueOf(3)), field(reference, simpleObjects.getLast(), "count"));

        var structured = copy(reference, "structures-and-link");
        var item = objects(structured).getLast();
        var values = (Repeated) field(reference, item, "details");
        assertEquals(2, values.values().size());
        var labels = values.values().stream().map(Structured.class::cast)
                .map(value -> value.values().values().iterator().next()).toList();
        assertEquals(List.of(new Text("second"), new Text("first")), labels);
        var parentRole = slot(reference, "Link", "parent");
        var payload = assertInstanceOf(Structured.class, item.value(parentRole).orElseThrow());
        assertEquals("p1", ((Reference) payload.value(parentRole).orElseThrow()).target().value());
        assertEquals(new Text("linked"), payload.value(slot(reference, "Link", "note")).orElseThrow());

        var document = parsed(structured.output());
        var wireItem = (Element) document.getElementsByTagNameNS("urn:reference24", "Item").item(0);
        assertEquals(List.of("required", "details", "details", "count", "parent"),
                children(wireItem).stream().map(Element::getLocalName).toList());
        var wireLink = (Element) document.getElementsByTagNameNS("urn:reference24", "parent").item(0);
        assertEquals("p1", wireLink.getAttributeNS("http://www.interlis.ch/xtf/2.4/INTERLIS", "ref"));
        assertEquals("Link", children(wireLink).getFirst().getLocalName());
    }

    @Test
    void consumesNativeMultiGeometryAndBasketDomainFixtures() throws Exception {
        var geometry = copy(reference, "geometry");
        var item = objects(geometry).getFirst();
        assertEquals(2, ((MultiCoordinate) field(reference, item, "points")).coordinates().size());
        assertEquals(2, ((MultiPolyline) field(reference, item, "traces")).lines().size());
        assertEquals(2, ((MultiSurface) field(reference, item, "shapes")).surfaces().size());
        var arc = (Arc) ((Polyline) field(reference, item, "trace")).parts().getFirst().segments().getFirst();
        assertEquals("1.00", arc.radius().orElseThrow().sourceValue().lexeme());
        assertEquals(List.of("1.00", "1.00"), arc.support().ordinates().stream().map(NumberValue::lexeme).toList());
        var document = parsed(geometry.output());
        var multi = (Element) document.getElementsByTagNameNS("http://www.interlis.ch/geometry/1.0", "multipolyline").item(0);
        assertEquals(List.of("polyline", "polyline"), children(multi).stream().map(Element::getLocalName).toList());
        var surfaces = (Element) document.getElementsByTagNameNS("http://www.interlis.ch/geometry/1.0", "multisurface").item(0);
        assertEquals(2, children(surfaces).size());

        var domains = copy(generic, "generic-domains");
        var baskets = domains.events().stream().filter(StartBasket.class::isInstance).map(StartBasket.class::cast).toList();
        assertEquals(List.of("A", "B"), baskets.stream().map(basket -> generic.symbol(basket.domainBindings().getFirst().concrete())
                .known().orElseThrow().name().value()).toList());
        var points = objects(domains).stream().map(object -> (Coordinate) field(generic, object, "point")).toList();
        assertEquals(List.of("10.00", "250.00"), points.stream().map(point -> point.ordinates().getFirst().lexeme()).toList());
        var wireBaskets = parsed(domains.output()).getElementsByTagNameNS("http://www.interlis.ch/xtf/2.4/Generic24", "Data");
        assertEquals("Generic24.G=Generic24.A", ((Element) wireBaskets.item(0)).getAttributeNS("http://www.interlis.ch/xtf/2.4/INTERLIS", "domains"));
        assertEquals("Generic24.G=Generic24.B", ((Element) wireBaskets.item(1)).getAttributeNS("http://www.interlis.ch/xtf/2.4/INTERLIS", "domains"));
    }

    @Test
    void transportsTheSeparatelyValidatorRejectedFixture() throws Exception {
        var invalid = copy(reference, "invalid-data");
        var item = objects(invalid).getFirst();
        assertTrue(item.value(slot(reference, "Item", "required")).isEmpty());
        assertEquals(new NumberValue("999", BigDecimal.valueOf(999)), field(reference, item, "count"));
        var values = (Repeated) field(reference, item, "details");
        assertEquals(3, values.values().size());
        assertEquals(values.values().get(0), values.values().get(1));
        var document = parsed(invalid.output());
        assertEquals(0, document.getElementsByTagNameNS("urn:reference24", "required").getLength());
        assertEquals(3, document.getElementsByTagNameNS("urn:reference24", "details").getLength());
        assertEquals("999", document.getElementsByTagNameNS("urn:reference24", "count").item(0).getTextContent());
    }

    private static Result copy(TransferPlan plan, String name) throws Exception {
        var input = new ByteArrayInputStream(Files.readAllBytes(fixture(name + ".xtf"))) {
            boolean closed;
            @Override public void close() { closed = true; }
        };
        var output = new ByteArrayOutputStream() {
            boolean closed;
            @Override public void close() { closed = true; }
        };
        var events = new ArrayList<TransferEvent>();
        boolean end = false;
        try (var reader = new Xtf24Reader(plan, input); var writer = new Xtf24Writer(plan, output)) {
            for (var event = reader.readNext(CancellationToken.none()); event.isPresent();
                 event = reader.readNext(CancellationToken.none())) {
                var value = event.orElseThrow();
                events.add(value);
                writer.write(value);
                end = value instanceof TransferEvent.EndTransfer;
            }
            assertTrue(end);
            assertTrue(reader.readNext(CancellationToken.none()).isEmpty());
        }
        assertFalse(input.closed);
        assertFalse(output.closed);
        var immutable = List.copyOf(events);
        assertEquals(immutable, read(plan, output.toString(StandardCharsets.UTF_8)));
        Path generated = Path.of(System.getProperty("repo.root"), "target", "jenesis-jvm", "xtf24-fixture-output");
        Files.createDirectories(generated);
        Files.write(generated.resolve(name + ".xtf"), output.toByteArray());
        return new Result(immutable, output.toByteArray());
    }

    private static List<TransferObject> objects(Result result) {
        return result.events().stream().filter(ObjectEvent.class::isInstance).map(ObjectEvent.class::cast)
                .map(ObjectEvent::object).toList();
    }

    private static SymbolRef slot(TransferPlan plan, String owner, String name) {
        return plan.objects().stream().filter(object -> object.name().declared().last().equals(owner)).findFirst().orElseThrow()
                .slots().stream().filter(slot -> slot.name().declared().last().equals(name)).findFirst().orElseThrow().symbol();
    }

    private static TransferValue field(TransferPlan plan, TransferObject object, String name) {
        var slot = plan.object(object.type()).orElseThrow().slots().stream()
                .filter(candidate -> candidate.name().declared().last().equals(name)).findFirst().orElseThrow();
        return object.value(slot.symbol()).orElseThrow();
    }

    private static Path fixture(String name) {
        return Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24", name);
    }

    private static org.w3c.dom.Document parsed(byte[] xml) throws Exception {
        return DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static List<Element> children(Element element) {
        var result = new ArrayList<Element>();
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element value) result.add(value);
        }
        return result;
    }
}
