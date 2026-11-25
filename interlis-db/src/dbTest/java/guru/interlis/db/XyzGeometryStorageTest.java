package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XyzGeometryStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void keepsStoredXyzWhenAReplacementHasWrongDimensions(int version, Backend backend) throws Exception {
        var fixture = new XyzGeometryFixtures(work, version);
        var db = POSTGIS.target(work.resolve("xyz-storage.gpkg"), backend);
        fixture.schema(db);
        assertCliSuccess(db, "import", fixture.model, "xyz", fixture.input());
        var output = work.resolve("xyz-storage-export.xtf");

        try (var sql = db.connect()) {
            assertStorage(db, sql, version);
            assertSpatialHits(db, sql);
            var before = storedValues(db, sql, version);

            assertCliFailure(db, "replace", fixture.model, "xyz", fixture.lineWithMissingHeight(),
                    version == 23 ? "Expected C3" : "Expected a 3D coordinate");
            assertEquals(before, storedValues(db, sql, version));
            assertSpatialHits(db, sql);

            if (version == 24) {
                assertCliFailure(db, "replace", fixture.model, "xyz", fixture.mixedCollection(), "Expected a 3D coordinate");
                assertEquals(before, storedValues(db, sql, version));
                assertSpatialHits(db, sql);
            }
        }
        assertCliSuccess(db, "export", fixture.model, "xyz", output);
        fixture.assertTransfer(output);
    }

    private static Map<String, Set<String>> storedValues(Target db, Connection sql, int version) throws SQLException {
        var columns = new LinkedHashMap<>(Map.of("marker", "aposition", "track", "trace", "plot", "outline", "parcel", "perimeter"));
        if (version == 24) columns.putAll(Map.of("survey", "locations", "network", "routes", "park", "grounds", "district", "territory"));
        var values = new LinkedHashMap<String, Set<String>>();
        values.put("dataset", catalogKeys(sql, "SELECT CAST(t_id AS text) || ':' || datasetname FROM " + db.table("t_ili2db_dataset")));
        values.put("basket", catalogKeys(sql, "SELECT CAST(t_id AS text) || ':' || CAST(dataset AS text) || ':'"
                + " || t_ili_tid || ':' || topic FROM " + db.table("t_ili2db_basket")));
        for (var entry : columns.entrySet()) {
            String column = entry.getValue();
            String bytes = db.postgis() ? "encode(ST_AsEWKB(" + column + "), 'hex')" : "hex(" + column + ")";
            values.put(entry.getKey(), catalogKeys(sql, "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text)"
                    + " || ':' || t_ili_tid || ':' || CASE WHEN " + column + " IS NULL THEN '<null>' ELSE " + bytes
                    + " END FROM " + db.table(entry.getKey())));
        }
        return values;
    }

    private static void assertStorage(Target db, Connection sql, int version) throws SQLException {
        String table = db.table("marker");
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("plot") + " WHERE outline IS NOT NULL"));
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("parcel") + " WHERE perimeter IS NOT NULL"));
        assertEquals("3", scalar(sql, "SELECT count(*) FROM " + table));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + table + " WHERE aposition IS NULL"));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("track") + " WHERE t_ili_tid='track-1'"));
        if (version == 24) {
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("survey")));
        }
        if (db.postgis()) {
            assertTrue(scalar(sql, "SELECT ST_AsEWKT(trace) FROM " + db.table("track") + " WHERE t_ili_tid='arc-track'")
                    .contains("2602380 1202360 NaN"));
            assertEquals("42.875", scalar(sql, "SELECT ST_Z(aposition) FROM " + table + " WHERE t_ili_tid='above'"));
        }
    }

    private static void assertSpatialHits(Target db, Connection sql) throws SQLException {
        String arcHit = db.postgis()
                ? "SELECT count(*) FROM " + db.table("track")
                        + " WHERE trace && ST_MakeEnvelope(2602399,1202300,2602399,1202300,2056)"
                : "SELECT count(*) FROM rtree_track_trace WHERE minx<=2602399 AND maxx>=2602399"
                        + " AND miny<=1202300 AND maxy>=1202300";
        assertEquals("1", scalar(sql, arcHit));
        if (db.postgis()) {
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("marker")
                    + " WHERE aposition && ST_MakeEnvelope(2600000,1200000,2600001,1200001,2056)"));
        } else {
            assertEquals("1", scalar(sql, "SELECT count(*) FROM rtree_marker_aposition"
                    + " WHERE minx<=2600000.125 AND maxx>=2600000.125 AND miny<=1200000.375 AND maxy>=1200000.375"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM rtree_marker_aposition"));
        }
    }

}
