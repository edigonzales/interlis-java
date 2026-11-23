package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.indexColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.ProfileFixtures.LINKED_INDEXES;
import static guru.interlis.db.ProfileFixtures.LINKED_KEYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForeignKeyProfileTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    private enum Backend { GEOPACKAGE, POSTGIS }

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void deferredForeignKeysAndImportRollback(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            var fixtures = new ProfileFixtures(work, version);
            String model = "ProfileFk" + version;
            schema(db, model, fixtures.linkedModel(model), "--create-fk");
            assertCliSuccess(db, "import", model, "native", fixtures.linkedData(model, "native"));
            var output = work.resolve("native-out.xtf");
            assertCliSuccess(db, "export", model, "native", output);
            fixtures.assertLink(fixtures.rows(output, "native", 2).getFirst(), "native");

            try (var sql = db.connect()) {
                assertEquals(LINKED_KEYS, foreignKeyColumns(db, sql));
                if (!db.postgis()) execute(sql, "PRAGMA foreign_keys=ON");
                sql.setAutoCommit(false);
                execute(sql, "UPDATE " + db.table("asset") + " SET refasset=-123 WHERE t_ili_tid='native-asset'");
                var rejected = assertThrows(SQLException.class, sql::commit);
                if (db.postgis()) assertEquals("23503", rejected.getSQLState());
                else assertTrue(rejected.getMessage().contains("SQLITE_CONSTRAINT_FOREIGNKEY"), rejected::getMessage);
                sql.rollback();
                sql.setAutoCommit(true);
                assertEquals("native-target", scalar(sql, "SELECT t.t_ili_tid FROM " + db.table("asset")
                        + " a JOIN " + db.table("target") + " t ON t.t_id=a.refasset WHERE a.t_ili_tid='native-asset'"));
                assertEquals("present", scalar(sql, "SELECT n.message FROM " + db.table("note")
                        + " n JOIN " + db.table("asset") + " a ON a.t_id=n.asset_notes WHERE a.t_ili_tid='native-asset'"));
            }

            var invalid = fixtures.data(model, "broken",
                    fixtures.object(model, "Target", "broken-valid", "<Label>first</Label>")
                            + fixtures.object(model, "Asset", "broken-asset", fixtures.reference("RefAsset", "missing")));
            assertCliFailure(db, "import", model, "broken", invalid, "Cannot encode reference from broken-asset");
            try (var sql = db.connect()) {
                assertEquals("native-asset", scalar(sql, "SELECT t_ili_tid FROM " + db.table("asset")));
                assertEquals("native-target", scalar(sql, "SELECT t_ili_tid FROM " + db.table("target")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("note")));
                assertEquals("native-basket", scalar(sql, "SELECT t_ili_tid FROM " + db.table("t_ili2db_basket")));
                assertEquals("native", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            }
            String more = "ProfileFkMore" + version;
            schema(db, more, fixtures.source(more, "CLASS Extra = Label : TEXT*12; END Extra;"), "--add-models");
            try (var sql = db.connect()) {
                var keys = new HashSet<>(LINKED_KEYS);
                keys.add("extra:t_basket:t_ili2db_basket");
                assertEquals(keys, foreignKeyColumns(db, sql));
            }
        });
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void indexesWithoutForeignKeys(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            var fixtures = new ProfileFixtures(work, version);
            String model = "ProfileFkIndex" + version;
            schema(db, model, fixtures.linkedModel(model), "--create-fk-idx");
            assertCliSuccess(db, "import", model, "indexed", fixtures.linkedData(model, "indexed"));
            var output = work.resolve("indexed-out.xtf");
            assertCliSuccess(db, "export", model, "indexed", output);
            fixtures.assertLink(fixtures.rows(output, "indexed", 2).getFirst(), "indexed");
            try (var sql = db.connect()) {
                assertEquals(LINKED_INDEXES, indexColumns(db, sql));
                assertEquals(Set.of(), foreignKeyColumns(db, sql));
                String more = "ProfileFkMore" + version;
                schema(db, more, fixtures.source(more, "CLASS Extra = Label : TEXT*12; END Extra;"), "--add-models");
                var indexes = new HashSet<>(LINKED_INDEXES);
                indexes.add("extra:t_basket");
                assertEquals(indexes, indexColumns(db, sql));
                assertEquals(Set.of(), foreignKeyColumns(db, sql));
            }
        });
    }

    private static void schema(Target db, String model, Path ili, String option) {
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), option);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private void withDatabase(Backend backend, DatabaseCase test) throws Exception {
        var file = work.resolve("keys.gpkg");
        if (backend == Backend.GEOPACKAGE) {
            test.run(new Target(file, null, "foreign_keys"));
        } else {
            try (var db = POSTGIS.target(file)) {
                test.run(db);
            }
        }
    }

    @FunctionalInterface
    private interface DatabaseCase {
        void run(Target db) throws Exception;
    }
}
