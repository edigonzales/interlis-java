package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.SmartFixtures.assertGeometryStorage;
import static guru.interlis.db.SmartFixtures.assertStructureStorage;

final class SmartPayloadStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void structureOwnersAndSequences(Backend backend, int version) throws Exception {
        var fixture = new SmartFixtures(work, version);
        String model = "SmartStructures" + version;
        Path ili = fixture.fixture(model + ".ili");
        Path data = fixture.fixture(model + ".xtf");
        for (String layout : List.of("smart1", "smart2")) {
            var db = POSTGIS.target(work.resolve(layout + ".gpkg"), backend);
            assertSchemaWithIdentityColumns(db, model, ili, "--inheritance", layout);
            assertCliSuccess(db, "import", model, "structures", data);
            Path output = work.resolve(layout + "-structures.xtf");
            assertCliSuccess(db, "export", model, "structures", output);
            fixture.assertStructures(output, model, "fixture");
            try (var sql = db.connect()) {
                assertStructureStorage(db, sql, layout, 1);
                boolean shared = layout.equals("smart1");
                String owner = db.table(shared ? "survey" : "detailedsurvey");
                String secondOwner = shared ? "survey_details" : "detailedsurvey_notes";
                execute(sql, "UPDATE " + db.table("note") + " SET " + secondOwner + "=(SELECT t_id FROM " + owner
                        + " WHERE t_ili_tid='fixture-d') WHERE survey_notes=(SELECT t_id FROM " + db.table("survey")
                        + " WHERE t_ili_tid='fixture-s') AND t_seq=0");
            }
            assertExportRejected(db, model, "structures", output, "Expected exactly one structure owner");
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void geometryOwnersAndMissingSecondaryRows(Backend backend, int version) throws Exception {
        var fixture = new SmartFixtures(work, version);
        String model = "SmartGeometry" + version;
        Path ili = fixture.fixture(model + ".ili");
        Path data = fixture.fixture(model + ".xtf");
        for (String layout : List.of("smart1", "smart2")) {
            var db = POSTGIS.target(work.resolve(layout + ".gpkg"), backend);
            assertSchemaWithIdentityColumns(db, model, ili, "--inheritance", layout);
            assertCliSuccess(db, "import", model, "geometry", data);
            Path output = work.resolve(layout + "-geometry.xtf");
            assertCliSuccess(db, "export", model, "geometry", output);
            fixture.assertGeometries(output, model, "fixture");
            try (var sql = db.connect()) {
                assertGeometryStorage(db, sql, layout, 1);
                if (!db.postgis()) {
                    boolean shared = layout.equals("smart1");
                    String parent = db.table(shared ? "meter" : "smartunit");
                    String helper = db.table(shared ? "meter_extralocation" : "smartunit_location");
                    execute(sql, "DELETE FROM " + helper + " WHERE t_id=(SELECT t_id FROM " + parent
                            + " WHERE t_ili_tid='fixture-s')");
                    assertExportRejected(db, model, "geometry", output, "Missing secondary geometry row");
                }
            }
        }
    }
}
