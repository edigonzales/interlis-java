package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ScalarFixtures {
    static final String UUID = "80eb846c-f951-4084-9b05-03f596cc03ee";
    static final String GAP = "2024-03-10T02:30:15.125";
    static final String SPRING = "2024-03-31T01:59:58.375";
    static final String URI = "https://example.org/A%20B?x=1&y=2";
    static final String HIGH_DECIMAL = "9007199254740993.125";
    static final List<String> DECIMAL_ITEMS = List.of("-1.125", "2.250", "-1.125");

    private final Path work;
    private final int version;

    ScalarFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path copyModel(String model) throws Exception {
        Path path = work.resolve(model + ".ili");
        try (var source = ScalarFixtures.class.getResourceAsStream("/" + path.getFileName())) {
            Files.copy(Objects.requireNonNull(source, model), path);
        }
        return path;
    }

    static Map<String, String> modelPaths(String model) {
        var paths = new LinkedHashMap<String, String>();
        paths.put("ClassValue", model + ".Data.Asset");
        paths.put("StructureValue", model + ".Data.Digits");
        paths.put("PropertyPath", model + ".Data.Digits.Numeral");
        return paths;
    }

    static Map<String, String> alignmentValues() {
        var values = new LinkedHashMap<String, String>();
        values.put("HValue", "Center");
        values.put("VValue", "Cap");
        values.put("VAlias", "Bottom");
        return values;
    }

    static String scalarFields(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> field(entry.getKey(), entry.getValue())).collect(Collectors.joining());
    }

    String uuidField(String value) {
        return version == 23 ? "<ExternalId OID=\"" + value + "\"/>" : field("ExternalId", value);
    }

    String uuidValue(Element row) {
        var value = child(row, "ExternalId");
        return version == 23 ? value.getAttribute("OID") : value.getTextContent();
    }

    String numericOidLexeme() {
        return version == 23 ? "12" : "0012";
    }

    String numericOidField(String value) {
        return version == 23 ? "<NumberKey OID=\"" + value + "\"/>" : field("NumberKey", value);
    }

    String numericOidValue(Element row) {
        var value = child(row, "NumberKey");
        return version == 23 ? value.getAttribute("OID") : value.getTextContent();
    }

    String binaryField(String base64) {
        return field("BinaryValue", version == 23 ? field("BINBLBOX", base64) : base64);
    }

    byte[] binaryValue(Element row) {
        var value = child(row, "BinaryValue");
        String base64 = (version == 23 ? child(value, "BINBLBOX") : value).getTextContent();
        return Base64.getMimeDecoder().decode(base64);
    }

    String xmlField(String fragment) {
        return field("XmlValue", version == 23 ? field("XMLBLBOX", fragment) : fragment);
    }

    Element xmlFragment(Element row, boolean referenceOutput) throws Exception {
        var value = child(row, "XmlValue");
        if (version == 23) return children(child(value, "XMLBLBOX")).getFirst();
        return referenceOutput ? parseFragment(value.getTextContent()) : children(value).getFirst();
    }

    String decimalItems(String model, List<String> values) {
        if (version == 24) return values.stream().map(value -> field("Measurements", value)).collect(Collectors.joining());
        return field("Measurements", values.stream().map(value -> field(model + ".Data.Measure", field("Entry", value)))
                .collect(Collectors.joining()));
    }

    static void assertNumber(String expected, String actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), new BigDecimal(actual).stripTrailingZeros());
    }

    List<String> decimalItems(Element object) {
        if (version == 23) return children(child(object, "Measurements")).stream()
                .map(item -> child(item, "Entry").getTextContent()).toList();
        return children(object).stream().filter(item -> item.getLocalName().equals("Measurements"))
                .map(Element::getTextContent).toList();
    }

    static Element parseFragment(String fragment) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(fragment))).getDocumentElement();
    }

    static void assertFragment(Element element, String content) {
        assertEquals("urn:scalar", element.getNamespaceURI());
        assertEquals("sample", element.getLocalName());
        assertEquals("x", element.getAttributeNS("urn:scalar", "code"));
        assertEquals(content, element.getTextContent());
    }

    Path data(String model, String prefix, List<String> values) throws Exception {
        String tag = version == 23 ? model + ".Data.Asset" : "Asset";
        String attribute = version == 23 ? " TID" : " i:tid";
        String objects = IntStream.range(0, values.size()).mapToObj(i -> "<" + tag + attribute + "=\""
                + prefix + "-" + i + "\">" + values.get(i) + "</" + tag + ">").collect(Collectors.joining());
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    List<Element> rows(Path output, String prefix, int count) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-basket", id(baskets.getFirst(), version, "bid"));
        var byTid = children(baskets.getFirst()).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        assertEquals(count, byTid.size());
        return IntStream.range(0, count).mapToObj(i -> {
            String tid = prefix + "-" + i;
            var row = byTid.get(tid);
            assertNotNull(row, tid);
            return row;
        }).toList();
    }

    static String field(String name, String content) {
        return "<" + name + ">" + content + "</" + name + ">";
    }

    static String stored(Target db, Connection sql, String expression, String prefix, int row) throws Exception {
        return scalar(sql, "SELECT " + expression + " FROM " + db.table("asset")
                + " WHERE t_ili_tid = '" + prefix + "-" + row + "'");
    }

}
