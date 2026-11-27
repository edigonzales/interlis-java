package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ScalarFixtures.DECIMAL_ITEMS;
import static guru.interlis.db.ScalarFixtures.GAP;
import static guru.interlis.db.ScalarFixtures.HIGH_DECIMAL;
import static guru.interlis.db.ScalarFixtures.SPRING;
import static guru.interlis.db.ScalarFixtures.URI;
import static guru.interlis.db.ScalarFixtures.UUID;
import static guru.interlis.db.ScalarFixtures.assertFragment;
import static guru.interlis.db.ScalarFixtures.assertNumber;
import static guru.interlis.db.ScalarFixtures.field;
import static guru.interlis.db.ScalarFixtures.parseFragment;
import static guru.interlis.db.ScalarFixtures.stored;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ScalarInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();
    private static final List<String> WIDE_VALUES = List.of(
            "9007199254740993", "-9223372036854775808", "9223372036854775807");

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageScalars23() throws Exception { exchangeScalars(23, null); }
    @Test void geoPackageScalars24() throws Exception { exchangeScalars(24, null); }
    @Test void postgisScalars23() throws Exception { exchangeScalars(23, POSTGIS.container()); }
    @Test void postgisScalars24() throws Exception { exchangeScalars(24, POSTGIS.container()); }

    @Test void geoPackageScalarText23() throws Exception { exchangeScalarText(23, null); }
    @Test void geoPackageScalarText24() throws Exception { exchangeScalarText(24, null); }
    @Test void postgisScalarText23() throws Exception { exchangeScalarText(23, POSTGIS.container()); }
    @Test void postgisScalarText24() throws Exception { exchangeScalarText(24, POSTGIS.container()); }

    @Test void geoPackageDecimalArray23() throws Exception { exchangeDecimalArray(23, null); }
    @Test void geoPackageDecimalArray24() throws Exception { exchangeDecimalArray(24, null); }
    @Test void postgisDecimalArray23() throws Exception { exchangeDecimalArray(23, POSTGIS.container()); }
    @Test void postgisDecimalArray24() throws Exception { exchangeDecimalArray(24, POSTGIS.container()); }

    @Test void geoPackageAlignmentLabels23() throws Exception { exchangeAlignmentLabels(23, null); }
    @Test void geoPackageAlignmentLabels24() throws Exception { exchangeAlignmentLabels(24, null); }
    @Test void postgisAlignmentLabels23() throws Exception { exchangeAlignmentLabels(23, POSTGIS.container()); }
    @Test void postgisAlignmentLabels24() throws Exception { exchangeAlignmentLabels(24, POSTGIS.container()); }

    private void exchangeScalars(int version, PostgreSQLContainer postgres) throws Exception {
        var old = new TestDatabase(work, postgres, "scalar_reference", "v" + version + "_");
        var own = old.peer("scalar_own");
        var reference = new ReferenceTool(work, old.postgis(), "UTC");
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        Path ili = fixture.copyModel(model);
        referenceSchema(reference, old, ili);
        assertSchemaWithIdentityColumns(own, model, ili);
        compareCatalog(old, own);

        String uri = URI.replace("&", "&amp;");
        String ascii = "<p:sample xmlns:p=\"urn:scalar\" p:code=\"x\">ascii</p:sample>";
        var values = List.of(
                field("WideNo", WIDE_VALUES.get(0)) + field("SmallNo", "13") + field("PriceValue", "-1.125")
                        + field("Measure", "2.250") + field("CalendarDay", "2024-02-29") + field("ReadingTime", "00:00:00.000")
                        + field("ReadingStamp", GAP) + field("TicketCode", "0012") + field("RestrictedDay", "2024-02-29")
                        + field("NameCode", "A_1") + field("UriCode", uri)
                        + ScalarFixtures.scalarFields(ScalarFixtures.modelPaths(model))
                        + ScalarFixtures.scalarFields(ScalarFixtures.alignmentValues())
                        + fixture.uuidField(UUID.toUpperCase(Locale.ROOT))
                        + fixture.binaryField("AAEC/w==") + fixture.xmlField(ascii),
                field("WideNo", WIDE_VALUES.get(1)) + field("CalendarDay", "1999-12-31")
                        + field("ReadingTime", "23:59:58.125") + field("ReadingStamp", SPRING) + fixture.uuidField(UUID),
                field("WideNo", WIDE_VALUES.get(2)), "");
        Path legacy = fixture.data(model, "legacy", values);
        Path nativeInput = fixture.data(model, "native", values);
        Path rounded = fixture.data(model, "rounded", List.of(field("PriceValue", HIGH_DECIMAL)));
        for (var db : List.of(old, own)) {
            String suffix = db == old ? "old" : "own";
            String ticketColumn = db == old ? "stored_ticket" : "ticketcode";
            try (var sql = db.connect()) {
                if (db == old) {
                    execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN ticketcode TO stored_ticket");
                    execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_ticket' WHERE sqlname='ticketcode'");
                    execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                            + " SET columnname='stored_ticket' WHERE columnname='ticketcode'");
                }
                referenceImport(reference, db, "legacy", legacy);
                Path exported = work.resolve("scalars-native-" + suffix + ".xtf");
                assertCliSuccess(db, "export", model, "legacy", exported);
                assertScalars(fixture, fixture.rows(exported, "legacy", values.size()), model, false);

                assertCliSuccess(db, "import", model, "native", nativeInput);
                Path returned = work.resolve("scalars-reference-" + suffix + ".xtf");
                referenceExport(reference, db, "native", returned);
                assertScalars(fixture, fixture.rows(returned, "native", values.size()), model, true);
                assertStoredScalars(db, sql, ticketColumn, model, "legacy");
                assertStoredScalars(db, sql, ticketColumn, model, "native");
                assertEquals("m", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                        + " WHERE tablename='asset' AND columnname='measure' AND tag='ch.ehi.ili2db.unit'"));
                if (db.postgis()) {
                    assertEquals("19:3", scalar(sql, "SELECT numeric_precision || ':' || numeric_scale FROM information_schema.columns"
                            + " WHERE table_schema='" + db.schema() + "' AND table_name='asset' AND column_name='pricevalue'"));
                }
                referenceImport(reference, db, "rounded", rounded);
                assertNotEquals(0, new BigDecimal(HIGH_DECIMAL)
                        .compareTo(new BigDecimal(stored(db, sql, "pricevalue", "rounded", 0))));
            }
        }
        if (version == 23 && !old.postgis()) {
            String unicode = fixture.xmlField("<p:sample p:code=\"x\">Grüsse</p:sample>")
                    .replace("<XmlValue>", "<XmlValue xmlns:p=\"urn:scalar\">");
            Path input = fixture.data(model, "unicode", List.of(unicode));
            assertCliSuccess(old, "import", model, "unicode", input);
            Path export = work.resolve("unicode-native.xtf");
            assertCliSuccess(old, "export", model, "unicode", export);
            assertFragment(fixture.xmlFragment(fixture.rows(export, "unicode", 1).getFirst(), false), "Grüsse");
            reference.expectFailure(old, "%ILI_FROM_DB", "Invalid byte", "--export", "--dataset", "unicode",
                    "--exportTid", work.resolve("unicode-reference.xtf").toString());
        }
    }

    private void exchangeScalarText(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "scalar_text_reference", "v" + version + "_");
        var own = db.peer("scalar_text_own");
        var reference = new ReferenceTool(work, db.postgis(), "UTC");
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        Path ili = fixture.copyModel(model);
        referenceSchema(reference, db, ili, "--sqlColsAsText");
        assertSchemaWithIdentityColumns(own, model, ili, "--sql-cols-as-text");
        compareCatalog(db, own);

        var scalars = new LinkedHashMap<String, String>();
        scalars.put("Caption", "Caption");
        scalars.put("Remarks", "line one\nline two");
        scalars.put("Active", "true");
        scalars.put("State", "More.Free");
        scalars.put("WideNo", "9007199254740993");
        scalars.put("PriceValue", HIGH_DECIMAL);
        scalars.put("Measure", "2.250");
        scalars.put("ReadingTime", "10:15:20.000");
        scalars.put("ReadingStamp", "2024-02-29T10:15:20.120");
        scalars.put("TicketCode", "0012");
        scalars.putAll(ScalarFixtures.modelPaths(model));
        scalars.putAll(ScalarFixtures.alignmentValues());
        String uuid = UUID.toUpperCase(Locale.ROOT);
        String fields = scalars.entrySet().stream()
                .map(entry -> field(entry.getKey(), entry.getValue())).collect(java.util.stream.Collectors.joining())
                + fixture.uuidField(uuid) + fixture.binaryField("AAEC/w==")
                + fixture.xmlField("<p:sample xmlns:p=\"urn:scalar\" p:code=\"x\">ascii</p:sample>");
        var values = List.of(fields, "");
        Path legacy = fixture.data(model, "legacy", values);
        Path nativeInput = fixture.data(model, "native", values);
        exchangeScalarText(db, reference, fixture, model, legacy, nativeInput, scalars, uuid);
        exchangeScalarText(own, reference, fixture, model, legacy, nativeInput, scalars, uuid);

        String extension = "ScalarTextExtension" + version;
        Path extensionIli = Files.writeString(work.resolve(extension + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data = CLASS Extra = ValueNo : 0.000 .. 9999999999999999.999; END Extra; END Data;
                END %s.
                """.formatted(version - 20, extension, extension));
        assertSchemaWithIdentityColumns(own, extension, extensionIli, "--add-models");
        String object = version == 23 ? "<" + extension + ".Data.Extra TID=\"new-1\"><ValueNo>" + HIGH_DECIMAL
                + "</ValueNo></" + extension + ".Data.Extra>" : "<Extra i:tid=\"new-1\"><ValueNo>" + HIGH_DECIMAL
                + "</ValueNo></Extra>";
        Path extra = Files.writeString(work.resolve("extension.xtf"),
                DbTestSupport.referenceDatasetFixture(extension, version, "extension-basket", object));
        referenceImport(reference, own, "extension", extra);
        try (var sql = own.connect()) {
            assertEquals(HIGH_DECIMAL, scalar(sql, "SELECT valueno FROM " + own.table("extra")));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + own.table("extra")
                    + " WHERE t_id > (SELECT max(t_id) FROM " + own.table("asset") + ")"));
        }
    }

    private void exchangeScalarText(TestDatabase db, ReferenceTool reference, ScalarFixtures fixture, String model,
                                    Path legacy, Path nativeInput, Map<String, String> scalars, String uuid) throws Exception {
        referenceImport(reference, db, "legacy", legacy);
        Path exported = work.resolve("scalar-text-native.xtf");
        assertCliSuccess(db, "export", model, "legacy", exported);
        assertScalarText(fixture, exported, "legacy", scalars, uuid, false);

        assertCliSuccess(db, "import", model, "native", nativeInput);
        Path returned = work.resolve("scalar-text-reference.xtf");
        referenceExport(reference, db, "native", returned);
        assertScalarText(fixture, returned, "native", scalars, uuid, true);
        try (var sql = db.connect()) {
            for (String dataset : List.of("legacy", "native")) {
                for (var entry : scalars.entrySet()) {
                    String column = scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_attrname")
                            + " WHERE iliname='" + model + ".Data.Asset." + entry.getKey() + "'");
                    assertEquals(entry.getValue(), stored(db, sql, column, dataset, 0), dataset + "." + column);
                    assertNull(stored(db, sql, column, dataset, 1), dataset + "." + column);
                }
                assertEquals(uuid, stored(db, sql, "externalid", dataset, 0));
                assertEquals("AAEC/w==", stored(db, sql, "binaryvalue", dataset, 0));
                assertFragment(parseFragment(stored(db, sql, "xmlvalue", dataset, 0)), "ascii");
            }
            byte[] previous = Files.readAllBytes(exported);
            execute(sql, "UPDATE " + db.table("asset") + " SET pricevalue='abc' WHERE t_ili_tid='legacy-0'");
            assertCliFailure(db, "export", model, "legacy", exported, "PriceValue of object legacy-0");
            assertArrayEquals(previous, Files.readAllBytes(exported));
            execute(sql, "UPDATE " + db.table("asset") + " SET pricevalue='+.125' WHERE t_ili_tid='legacy-0'");
            assertCliFailure(db, "export", model, "legacy", exported, "Object legacy-0: " + model + ".Data.Asset.PriceValue");
            assertArrayEquals(previous, Files.readAllBytes(exported));
        }
    }

    private static void assertScalarText(ScalarFixtures fixture, Path file, String dataset, Map<String, String> scalars,
                                         String uuid, boolean referenceOutput) throws Exception {
        var rows = fixture.rows(file, dataset, 2);
        var first = rows.getFirst();
        for (var entry : scalars.entrySet()) {
            assertEquals(entry.getValue(), child(first, entry.getKey()).getTextContent(), entry.getKey());
            assertNull(child(rows.getLast(), entry.getKey()), entry.getKey());
        }
        assertEquals(uuid, fixture.uuidValue(first));
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 0xff}, fixture.binaryValue(first));
        assertFragment(fixture.xmlFragment(first, referenceOutput), "ascii");
    }

    private void exchangeAlignmentLabels(int version, PostgreSQLContainer postgres) throws Exception {
        var legacy = new TestDatabase(work, postgres, "alignment_labels_ref", "v" + version + "_");
        var own = legacy.peer("alignment_labels_own");
        var reference = new ReferenceTool(work, legacy.postgis());
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        Path ili = fixture.copyModel(model);
        referenceSchema(reference, legacy, ili, "--createEnumTxtCol");
        assertSchemaWithIdentityColumns(own, model, ili, "--create-enum-txt-col");
        compareCatalog(legacy, own);

        var alignments = ScalarFixtures.alignmentValues();
        var values = List.of(ScalarFixtures.scalarFields(alignments), "");
        Path legacyInput = fixture.data(model, "legacy", values);
        Path nativeInput = fixture.data(model, "native", values);
        for (var db : List.of(legacy, own)) {
            referenceImport(reference, db, "legacy", legacyInput);
            Path nativeOutput = work.resolve("alignment-native.xtf");
            assertCliSuccess(db, "export", model, "legacy", nativeOutput);
            assertScalarValues(fixture.rows(nativeOutput, "legacy", 2), alignments);
            assertCliSuccess(db, "import", model, "native", nativeInput);
            Path referenceOutput = work.resolve("alignment-reference.xtf");
            referenceExport(reference, db, "native", referenceOutput);
            assertScalarValues(fixture.rows(referenceOutput, "native", 2), alignments);
            try (var sql = db.connect()) {
                for (var dataset : List.of("legacy", "native")) {
                    assertStoredScalars(db, sql, dataset, alignments, 1);
                    for (var entry : alignments.entrySet()) {
                        String label = entry.getKey().toLowerCase(Locale.ROOT) + "_txt";
                        assertEquals(entry.getValue(), stored(db, sql, label, dataset, 0));
                        assertNull(stored(db, sql, label, dataset, 1));
                    }
                }
            }
        }
    }

    private void exchangeDecimalArray(int version, PostgreSQLContainer postgres) throws Exception {
        var old = new TestDatabase(work, postgres, "decimal_array_reference", "v" + version + "_");
        var own = old.peer("decimal_array_own");
        var reference = new ReferenceTool(work, old.postgis());
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarDecimalArray" + version;
        Path ili = fixture.copyModel(model);
        referenceSchema(reference, old, ili, "--coalesceArray");
        assertSchemaWithIdentityColumns(own, model, ili, "--coalesce-array");
        compareCatalog(old, own);
        var values = List.of(fixture.decimalItems(model, DECIMAL_ITEMS), "");
        Path legacy = fixture.data(model, "legacy", values);
        Path nativeInput = fixture.data(model, "native", values);
        for (var db : List.of(old, own)) {
            String suffix = db == old ? "old" : "own";
            referenceImport(reference, db, "legacy", legacy);
            Path exported = work.resolve("array-native-" + suffix + ".xtf");
            assertCliSuccess(db, "export", model, "legacy", exported);
            assertArray(fixture, exported, "legacy");

            assertCliSuccess(db, "import", model, "native", nativeInput);
            Path returned = work.resolve("array-reference-" + suffix + ".xtf");
            referenceExport(reference, db, "native", returned);
            assertArray(fixture, returned, "native");
            try (var sql = db.connect()) {
                String size = db.postgis() ? "cardinality(measurements)" : "json_array_length(measurements)";
                String type = db.postgis() ? "pg_typeof(measurements)::text" : "json_type(measurements, '$[0]')";
                for (var dataset : List.of("legacy", "native")) {
                    assertEquals("3", stored(db, sql, size, dataset, 0));
                    assertEquals(db.postgis() ? "numeric[]" : "real", stored(db, sql, type, dataset, 0));
                    assertNull(stored(db, sql, "measurements", dataset, 1));
                }
            }
        }
    }

    private static void assertScalars(ScalarFixtures fixture, List<Element> objects, String model, boolean referenceOutput)
            throws Exception {
        for (int i = 0; i < WIDE_VALUES.size(); i++) {
            assertEquals(WIDE_VALUES.get(i), child(objects.get(i), "WideNo").getTextContent());
        }
        var first = objects.getFirst();
        assertEquals("13", child(first, "SmallNo").getTextContent());
        assertNumber("-1.125", child(first, "PriceValue").getTextContent());
        assertNumber("2.250", child(first, "Measure").getTextContent());
        assertEquals("2024-02-29", child(first, "CalendarDay").getTextContent());
        assertEquals("1999-12-31", child(objects.get(1), "CalendarDay").getTextContent());
        assertEquals("00:00:00.000", child(first, "ReadingTime").getTextContent());
        assertEquals("23:59:58.125", child(objects.get(1), "ReadingTime").getTextContent());
        assertEquals(GAP, child(first, "ReadingStamp").getTextContent());
        assertEquals(SPRING, child(objects.get(1), "ReadingStamp").getTextContent());
        assertEquals("0012", child(first, "TicketCode").getTextContent());
        assertEquals("2024-02-29", child(first, "RestrictedDay").getTextContent());
        assertEquals("A_1", child(first, "NameCode").getTextContent());
        assertEquals(URI, child(first, "UriCode").getTextContent());
        assertScalarValues(objects, ScalarFixtures.modelPaths(model));
        assertScalarValues(objects, ScalarFixtures.alignmentValues());
        assertEquals(UUID, fixture.uuidValue(first));
        assertEquals(UUID, fixture.uuidValue(objects.get(1)));
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 0xff}, fixture.binaryValue(first));
        assertFragment(fixture.xmlFragment(first, referenceOutput), "ascii");
        for (var name : List.of("WideNo", "PriceValue", "CalendarDay", "ReadingTime", "ReadingStamp",
                "TicketCode", "ExternalId", "BinaryValue", "XmlValue")) {
            assertNull(child(objects.getLast(), name), name);
        }
    }

    private static void assertStoredScalars(TestDatabase db, Connection sql, String ticketColumn, String model, String dataset)
            throws Exception {
        for (int i = 0; i < WIDE_VALUES.size(); i++) {
            assertEquals(WIDE_VALUES.get(i), stored(db, sql, "wideno", dataset, i));
        }
        assertNumber("-1.125", stored(db, sql, "pricevalue", dataset, 0));
        assertEquals("2024-02-29", stored(db, sql, "calendarday", dataset, 0));
        assertEquals("23:59:58.125", stored(db, sql, "readingtime", dataset, 1));
        assertEquals(db.postgis() ? SPRING.replace('T', ' ') : SPRING + "Z",
                stored(db, sql, db.postgis() ? "readingstamp::text" : "readingstamp", dataset, 1));
        assertEquals("0012", stored(db, sql, ticketColumn, dataset, 0));
        assertStoredScalars(db, sql, dataset, ScalarFixtures.modelPaths(model), 3);
        assertStoredScalars(db, sql, dataset, ScalarFixtures.alignmentValues(), 3);
        assertEquals(UUID, stored(db, sql, "externalid", dataset, 0));
        String hex = db.postgis() ? "lower(encode(binaryvalue,'hex'))" : "lower(hex(binaryvalue))";
        assertEquals("000102ff", stored(db, sql, hex, dataset, 0));
        assertFragment(parseFragment(stored(db, sql, db.postgis() ? "xmlvalue::text" : "xmlvalue", dataset, 0)), "ascii");
        for (var column : List.of("wideno", "pricevalue", "calendarday", "readingtime", "readingstamp",
                ticketColumn, "externalid", "binaryvalue", "xmlvalue")) {
            assertNull(stored(db, sql, column, dataset, 3), column);
        }
    }

    private static void assertScalarValues(List<Element> objects, Map<String, String> values) {
        for (var entry : values.entrySet()) {
            assertEquals(entry.getValue(), child(objects.getFirst(), entry.getKey()).getTextContent());
            assertNull(child(objects.getLast(), entry.getKey()));
        }
    }

    private static void assertStoredScalars(TestDatabase db, Connection sql, String dataset,
                                            Map<String, String> values, int nullRow) throws Exception {
        for (var entry : values.entrySet()) {
            String column = entry.getKey().toLowerCase(Locale.ROOT);
            assertEquals(entry.getValue(), stored(db, sql, column, dataset, 0));
            assertNull(stored(db, sql, column, dataset, nullRow));
        }
    }

    private static void assertArray(ScalarFixtures fixture, Path path, String prefix) throws Exception {
        var objects = fixture.rows(path, prefix, 2);
        var values = fixture.decimalItems(objects.getFirst());
        assertEquals(DECIMAL_ITEMS.size(), values.size());
        for (int i = 0; i < values.size(); i++) assertNumber(DECIMAL_ITEMS.get(i), values.get(i));
        assertNull(child(objects.getLast(), "Measurements"));
    }

    private void referenceSchema(ReferenceTool reference, TestDatabase db, Path ili, String... extraOptions) throws Exception {
        var options = new java.util.ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056"));
        options.addAll(List.of(extraOptions));
        options.add(ili.toString());
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private static void referenceImport(ReferenceTool reference, TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private static void referenceExport(ReferenceTool reference, TestDatabase db, String dataset, Path output) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }

    private static void compareCatalog(TestDatabase old, TestDatabase own) throws Exception {
        try (var left = old.connect(); var right = own.connect()) {
            DbSchemaCatalog.read(left, old.postgis(), old.schema()).assertSameAs(DbSchemaCatalog.read(right, own.postgis(), own.schema()));
        }
    }
}
