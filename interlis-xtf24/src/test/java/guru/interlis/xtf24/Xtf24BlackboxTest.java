package guru.interlis.xtf24;

import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Binary;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.XmlFragment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24BlackboxTest {
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = plan("""
                INTERLIS 2.4;
                MODEL Files AT "https://example.org" VERSION "1" =
                  XMLNS "urn:files";
                  TOPIC T = CLASS C = xml: BLACKBOX XML; bin: BLACKBOX BINARY; END C; END T;
                END Files.
                """, "Files");
    }

    @Test
    void preservesNamespaceContextsShadowingMixedTextAndAttributeWhitespace() throws Exception {
        var events = read(plan, transfer("""
                <xml xmlns:marker="urn:marker">before<!-- <fake a="quoted"> --><?t quoted="yes" &amp;?><p:one a="line&#xA;tab&#x9;cr&#xD;&amp;&quot;">
                  <p:two xmlns:p="urn:inner">p:Literal</p:two>
                </p:one><two xmlns="urn:default">marker:Thing</two>after</xml>
                <bin>AA EC A/8=</bin>
                """));
        var fragment = (XmlFragment) field(events, "xml");
        assertEquals("urn:outer", fragment.namespaceBindings().get("p"));
        assertEquals("urn:marker", fragment.namespaceBindings().get("marker"));
        assertArrayEquals(new byte[]{0, 1, 2, 3, (byte) 255}, ((Binary) field(events, "bin")).bytes());
        var written = xmlValue(write(plan, events));
        var one = (Element) written.getElementsByTagNameNS("urn:outer", "one").item(0);
        assertEquals("line\ntab\tcr\r&\"", one.getAttribute("a"));
        var two = (Element) one.getElementsByTagNameNS("urn:inner", "two").item(0);
        assertEquals("p:Literal", two.getTextContent());
        assertEquals("urn:inner", two.lookupNamespaceURI("p"));
        var defaultNode = (Element) written.getElementsByTagNameNS("urn:default", "two").item(0);
        assertEquals("urn:marker", defaultNode.lookupNamespaceURI("marker"));
        assertEquals("marker:Thing", defaultNode.getTextContent());
        assertTrue(written.getTextContent().startsWith("before"));
        var comment = written.getFirstChild().getNextSibling();
        assertEquals(org.w3c.dom.Node.COMMENT_NODE, comment.getNodeType());
        assertEquals(" <fake a=\"quoted\"> ", comment.getNodeValue());
        var instruction = comment.getNextSibling();
        assertEquals(org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE, instruction.getNodeType());
        assertEquals("quoted=\"yes\" &amp;", instruction.getNodeValue());
        assertTrue(written.getTextContent().endsWith("after"));
        var reread = (XmlFragment) field(read(plan, write(plan, events)), "xml");
        fragment.namespaceBindings().forEach((prefix, uri) -> assertEquals(uri, reread.namespaceBindings().get(prefix)));
        assertArrayEquals(((Binary) field(events, "bin")).bytes(),
                ((Binary) field(read(plan, write(plan, events)), "bin")).bytes());
    }

    @Test
    void preservesQNameTextWithoutAChildElementAndEmptyValues() throws Exception {
        var events = read(plan, transfer("<xml>p:Literal</xml><bin/>"));
        assertEquals("p:Literal", ((XmlFragment) field(events, "xml")).xml());
        var written = xmlValue(write(plan, events));
        assertEquals("p:Literal", written.getTextContent());
        assertEquals("urn:outer", written.lookupNamespaceURI("p"));
        assertArrayEquals(new byte[0], ((Binary) field(events, "bin")).bytes());
        var empty = read(plan, transfer("<xml/><bin/>"));
        assertEquals("", ((XmlFragment) field(empty, "xml")).xml());
        assertEquals("", xmlValue(write(plan, empty)).getTextContent());
    }

    @Test
    void preservesAnAbsentDefaultNamespaceInFullyPrefixedTransfers() throws Exception {
        var events = read(plan, """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns:f="urn:files" xmlns:p="urn:p">
                  <i:headersection><i:models><i:model>Files</i:model></i:models></i:headersection>
                  <i:datasection><f:T i:bid="b"><f:C i:tid="one"><f:xml>p:Name</f:xml></f:C></f:T></i:datasection>
                </i:transfer>
                """);
        var fragment = (XmlFragment) field(events, "xml");
        assertEquals("", fragment.namespaceBindings().get(""));
        var xml = xmlValue(write(plan, events));
        assertEquals("p:Name", xml.getTextContent());
        assertEquals("urn:p", xml.lookupNamespaceURI("p"));
        assertNull(xml.lookupNamespaceURI(null));
    }

    @Test
    void rejectsFragmentBreakoutAndMalformedBase64() {
        var events = read(plan, transfer("<xml/>"));
        for (String xml : List.of("</fragment><outside/>", "<broken>", "<!DOCTYPE x><x/>")) {
            var changed = withValue(events, "xml", new XmlFragment(xml));
            assertThrows(Xtf24Exception.class, () -> write(plan, changed));
        }
        for (String base64 : List.of("AA$=", "A", "AB==", "AAAA=", "AA==junk")) {
            assertEquals(Xtf24Exception.Reason.INVALID_WIRE,
                    assertThrows(Xtf24Exception.class, () -> read(plan, transfer("<bin>" + base64 + "</bin>"))).reason());
        }
    }

    @Test
    void copiesNamespaceBindingsAndKeepsTheExistingConstructor() {
        var bindings = new LinkedHashMap<String, String>();
        bindings.put("p", "urn:p");
        var fragment = new XmlFragment("p:Value", bindings);
        bindings.clear();
        assertEquals(Map.of("p", "urn:p"), fragment.namespaceBindings());
        assertThrows(UnsupportedOperationException.class, () -> fragment.namespaceBindings().clear());
        assertEquals(Map.of(), new XmlFragment("<x/>").namespaceBindings());
    }

    private static TransferValue field(List<TransferEvent> events, String name) {
        var object = ((ObjectEvent) events.get(2)).object();
        var slot = plan.object(object.type()).orElseThrow().slots().stream()
                .filter(candidate -> candidate.name().declared().last().equals(name)).findFirst().orElseThrow();
        return object.value(slot.symbol()).orElseThrow();
    }

    private static List<TransferEvent> withValue(List<TransferEvent> events, String name, TransferValue value) {
        var object = ((ObjectEvent) events.get(2)).object();
        var values = new LinkedHashMap<>(object.data().values());
        var slot = plan.object(object.type()).orElseThrow().slots().stream()
                .filter(candidate -> candidate.name().declared().last().equals(name)).findFirst().orElseThrow();
        values.put(slot.symbol(), value);
        var copy = new ArrayList<>(events);
        copy.set(2, new ObjectEvent(new TransferObject(object.identity(), new Structured(object.type(), values)),
                Optional.empty(), TransferEvent.Consistency.COMPLETE));
        return copy;
    }

    private static String transfer(String values) {
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:files" xmlns:p="urn:outer">
                  <i:headersection><i:models><i:model>Files</i:model></i:models></i:headersection>
                  <i:datasection><T i:bid="b"><C i:tid="one">%s</C></T></i:datasection>
                </i:transfer>
                """.formatted(values);
    }

    private static Element xmlValue(String xml) throws Exception {
        var document = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return (Element) document.getElementsByTagNameNS("urn:files", "xml").item(0);
    }
}
