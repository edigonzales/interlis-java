package guru.interlis.xtf23;

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
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class Xtf23BlackboxTest {
    private static final String MODELS = """
            <MODELS><MODEL NAME="Boxes" VERSION="1" URI="https://example.invalid/boxes"/></MODELS>
            """;
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = compileInline("Boxes", """
                INTERLIS 2.3;
                MODEL Boxes (en) AT "https://example.invalid/boxes" VERSION "1" =
                  TOPIC Data =
                    CLASS Item =
                      document : BLACKBOX XML;
                      payload : BLACKBOX BINARY;
                    END Item;
                  END Data;
                END Boxes.
                """);
    }

    @Test
    void keepsMixedContentAndInScopeNamespacesWithExactAttributeValues() {
        String content = """
                leading<p:root p:key="a&#xD;&#xA;&#x9;b" kind="q:kind">one<![CDATA[<&]]>                <q:child xmlns="urn:nested" xmlns:q="urn:shadow"><local/></q:child>                <!-- comment --><?step go?>two&#xD;</p:root><plain xmlns="">last</plain>tail""";
        var events = read(transfer("<document><XMLBLBOX>" + content + "</XMLBLBOX></document>"
                + "<payload><BINBLBOX> A Q I D \n /w==\t</BINBLBOX></payload>"));
        var xml = assertInstanceOf(TransferValue.XmlFragment.class, field(events, "document")).xml();
        assertTrue(xml.startsWith("leading<p:root "));
        assertTrue(xml.contains("xmlns:p=\"urn:payload\""));
        assertTrue(xml.contains("xmlns:q=\"urn:qualified-name\""));
        assertTrue(xml.contains("p:key=\"a&#xD;&#xA;&#x9;b\""));
        assertTrue(xml.contains("<!-- comment --><?step go?>two&#xD;"));
        assertTrue(xml.contains("<plain xmlns=\"\""));
        var binary = assertInstanceOf(TransferValue.Binary.class, field(events, "payload"));
        assertArrayEquals(new byte[]{1, 2, 3, -1}, binary.bytes());
        assertEquals(events, read(write(events)));
    }

    @Test
    void keepsPresentEmptyValuesAndWritesCallerSuppliedFragmentsSafely() {
        var events = read(transfer("<document><XMLBLBOX/></document><payload><BINBLBOX/></payload>"));
        assertEquals(new TransferValue.XmlFragment(""), field(events, "document"));
        assertEquals(new TransferValue.Binary(new byte[0]), field(events, "payload"));
        assertEquals(events, read(write(events)));

        String encoded = write(withDocument(events, new TransferValue.XmlFragment("<local v='a&#xA;b'/>")));
        assertTrue(encoded.contains("<XMLBLBOX><local xmlns=\"\" v=\"a&#xA;b\">"));
        assertEquals(new TransferValue.XmlFragment("<local xmlns=\"\" v=\"a&#xA;b\"></local>"),
                field(read(encoded), "document"));
    }

    @Test
    void rejectsInvalidBinaryAndWrongWrappers() {
        for (String bad : List.of("Zg", "Zh==", "Zg=", "Zg===", "Z-g=", "Zg==&#160;", "Zg==<extra/>")) {
            var xml = transfer("<payload><BINBLBOX>" + bad + "</BINBLBOX></payload>");
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class, () -> read(xml), bad).reason());
        }
        for (String wrong : List.of("<payload><XMLBLBOX/></payload>", "<document><BINBLBOX/></document>",
                "<document><XMLBLBOX BAD='x'/></document>", "<document><XMLBLBOX/><XMLBLBOX/></document>")) {
            assertEquals(INVALID_WIRE, assertThrows(Xtf23Exception.class, () -> read(transfer(wrong))).reason());
        }
    }

    @Test
    void rejectsXmlDocumentInjectionEntitiesAndOversizePayloads() {
        var events = read(transfer("<document><XMLBLBOX/></document>"));
        for (String bad : List.of("<!DOCTYPE x SYSTEM 'file:///must-not-open'><x/>", "&external;",
                "<x><y></x>", "</blackboxFragment><injected/>", "<?xml version='1.0'?><root/>", "<p:unbound/>")) {
            var output = new ByteArrayOutputStream();
            assertThrows(Xtf23Exception.class,
                    () -> writeTo(withDocument(events, new TransferValue.XmlFragment(bad)), output), bad);
            assertFalse(output.toString(StandardCharsets.UTF_8).contains("<injected"));
        }
        String dtd = transfer("<document><XMLBLBOX>&xxe;</XMLBLBOX></document>")
                .replace("<TRANSFER", "<!DOCTYPE TRANSFER [<!ENTITY xxe SYSTEM 'file:///must-not-open'>]><TRANSFER");
        assertEquals(UNSAFE_XML, assertThrows(Xtf23Exception.class, () -> read(dtd)).reason());

        String longText = "A".repeat(Xtf23Input.MAX_TEXT + 1);
        for (String content : List.of("<document><XMLBLBOX>" + longText + "</XMLBLBOX></document>",
                "<payload><BINBLBOX>" + longText + "</BINBLBOX></payload>")) {
            assertEquals(LIMIT_EXCEEDED,
                    assertThrows(Xtf23Exception.class, () -> read(transfer(content))).reason());
        }
        assertEquals(LIMIT_EXCEEDED, assertThrows(Xtf23Exception.class,
                () -> write(withDocument(events, new TransferValue.XmlFragment(longText)))).reason());
        assertEquals(LIMIT_EXCEEDED, assertThrows(Xtf23Exception.class,
                () -> write(withField(events, "payload",
                        new TransferValue.Binary(new byte[Xtf23Input.MAX_TEXT / 4 * 3 + 1])))).reason());
        String tooDeep = "<x>".repeat(Xtf23Input.MAX_DEPTH) + "</x>".repeat(Xtf23Input.MAX_DEPTH);
        assertEquals(LIMIT_EXCEEDED, assertThrows(Xtf23Exception.class,
                () -> write(withDocument(events, new TransferValue.XmlFragment(tooDeep)))).reason());
    }

    private static String transfer(String fields) {
        return document(MODELS, "<Boxes.Data BID=\"b\" xmlns:p=\"urn:payload\" xmlns:q=\"urn:qualified-name\">"
                + "<Boxes.Data.Item TID=\"o\">" + fields + "</Boxes.Data.Item></Boxes.Data>");
    }

    private static List<TransferEvent> read(String source) {
        try (var reader = new Xtf23Reader(plan, input(source))) {
            return readAll(reader);
        }
    }

    private static String write(List<TransferEvent> events) {
        var output = new ByteArrayOutputStream();
        writeTo(events, output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void writeTo(List<TransferEvent> events, ByteArrayOutputStream output) {
        try (var writer = new Xtf23Writer(plan, output)) {
            for (var event : events) {
                writer.write(event, TOKEN);
            }
        }
    }

    private static TransferValue field(List<TransferEvent> events, String name) {
        var object = ((ObjectEvent) events.get(2)).object();
        return object.value(slot(object, name)).orElseThrow();
    }

    private static List<TransferEvent> withDocument(List<TransferEvent> events, TransferValue value) {
        return withField(events, "document", value);
    }

    private static List<TransferEvent> withField(List<TransferEvent> events, String name, TransferValue value) {
        var replacement = new ArrayList<>(events);
        var event = (ObjectEvent) events.get(2);
        var object = event.object();
        replacement.set(2, new ObjectEvent(new TransferObject(object.identity(),
                new TransferValue.Structured(object.type(), Map.of(slot(object, name), value))),
                event.operation(), event.consistency()));
        return replacement;
    }

    private static guru.interlis.model.SymbolRef slot(TransferObject object, String name) {
        return plan.object(object.type()).orElseThrow().slots().stream()
                .filter(slot -> slot.name().declared().last().equals(name)).findFirst().orElseThrow().symbol();
    }
}
