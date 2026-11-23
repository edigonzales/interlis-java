package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliInZone;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ScalarCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ScalarCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void wideIntegers(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "WideInteger" + version;
        schemas(expected, own, model, """
                CLASS Asset =
                  WideNo : -9223372036854775808 .. 9223372036854775807;
                  SmallNo : -100 .. 100;
                END Asset;
                """);
        var numbers = List.of("9007199254740993", "-9223372036854775808", "9223372036854775807");
        var values = new ArrayList<>(numbers.stream().map(value -> field("WideNo", value) + field("SmallNo", "13")).toList());
        values.add("");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> {
                for (int i = 0; i < numbers.size(); i++) {
                    assertEquals(numbers.get(i), child(rows.get(i), "WideNo").getTextContent());
                    assertEquals("13", child(rows.get(i), "SmallNo").getTextContent());
                }
                assertNull(child(rows.getLast(), "WideNo"));
            });
            try (var sql = db.connect()) {
                for (int i = 0; i < numbers.size(); i++) {
                    assertEquals(numbers.get(i), stored(db, sql, "wideno", "native", i));
                }
                assertNull(stored(db, sql, "wideno", "native", numbers.size()));
                var broken = data(model, "broken", List.of(values.getFirst(), field("WideNo", "9223372036854775808")));
                assertCliFailure(db, "import", model, "broken", broken, "64-bit integer encoding");
                assertCounts(db, sql, 8);
            }
        }
    }

    void decimals(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarDecimal" + version;
        schemas(expected, own, model, """
                DOMAIN Length = -100.000 .. 100.000 [INTERLIS.m];
                CLASS Asset =
                  PriceValue : -9999999999999999.999 .. 9999999999999999.999;
                  Measure : Length;
                END Asset;
                """);
        var values = List.of(field("PriceValue", "-1.125") + field("Measure", "2.250"), "");
        var high = new BigDecimal("9007199254740993.125");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> {
                assertNumber("-1.125", child(rows.getFirst(), "PriceValue").getTextContent());
                assertNumber("2.250", child(rows.getFirst(), "Measure").getTextContent());
                assertNull(child(rows.getLast(), "PriceValue"));
            });
            try (var sql = db.connect()) {
                assertNumber("-1.125", stored(db, sql, "pricevalue", "native", 0));
                assertNull(stored(db, sql, "pricevalue", "native", 1));
                assertEquals("m", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                        + " WHERE tablename = 'asset' AND columnname = 'measure' AND tag = 'ch.ehi.ili2db.unit'"));
                if (db.postgis()) {
                    assertEquals("19:3", scalar(sql, "SELECT numeric_precision || ':' || numeric_scale FROM information_schema.columns"
                            + " WHERE table_schema = '" + db.schema() + "' AND table_name = 'asset' AND column_name = 'pricevalue'"));
                }
                referenceImport(db, "rounded", data(model, "rounded", List.of(field("PriceValue", high.toPlainString()))));
                assertNotEquals(0, high.compareTo(new BigDecimal(stored(db, sql, "pricevalue", "rounded", 0))));
                assertCliSuccess(db, "import", model, "precise", data(model, "precise", List.of(field("PriceValue", high.toPlainString()))));
                var output = work.resolve("out.xtf");
                assertCliSuccess(db, "export", model, "precise", output);
                String exact = db.postgis() ? high.toPlainString() : BigDecimal.valueOf(high.doubleValue()).toPlainString();
                assertNumber(exact, child(rows(output, "precise", 1).getFirst(), "PriceValue").getTextContent());
            }
        }
    }

    private static void assertNumber(String expected, String actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), new BigDecimal(actual).stripTrailingZeros());
    }

    void calendarDate(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarCalendar" + version;
        String dateDomain = version == 23 ? "" : "DOMAIN DayValue = DATE;";
        schemas(expected, own, model, """
                %s
                CLASS Asset = CalendarDay : %s; END Asset;
                """.formatted(dateDomain, version == 23 ? "INTERLIS.XMLDate" : "DayValue"));
        var dates = List.of("2024-02-29", "1999-12-31");
        var values = new ArrayList<>(dates.stream().map(value -> field("CalendarDay", value)).toList());
        values.add("");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> assertTextValues(rows, "CalendarDay", "2024-02-29", "1999-12-31", null));
            try (var sql = db.connect()) {
                assertEquals("2024-02-29", stored(db, sql, "calendarday", "native", 0));
                assertNull(stored(db, sql, "calendarday", "native", 2));
                execute(sql, "UPDATE " + db.table("asset") + " SET calendarday = "
                        + (db.postgis() ? "'infinity'::date" : "'2024-02-30'") + " WHERE t_ili_tid = 'native-0'");
                var output = work.resolve("out.xtf");
                byte[] previous = Files.readAllBytes(output);
                assertCliFailure(db, "export", model, "native", output, "Invalid SQL date");
                assertArrayEquals(previous, Files.readAllBytes(output));
            }
        }
    }

    void localTime(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarClock" + version;
        schemas(expected, own, model, "CLASS Asset = ReadingTime : "
                + (version == 23 ? "INTERLIS.XMLTime" : "TIMEOFDAY") + "; END Asset;");
        var values = List.of(field("ReadingTime", "00:00:00.000"), field("ReadingTime", "23:59:58.125"), "");
        CheckRows check = rows -> assertTextValues(rows, "ReadingTime", "00:00:00.000", "23:59:58.125", null);
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, check);
            differentZones(db, model, values, check);
            try (var sql = db.connect()) {
                assertEquals("23:59:58.125", stored(db, sql, "readingtime", "zoned", 1));
                assertNull(stored(db, sql, "readingtime", "native", 2));
            }
        }
    }

    void localTimestamp(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarTimestamp" + version;
        schemas(expected, own, model, "CLASS Asset = ReadingStamp : "
                + (version == 23 ? "INTERLIS.XMLDateTime" : "DATETIME") + "; END Asset;");
        String gap = "2024-03-10T02:30:15.125";
        String spring = "2024-03-31T01:59:58.375";
        var values = List.of(field("ReadingStamp", gap), field("ReadingStamp", spring), "");
        CheckRows check = rows -> assertTextValues(rows, "ReadingStamp", gap, spring, null);
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, check);
            differentZones(db, model, values, check);
            try (var sql = db.connect()) {
                String stored = db.postgis() ? "readingstamp::text" : "readingstamp";
                assertEquals(db.postgis() ? gap.replace('T', ' ') : gap + "Z", stored(db, sql, stored, "zoned", 0));
                assertEquals(db.postgis() ? spring.replace('T', ' ') : spring + "Z", stored(db, sql, stored, "native", 1));
                assertNull(stored(db, sql, stored, "legacy", 2));
            }
        }
    }

    void formatted(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarFormatted" + version;
        schemas(expected, own, model, """
                STRUCTURE Digits = Numeral : 0..9999; END Digits;
                DOMAIN Ticket = FORMAT BASED ON Digits (Numeral/4) "0000" .. "9999";
                       CalRange EXTENDS INTERLIS.XMLDate = "1900-01-01" .. "2100-12-31";
                CLASS Asset =
                  TicketCode : Ticket;
                  RestrictedDay : CalRange;
                  NameCode : NAME;
                  UriCode : URI;
                END Asset;
                """);
        String uri = "https://example.org/A%20B?x=1&y=2";
        var values = List.of(field("TicketCode", "0012") + field("RestrictedDay", "2024-02-29")
                + field("NameCode", "A_1") + field("UriCode", uri.replace("&", "&amp;")), "");
        for (var db : List.of(expected, own)) {
            try (var sql = db.connect()) {
                execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN ticketcode TO stored_ticket");
                execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname = 'stored_ticket' WHERE sqlname = 'ticketcode'");
                execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                        + " SET columnname = 'stored_ticket' WHERE columnname = 'ticketcode'");
                mixed(db, model, values, rows -> {
                    assertTextValues(rows, "TicketCode", "0012", null);
                    assertTextValues(rows, "RestrictedDay", "2024-02-29", null);
                    assertTextValues(rows, "NameCode", "A_1", null);
                    assertTextValues(rows, "UriCode", uri, null);
                });
                assertEquals("0012", stored(db, sql, "stored_ticket", "native", 0));
                assertNull(stored(db, sql, "stored_ticket", "native", 1));
            }
        }
    }

    void uuidAttribute(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarUuid" + version;
        schemas(expected, own, model, "CLASS Asset = ExternalId : INTERLIS.UUIDOID; END Asset;");
        String upper = "80EB846C-F951-4084-9B05-03F596CC03EE";
        String lower = "80eb846c-f951-4084-9b05-03f596cc03ee";
        var values = List.of(uuidField(upper), uuidField(lower), "");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> {
                for (int i = 0; i < 2; i++) {
                    var value = child(rows.get(i), "ExternalId");
                    assertEquals(lower, version == 23 ? value.getAttribute("OID") : value.getTextContent());
                }
                assertNull(child(rows.getLast(), "ExternalId"));
            });
            try (var sql = db.connect()) {
                assertEquals(lower, stored(db, sql, "externalid", "native", 0));
                assertNull(stored(db, sql, "externalid", "native", 2));
                var broken = data(model, "broken", List.of(values.getFirst(), uuidField("not-a-uuid")));
                assertCliFailure(db, "import", model, "broken", broken, "Invalid UUID");
                assertCounts(db, sql, 6);
            }
        }
    }

    private String uuidField(String value) {
        return version == 23 ? "<ExternalId OID=\"" + value + "\"/>" : field("ExternalId", value);
    }

    void binary(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarBinary" + version;
        schemas(expected, own, model, "CLASS Asset = Payload : BLACKBOX BINARY; END Asset;");
        byte[] payload = {0, 1, 2, (byte) 0xff};
        var values = List.of(binaryField(Base64.getEncoder().encodeToString(payload)), "");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> {
                assertArrayEquals(payload, binaryValue(rows.getFirst()));
                assertNull(child(rows.getLast(), "Payload"));
            });
            Path output = work.resolve("out.xtf");
            assertCliSuccess(db, "import", model, "empty", data(model, "empty", List.of(binaryField(""), "")));
            assertCliSuccess(db, "export", model, "empty", output);
            var empty = rows(output, "empty", 2);
            assertArrayEquals(new byte[0], binaryValue(empty.getFirst()));
            assertNull(child(empty.getLast(), "Payload"));
            try (var sql = db.connect()) {
                String hex = db.postgis() ? "lower(encode(payload, 'hex'))" : "lower(hex(payload))";
                assertEquals("000102ff", stored(db, sql, hex, "legacy", 0));
                assertEquals("000102ff", stored(db, sql, hex, "native", 0));
                assertNull(stored(db, sql, "payload", "native", 1));
                assertEquals("0", stored(db, sql, db.postgis() ? "octet_length(payload)" : "length(payload)", "empty", 0));
                String large = db.postgis() ? "decode(repeat('00', 786433), 'hex')" : "zeroblob(786433)";
                execute(sql, "UPDATE " + db.table("asset") + " SET payload = " + large + " WHERE t_ili_tid = 'native-0'");
                byte[] previous = Files.readAllBytes(output);
                assertCliFailure(db, "export", model, "native", output, "Oversized BLACKBOX BINARY");
                assertArrayEquals(previous, Files.readAllBytes(output));
            }
        }
    }

    private String binaryField(String base64) {
        return field("Payload", version == 23 ? field("BINBLBOX", base64) : base64);
    }

    private byte[] binaryValue(Element row) {
        var payload = child(row, "Payload");
        String encoded = (version == 23 ? child(payload, "BINBLBOX") : payload).getTextContent();
        return Base64.getMimeDecoder().decode(encoded);
    }

    void xml(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarXml" + version;
        schemas(expected, own, model, "CLASS Asset = Payload : BLACKBOX XML; END Asset;");
        String ascii = "<p:sample xmlns:p=\"urn:scalar\" p:code=\"x\">ascii</p:sample>";
        var values = List.of(xmlField(ascii), "");
        for (var db : List.of(expected, own)) {
            Path output = work.resolve("out.xtf");
            referenceImport(db, "legacy", data(model, "legacy", values));
            assertCliSuccess(db, "export", model, "legacy", output);
            var exported = rows(output, "legacy", 2);
            assertFragment(children(xmlPayload(exported.getFirst())).getFirst(), "ascii");
            assertNull(xmlPayload(exported.getLast()));

            assertCliSuccess(db, "import", model, "native", data(model, "native", values));
            referenceExport(db, "native", output);
            var roundTrip = rows(output, "native", 2);
            var payload = xmlPayload(roundTrip.getFirst());
            assertFragment(version == 23 ? children(payload).getFirst() : parseFragment(payload.getTextContent()), "ascii");
            assertNull(xmlPayload(roundTrip.getLast()));

            String unicode = xmlField("<p:sample p:code=\"x\">Grüsse</p:sample>")
                    .replace("<Payload>", "<Payload xmlns:p=\"urn:scalar\">");
            assertCliSuccess(db, "import", model, "unicode", data(model, "unicode", List.of(unicode)));
            assertCliSuccess(db, "export", model, "unicode", output);
            assertFragment(children(xmlPayload(rows(output, "unicode", 1).getFirst())).getFirst(), "Grüsse");
            if (version == 23 && !db.postgis()) {
                reference.expectFailure(db, "%ILI_FROM_DB", "Invalid byte", "--export", "--dataset", "unicode",
                        "--exportTid", work.resolve("reference-unicode.xtf").toString());
                Files.deleteIfExists(work.resolve("reference-unicode.xtf"));
            }
            try (var sql = db.connect()) {
                String xml = db.postgis() ? "payload::text" : "payload";
                assertFragment(parseFragment(stored(db, sql, xml, "legacy", 0)), "ascii");
                assertFragment(parseFragment(stored(db, sql, xml, "native", 0)), "ascii");
                assertNull(stored(db, sql, "payload", "native", 1));
                String large = db.postgis() ? "repeat('x', 1048577)::xml" : "printf('%01048577d', 0)";
                execute(sql, "UPDATE " + db.table("asset") + " SET payload = " + large + " WHERE t_ili_tid = 'native-0'");
                byte[] previous = Files.readAllBytes(output);
                assertCliFailure(db, "export", model, "native", output, "Oversized BLACKBOX XML");
                assertArrayEquals(previous, Files.readAllBytes(output));
            }
        }
    }

    private String xmlField(String fragment) {
        return field("Payload", version == 23 ? field("XMLBLBOX", fragment) : fragment);
    }

    private Element xmlPayload(Element row) {
        var payload = child(row, "Payload");
        return payload != null && version == 23 ? child(payload, "XMLBLBOX") : payload;
    }

    private static Element parseFragment(String fragment) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(fragment))).getDocumentElement();
    }

    private static void assertFragment(Element element, String content) {
        assertEquals("urn:scalar", element.getNamespaceURI());
        assertEquals("sample", element.getLocalName());
        assertEquals("x", element.getAttributeNS("urn:scalar", "code"));
        assertEquals(content, element.getTextContent());
    }

    void decimalArrays(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarDecimalArray" + version;
        String wrapper = version == 23 ? "STRUCTURE Measure = Entry : Price; END Measure;" : "";
        var ili = source(model, """
                DOMAIN Price = -9999999999999999.999 .. 9999999999999999.999;
                %s
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Measurements : LIST OF %s;
                END Asset;
                """.formatted(wrapper, version == 23 ? "Measure" : "Price"));
        referenceSchema(expected, model, "--coalesceArray");
        var schema = schemaWithIdentityColumns(own, model, ili, CancellationToken.none(), "--coalesce-array");
        assertEquals(0, schema.code(), schema::diagnostics);
        sameCatalog(expected, own);
        var numbers = List.of("-1.125", "2.250", "-1.125");
        var values = List.of(decimalItems(model, numbers), "");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> {
                var found = decimalItems(rows.getFirst());
                assertEquals(numbers.size(), found.size());
                for (int i = 0; i < numbers.size(); i++) assertNumber(numbers.get(i), found.get(i));
                assertNull(child(rows.getLast(), "Measurements"));
            });
            try (var sql = db.connect()) {
                String size = db.postgis() ? "cardinality(measurements)" : "json_array_length(measurements)";
                String type = db.postgis() ? "pg_typeof(measurements)::text" : "json_type(measurements, '$[0]')";
                assertEquals("3", stored(db, sql, size, "native", 0));
                assertEquals(db.postgis() ? "numeric[]" : "real", stored(db, sql, type, "native", 0));
                assertNull(stored(db, sql, "measurements", "native", 1));
                var broken = data(model, "broken", List.of(values.getFirst(), decimalItems(model, List.of("invalid"))));
                assertCliFailure(db, "import", model, "broken", broken,
                        version == 23 ? "Invalid XTF decimal value" : "Invalid XTF numeric lexeme");
                assertCounts(db, sql, 4);
            }
            String large = "9007199254740993.125";
            assertCliSuccess(db, "import", model, "exact", data(model, "exact", List.of(decimalItems(model, List.of(large)))));
            Path output = work.resolve("out.xtf");
            assertCliSuccess(db, "export", model, "exact", output);
            assertEquals(List.of(large), decimalItems(rows(output, "exact", 1).getFirst()));
        }
    }

    private String decimalItems(String model, List<String> values) {
        if (version == 24) return values.stream().map(value -> field("Measurements", value)).collect(Collectors.joining());
        return field("Measurements", values.stream().map(value -> field(model + ".Data.Measure", field("Entry", value)))
                .collect(Collectors.joining()));
    }

    private List<String> decimalItems(Element object) {
        if (version == 23) return children(child(object, "Measurements")).stream()
                .map(item -> child(item, "Entry").getTextContent()).toList();
        return children(object).stream().filter(item -> item.getLocalName().equals("Measurements"))
                .map(Element::getTextContent).toList();
    }

    private void differentZones(TestDatabase db, String model, List<String> values, CheckRows check) throws Exception {
        Path output = work.resolve("zoned-out.xtf");
        assertCliInZone(db, "export", model, "legacy", output, "Pacific/Auckland");
        check.check(rows(output, "legacy", values.size()));
        assertCliInZone(db, "import", model, "zoned", data(model, "zoned", values), "America/New_York");
        referenceExport(db, "zoned", output);
        check.check(rows(output, "zoned", values.size()));
    }

    private static void assertTextValues(List<Element> rows, String attribute, String... values) {
        assertEquals(values.length, rows.size());
        for (int i = 0; i < values.length; i++) {
            var value = child(rows.get(i), attribute);
            assertEquals(values[i], value == null ? null : value.getTextContent(), attribute + " in object " + i);
        }
    }

    @FunctionalInterface
    private interface CheckRows {
        void check(List<Element> rows) throws Exception;
    }

    private void mixed(TestDatabase db, String model, List<String> fields, CheckRows check) throws Exception {
        Path output = work.resolve("out.xtf");
        referenceImport(db, "legacy", data(model, "legacy", fields));
        assertCliSuccess(db, "export", model, "legacy", output);
        check.check(rows(output, "legacy", fields.size()));
        assertCliSuccess(db, "import", model, "native", data(model, "native", fields));
        referenceExport(db, "native", output);
        check.check(rows(output, "native", fields.size()));
    }

    private void schemas(TestDatabase expected, TestDatabase own, String model, String declarations) throws Exception {
        Path ili = source(model, declarations);
        referenceSchema(expected, model);
        assertSchemaWithIdentityColumns(own, model, ili);
        sameCatalog(expected, own);
    }

    private Path source(String model, String declarations) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, declarations, model));
    }

    private void referenceSchema(TestDatabase db, String model, String... extra) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
        options.addAll(List.of(extra));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }

    private static void sameCatalog(TestDatabase expected, TestDatabase own) throws Exception {
        try (var left = expected.connect(); var right = own.connect()) {
            DbSchemaCatalog.read(left, expected.postgis(), expected.schema())
                    .assertSameAs(DbSchemaCatalog.read(right, own.postgis(), own.schema()));
        }
    }

    private Path data(String model, String prefix, List<String> values) throws Exception {
        String tag = version == 23 ? model + ".Data.Asset" : "Asset";
        String attribute = version == 23 ? " TID" : " i:tid";
        String objects = IntStream.range(0, values.size()).mapToObj(i -> "<" + tag + attribute + "=\""
                + prefix + "-" + i + "\">" + values.get(i) + "</" + tag + ">").collect(Collectors.joining());
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    private List<Element> rows(Path output, String prefix, int count) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-basket", id(baskets.getFirst(), version, "bid"));
        var byTid = children(baskets.getFirst()).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        assertEquals(count, byTid.size());
        return IntStream.range(0, count).mapToObj(i -> byTid.get(prefix + "-" + i)).toList();
    }

    private static String field(String name, String content) {
        return "<" + name + ">" + content + "</" + name + ">";
    }

    private static String stored(TestDatabase db, Connection sql, String expression, String prefix, int row) throws Exception {
        return scalar(sql, "SELECT " + expression + " FROM " + db.table("asset")
                + " WHERE t_ili_tid = '" + prefix + "-" + row + "'");
    }

    private static void assertCounts(TestDatabase db, Connection sql, int objects) throws Exception {
        assertEquals(Integer.toString(objects), scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
    }
}
