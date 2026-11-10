package guru.interlis.xtf24;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24ValueTest {
    private static final String NS = "urn:values";
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = plan("""
                INTERLIS 2.4;
                MODEL Values AT "https://example.org" VERSION "1" =
                  XMLNS "urn:values";
                  DOMAIN TextId = OID TEXT*3; NumericId = OID 0..1000;
                  STRUCTURE Detail = label: TEXT; END Detail;
                  TOPIC Data =
                    CLASS Item =
                      required : MANDATORY TEXT;
                      title : TEXT*3;
                      note : MTEXT;
                      enabled : BOOLEAN;
                      choice : (root(a,b), other);
                      exact : 0.00..10.00;
                      day : INTERLIS.XMLDate;
                      stamp : INTERLIS.XMLDateTime;
                      oid : TextId;
                      numericOid : NumericId;
                      className : CLASS;
                      attributeName : ATTRIBUTE;
                      tags : BAG {0..1} OF TEXT;
                      order : LIST {0..2} OF 0..9;
                      detail : LIST {0..2} OF Detail;
                    END Item;
                  END Data;
                END Values.
                """, "Values");
    }

    @Test
    void retainsDecodableConstraintViolationsAndExactScalars() throws Exception {
        var events = read(plan, document("""
                <title>  too long  </title><note>first&#xD;second&#x9;</note>
                <enabled>false</enabled><choice>unknown.branch</choice><exact>+999.1234567890123456789e-1</exact>
                <day>2026-02-31</day><stamp>2026-11-10T12:30:00.123</stamp>
                <oid>space:Long_ID</oid><numericOid>ids:1001.20e-2</numericOid>
                <className>Unknown.T.Thing</className><attributeName>Unknown.T.Thing.flag</attributeName>
                """));
        var object = object(events);
        assertFalse(object.data().values().containsKey(slot("required")));
        assertEquals(new Text("  too long  "), field(object, "title"));
        assertEquals(new Text("first\rsecond\t"), field(object, "note"));
        assertEquals(new BooleanValue(false), field(object, "enabled"));
        assertEquals(new Enumeration(List.of("unknown", "branch")), field(object, "choice"));
        assertEquals(new NumberValue("+999.1234567890123456789e-1", new BigDecimal("+999.1234567890123456789e-1")),
                field(object, "exact"));
        assertEquals(new Text("2026-02-31"), field(object, "day"));
        assertEquals(new Text("2026-11-10T12:30:00.123"), field(object, "stamp"));
        assertEquals(new Oid("space:Long_ID"), field(object, "oid"));
        assertEquals(new Oid("ids:1001.20e-2"), field(object, "numericOid"));
        assertEquals(new ClassName(QualifiedName.of("Unknown", "T", "Thing")), field(object, "className"));
        assertEquals(new AttributePath(QualifiedName.of("Unknown", "T", "Thing", "flag")),
                field(object, "attributeName"));

        var written = item(write(plan, events));
        assertEquals(List.of("title", "note", "enabled", "choice", "exact", "day", "stamp", "oid",
                "numericOid", "className", "attributeName"), children(written).stream().map(Element::getLocalName).toList());
        assertEquals(NS, children(written).getFirst().getNamespaceURI());
        assertEquals("  too long  ", written.getElementsByTagNameNS(NS, "title").item(0).getTextContent());
        assertEquals("first\rsecond\t", written.getElementsByTagNameNS(NS, "note").item(0).getTextContent());
        assertEquals("+999.1234567890123456789e-1", written.getElementsByTagNameNS(NS, "exact").item(0).getTextContent());
        assertEquals("space:Long_ID", written.getElementsByTagNameNS(NS, "oid").item(0).getTextContent());
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void retainsRepeatsAndNestedValuesAndWritesOneTagPerItem() throws Exception {
        var events = read(plan, document("""
                <title/><enabled>true</enabled><choice>OTHERS</choice>
                <tags>b</tags><tags>b</tags><tags>a</tags>
                <order>2</order><order>1</order><order>2</order>
                <detail><Detail><label/></Detail></detail>
                <detail><Detail><label>after</label></Detail></detail>
                """));
        var object = object(events);
        assertEquals(new Text(""), field(object, "title"));
        assertEquals(new BooleanValue(true), field(object, "enabled"));
        assertEquals(new Enumeration(List.of("OTHERS")), field(object, "choice"));
        assertEquals(new Repeated(CollectionKind.BAG, List.of(new Text("b"), new Text("b"), new Text("a"))),
                field(object, "tags"));
        assertEquals(new Repeated(CollectionKind.LIST, List.of(
                new NumberValue("2", BigDecimal.valueOf(2)), new NumberValue("1", BigDecimal.ONE),
                new NumberValue("2", BigDecimal.valueOf(2)))), field(object, "order"));
        var details = (Repeated) field(object, "detail");
        var detail = plan.objects().stream().filter(type -> type.name().declared().last().equals("Detail"))
                .findFirst().orElseThrow();
        assertEquals(new Structured(detail.symbol(), java.util.Map.of(detail.slots().getFirst().symbol(), new Text(""))),
                details.values().getFirst());
        var written = item(write(plan, events));
        assertEquals(List.of("title", "enabled", "choice", "tags", "tags", "tags", "order", "order", "order", "detail", "detail"),
                children(written).stream().map(Element::getLocalName).toList());
        assertEquals(List.of("b", "b", "a"), children(written).stream().filter(field -> field.getLocalName().equals("tags"))
                .map(Element::getTextContent).toList());
        var nested = children(children(written).getLast()).getFirst();
        assertEquals("Detail", nested.getLocalName());
        assertEquals(NS, nested.getNamespaceURI());
        assertEquals("after", children(nested).getFirst().getTextContent());
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void rejectsWireGrammarAndAmbiguousMultiplicity() {
        for (String fields : List.of("<enabled>maybe</enabled>", "<exact>twelve</exact>", "<choice>root..a</choice>",
                "<title>x</title><title>y</title>", "<enabled>true</enabled><title>out of order</title>",
                "<detail><Detail/><Detail/></detail>", "<className>single</className>",
                "<oid>invalid/id</oid>", "<unexpected/>")) {
            assertEquals(Xtf24Exception.Reason.INVALID_WIRE,
                    assertThrows(Xtf24Exception.class, () -> read(plan, document(fields)), fields).reason());
        }
        assertEquals(Xtf24Exception.Reason.LIMIT_EXCEEDED,
                assertThrows(Xtf24Exception.class, () -> read(plan, document("<exact>1e999999</exact>"))).reason());
    }

    @Test
    void writerRejectsValuesWithoutDistinctWireRepresentations() {
        var events = read(plan, document(""));
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED, assertThrows(Xtf24Exception.class,
                () -> write(plan, withValue(events, "title", Undefined.INSTANCE))).reason());
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED, assertThrows(Xtf24Exception.class,
                () -> write(plan, withValue(events, "tags", new Repeated(CollectionKind.BAG, List.of())))).reason());
        assertEquals(Xtf24Exception.Reason.INVALID_WIRE, assertThrows(Xtf24Exception.class,
                () -> write(plan, withValue(events, "exact", new NumberValue("1", BigDecimal.TWO)))).reason());
    }

    private static List<TransferEvent> withValue(List<TransferEvent> events, String slot, TransferValue value) {
        var object = object(events);
        var fields = new LinkedHashMap<>(object.data().values());
        fields.put(slot(slot), value);
        var changed = new TransferObject(object.identity(), new Structured(object.type(), fields));
        var copy = new ArrayList<>(events);
        copy.set(2, new ObjectEvent(changed, Optional.empty(), TransferEvent.Consistency.COMPLETE));
        return copy;
    }

    private static ObjectEvent occurrence(List<TransferEvent> events) {
        return assertInstanceOf(ObjectEvent.class, events.get(2));
    }

    private static TransferObject object(List<TransferEvent> events) {
        return occurrence(events).object();
    }

    private static guru.interlis.model.SymbolRef slot(String name) {
        return plan.objects().stream().filter(type -> type.name().declared().last().equals("Item")).findFirst().orElseThrow()
                .slots().stream().filter(slot -> slot.name().declared().last().equals(name)).findFirst().orElseThrow().symbol();
    }

    private static TransferValue field(TransferObject object, String name) {
        return object.value(slot(name)).orElseThrow();
    }

    private static String document(String attributes) {
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:values">
                  <i:headersection><i:models><i:model>Values</i:model></i:models></i:headersection>
                  <i:datasection><Data i:bid="b"><Item i:tid="1">%s</Item></Data></i:datasection>
                </i:transfer>
                """.formatted(attributes);
    }

    private static Element item(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var parsed = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return (Element) parsed.getElementsByTagNameNS(NS, "Item").item(0);
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
