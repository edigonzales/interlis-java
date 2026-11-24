package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @ParameterizedTest(name = "{0}, XTF {1}")
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

    @ParameterizedTest(name = "{0}, XTF {1}")
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
