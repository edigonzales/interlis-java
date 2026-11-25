package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ScalarFixtures.assertNumber;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmartClassStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void scalarStorageAndDiscriminator(Backend backend, int version) throws Exception {
        var fixtures = new SmartFixtures(work, version);
        String model = "SmartMeter" + version;
        Path ili = fixtures.fixture(model + ".ili");
        Path input = fixtures.fixture(model + ".xtf");
        for (String layout : List.of("smart1", "smart2")) {
            var db = POSTGIS.target(work.resolve(layout + ".gpkg"), backend);
            assertSchemaWithIdentityColumns(db, model, ili, "--inheritance", layout);
            boolean smart2 = layout.equals("smart2");
            if (smart2) renameMemo(db, model);
            Path output = roundTrip(db, model, input, layout);
            fixtures.assertScalarObjects(output, model, "fixture", true);
            try (var sql = db.connect()) {
                SmartFixtures.assertScalarStorage(db, sql, model, layout, smart2 ? "stored_memo" : "memo", 1);
                if (!smart2) {
                    execute(sql, "UPDATE " + db.table("meter") + " SET t_type='bogus' WHERE t_ili_tid='fixture-s'");
                    assertExportRejected(db, model, "fixture", output, "Unknown or abstract T_Type bogus");
                }
            }
        }
    }

    private static void renameMemo(Target db, String model) throws Exception {
        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + db.table("smartunit") + " RENAME COLUMN memo TO stored_memo");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_memo'"
                    + " WHERE colowner='smartunit' AND iliname='" + model + ".Data.Instrument.Memo'");
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop") + " SET columnname='stored_memo'"
                    + " WHERE tablename='smartunit' AND columnname='memo'");
            try (var columns = sql.getMetaData().getColumns(null, db.schema(), "smartunit", "%")) {
                var names = new HashSet<String>();
                while (columns.next()) names.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                assertTrue(names.contains("stored_memo"));
                assertFalse(names.contains("t_type"));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void rejectsWrongTypeAndMultipleTargets(Backend backend, int version) throws Exception {
        var fixtures = new SmartFixtures(work, version);
        String smart1 = "SmartRefs" + version;
        var shared = POSTGIS.target(work.resolve("smart1.gpkg"), backend);
        assertSchemaWithIdentityColumns(shared, smart1, fixtures.fixture(smart1 + ".ili"),
                "--inheritance", "smart1", "--create-num-checks");
        Path input = fixtures.fixture(smart1 + ".xtf");
        Path output = roundTrip(shared, smart1, input, "smart1");
        fixtures.assertReferences(output, smart1, "fixture");
        fixtures.assertNumericOverrides(output, smart1, "fixture");

        Path bad = Files.writeString(work.resolve("wrong-target.xtf"), Files.readString(input)
                .replace("fixture-", "bad-").replace("ref=\"bad-s\"", "ref=\"bad-t\"").replace("REF=\"bad-s\"", "REF=\"bad-t\""));
        assertCliFailure(shared, "import", smart1, "bad", bad, "Cannot encode reference from bad-survey");
        try (var sql = shared.connect()) {
            SmartFixtures.assertReferenceStorage(shared, sql, "smart1", 1);
            for (var table : List.of("survey", "t_ili2db_dataset", "t_ili2db_basket")) {
                assertCount(shared, sql, table, "1");
            }
            assertCount(shared, sql, "instrument", "3");
            String instrument = shared.table("instrument");
            assertEquals("meter", scalar(sql, "select t_type from " + instrument + " where t_ili_tid='fixture-m'"));
            assertEquals("smartunit", scalar(sql, "select t_type from " + instrument + " where t_ili_tid='fixture-s'"));
            assertStoredNumbers(sql, instrument, "fixture-m", "75", "45000000000", "12.50");
            assertStoredNumbers(sql, instrument, "fixture-s", "7", "35000000000", "-1.25");
            assertNumericCheck(shared, sql, instrument, "fixture-s", "counter", "100", "101");
            assertNumericCheck(shared, sql, instrument, "fixture-s", "widevalue", "50000000000", "50000000001");
            assertNumericCheck(shared, sql, instrument, "fixture-s", "tilt", "90.00", "90.01");
            String regional = shared.table("regionalreading");
            assertEquals("localreading", scalar(sql, "select t_type from " + regional
                    + " where t_ili_tid='fixture-local-reading'"));
            assertNumericCheck(shared, sql, regional, "fixture-local-reading", "numericvalue", "79", "80");
            assertEquals("93", scalar(sql, "select numericvalue from " + shared.table("sparereading")
                    + " where t_ili_tid='fixture-spare-reading'"));
        }

        String smart2 = "SmartPoly" + version;
        var concrete = POSTGIS.target(work.resolve("smart2.gpkg"), backend);
        assertSchemaWithIdentityColumns(concrete, smart2, fixtures.fixture(smart2 + ".ili"),
                "--inheritance", "smart2", "--create-num-checks");
        output = roundTrip(concrete, smart2, fixtures.fixture(smart2 + ".xtf"), "smart2");
        fixtures.assertPolymorphicReferences(output, smart2, "fixture");
        fixtures.assertNumericOverrides(output, smart2, "fixture");
        try (var sql = concrete.connect()) {
            SmartFixtures.assertReferenceStorage(concrete, sql, "smart2", 1);
            String meter = concrete.table("meter");
            String smartUnit = concrete.table("smartunit");
            assertStoredNumbers(sql, meter, "fixture-m", "75", "45000000000", "12.50");
            assertStoredNumbers(sql, smartUnit, "fixture-s", "7", "35000000000", "-1.25");
            assertNumericCheck(concrete, sql, meter, "fixture-m", "counter", "100", "101");
            assertNumericCheck(concrete, sql, smartUnit, "fixture-s", "counter", "49", "50");
            assertNumericCheck(concrete, sql, smartUnit, "fixture-s", "widevalue", "40000000000", "40000000001");
            assertNumericCheck(concrete, sql, smartUnit, "fixture-s", "tilt", "9.50", "9.51");
            execute(sql, "UPDATE " + concrete.table("survey") + " SET observed_terminal=(SELECT t_id FROM "
                    + concrete.table("terminal") + " WHERE t_ili_tid='fixture-t') WHERE t_ili_tid='fixture-survey'");
        }
        assertExportRejected(concrete, smart2, "fixture", output, "Multiple stored values for " + smart2 + ".Data.Observation.Observed");
    }

    private static void assertStoredNumbers(Connection sql, String table, String tid, String counter, String wideValue,
                                            String tilt) throws SQLException {
        String row = " from " + table + " where t_ili_tid='" + tid + "'";
        assertEquals(counter, scalar(sql, "select counter" + row), tid);
        assertEquals(wideValue, scalar(sql, "select widevalue" + row), tid);
        assertNumber(tilt, scalar(sql, "select tilt" + row));
    }

    private static void assertNumericCheck(Target db, Connection sql, String table, String tid, String column,
                                           String maximum, String outside) throws SQLException {
        String row = " where t_ili_tid='" + tid + "'";
        String update = "update " + table + " set " + column + "=";
        sql.setAutoCommit(false);
        try {
            execute(sql, update + maximum + row);
            assertNumber(maximum, scalar(sql, "select " + column + " from " + table + row));
            var rejected = assertThrows(SQLException.class, () -> execute(sql, update + outside + row));
            if (db.postgis) {
                assertEquals("23514", rejected.getSQLState(), rejected::getMessage);
            } else {
                assertTrue(rejected.getMessage().contains("SQLITE_CONSTRAINT_CHECK"), rejected::getMessage);
            }
            assertTrue(rejected.getMessage().contains(column), rejected::getMessage);
        } finally {
            sql.rollback();
            sql.setAutoCommit(true);
        }
    }

    private static void assertCount(Target db, Connection sql, String table, String count) throws Exception {
        assertEquals(count, scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
    }

    private Path roundTrip(Target db, String model, Path input, String layout) {
        Path output = work.resolve(layout + "-out.xtf");
        assertCliSuccess(db, "import", model, "fixture", input);
        assertCliSuccess(db, "export", model, "fixture", output);
        return output;
    }
}
