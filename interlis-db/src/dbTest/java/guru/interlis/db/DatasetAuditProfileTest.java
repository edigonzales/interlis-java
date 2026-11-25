package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.indexColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ProfileFixtures.AUDIT_TABLES;
import static guru.interlis.db.ProfileFixtures.DATASET_INDEXES;
import static guru.interlis.db.ProfileFixtures.DATASET_TABLES;
import static guru.interlis.db.ProfileFixtures.assertDatasetColumns;
import static guru.interlis.db.ProfileFixtures.assertInitialAuditColumns;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatasetAuditProfileTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    private enum Backend { GEOPACKAGE, POSTGIS }

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void datasetColumnsOnImportUpdateAndReplace(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            var fixtures = new ProfileFixtures(work, version);
            String model = "ProfileDataset" + version;
            assertSchemaWithIdentityColumns(db, model, fixtures.linkedModel(model), "--create-dataset-col");
            assertCliSuccess(db, "import", model, "legacy", fixtures.linkedData(model, "legacy"));
            Path nativeInput = fixtures.linkedData(model, "native");
            assertCliSuccess(db, "import", model, "native", nativeInput);

            try (var sql = db.connect()) {
                assertEquals(DATASET_INDEXES, indexColumns(db, sql));
                for (String table : DATASET_TABLES) {
                    try (var query = sql.createStatement();
                         var rows = query.executeQuery("SELECT * FROM " + db.table(table) + " WHERE 1=0")) {
                        ResultSetMetaData metadata = rows.getMetaData();
                        var names = new HashSet<String>();
                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            names.add(metadata.getColumnName(i).toLowerCase(Locale.ROOT));
                        }
                        assertTrue(names.contains("t_datasetname"), names::toString);
                        assertTrue(Collections.disjoint(names, Set.of("t_createdate", "t_lastchange", "t_user")),
                                names::toString);
                    }
                }
                assertDatasetColumns(db, sql, DATASET_TABLES);
                String legacy = " FROM " + db.table("asset") + " WHERE t_datasetname='legacy'";
                String nativeRow = " FROM " + db.table("asset") + " WHERE t_datasetname='native'";
                String legacyId = scalar(sql, "SELECT t_id" + legacy);
                String nativeId = scalar(sql, "SELECT t_id" + nativeRow);

                Path changed = changedInput(nativeInput);
                assertCliSuccess(db, "update", model, "native", changed);
                assertEquals(nativeId, scalar(sql, "SELECT t_id" + nativeRow));
                assertEquals("changed", scalar(sql, "SELECT label" + nativeRow));
                assertDatasetColumns(db, sql, DATASET_TABLES);
                assertEquals(legacyId, scalar(sql, "SELECT t_id" + legacy));
                assertEquals("linked", scalar(sql, "SELECT label" + legacy));

                assertCliSuccess(db, "replace", model, "native", nativeInput);
                assertNotEquals(nativeId, scalar(sql, "SELECT t_id" + nativeRow));
                assertEquals("linked", scalar(sql, "SELECT label" + nativeRow));
                assertDatasetColumns(db, sql, DATASET_TABLES);
                assertEquals(legacyId, scalar(sql, "SELECT t_id" + legacy));
                assertEquals("linked", scalar(sql, "SELECT label" + legacy));

                String extra = "ProfileDatasetExtra" + version;
                assertSchemaWithIdentityColumns(db, extra, fixtures.source(extra,
                        "CLASS Extra = Label : TEXT*24; END Extra;"), "--add-models");
                var indexes = new HashSet<>(DATASET_INDEXES);
                indexes.add("extra:t_datasetname");
                assertEquals(indexes, indexColumns(db, sql));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void auditColumnsOnImportUpdateAndReplace(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            var fixtures = new ProfileFixtures(work, version);
            String model = "ProfileAudit" + version;
            var options = new ArrayList<>(List.of("--create-std-cols", "--create-dataset-col"));
            if (db.postgis()) options.add("--one-geom-per-table");
            assertSchemaWithIdentityColumns(db, model, fixtures.auditModel(model), options.toArray(String[]::new));
            assertCliSuccess(db, "import", model, "legacy", fixtures.auditData(model, "legacy"));
            Path nativeInput = fixtures.auditData(model, "native");
            assertCliSuccess(db, "import", model, "native", nativeInput);

            try (var sql = db.connect()) {
                assertDatasetColumns(db, sql, AUDIT_TABLES);
                assertInitialAuditColumns(db, sql);
                var legacy = auditRows(db, sql, "legacy");
                String old = db.postgis() ? "TIMESTAMP '2000-02-03 00:00:00'" : "949536000000";
                String user = db.postgis() ? sql.getMetaData().getUserName() : System.getProperty("user.name");
                for (String table : List.of("asset", "asset_checkpoint")) {
                    execute(sql, "UPDATE " + db.table(table) + " SET t_createdate=" + old + ", t_lastchange=" + old
                            + ", t_user='previous' WHERE t_datasetname='native'");
                }
                String nativeRow = " FROM " + db.table("asset") + " WHERE t_datasetname='native'";
                String nativeId = scalar(sql, "SELECT t_id" + nativeRow);
                assertCliSuccess(db, "update", model, "native", changedInput(nativeInput));
                assertEquals(nativeId, scalar(sql, "SELECT t_id" + nativeRow));
                assertEquals("changed", scalar(sql, "SELECT label" + nativeRow));
                for (String table : List.of("asset", "asset_checkpoint")) {
                    assertEquals(nativeId, scalar(sql, "SELECT t_id FROM " + db.table(table) + " WHERE t_datasetname='native'"));
                    assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)
                            + " WHERE t_datasetname='native' AND t_createdate=" + old + " AND t_lastchange>" + old
                            + " AND t_user='" + user.replace("'", "''") + "'"));
                }
                assertDatasetColumns(db, sql, AUDIT_TABLES);
                assertEquals(legacy, auditRows(db, sql, "legacy"));

                assertCliSuccess(db, "replace", model, "native", nativeInput);
                assertNotEquals(nativeId, scalar(sql, "SELECT t_id" + nativeRow));
                assertEquals("linked", scalar(sql, "SELECT label" + nativeRow));
                assertDatasetColumns(db, sql, AUDIT_TABLES);
                assertInitialAuditColumns(db, sql);
                assertEquals(legacy, auditRows(db, sql, "legacy"));

                String extra = "ProfileAuditExtra" + version;
                assertSchemaWithIdentityColumns(db, extra, fixtures.source(extra,
                        "CLASS Extra = Label : TEXT*24; END Extra;"), "--add-models");
                var rejected = assertThrows(SQLException.class, () -> execute(sql, "INSERT INTO " + db.table("extra")
                        + " (t_id,t_basket,t_datasetname) VALUES (999,(SELECT min(t_id) FROM "
                        + db.table("t_ili2db_basket") + "),'native')"));
                if (db.postgis()) assertEquals("23502", rejected.getSQLState());
                else assertTrue(rejected.getMessage().contains("SQLITE_CONSTRAINT_NOTNULL"), rejected::getMessage);
            }
        });
    }

    private Path changedInput(Path input) throws IOException {
        return Files.writeString(work.resolve("changed.xtf"), Files.readString(input).replace(">linked<", ">changed<"));
    }

    private static Map<String, Set<String>> auditRows(Target db, Connection sql, String dataset) throws SQLException {
        var result = new LinkedHashMap<String, Set<String>>();
        for (String table : AUDIT_TABLES) {
            result.put(table, catalogKeys(sql, "SELECT t_id || ':' || t_createdate || ':' || t_lastchange || ':' || t_user FROM "
                    + db.table(table) + " WHERE t_datasetname='" + dataset + "'"));
        }
        return Map.copyOf(result);
    }

    private void withDatabase(Backend backend, DatabaseCase test) throws Exception {
        var file = work.resolve("audit.gpkg");
        if (backend == Backend.GEOPACKAGE) {
            test.run(new Target(file, null, "dataset_audit"));
        } else {
            test.run(POSTGIS.target(file));
        }
    }

    @FunctionalInterface
    private interface DatabaseCase {
        void run(Target db) throws Exception;
    }
}
