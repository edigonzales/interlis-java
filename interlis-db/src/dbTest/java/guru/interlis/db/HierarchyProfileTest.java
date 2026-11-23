package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.HierarchyProfileFixtures.assertStoredDiscriminators;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HierarchyProfileTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    private enum Backend { GEOPACKAGE, POSTGIS }
    private enum Layout { NEW_CLASS, SMART2 }

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}, {2}")
    @CsvSource({
            "GEOPACKAGE,23,NEW_CLASS", "GEOPACKAGE,24,NEW_CLASS", "POSTGIS,23,NEW_CLASS", "POSTGIS,24,NEW_CLASS",
            "GEOPACKAGE,23,SMART2", "GEOPACKAGE,24,SMART2", "POSTGIS,23,SMART2", "POSTGIS,24,SMART2"
    })
    void hierarchyKeysAndDatasetMutations(Backend backend, int version, Layout layout) throws Exception {
        withDatabase(backend, db -> {
            var fixtures = new HierarchyProfileFixtures(work, version);
            String model = fixtures.hierarchyModel();
            var options = new ArrayList<>(List.of("--create-fk"));
            if (db.postgis()) options.add("--one-geom-per-table");
            if (layout == Layout.SMART2) options.addAll(List.of("--inheritance", "smart2"));
            assertSchemaWithIdentityColumns(db, model, fixtures.hierarchySource(), options.toArray(String[]::new));
            assertCliSuccess(db, "import", model, "legacy", fixtures.hierarchyData("fixture"));
            var nativeData = fixtures.hierarchyData("native");
            assertCliSuccess(db, "import", model, "native", nativeData);
            Path output = work.resolve("hierarchy-out.xtf");
            assertCliSuccess(db, "export", model, "native", output);
            fixtures.assertHierarchy(output, "native", "smart");

            try (var sql = db.connect()) {
                var keys = foreignKeyColumns(db, sql);
                assertTrue(keys.contains("marker_checkpoint:t_id:marker"), keys::toString);
                assertTrue(keys.contains("calibration:applied:marker"), keys::toString);
                assertTrue(keys.contains(layout == Layout.SMART2 ? "crosscheck:checkedinstrument_terminal:terminal"
                        : "meter:t_id:instrument"), keys::toString);
                String root = db.table(layout == Layout.SMART2 ? "smartunit" : "instrument");
                String nativeRow = " FROM " + root + " WHERE t_ili_tid='native-s'";
                String legacyRow = " FROM " + root + " WHERE t_ili_tid='fixture-s'";
                String original = scalar(sql, "SELECT t_id" + nativeRow);
                String legacyId = scalar(sql, "SELECT t_id" + legacyRow);

                Path update = Files.writeString(work.resolve("hierarchy-update.xtf"),
                        Files.readString(nativeData).replace(">smart<", ">changed<"));
                assertCliSuccess(db, "update", model, "native", update);
                assertEquals(original, scalar(sql, "SELECT t_id" + nativeRow));

                assertCliSuccess(db, "import", model, "incoming", fixtures.incomingData());
                assertCliFailure(db, "delete", model, "native", null, "referenced object");
                assertCliFailure(db, "replace", model, "native", nativeData, "referenced object");
                assertEquals(original, scalar(sql, "SELECT t_id" + nativeRow));
                assertEquals("incoming-p", scalar(sql, "SELECT t_ili_tid FROM " + db.table("pointer")));
                assertCliSuccess(db, "export", model, "native", output);
                fixtures.assertHierarchy(output, "native", "changed");

                assertCliSuccess(db, "delete", model, "incoming", null);
                assertCliSuccess(db, "replace", model, "native", nativeData);
                assertNotEquals(original, scalar(sql, "SELECT t_id" + nativeRow));
                assertCliSuccess(db, "export", model, "native", output);
                fixtures.assertHierarchy(output, "native", "smart");
                assertCliSuccess(db, "delete", model, "native", null);

                assertNull(scalar(sql, "SELECT t_id" + nativeRow));
                assertEquals(legacyId, scalar(sql, "SELECT t_id" + legacyRow));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("pointer")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("marker_checkpoint")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("calibration")));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("crosscheck")));
                assertCliSuccess(db, "export", model, "legacy", output);
                fixtures.assertHierarchy(output, "fixture", "smart");
            }
        });
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void discriminatorAndConstraintOnAddedModel(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            var fixtures = new HierarchyProfileFixtures(work, version);
            String model = fixtures.discriminatorModel();
            assertSchemaWithIdentityColumns(db, model, fixtures.discriminatorSource(),
                    "--create-type-discriminator", "--create-type-constraint");
            assertCliSuccess(db, "import", model, "native", fixtures.discriminatorData("native"));
            Path output = work.resolve("typed-out.xtf");
            assertCliSuccess(db, "export", model, "native", output);
            fixtures.assertTypedTransfer(output, "native");
            try (var sql = db.connect()) {
                assertStoredDiscriminators(db, sql, "native");
                var rejected = assertThrows(SQLException.class,
                        () -> execute(sql, "UPDATE " + db.table("asset") + " SET t_type='unknown'"));
                assertTypeCheck(db, rejected);
                assertStoredDiscriminators(db, sql, "native");

                String extra = "ProfileTypeExtra" + version;
                Path ili = new ProfileFixtures(work, version).source(extra, "CLASS Other = Label : TEXT*24; END Other;");
                assertSchemaWithIdentityColumns(db, extra, ili, "--add-models");
                String insert = "INSERT INTO " + db.table("other") + " (t_id,t_basket,t_type) VALUES (999,(SELECT t_id FROM "
                        + db.table("t_ili2db_basket") + "),";
                rejected = assertThrows(SQLException.class, () -> execute(sql, insert + "'unknown')"));
                assertTypeCheck(db, rejected);
                execute(sql, insert + "'other')");
                assertEquals("other", scalar(sql, "SELECT t_type FROM " + db.table("other")));
            }
        });
    }

    private static void assertTypeCheck(Target db, SQLException rejected) {
        if (db.postgis()) assertEquals("23514", rejected.getSQLState());
        else assertTrue(rejected.getMessage().contains("SQLITE_CONSTRAINT_CHECK"), rejected::getMessage);
    }

    private void withDatabase(Backend backend, DatabaseCase test) throws Exception {
        var file = work.resolve("hierarchy.gpkg");
        if (backend == Backend.GEOPACKAGE) {
            test.run(new Target(file, null, "hierarchy_profile"));
        } else {
            test.run(POSTGIS.target(file));
        }
    }

    @FunctionalInterface
    private interface DatabaseCase {
        void run(Target db) throws Exception;
    }
}
