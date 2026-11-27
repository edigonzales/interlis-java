package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.Connection;

import static guru.interlis.db.CustomRowIdFixtures.KEY;
import static guru.interlis.db.CustomRowIdFixtures.SETTING;
import static guru.interlis.db.CustomRowIdFixtures.assertRelation;
import static guru.interlis.db.CustomRowIdFixtures.assertSpatialIndex;
import static guru.interlis.db.CustomRowIdFixtures.assetId;
import static guru.interlis.db.CustomRowIdFixtures.key;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomRowIdStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();
    private static final long MANUAL_ID = 100_000;
    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void customKeyLifecycle(Backend backend, int version) throws Exception {
        var fixtures = new CustomRowIdFixtures(work, version);
        var db = database(backend);
        var source = fixtures.modelFile();
        assertSchemaWithIdentityColumns(db, fixtures.model, source, db.postgis()
                ? new String[]{"--create-geom-index", "--one-geom-per-table"} : new String[]{"--create-geom-index"});
        customizePrimaryKeys(db);
        var first = fixtures.delivery("first", "first", "2601100.125", "original");
        var retained = fixtures.delivery("second", "second", "2601200.125", "retained");
        var changed = fixtures.delivery("changed", "first", "2601300.125", "updated");
        assertCliSuccess(db, "import", fixtures.model, "first", first);
        try (var sql = db.connect()) {
            String startId = assetId(db, sql, "first");
            if (!db.postgis()) {
                execute(sql, "INSERT INTO site (" + key(db) + ",t_basket,t_ili_tid,label) SELECT " + MANUAL_ID
                        + ",t_basket,'manual-site','externally allocated' FROM site WHERE t_ili_tid='first-site'");
            }
            assertCliSuccess(db, "import", fixtures.model, "second", retained);
            String retainedId = assetId(db, sql, "second");
            if (!db.postgis()) {
                assertTrue(Long.parseLong(retainedId) > MANUAL_ID, retainedId);
            }
            assertCliSuccess(db, "update", fixtures.model, "first", changed);
            assertEquals(startId, assetId(db, sql, "first"));
            assertRelation(db, sql, "first");
            assertSpatialIndex(db, sql, startId, "2601300.125");
            assertCliSuccess(db, "replace", fixtures.model, "first", changed);
            String replacedId = assetId(db, sql, "first");
            assertNotEquals(startId, replacedId);
            assertRelation(db, sql, "first");
            assertSpatialIndex(db, sql, replacedId, "2601300.125");
            assertCliSuccess(db, "delete", fixtures.model, "first", null);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE " + key(db) + "=" + replacedId));
            assertEquals(retainedId, assetId(db, sql, "second"));
            assertRelation(db, sql, "second");
            if (!db.postgis()) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_asset_center WHERE id IN (" + startId + "," + replacedId + ")"));
            }
        }
        var output = work.resolve("retained.xtf");
        assertCliSuccess(db, "export", fixtures.model, "second", output);
        fixtures.assertDelivery(output, "second", "2601200.125", "retained");
        var extension = new ProfileFixtures(work, version).source("RowIdExtension", "CLASS Spare = END Spare;");
        assertCliFailure(db, "schemaimport", "RowIdExtension", null, extension,
                "--add-models requires the standard T_Id primary key", "--add-models");
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_classname") + " WHERE sqlname='spare'"));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void rejectedKeyDefinitionsPreserveOutput(Backend backend, int version) throws Exception {
        var fixtures = new CustomRowIdFixtures(work, version);
        var db = database(backend);
        assertSchemaWithIdentityColumns(db, fixtures.model, fixtures.modelFile(), db.postgis()
                ? new String[]{"--one-geom-per-table"} : new String[]{});
        customizePrimaryKeys(db);
        var source = fixtures.delivery("source", "source", "2601100.125", "unchanged");
        assertCliSuccess(db, "import", fixtures.model, "source", source);
        var output = work.resolve("unchanged.xtf");
        assertCliSuccess(db, "export", fixtures.model, "source", output);
        try (var sql = db.connect()) {
            setCustomKey(db, sql, "datasetname");
            assertExportRejected(db, fixtures.model, "source", output, "Missing physical column t_ili2db_basket.datasetname");
            setCustomKey(db, sql, "dataset");
            assertExportRejected(db, fixtures.model, "source", output, "Expected dataset primary key in t_ili2db_basket");
            setCustomKey(db, sql, KEY);
            String dataset = db.table("t_ili2db_dataset");
            execute(sql, "ALTER TABLE " + dataset + " RENAME TO original_dataset");
            execute(sql, "CREATE TABLE " + dataset + " (" + key(db) + " INTEGER, datasetname TEXT, PRIMARY KEY ("
                    + key(db) + ",datasetname))");
            execute(sql, "INSERT INTO " + dataset + " SELECT * FROM " + db.table("original_dataset"));
            assertExportRejected(db, fixtures.model, "source", output,
                    "Expected " + CustomRowIdFixtures.physicalKey(db) + " primary key in t_ili2db_dataset");
        }
    }

    private Target database(Backend backend) {
        return POSTGIS.target(work.resolve("custom-key.gpkg"), backend);
    }

    private static void customizePrimaryKeys(Target db) throws Exception {
        try (var sql = db.connect()) {
            for (String table : new String[]{"asset", "asset_checkpoint", "site", "memo", "t_ili2db_dataset", "t_ili2db_basket"}) {
                execute(sql, "ALTER TABLE " + db.table(table) + " RENAME COLUMN t_id TO " + key(db));
            }
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop") + " SET columnname='" + KEY
                    + "' WHERE tablename='asset_checkpoint' AND lower(columnname)='t_id'");
            execute(sql, "INSERT INTO " + db.table("t_ili2db_settings") + " (tag,setting) VALUES ('" + SETTING + "','" + KEY + "')");
        }
    }

    private static void setCustomKey(Target db, Connection sql, String name) throws Exception {
        execute(sql, "UPDATE " + db.table("t_ili2db_settings") + " SET setting='" + name + "' WHERE tag='" + SETTING + "'");
    }
}
