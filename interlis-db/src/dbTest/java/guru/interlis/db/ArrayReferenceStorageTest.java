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
import java.util.List;

import static guru.interlis.db.ArrayReferenceFixtures.ORDER;
import static guru.interlis.db.ArrayReferenceFixtures.assertStoredDelivery;
import static guru.interlis.db.ArrayReferenceFixtures.storedReferences;
import static guru.interlis.db.ArrayReferenceFixtures.targetId;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ArrayReferenceStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void invalidReferencesAndDatasetChanges(Backend backend, int version) throws Exception {
        var fixture = new ArrayReferenceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("refs.gpkg"), backend);
        fixture.schema(db);
        assertCliSuccess(db, "import", fixture.model, "native", fixture.delivery("native-input", "native", ORDER, true));
        assertCliSuccess(db, "import", fixture.model, "other", fixture.delivery("other-input", "other", ORDER, true));
        Path output = work.resolve("published.xtf");
        assertCliSuccess(db, "export", fixture.model, "other", output);
        fixture.assertDelivery(output, "other", ORDER, true);
        byte[] previous = Files.readAllBytes(output);

        try (var sql = db.connect()) {
            assertStoredDelivery(db, sql, "native", ORDER);
            assertStoredDelivery(db, sql, "other", ORDER);
            String a = targetId(db, sql, "native-target-a");
            String b = targetId(db, sql, "native-target-b");
            assertNotNull(a);
            assertNotNull(b);
            assertCliFailure(db, "import", fixture.model, "broken",
                    fixture.delivery("broken-input", "broken", List.of("absent"), true),
                    "Cannot encode array reference to broken-target-absent");
            for (String table : List.of("asset", "target", "t_ili2db_dataset", "t_ili2db_basket")) {
                String expected = table.startsWith("t_ili2db_") ? "2" : "4";
                assertEquals(expected, scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertStoredDelivery(db, sql, "native", ORDER);
            assertStoredDelivery(db, sql, "other", ORDER);

            storeReference(db, sql, "other-missing", a);
            assertCliFailure(db, "export", fixture.model, "other", output, "Missing same-basket array target T_Id " + a);
            assertArrayEquals(previous, Files.readAllBytes(output));
            Path smaller = fixture.delivery("smaller-input", "native", List.of("b"), false);
            assertDeleteProtected(db, fixture.model, "native", smaller, a);
            assertEquals(a, targetId(db, sql, "native-target-a"));
            assertEquals(b, targetId(db, sql, "native-target-b"));
            assertStoredDelivery(db, sql, "native", ORDER);

            execute(sql, "UPDATE " + db.table("asset") + " SET links=NULL WHERE t_ili_tid='other-missing'");
            assertCliSuccess(db, "update", fixture.model, "native", smaller);
            assertEquals(List.of(b), storedReferences(db, sql, "native-value", 1));
            assertNull(targetId(db, sql, "native-target-a"));
            assertEquals(b, targetId(db, sql, "native-target-b"));
            assertCliSuccess(db, "export", fixture.model, "native", output);
            fixture.assertDelivery(output, "native", List.of("b"), false);

            assertCliSuccess(db, "delete", fixture.model, "native", null);
            assertEquals("other", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            assertEquals("other-b", scalar(sql, "SELECT t_ili_tid FROM " + db.table("t_ili2db_basket")));
            for (String table : List.of("asset", "target")) {
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertStoredDelivery(db, sql, "other", ORDER);
            assertCliSuccess(db, "export", fixture.model, "other", output);
            fixture.assertDelivery(output, "other", ORDER, true);
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void storedReferenceFromUnselectedModelProtectsTarget(Backend backend, int version) throws Exception {
        var fixture = new ArrayReferenceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("unselected.gpkg"), backend);
        fixture.schema(db);
        assertCliSuccess(db, "import", fixture.model, "source", fixture.delivery("source-input", "source", ORDER, true));

        String model = "ArrayTarget" + version;
        var ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    CLASS OtherTarget = Label : TEXT*40; END OtherTarget;
                  END Data;
                END %s.
                """.formatted(version - 20, model, model));
        assertSchemaWithIdentityColumns(db, model, ili, "--add-models");
        assertCliSuccess(db, "import", model, "target", fixture.otherTargetDelivery(model, "target-input", true));
        Path smaller = fixture.otherTargetDelivery(model, "target-smaller", false);

        try (var sql = db.connect()) {
            String targets = db.table("othertarget");
            String referenced = scalar(sql, "SELECT t_id FROM " + targets + " WHERE t_ili_tid='referenced'");
            assertNotNull(referenced);
            // Simulate a stored array mapping in a model outside the CLI selection.
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                    + " SET setting='othertarget' WHERE tablename='asset' AND columnname='links' AND tag='ch.ehi.ili2db.foreignKey'");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname")
                    + " SET target='othertarget' WHERE colowner='asset' AND sqlname='links'");
            storeReference(db, sql, "source-missing", referenced);

            assertDeleteProtected(db, model, "target", smaller, referenced);
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + targets));
            assertEquals(referenced, scalar(sql, "SELECT t_id FROM " + targets + " WHERE t_ili_tid='referenced'"));
            assertEquals("Drop", scalar(sql, "SELECT label FROM " + targets + " WHERE t_ili_tid='referenced'"));
            assertEquals("Keep", scalar(sql, "SELECT label FROM " + targets + " WHERE t_ili_tid='kept'"));
            assertEquals(List.of(referenced), storedReferences(db, sql, "source-missing", 1));
        }
    }

    private static void assertDeleteProtected(Target db, String model, String dataset, Path smaller, String target) {
        assertCliFailure(db, "update", model, dataset, smaller, "Cannot delete referenced object T_Id " + target);
        assertCliFailure(db, "replace", model, dataset, smaller, "Cannot replace referenced object T_Id " + target);
        assertCliFailure(db, "delete", model, dataset, null, "Cannot delete referenced object T_Id " + target);
    }

    private static void storeReference(Target db, Connection sql, String owner, String target) throws Exception {
        String encoded = db.postgis() ? "ARRAY[" + target + "]::bigint[]" : "'[\"" + target + "\"]'";
        execute(sql, "UPDATE " + db.table("asset") + " SET links=" + encoded + " WHERE t_ili_tid='" + owner + "'");
    }
}
