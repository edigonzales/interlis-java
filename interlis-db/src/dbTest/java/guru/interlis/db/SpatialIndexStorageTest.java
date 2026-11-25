package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.SpatialIndexFixtures.assertCollectionOrder;
import static guru.interlis.db.SpatialIndexFixtures.assertGeoPackageHeader;
import static guru.interlis.db.SpatialIndexFixtures.assertIndex;
import static guru.interlis.db.SpatialIndexFixtures.assertSearch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpatialIndexStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void pointUpdatesAndRollback(Backend backend, int version) throws Exception {
        points(POSTGIS.target(work.resolve("points.gpkg"), backend), new SpatialIndexFixtures(work, version), version);
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void straightAndCurvedEnvelopes(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("geometries.gpkg"), backend);
        var fixtures = new SpatialIndexFixtures(work, version);
        String model = "DbSpatial" + version;
        createSchema(db, fixtures, model);
        var straight = fixtures.delivery("straight", model, "straight", fixtures.straight(model));
        var curves = fixtures.delivery("curves", model, "curves", fixtures.curves(model));
        assertCliSuccess(db, "import", model, "straight", straight);
        assertCliSuccess(db, "import", model, "curves", curves);
        try (var sql = db.connect()) {
            assertSearch(db, sql, "indexedline", "trace", "2601005", "1201005", true);
            assertSearch(db, sql, "indexedplot", "footprint", "2601062", "1201062", true);
            assertSearch(db, sql, "indexedarea", "boundary", "2601110", "1201110", true);
            assertSearch(db, sql, "indexedline", "trace", "2602399", "1202300", true);
            assertSearch(db, sql, "indexedplot", "footprint", "2602399", "1202300", true);
            assertSearch(db, sql, "indexedline", "trace", "2602405", "1202300", false);
            assertGeoPackageHeader(db, sql, "indexedline", "trace");
            assertGeoPackageHeader(db, sql, "indexedplot", "footprint");
            assertGeoPackageHeader(db, sql, "indexedarea", "boundary");
            var changed = fixtures.delivery("move-line", model, "straight", fixtures.straight(model)
                    .replace("2601000.125", "2601200.125").replace("2601020.250", "2601220.250"));
            assertCliSuccess(db, "update", model, "straight", changed);
            assertSearch(db, sql, "indexedline", "trace", "2601005", "1201005", false);
            assertSearch(db, sql, "indexedline", "trace", "2601205", "1201005", true);
            assertCliSuccess(db, "replace", model, "curves", curves);
            assertSearch(db, sql, "indexedline", "trace", "2602399", "1202300", true);
            assertCliSuccess(db, "delete", model, "curves", null);
            assertSearch(db, sql, "indexedline", "trace", "2602399", "1202300", false);
            assertSearch(db, sql, "indexedline", "trace", "2601205", "1201005", true);
        }
    }

    @ParameterizedTest(name = "{0}, XTF 24")
    @EnumSource(Backend.class)
    void collectionIndexesAndNulls(Backend backend) throws Exception {
        collections(POSTGIS.target(work.resolve("collections.gpkg"), backend), new SpatialIndexFixtures(work, 24));
    }

    private static void createSchema(Target db, SpatialIndexFixtures fixtures, String model) throws Exception {
        assertSchemaWithIdentityColumns(db, model, fixtures.copy(model + ".ili"), "--create-geom-index");
    }

    private void points(Target db, SpatialIndexFixtures fixtures, int version) throws Exception {
        String model = "DbMutGeom" + version;
        createSchema(db, fixtures, model);
        Path start = fixtures.delivery("indexed-points-original", model, "points",
                fixtures.feature(model, "GeoLeaf", "moved", "2600100.125")
                + fixtures.feature(model, "GeoLeaf", "cleared", "2600200.250")
                + fixtures.feature(model, "GeoLeaf", "dropped", "2600300.375"));
        Path update = fixtures.delivery("indexed-points-update", model, "points",
                fixtures.feature(model, "GeoLeaf", "moved", "2600500.625") + fixtures.feature(model, "GeoLeaf", "cleared", null));
        Path replacement = fixtures.delivery("indexed-points-replace", model, "points",
                fixtures.feature(model, "GeoLeaf", "moved", "2600600.750"));

        assertCliSuccess(db, "import", model, "points", start);
        try (var sql = db.connect()) {
            assertIndex(db, sql, "georoot", "center");
            String object = scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'");
            assertPointHit(db, sql, "moved", "2600100.125", true);

            assertCliSuccess(db, "update", model, "points", update);
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'"));
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600500.625", true);
            Path exported = work.resolve("updated-points.xtf");
            assertCliSuccess(db, "export", model, "points", exported);
            assertPointXtf(exported, version, "moved", "2600500.625");
            assertPointHit(db, sql, "moved", "2600100.125", false);
            assertPointHit(db, sql, "cleared", "2600200.250", false);
            assertPointHit(db, sql, "dropped", "2600300.375", false);

            Path malformed = Files.writeString(work.resolve("indexed-points-rollback.xtf"),
                    Files.readString(replacement) + "<trailing/>");
            assertNotEquals(0, cli(db, "replace", model, "points", malformed, CancellationToken.none()).code());
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600500.625", true);
            assertPointHit(db, sql, "moved", "2600600.750", false);
            assertCliSuccess(db, "replace", model, "points", replacement);
            assertNotEquals(object, scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'"));
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600600.750", true);
            assertPointHit(db, sql, "moved", "2600500.625", false);

            if (!db.postgis()) {
                String trigger = scalar(sql, "SELECT sql FROM sqlite_schema WHERE name='rtree_georoot_center_update2'");
                execute(sql, "DROP TRIGGER rtree_georoot_center_update2");
                var failed = cli(db, "update", model, "points", update, CancellationToken.none());
                assertNotEquals(0, failed.code(), failed::diagnostics);
                assertTrue(failed.diagnostics().contains("missing index maintenance trigger"), failed::diagnostics);
                assertPointHit(db, sql, "moved", "2600600.750", true);
                execute(sql, trigger);
            }

            assertCliSuccess(db, "delete", model, "points", null);
            assertIndexRows(db, sql, 0);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("georoot")));
        }
    }

    private void collections(Target db, SpatialIndexFixtures fixtures) throws Exception {
        String model = "BaseMapping24";
        createSchema(db, fixtures, model);
        Path input = fixtures.copy("BaseMapping24.xtf");
        assertCliSuccess(db, "import", model, "collections", input);
        try (var sql = db.connect()) {
            assertIndex(db, sql, "survey", "locations");
            assertIndex(db, sql, "routegroup", "traces");
            assertIndex(db, sql, "park", "coverage");
            assertIndex(db, sql, "district", "coverage");
            assertCollectionHits(db, sql, true);
            for (String table : List.of("park", "district", "routegroup", "survey")) {
                String field = switch (table) {
                    case "routegroup" -> "traces";
                    case "survey" -> "locations";
                    default -> "coverage";
                };
                // These classes also have absent values in another basket.
                if (!db.postgis()) {
                    assertEquals("02", scalar(sql, "SELECT hex(substr(" + field + ",4,1)) FROM " + table
                            + " WHERE " + field + " IS NOT NULL"));
                }
            }
            Path nativeOutput = work.resolve("indexed-collections-native.xtf");
            assertCliSuccess(db, "export", model, "collections", nativeOutput);
            assertCollectionOrder(nativeOutput);

            String surveyId = scalar(sql, "SELECT t_id FROM " + db.table("survey") + " WHERE t_ili_tid='legacy-survey-1'");
            Path clear = fixtures.delivery("indexed-collections-clear", model, "legacy-basket-1",
                    fixtures.object(model, "Survey", "legacy-survey-1", "") + fixtures.object(model, "RouteGroup", "legacy-routegroup-1", "")
                    + fixtures.object(model, "Park", "legacy-park-1", "") + fixtures.object(model, "District", "legacy-district-1", ""));
            assertCliSuccess(db, "update", model, "collections", clear);
            assertEquals(surveyId, scalar(sql, "SELECT t_id FROM " + db.table("survey")
                    + " WHERE t_ili_tid='legacy-survey-1' AND locations IS NULL"));
            assertCollectionHits(db, sql, false);
            assertCliSuccess(db, "delete", model, "collections", null);

            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("survey")));
        }
    }

    private static void assertCollectionHits(Target db, Connection sql, boolean present) throws SQLException {
        assertSearch(db, sql, "survey", "locations", "2601500.625", "1201500.500", present);
        assertSearch(db, sql, "survey", "locations", "2601100.125", "1201100.750", present);
        assertSearch(db, sql, "routegroup", "traces", "2600620.500", "1200600.250", present);
        assertSearch(db, sql, "routegroup", "traces", "2600123.125", "1200456.375", present);
        assertSearch(db, sql, "park", "coverage", "2601001", "1201001", present);
        assertSearch(db, sql, "park", "coverage", "2601210.250", "1201192", present);
        assertSearch(db, sql, "district", "coverage", "2601210.250", "1201192", present);
    }

    private static void assertPointXtf(Path path, int version, String tid, String x) throws Exception {
        var objects = children(children(readDataSection(path, version)).getFirst());
        var point = objects.stream().filter(o -> tid.equals(id(o, version, "tid"))).findFirst().orElseThrow();
        var actual = children(children(child(point, "Center")).getFirst()).getFirst().getTextContent();
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(actual)));
    }

    private static void assertIndexRows(Target db, Connection sql, int expected) throws SQLException {
        String query = db.postgis() ? "SELECT count(*) FROM " + db.table("georoot") + " WHERE center IS NOT NULL"
                : "SELECT count(*) FROM rtree_georoot_center";
        assertEquals(Integer.toString(expected), scalar(sql, query));
    }

    private static void assertPointHit(Target db, Connection sql, String tid, String x, boolean expected) throws SQLException {
        String table = db.table("georoot");
        String query = db.postgis() ? "SELECT count(*) FROM " + table + " WHERE t_ili_tid='" + tid
                + "' AND center && ST_MakeEnvelope(" + x + ",1200000," + x + ",1200001,2056)"
                : "SELECT count(*) FROM " + table + " p JOIN rtree_georoot_center idx ON p.t_id=idx.id"
                + " WHERE p.t_ili_tid='" + tid + "' AND idx.minx <= " + x + " AND idx.maxx >= " + x
                + " AND idx.miny <= 1200000.375 AND idx.maxy >= 1200000.375";
        assertEquals(expected ? "1" : "0", scalar(sql, query));
    }
}
