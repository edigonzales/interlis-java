package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.SpatialIndexFixtures.assertSearch;
import static guru.interlis.db.StructurePointFixtures.ORIGINAL;
import static guru.interlis.db.StructurePointFixtures.REJECTED;
import static guru.interlis.db.StructurePointFixtures.UPDATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class StructurePointStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0} XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void updatesAndRollsBackIndexedStructurePoints(PostgisTestServer.Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("points.gpkg"), backend);
        var fixture = new StructurePointFixtures(work, version);
        fixture.schema(db);
        var input = fixture.input("delivery", ORIGINAL);
        assertCliSuccess(db, "import", fixture.model, "points", input);
        String owner;
        try (var sql = db.connect()) {
            fixture.assertRows(db, sql, ORIGINAL);
            owner = scalar(sql, "SELECT t_id FROM " + db.table("item"));
            assertSearch(db, sql, "tag", "center", "2600001.375", "1200002.625", true);
        }

        assertCliSuccess(db, "update", fixture.model, "points", fixture.input("delivery", UPDATED));
        try (var sql = db.connect()) {
            fixture.assertRows(db, sql, UPDATED);
            assertEquals(owner, scalar(sql, "SELECT t_id FROM " + db.table("item")));
            assertSearch(db, sql, "tag", "center", "2600001.375", "1200002.625", false);
            assertSearch(db, sql, "tag", "center", "2600008.125", "1200009.375", true);
        }

        Path broken = fixture.input("delivery", REJECTED);
        Files.writeString(broken, Files.readString(broken) + "<broken/>");
        assertCliFailure(db, "update", fixture.model, "points", broken, "XML");
        try (var sql = db.connect()) {
            fixture.assertRows(db, sql, UPDATED);
            assertEquals(owner, scalar(sql, "SELECT t_id FROM " + db.table("item")));
            assertSearch(db, sql, "tag", "center", "2600008.125", "1200009.375", true);
            assertSearch(db, sql, "tag", "center", "2600012.125", "1200014.375", false);
        }
    }

    @ParameterizedTest(name = "{0} XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void rejectsMultiplePointsInOneStructureBeforePublishingSchema(PostgisTestServer.Backend backend, int version)
            throws Exception {
        String model = "MultipleStructurePoints" + version;
        Path source = new ProfileFixtures(work, version).source(model, """
                DOMAIN LocalGrid = COORD 2480000.000 .. 2840000.000, 1070000.000 .. 1300000.000;
                STRUCTURE Tag = Center : LocalGrid; Origin : LocalGrid; END Tag;
                CLASS Item = Marker : Tag; END Item;
                """);
        var db = POSTGIS.target(work.resolve("multiple-points.gpkg"), backend);
        assertCliFailure(db, "schemaimport", model, null, source, "multiple geometries in a structure with a point");
        if (db.postgis()) {
            try (var sql = db.connect()) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM information_schema.schemata WHERE schema_name = '"
                        + db.schema() + "'"));
            }
        } else {
            assertFalse(Files.exists(db.file));
        }
    }
}
