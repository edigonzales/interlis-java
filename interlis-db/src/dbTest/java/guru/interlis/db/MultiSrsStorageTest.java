package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.CrsFixtures.assertHits;
import static guru.interlis.db.CrsFixtures.assertMixed;
import static guru.interlis.db.CrsFixtures.assertMixedSrs;
import static guru.interlis.db.CrsFixtures.assertStoredEpsg;
import static guru.interlis.db.CrsFixtures.assertStoredSrid;
import static guru.interlis.db.CrsFixtures.nativeLayout;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MultiSrsStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void mixedSridMutationsAndInvalidMetadata(Backend backend, int version) throws Exception {
        var fixtures = new CrsFixtures(work, version);
        String model = "MixedSrid" + version;
        for (boolean split : List.of(true, false)) {
            var db = POSTGIS.target(work.resolve(split ? "mixed-split.gpkg" : "mixed-wide.gpkg"), backend);
            var options = new ArrayList<>(nativeLayout(db, split));
            options.add("--create-geom-index");
            assertSchemaWithIdentityColumns(db, model, fixtures.fixture(model), options.toArray(String[]::new));
            seed(db, fixtures, model, "legacy");
            seed(db, fixtures, model, "own");
            if (split) rejectWrongTrafo(db, model);
            else if (!db.postgis()) assertGeoPackageMultiSrs(db, model);
            assertMixedMutations(db, model, split, fixtures);
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void renamedFixedSridColumn(Backend backend, int version) throws Exception {
        var fixtures = new CrsFixtures(work, version);
        String model = "MixedSrid" + version;
        for (boolean split : List.of(true, false)) {
            var db = POSTGIS.target(work.resolve(split ? "suffix-split.gpkg" : "suffix-wide.gpkg"), backend);
            var options = new ArrayList<>(nativeLayout(db, split));
            options.add("--multi-srs");
            assertSchemaWithIdentityColumns(db, model, fixtures.fixture(model), options.toArray(String[]::new));
            seed(db, fixtures, model, "legacy");
            try (var sql = db.connect()) {
                assertMixedSrs(db, sql, "legacy-mixed", split, true, true);
                execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN center_2056 TO renamed_center");
                execute(sql, "UPDATE " + db.table("t_ili2db_attrname")
                        + " SET sqlname='renamed_center' WHERE sqlname='center_2056'");
                execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                        + " SET columnname='renamed_center' WHERE columnname='center_2056'");
                if (!db.postgis()) {
                    execute(sql, "UPDATE gpkg_geometry_columns SET column_name='renamed_center'"
                            + " WHERE table_name='asset' AND column_name='center_2056'");
                }
                var renamed = work.resolve("renamed.xtf");
                assertCliSuccess(db, "export", model, "legacy", renamed);
                assertMixed(fixtures.object(fixtures.basket(renamed, "legacy-mixed"), "legacy-mixed"), "2600001.625", true);
                seed(db, fixtures, model, "own");
                assertStoredEpsg(db, sql, "asset", "renamed_center", "own-mixed", 2056);
            }
        }
    }

    private static void seed(Target db, CrsFixtures fixtures, String model, String dataset) throws Exception {
        String tid = dataset + "-mixed";
        var input = fixtures.delivery(model, "basket-" + tid, fixtures.mixedObject(model, tid, "2600001.625", true));
        assertCliSuccess(db, "import", model, dataset, input);
    }

    private void rejectWrongTrafo(Target db, String model) throws Exception {
        String table = db.table("t_ili2db_trafo");
        String path = model + ".Data.Asset.OldPosition:21781(" + model + ".Data.Asset)";
        String wrong = path.replace(":21781(", ":2056(");
        var output = work.resolve("wrong-trafo.xtf");
        assertCliSuccess(db, "export", model, "own", output);
        try (var sql = db.connect()) {
            assertEquals(path, scalar(sql, "SELECT iliname FROM " + table + " WHERE setting='asset_oldposition'"));
            execute(sql, "UPDATE " + table + " SET iliname='" + wrong + "' WHERE iliname='" + path + "'");
            assertExportRejected(db, model, "own", output, "secondary geometry CLASSNAME/TRAFO/ATTRNAME mapping");
            execute(sql, "UPDATE " + table + " SET iliname='" + path + "' WHERE iliname='" + wrong + "'");
        }
    }

    private void assertMixedMutations(Target db, String model, boolean split, CrsFixtures fixtures) throws Exception {
        String assets = db.table("asset");
        String baskets = db.table("t_ili2db_basket");
        String datasets = db.table("t_ili2db_dataset");
        String oldTable = split ? "asset_oldposition" : "asset";
        String geoTable = split ? "asset_geoposition" : "asset";
        String centerX = "2600001.625";
        String updatedX = "2600800.125";
        String replacementX = "2600900.125";
        Path updated = fixtures.delivery(model, "basket-own-mixed", fixtures.mixedObject(model, "own-mixed", updatedX, false));
        Path replacement = fixtures.delivery(model, "replacement-srid", fixtures.mixedObject(model, "replaced", replacementX, true));
        Path invalid = fixtures.delivery(model, "rollback-srid", fixtures.mixedObject(model, "rolled-back", replacementX, true)
                + fixtures.mixedObject(model, "wrong-srid", updatedX, true));
        Path output = work.resolve(db.schema() + "-updated-srid.xtf");
        try (var sql = db.connect()) {
            String originalId = scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'");
            String basketId = scalar(sql, "SELECT t_id FROM " + baskets + " WHERE t_ili_tid='basket-own-mixed'");
            String datasetId = scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='own'");
            rejectWrongSridProperty(db, sql, model, oldTable, updated, output);
            assertHits(db, sql, "asset", "center", centerX, "1200002.125", 2056, 2);
            assertHits(db, sql, "asset", "center", updatedX, "1200002.125", 2056, 0);

            assertCliSuccess(db, "update", model, "own", updated);
            assertEquals(originalId, scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertEquals(basketId, scalar(sql, "SELECT t_basket FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertStoredEpsg(db, sql, "asset", "center", "own-mixed", 2056);
            assertStoredSrid(db, sql, " FROM " + db.table(geoTable) + " WHERE t_id=" + originalId, "geoposition", 4326);
            assertHits(db, sql, "asset", "center", centerX, "1200002.125", 2056, 1);
            assertHits(db, sql, "asset", "center", updatedX, "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, geoTable, "geoposition", "7.125", "46.375", 4326, 2);
            assertCliSuccess(db, "export", model, "own", output);
            assertMixed(fixtures.object(fixtures.basket(output, "own-mixed"), "own-mixed"), updatedX, false);

            // Only the second new object fails the database check, after the first was written.
            if (db.postgis()) {
                execute(sql, "ALTER TABLE " + assets + " ADD CONSTRAINT test_srid_check"
                        + " CHECK (t_ili_tid <> 'wrong-srid' OR ST_SRID(center)=21781)");
            } else {
                execute(sql, """
                        CREATE TRIGGER test_srid_check BEFORE INSERT ON asset
                        WHEN NEW.t_ili_tid='wrong-srid' AND substr(hex(NEW.center),9,8) <> '00005515'
                        BEGIN SELECT RAISE(ABORT, 'test_srid_check'); END
                        """);
            }
            var failure = cli(db, "replace", model, "own", invalid, CancellationToken.none());
            assertEquals(1, failure.code(), failure::diagnostics);
            String sqlCode = db.postgis() ? "23514" : "19";
            assertEquals("Database operation failed (SQL code " + sqlCode + ")", failure.diagnostics().strip());
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + assets));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + baskets));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + datasets));
            assertEquals(originalId, scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertEquals(basketId, scalar(sql, "SELECT t_id FROM " + baskets + " WHERE t_ili_tid='basket-own-mixed'"));
            assertHits(db, sql, "asset", "center", updatedX, "1200002.125", 2056, 1);
            assertHits(db, sql, "asset", "center", replacementX, "1200002.125", 2056, 0);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, geoTable, "geoposition", "7.125", "46.375", 4326, 2);
            execute(sql, db.postgis() ? "ALTER TABLE " + assets + " DROP CONSTRAINT test_srid_check"
                    : "DROP TRIGGER test_srid_check");

            assertCliSuccess(db, "replace", model, "own", replacement);
            assertEquals(datasetId, scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='own'"));
            assertNull(scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertHits(db, sql, "asset", "center", replacementX, "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 2);
            assertCliSuccess(db, "delete", model, "own", null);
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + assets));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + datasets));
            assertHits(db, sql, "asset", "center", replacementX, "1200002.125", 2056, 0);
            assertHits(db, sql, "asset", "center", centerX, "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, geoTable, "geoposition", "7.125", "46.375", 4326, 1);
        }
    }

    private static void rejectWrongSridProperty(Target db, Connection sql, String model, String table,
                                                 Path updated, Path output) throws Exception {
        String properties = db.table("t_ili2db_column_prop");
        String where = " WHERE tablename='" + table + "' AND columnname='oldposition' AND tag='ch.ehi.ili2db.srid'";
        assertEquals("21781", scalar(sql, "SELECT setting FROM " + properties + where));
        execute(sql, "UPDATE " + properties + " SET setting='2056'" + where);
        Files.writeString(output, "previous output");
        for (String operation : List.of("export", "update")) {
            var failure = cli(db, operation, model, "own", operation.equals("export") ? output : updated,
                    CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("COLUMN_PROP.srid for " + table + ".oldposition"), failure::diagnostics);
        }
        assertEquals("previous output", Files.readString(output));
        execute(sql, "UPDATE " + properties + " SET setting='21781'" + where);
    }

    private void assertGeoPackageMultiSrs(Target db, String model) throws Exception {
        try (var sql = db.connect()) {
            assertEquals("2056", scalar(sql, "SELECT srs_id FROM gpkg_contents WHERE table_name='asset'"));
            assertEquals("2460000.0", scalar(sql, "SELECT min_x FROM gpkg_contents WHERE table_name='asset'"));
            assertHits(db, sql, "asset", "center", "2600001.625", "1200002.125", 2056, 2);
            assertHits(db, sql, "asset", "oldposition", "600100.125", "200200.375", 21781, 2);
            assertHits(db, sql, "asset", "geoposition", "7.125", "46.375", 4326, 2);
            execute(sql, "UPDATE gpkg_contents SET srs_id=4326 WHERE table_name='asset'");
            Path output = Files.writeString(work.resolve("wrong-contents-srs.xtf"), "previous output");
            var result = cli(db, "export", model, "own", output, CancellationToken.none());
            assertNotEquals(0, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("contents SRS differs from the first geometry column"), result::diagnostics);
            assertEquals("previous output", Files.readString(output));
            execute(sql, "UPDATE gpkg_contents SET srs_id=2056 WHERE table_name='asset'");
        }
    }

}
