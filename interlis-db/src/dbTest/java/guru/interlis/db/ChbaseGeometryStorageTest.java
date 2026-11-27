package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static guru.interlis.db.ChbaseXyzGeometryFixtures.assertColumn;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.SpatialIndexFixtures.assertIndex;
import static guru.interlis.db.SpatialIndexFixtures.assertSearch;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseGeometryStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void collectionStorageAndAtomicExport(Backend backend, int version) throws Exception {
        var fixture = new ChbaseGeometryFixtures(work, version);
        var db = POSTGIS.target(work.resolve("chbase-geometry.gpkg"), backend);
        fixture.schema(db);
        var imported = cli(db, "import", fixture.model(), "shapes", fixture.input,
                CancellationToken.none(), fixture.extraModels());
        assertEquals(0, imported.code(), imported::diagnostics);
        Path output = work.resolve("shapes.xtf");
        var exported = cli(db, "export", fixture.model(), "shapes", output, CancellationToken.none(), fixture.extraModels());
        assertEquals(0, exported.code(), exported::diagnostics);
        fixture.assertDelivery(output, "fixture");

        String covers = db.table("landcover");
        try (var sql = db.connect()) {
            for (String table : List.of("pingroup", "pinpart", "linestructure", "directedlinestructure", "surfacestructure")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            for (String table : List.of("pinset", "piperoute", "landcover")) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table) + " WHERE shape IS NULL"), table);
            }
            if (db.postgis()) {
                assertEquals("3", scalar(sql, "SELECT ST_NumGeometries(shape) FROM " + db.table("pinset")
                        + " WHERE t_ili_tid='fixture-points-value' AND ST_SRID(shape)=2056"));
                assertEquals("2", scalar(sql, "SELECT ST_NumGeometries(shape) FROM " + db.table("piperoute")
                        + " WHERE t_ili_tid='fixture-lines-value' AND ST_HasArc(shape) AND ST_SRID(shape)=2056"));
                assertEquals("2", scalar(sql, "SELECT ST_NumGeometries(shape) FROM " + covers
                        + " WHERE t_ili_tid='fixture-surfaces-value' AND ST_HasArc(shape) AND ST_SRID(shape)=21781"));
                execute(sql, "ALTER TABLE " + covers + " ALTER COLUMN shape TYPE geometry(MultiSurface, 2056)"
                        + " USING ST_SetSRID(shape, 2056)");
            } else {
                execute(sql, "UPDATE " + covers + " SET shape=X'00' WHERE t_ili_tid='fixture-surfaces-value'");
            }
        }
        byte[] previous = Files.readAllBytes(output);
        assertCliFailure(db, "export", fixture.model(), "shapes", output,
                db.postgis() ? "Expected an EPSG:21781" : "GeoPackage", fixture.extraModels());
        assertArrayEquals(previous, Files.readAllBytes(output));
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void keepsXyzCollectionsAndOwnerIdsAcrossUpdates(Backend backend, int version) throws Exception {
        var fixture = new ChbaseXyzGeometryFixtures(work, version);
        var db = POSTGIS.target(work.resolve("chbase-xyz.gpkg"), backend);
        fixture.schema(db);
        assertCliSuccess(db, "import", fixture.model, "survey", fixture.input);

        try (var sql = db.connect()) {
            assertColumn(db, sql, "pins", "MULTIPOINT", 2056, "-999.125", "999.875");
            assertColumn(db, sql, "paths", "MULTICURVE", 2056, "-999.125", "999.875");
            assertColumn(db, sql, "tiles", "MULTISURFACE", 21781, "-888.125", "888.875");
            var before = xyzGeometryRows(db, sql);
            assertCliFailure(db, "update", fixture.model, "survey", fixture.invalidAfterValidAsset(),
                    version == 23 ? "Expected C3" : "Expected a 3D coordinate");
            assertEquals(before, xyzGeometryRows(db, sql));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("asset")));

            var pinTable = db.table("asset_pins");
            var owner = scalar(sql, "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text) FROM " + pinTable);
            assertNotNull(owner);
            assertSearch(db, sql, "asset_pins", "pins", "2600010", "1200010", true);
            assertCliSuccess(db, "update", fixture.model, "survey", fixture.withoutPins());
            var after = xyzGeometryRows(db, sql);
            for (String column : List.of("anchor", "paths", "tiles")) {
                assertEquals(before.get(column), after.get(column), column);
            }
            assertEquals(owner + ":null", after.get("pins"));
            assertSearch(db, sql, "asset_pins", "pins", "2600010", "1200010", false);
        }

        var output = work.resolve("updated-xyz.xtf");
        assertCliSuccess(db, "export", fixture.model, "survey", output);
        fixture.assertDelivery(output, "fixture", false);
    }

    private static Map<String, String> xyzGeometryRows(Target db, Connection sql) throws SQLException {
        var result = new LinkedHashMap<String, String>();
        for (String column : List.of("anchor", "pins", "paths", "tiles")) {
            String table = db.table(column.equals("anchor") ? "asset" : "asset_" + column);
            String bytes = db.postgis() ? "encode(ST_AsEWKB(" + column + "), 'hex')" : "hex(" + column + ")";
            result.put(column, scalar(sql, "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text)"
                    + " || ':' || CASE WHEN " + column + " IS NULL THEN 'null' ELSE " + bytes + " END FROM " + table));
        }
        return result;
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2MutationKeepsOwnersAndIndexesInSync(Backend backend, int version) throws Exception {
        var fixture = new ChbaseSmartFixtures(work, version);
        var db = POSTGIS.target(work.resolve("chbase-smart2.gpkg"), backend);
        fixture.schema(db);
        assertCliSuccess(db, "import", fixture.model, "survey", fixture.input);
        try (var sql = db.connect()) {
            assertIndex(db, sql, "surveyasset", "anchor");
            assertIndex(db, sql, "surveyasset_shape", "shape");
            String asset = db.table("surveyasset");
            String shape = db.table("surveyasset_shape");
            String object = scalar(sql, "SELECT t_id FROM " + asset);
            String basket = scalar(sql, "SELECT t_basket FROM " + asset);
            assertNotNull(object);
            assertNotNull(basket);
            assertEquals(basket, scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='fixture-b'"));
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + shape));
            assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + shape));
            assertSearch(db, sql, "surveyasset_shape", "shape", "2600100", "1200100", true);

            assertCliSuccess(db, "update", fixture.model, "survey", fixture.update);
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + asset));
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + shape));
            assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + asset));
            assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + shape));
            assertEquals("Neu", scalar(sql, "SELECT caption_de FROM " + asset));
            assertNull(scalar(sql, "SELECT caption_fr FROM " + asset));
            assertSearch(db, sql, "surveyasset_shape", "shape", "2600100", "1200100", false);
            assertSearch(db, sql, "surveyasset_shape", "shape", "2600300", "1200300", true);
            var output = work.resolve("updated.xtf");
            assertCliSuccess(db, "export", fixture.model, "survey", output);
            fixture.assertDelivery(output, "fixture", "Neu", null, 2600300, 1200300);

            assertCliSuccess(db, "delete", fixture.model, "survey", null);
            for (String table : List.of("surveyasset", "surveyasset_shape", "t_ili2db_basket", "t_ili2db_dataset")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertSearch(db, sql, "surveyasset_shape", "shape", "2600300", "1200300", false);
            if (!db.postgis()) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_shape_shape"));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_anchor"));
            }
        }
    }
}
