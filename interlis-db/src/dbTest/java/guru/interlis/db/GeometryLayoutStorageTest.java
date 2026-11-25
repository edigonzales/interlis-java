package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.SpatialIndexFixtures.assertIndex;
import static guru.interlis.db.SpatialIndexFixtures.assertSearch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GeometryLayoutStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void updatesGeometryColumnsAndTheirIndexes(int version, Backend backend) throws Exception {
        var fixture = new GeometryLayoutFixtures(work, version);
        var db = POSTGIS.target(work.resolve("geometry-columns.gpkg"), backend);
        fixture.schema(db, false);
        assertCliSuccess(db, "import", fixture.model, "assets", fixture.assetInput());
        var update = fixture.assetUpdate();
        try (var sql = db.connect()) {
            for (var field : List.of("anchor", "surveyed", "trace")) assertIndex(db, sql, "asset", field);
            var before = ids(db, sql, "asset", "a1", "assets", "layout-basket");
            assertSearch(db, sql, "asset", "anchor", "2600100.125", "1200200.375", true);
            assertSearch(db, sql, "asset", "anchor", "2600400.500", "1200200.375", false);
            if (db.postgis()) {
                assertEquals("3", scalar(sql, "SELECT count(*) FROM pg_indexes WHERE schemaname='" + db.schema()
                        + "' AND tablename='asset' AND indexdef LIKE '%USING gist%'"));
            } else {
                assertEquals("3", scalar(sql, "SELECT count(*) FROM gpkg_geometry_columns WHERE table_name='asset'"));
                assertEquals("2460000.125", scalar(sql, "SELECT min_x FROM gpkg_contents WHERE table_name='asset'"));
                assertEquals("1", rows(db, sql, "rtree_asset_anchor"));
                assertEquals("2", rows(db, sql, "rtree_asset_surveyed"));
                assertEquals("1", rows(db, sql, "rtree_asset_trace"));

                String trigger = scalar(sql, "SELECT sql FROM sqlite_schema WHERE name='rtree_asset_surveyed_update2'");
                assertNotNull(trigger);
                execute(sql, "DROP TRIGGER rtree_asset_surveyed_update2");
                assertCliFailure(db, "update", fixture.model, "assets", update, "asset.surveyed: missing index maintenance trigger");
                assertEquals(before, ids(db, sql, "asset", "a1", "assets", "layout-basket"));
                assertEquals("2", rows(db, sql, "asset"));
                assertEquals("1", rows(db, sql, "rtree_asset_anchor"));
                assertEquals("2", rows(db, sql, "rtree_asset_surveyed"));
                assertEquals("1", rows(db, sql, "rtree_asset_trace"));
                assertSearch(db, sql, "asset", "anchor", "2600100.125", "1200200.375", true);
                execute(sql, trigger);
                var output = work.resolve("assets-after-trigger-failure.xtf");
                assertCliSuccess(db, "export", fixture.model, "assets", output);
                fixture.assertAssetDelivery(output, false);
            }
            assertCliSuccess(db, "update", fixture.model, "assets", update);
            assertEquals(before, ids(db, sql, "asset", "a1", "assets", "layout-basket"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE surveyed IS NULL"));
            assertSearch(db, sql, "asset", "anchor", "2600100.125", "1200200.375", false);
            assertSearch(db, sql, "asset", "anchor", "2600400.500", "1200200.375", true);
            assertSearch(db, sql, "asset", "trace", "2600600.125", "1200600.375", true);
            if (!db.postgis()) {
                assertEquals("0", rows(db, sql, "rtree_asset_surveyed"));
                assertEquals("1", rows(db, sql, "rtree_asset_trace"));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void replacesAndDeletesOptionalGeometryFragments(int version, Backend backend) throws Exception {
        var fixture = new GeometryLayoutFixtures(work, version);
        var db = POSTGIS.target(work.resolve("geometry-fragments.gpkg"), backend);
        fixture.schema(db, true);
        assertCliSuccess(db, "import", fixture.model, "assets", fixture.assetInput());
        var update = fixture.assetUpdate();
        var output = work.resolve("geometry-assets-export.xtf");
        try (var sql = db.connect()) {
            assertIndex(db, sql, "asset_trace", "trace");
            assertIndex(db, sql, "asset_surveyed", "surveyed");
            var before = ids(db, sql, "asset", "a1", "assets", "layout-basket");
            var trace = db.table("asset_trace");
            var surveyed = db.table("asset_surveyed");
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + trace + " WHERE trace IS NULL"));
            assertEquals("2", rows(db, sql, "asset_trace"));
            assertEquals("2", rows(db, sql, "asset_surveyed"));
            assertCliSuccess(db, "update", fixture.model, "assets", update);
            assertEquals(before, ids(db, sql, "asset", "a1", "assets", "layout-basket"));
            assertEquals(before.basket(), scalar(sql, "SELECT t_basket FROM " + trace + " WHERE t_id=" + before.object()));
            assertEquals(before.basket(), scalar(sql, "SELECT t_basket FROM " + surveyed + " WHERE t_id=" + before.object()));
            assertEquals("1", rows(db, sql, "asset_trace"));
            assertEquals("1", rows(db, sql, "asset_surveyed"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + surveyed + " WHERE surveyed IS NULL"));
            assertCliSuccess(db, "export", fixture.model, "assets", output);
            fixture.assertAssetDelivery(output, true);

            if (!db.postgis()) GeoPackageRtree.register(sql);
            execute(sql, "UPDATE " + trace + " SET t_basket=-1 WHERE t_id=" + before.object());
            assertExportRejected(db, fixture.model, "assets", output, "Secondary geometry belongs to a different basket");
            execute(sql, "UPDATE " + trace + " SET t_basket=" + before.basket() + " WHERE t_id=" + before.object());

            assertCliSuccess(db, "replace", fixture.model, "assets", update);
            var replaced = ids(db, sql, "asset", "a1", "assets", "layout-basket");
            assertEquals(before.dataset(), replaced.dataset());
            assertNotEquals(before.object(), replaced.object());
            assertNotEquals(before.basket(), replaced.basket());
            assertEquals("1", rows(db, sql, "asset_trace"));
            assertEquals(replaced.basket(), scalar(sql, "SELECT t_basket FROM " + trace + " WHERE t_id=" + replaced.object()));
            assertCliSuccess(db, "delete", fixture.model, "assets", null);
            for (var table : List.of("asset", "asset_trace", "asset_surveyed")) assertEquals("0", rows(db, sql, table), table);
            if (!db.postgis()) {
                assertEquals("0", rows(db, sql, "rtree_asset_trace_trace"));
                assertEquals("0", rows(db, sql, "rtree_asset_surveyed_surveyed"));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void keepsHierarchyAndHelperRowsConsistentAcrossMutations(int version, Backend backend) throws Exception {
        var fixture = new GeometryLayoutFixtures(work, version);
        var input = fixture.hierarchyInput();
        var update = fixture.hierarchyUpdate();
        var independent = fixture.independentInput();
        for (boolean split : List.of(false, true)) {
            var db = POSTGIS.target(work.resolve("geometry-hierarchy-" + split + ".gpkg"), backend);
            fixture.schema(db, split);
            assertCliSuccess(db, "import", fixture.model, "hierarchy", input);
            assertCliSuccess(db, "import", fixture.model, "independent", independent);
            var output = work.resolve("geometry-hierarchy-" + split + "-export.xtf");
            try (var sql = db.connect()) {
                String trackTable = split ? "recorder_track" : "recorder";
                assertIndex(db, sql, trackTable, "track");
                var before = ids(db, sql, "device", "rec", "hierarchy", "hierarchy-basket");
                var other = ids(db, sql, "device", "other", "independent", "other-basket");
                assertTrackOwner(db, sql, trackTable, before);
                assertSearch(db, sql, trackTable, "track", "2601000.125", "1201000.375", true);

                assertCliSuccess(db, "update", fixture.model, "hierarchy", update);
                assertEquals(before, ids(db, sql, "device", "rec", "hierarchy", "hierarchy-basket"));
                assertEquals("1", rows(db, sql, "beacon"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("recorder") + " WHERE peg IS NULL"));
                assertSearch(db, sql, trackTable, "track", "2601000.125", "1201000.375", false);
                assertSearch(db, sql, trackTable, "track", "2601400.125", "1201000.375", true);
                assertTrackOwner(db, sql, trackTable, before);

                if (split) assertForeignBasketRejected(db, sql, fixture, before, other, update);

                assertCliSuccess(db, "replace", fixture.model, "hierarchy", update);
                var replaced = ids(db, sql, "device", "rec", "hierarchy", "hierarchy-basket");
                assertEquals(before.dataset(), replaced.dataset());
                assertNotEquals(before.object(), replaced.object());
                assertNotEquals(before.basket(), replaced.basket());
                assertTrackOwner(db, sql, trackTable, replaced);
                assertCliSuccess(db, "export", fixture.model, "hierarchy", output);
                fixture.assertHierarchyDelivery(output, true);

                assertCliSuccess(db, "delete", fixture.model, "hierarchy", null);
                assertEquals("0", rows(db, sql, trackTable));
                assertSearch(db, sql, trackTable, "track", "2601400.125", "1201000.375", false);
                assertEquals("other", scalar(sql, "SELECT t_ili_tid FROM " + db.table("device")));
                assertEquals("1", rows(db, sql, "beacon"));
                assertEquals(other, ids(db, sql, "device", "other", "independent", "other-basket"));
                assertCliSuccess(db, "export", fixture.model, "independent", output);
                fixture.assertIndependentDelivery(output);
            }
        }
    }

    private void assertForeignBasketRejected(Target db, Connection sql, GeometryLayoutFixtures fixture,
                                             Ids owner, Ids other, Path update) throws Exception {
        var output = work.resolve("hierarchy-before-corruption.xtf");
        assertCliSuccess(db, "export", fixture.model, "hierarchy", output);
        fixture.assertHierarchyDelivery(output, true);
        if (!db.postgis()) GeoPackageRtree.register(sql);
        String track = db.table("recorder_track");
        execute(sql, "UPDATE " + track + " SET t_basket=" + other.basket() + " WHERE t_id=" + owner.object());
        assertExportRejected(db, fixture.model, "hierarchy", output, "Secondary geometry belongs to a different basket");
        for (var dataset : List.of("hierarchy", "independent")) {
            assertCliFailure(db, "delete", fixture.model, dataset, null, "basket-mismatched secondary geometry");
        }
        assertCliFailure(db, "replace", fixture.model, "hierarchy", update, "basket-mismatched secondary geometry");
        assertEquals("2", rows(db, sql, "device"));
        assertEquals(owner, ids(db, sql, "device", "rec", "hierarchy", "hierarchy-basket"));
        assertEquals(other, ids(db, sql, "device", "other", "independent", "other-basket"));
        assertEquals(other.basket(), scalar(sql, "SELECT t_basket FROM " + track + " WHERE t_id=" + owner.object()));
        assertSearch(db, sql, "recorder_track", "track", "2601400.125", "1201000.375", true);
        execute(sql, "UPDATE " + track + " SET t_basket=" + owner.basket() + " WHERE t_id=" + owner.object());
        assertCliSuccess(db, "export", fixture.model, "hierarchy", output);
        fixture.assertHierarchyDelivery(output, true);
    }

    private static void assertTrackOwner(Target db, Connection sql, String trackTable, Ids owner) throws Exception {
        assertEquals("recorder", scalar(sql, "SELECT t_type FROM " + db.table("device") + " WHERE t_id=" + owner.object()));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(trackTable) + " WHERE t_id=" + owner.object()
                + " AND t_basket=" + owner.basket() + " AND track IS NOT NULL"));
    }

    private record Ids(String object, String basket, String dataset) { }

    private static Ids ids(Target db, Connection sql, String table, String tid, String dataset, String bid) throws Exception {
        var datasetId = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='" + dataset + "'");
        assertNotNull(datasetId);
        var basketId = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='" + bid + "' AND dataset=" + datasetId);
        assertNotNull(basketId);
        var objectId = scalar(sql, "SELECT t_id FROM " + db.table(table) + " WHERE t_ili_tid='" + tid + "' AND t_basket=" + basketId);
        assertNotNull(objectId);
        return new Ids(objectId, basketId, datasetId);
    }

    private static String rows(Target db, Connection sql, String table) throws Exception {
        return scalar(sql, "SELECT count(*) FROM " + db.table(table));
    }
}
