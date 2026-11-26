package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.logText;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScalarStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void correctsRawScalarsAndRollsBackMalformedXml(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.copyModel(model), "--sql-cols-as-text");
        String raw = field("Active", "maybe") + field("SmallNo", "abc") + field("CalendarDay", "bad date");
        Path input = fixture.data(model, "raw", List.of(raw));
        assertCliFailure(db, "import", model, "raw", input, "Expected XTF BOOLEAN");
        assertCliSuccess(db, "import", model, "raw", input, "--raw-scalar-text");
        Path output = Files.writeString(work.resolve("corrected.xtf"), "previous export");
        assertCliFailure(db, "export", model, "raw", output, "Active of object raw-0");
        assertEquals("previous export", Files.readString(output));

        Path broken = fixture.data(model, "broken", List.of(raw, field("SmallNo", "<Nested/>")));
        assertCliFailure(db, "import", model, "broken", broken, "Expected text without child elements", "--raw-scalar-text");
        try (var sql = db.connect()) {
            assertOnlyDataset(db, sql, 1);
            assertEquals("maybe", stored(db, sql, "aactive", "raw", 0));
            assertEquals("abc", stored(db, sql, "smallno", "raw", 0));
            assertEquals("bad date", stored(db, sql, "calendarday", "raw", 0));
            execute(sql, "UPDATE " + db.table("asset") + " SET aactive='true', smallno='13', calendarday='2024-02-29'");
        }
        assertCliSuccess(db, "export", model, "raw", output);
        var row = fixture.rows(output, "raw", 1).getFirst();
        assertEquals("true", child(row, "Active").getTextContent());
        assertEquals("13", child(row, "SmallNo").getTextContent());
        assertEquals("2024-02-29", child(row, "CalendarDay").getTextContent());
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void modelExtensionCannotChangeScalarStorage(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.copyModel(model));
        var input = fixture.data(model, "typed", List.of(field("SmallNo", "13")));
        assertCliSuccess(db, "import", model, "typed", input);
        Path addition = Files.writeString(work.resolve("ExtraModel.ili"), """
                INTERLIS 2.%d;
                MODEL ExtraModel (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data = CLASS Extra = Label : TEXT; END Extra; END Data;
                END ExtraModel.
                """.formatted(version - 20));
        assertCliFailure(db, "schemaimport", "ExtraModel", null, addition,
                "conflict with the existing ili2db SETTINGS", "--add-models", "--sql-cols-as-text");
        try (var sql = db.connect()) {
            assertNull(scalar(sql, "SELECT max(sqlname) FROM " + db.table("t_ili2db_classname")
                    + " WHERE iliname='ExtraModel.Data.Extra'"));
            assertNull(scalar(sql, "SELECT max(setting) FROM " + db.table("t_ili2db_settings")
                    + " WHERE tag='ch.ehi.ili2db.SqlColsAsText'"));
            assertEquals("13", stored(db, sql, "smallno", "typed", 0));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void numericPrecisionAndLateOverflow(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.copyModel(model));
        var numbers = List.of("9007199254740993", "-9223372036854775808", "9223372036854775807");
        var fields = new ArrayList<>(numbers.stream().map(value -> field("WideNo", value)).toList());
        fields.add(field("PriceValue", HIGH_DECIMAL));
        Path output = roundTrip(db, fixture, model, "native", fields);
        var objects = fixture.rows(output, "native", 4);
        String decimal = db.postgis() ? HIGH_DECIMAL : BigDecimal.valueOf(Double.parseDouble(HIGH_DECIMAL)).toPlainString();
        try (var sql = db.connect()) {
            for (int i = 0; i < numbers.size(); i++) {
                assertEquals(numbers.get(i), stored(db, sql, "wideno", "native", i));
                assertEquals(numbers.get(i), child(objects.get(i), "WideNo").getTextContent());
            }
            assertNull(stored(db, sql, "wideno", "native", 3));
            assertNumber(decimal, child(objects.getLast(), "PriceValue").getTextContent());
            assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                    List.of(fields.getFirst(), field("WideNo", "9223372036854775808"))), "64-bit integer encoding");
            assertOnlyDataset(db, sql, 4);
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void localTemporalValuesAcrossJvmTimeZones(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.copyModel(model));
        var input = fixture.data(model, "zoned", List.of(
                field("CalendarDay", "2024-02-29") + field("ReadingTime", "00:00:00.000") + field("ReadingStamp", GAP),
                field("CalendarDay", "1999-12-31") + field("ReadingTime", "23:59:58.125") + field("ReadingStamp", SPRING), ""));
        assertCliInZone(db, "import", model, "zoned", input, "America/New_York");
        Path output = work.resolve("zoned-out.xtf");
        assertCliInZone(db, "export", model, "zoned", output, "Pacific/Auckland");
        var values = fixture.rows(output, "zoned", 3);
        assertEquals("2024-02-29", child(values.get(0), "CalendarDay").getTextContent());
        assertEquals("1999-12-31", child(values.get(1), "CalendarDay").getTextContent());
        assertEquals("00:00:00.000", child(values.get(0), "ReadingTime").getTextContent());
        assertEquals("23:59:58.125", child(values.get(1), "ReadingTime").getTextContent());
        assertEquals(GAP, child(values.get(0), "ReadingStamp").getTextContent());
        assertEquals(SPRING, child(values.get(1), "ReadingStamp").getTextContent());
        assertNull(child(values.get(2), "ReadingTime"));
        assertNull(child(values.get(2), "ReadingStamp"));
        try (var sql = db.connect()) {
            assertEquals("2024-02-29", stored(db, sql, "calendarday", "zoned", 0));
            assertEquals("23:59:58.125", stored(db, sql, "readingtime", "zoned", 1));
            String stamp = db.postgis() ? "readingstamp::text" : "readingstamp";
            assertEquals(db.postgis() ? GAP.replace('T', ' ') : GAP + "Z", stored(db, sql, stamp, "zoned", 0));
            assertEquals(db.postgis() ? SPRING.replace('T', ' ') : SPRING + "Z", stored(db, sql, stamp, "zoned", 1));
            assertNull(stored(db, sql, "calendarday", "zoned", 2));
            assertNull(stored(db, sql, "readingtime", "zoned", 2));
            assertNull(stored(db, sql, stamp, "zoned", 2));
            execute(sql, "UPDATE " + db.table("asset") + " SET calendarday = "
                    + (db.postgis() ? "'infinity'::date" : "'2024-02-30'") + " WHERE t_ili_tid = 'zoned-0'");
        }
        assertExportRejected(db, model, "zoned", output, "Invalid SQL date");
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void storedNameAndUuidAttributes(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.copyModel(model));
        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN ticketcode TO stored_ticket");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_ticket' WHERE sqlname='ticketcode'");
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                    + " SET columnname='stored_ticket' WHERE columnname='ticketcode'");
            String first = field("TicketCode", "0012") + field("RestrictedDay", "2024-02-29")
                    + field("NameCode", "A_1") + field("UriCode", URI.replace("&", "&amp;"))
                    + fixture.uuidField(UUID.toUpperCase(Locale.ROOT));
            Path output = roundTrip(db, fixture, model, "native", List.of(first, fixture.uuidField(UUID), ""));
            var objects = fixture.rows(output, "native", 3);
            assertEquals("0012", child(objects.getFirst(), "TicketCode").getTextContent());
            assertEquals("2024-02-29", child(objects.getFirst(), "RestrictedDay").getTextContent());
            assertEquals("A_1", child(objects.getFirst(), "NameCode").getTextContent());
            assertEquals(URI, child(objects.getFirst(), "UriCode").getTextContent());
            assertEquals("0012", stored(db, sql, "stored_ticket", "native", 0));
            assertNull(stored(db, sql, "stored_ticket", "native", 1));
            assertEquals(UUID, fixture.uuidValue(objects.get(0)));
            assertEquals(UUID, fixture.uuidValue(objects.get(1)));
            assertEquals(UUID, stored(db, sql, "externalid", "native", 0));
            assertNull(stored(db, sql, "externalid", "native", 2));
            assertCliFailure(db, "import", model, "broken",
                    fixture.data(model, "broken", List.of(first, fixture.uuidField("not-a-uuid"))), "Invalid UUID");
            assertOnlyDataset(db, sql, 3);
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void blackboxValuesAndAtomicExport(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarMapping" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.copyModel(model));
        String xml = fixture.xmlField("<p:sample p:code=\"x\">Grüsse</p:sample>")
                .replace("<XmlValue>", "<XmlValue xmlns:p=\"urn:scalar\">");
        Path output = roundTrip(db, fixture, model, "native", List.of(
                fixture.binaryField("AAEC/w==") + xml, fixture.binaryField(""), ""));
        var objects = fixture.rows(output, "native", 3);
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 0xff}, fixture.binaryValue(objects.get(0)));
        assertArrayEquals(new byte[0], fixture.binaryValue(objects.get(1)));
        assertNull(child(objects.get(2), "BinaryValue"));
        assertNull(child(objects.get(2), "XmlValue"));
        assertFragment(fixture.xmlFragment(objects.getFirst(), false), "Grüsse");
        try (var sql = db.connect()) {
            String hex = db.postgis() ? "lower(encode(binaryvalue,'hex'))" : "lower(hex(binaryvalue))";
            assertEquals("000102ff", stored(db, sql, hex, "native", 0));
            assertEquals("0", stored(db, sql, db.postgis() ? "octet_length(binaryvalue)" : "length(binaryvalue)", "native", 1));
            assertNull(stored(db, sql, "binaryvalue", "native", 2));
            assertFragment(parseFragment(stored(db, sql, db.postgis() ? "xmlvalue::text" : "xmlvalue", "native", 0)), "Grüsse");
            assertNull(stored(db, sql, "xmlvalue", "native", 2));

            String asset = db.table("asset");
            String largeBinary = db.postgis() ? "decode(repeat('00', 786433), 'hex')" : "zeroblob(786433)";
            execute(sql, "UPDATE " + asset + " SET binaryvalue=" + largeBinary + " WHERE t_ili_tid='native-0'");
            assertExportRejected(db, model, "native", output, "Oversized BLACKBOX BINARY");
            String largeXml = db.postgis() ? "repeat('x', 1048577)::xml" : "printf('%01048577d', 0)";
            execute(sql, "UPDATE " + asset + " SET binaryvalue=NULL, xmlvalue=" + largeXml + " WHERE t_ili_tid='native-0'");
            assertExportRejected(db, model, "native", output, "Oversized BLACKBOX XML");
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void decimalArrayPrecisionAndRollback(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixture = new ScalarFixtures(work, version);
        String model = "ScalarDecimalArray" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.copyModel(model), "--coalesce-array");
        Path output = roundTrip(db, fixture, model, "native",
                List.of(fixture.decimalItems(model, DECIMAL_ITEMS), "", fixture.decimalItems(model, List.of(HIGH_DECIMAL))));
        var values = fixture.rows(output, "native", 3);
        assertEquals(List.of(HIGH_DECIMAL), fixture.decimalItems(values.getLast()));
        assertNull(child(values.get(1), "Measurements"));
        try (var sql = db.connect()) {
            assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                    List.of(fixture.decimalItems(model, DECIMAL_ITEMS), fixture.decimalItems(model, List.of("invalid")))),
                    version == 23 ? "Invalid XTF decimal value" : "Invalid XTF numeric lexeme");
            assertOnlyDataset(db, sql, 3);
        }
    }

    private Target target(Backend backend) {
        return POSTGIS.target(work.resolve("scalars.gpkg"), backend);
    }

    private Path roundTrip(Target db, ScalarFixtures fixture, String model, String dataset, List<String> fields)
            throws Exception {
        Path output = work.resolve("out.xtf");
        assertCliSuccess(db, "import", model, dataset, fixture.data(model, dataset, fields));
        assertCliSuccess(db, "export", model, dataset, output);
        return output;
    }

    private static void assertOnlyDataset(Target db, java.sql.Connection sql, int objects) throws Exception {
        assertEquals(Integer.toString(objects), scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
    }

    private static void assertCliInZone(Target db, String operation, String model, String dataset, Path path, String zone)
            throws Exception {
        String modules = Main.class.getModule().getLayer().configuration().modules().stream()
                .flatMap(module -> module.reference().location().stream())
                .filter(uri -> "file".equals(uri.getScheme())).map(uri -> Path.of(uri).toString()).sorted()
                .collect(Collectors.joining(System.getProperty("path.separator")));
        var java = Path.of(System.getProperty("java.home"), "bin", "java");
        var command = new ArrayList<>(List.of(java.toString(), "-Duser.timezone=" + zone,
                "--enable-native-access=org.xerial.sqlitejdbc", "--module-path", modules,
                "--module", "guru.interlis.db/guru.interlis.db.Main",
                operation, "--model", model, "--dataset", dataset));
        command.addAll(db.nativeOptions());
        command.add(path.toString());
        Path log = Files.createTempFile(path.getParent(), "native-zone-", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("INTERLIS_DB_PASSWORD", db.password);
        var process = builder.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> "Native CLI timed out in " + zone);
            assertEquals(0, process.exitValue(), () -> logText(log, db.password));
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }
}
