package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.SpatialIndexFixtures.assertIndex;
import static guru.interlis.db.SpatialIndexFixtures.assertSearch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrokeSmart2StorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void inheritedGeometryKeepsIdsAndIndexEntriesInSync(int version, Backend backend) throws Exception {
        var fixture = new StrokeSmart2Fixtures(work, version);
        var db = POSTGIS.target(work.resolve("stroke-smart2.gpkg"), backend);
        fixture.schema(db);
        var input = fixture.delivery("native", "native", 600000, "first");
        assertCliSuccess(db, "import", fixture.model, "native", input);
        try (var sql = db.connect()) {
            assertIndex(db, sql, "surveyasset", "anchor");
            assertIndex(db, sql, "surveyasset_shape", "shape");
            String asset = db.table("surveyasset");
            String shape = db.table("surveyasset_shape");
            String basket = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='native-b'");
            String object = scalar(sql, "SELECT t_id FROM " + asset + " WHERE t_ili_tid='native-value'");
            assertNotNull(basket);
            assertNotNull(object);
            assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + asset));
            assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + shape));
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + shape));
            assertSearch(db, sql, "surveyasset", "anchor", "600000", "200000", true);
            assertStrokeSearch(db, sql, 600005, true);

            var update = fixture.delivery("update", "native", 600100, "updated");
            assertCliSuccess(db, "update", fixture.model, "native", update);
            assertEquals(basket, scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='native-b'"));
            assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + asset));
            assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + shape));
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + asset));
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + shape));
            assertSearch(db, sql, "surveyasset", "anchor", "600000", "200000", false);
            assertSearch(db, sql, "surveyasset", "anchor", "600100", "200000", true);
            assertStrokeSearch(db, sql, 600005, false);
            assertStrokeSearch(db, sql, 600105, true);

            assertCliSuccess(db, "delete", fixture.model, "native", null);
            for (var table : List.of("surveyasset", "surveyasset_shape", "t_ili2db_basket", "t_ili2db_dataset")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertSearch(db, sql, "surveyasset", "anchor", "600100", "200000", false);
            assertStrokeSearch(db, sql, 600105, false);
            if (!db.postgis()) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_anchor"));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_shape_shape"));
            }
        }
    }

    private static void assertStrokeSearch(Target db, Connection sql, int x, boolean expected) throws Exception {
        if (!db.postgis()) {
            assertEquals(expected ? "1" : "0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_shape_shape"
                    + " WHERE minx <= " + x + " AND maxx >= " + x + " AND miny <= 199995 AND maxy >= 199994"));
            return;
        }
        String query = "SELECT count(*) FROM " + db.table("surveyasset_shape")
                + " WHERE shape && ST_MakeEnvelope(" + (x - 1) + ",199994," + (x + 1) + ",199995,2056)";
        assertEquals(expected ? "1" : "0", scalar(sql, query));
        var index = scalar(sql, "SELECT indexname FROM pg_indexes WHERE schemaname='" + db.schema()
                + "' AND tablename='surveyasset_shape' AND indexdef LIKE '%USING gist%' AND indexdef LIKE '%(shape)%'");
        assertNotNull(index);
        try (var statement = sql.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            try (var rows = statement.executeQuery("EXPLAIN " + query)) {
                var plan = new ArrayList<String>();
                while (rows.next()) plan.add(rows.getString(1));
                assertTrue(plan.stream().anyMatch(line -> line.contains(" using " + index + " ") || line.endsWith(" on " + index)),
                        () -> String.join("\n", plan));
            }
        }
    }
}
