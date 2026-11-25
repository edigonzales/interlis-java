package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.StrokeFixtures.assertGeometryColumn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrokeGeometryStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void renamedLineColumnAndWrongGeometryMetadata(int version, Backend backend) throws Exception {
        var fixture = new StrokeGeometryFixtures(work, version);
        var db = POSTGIS.target(work.resolve("renamed.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--stroke-arcs");
        var prop = db.table("t_ili2db_column_prop");
        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + db.table("straight") + " RENAME COLUMN shape TO stored_shape");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_shape' WHERE colowner='straight' AND sqlname='shape'");
            execute(sql, "UPDATE " + prop + " SET columnname='stored_shape' WHERE tablename='straight' AND columnname='shape'");
            if (!db.postgis()) {
                execute(sql, "UPDATE gpkg_geometry_columns SET column_name='stored_shape' WHERE table_name='straight'");
            }
        }
        assertGeometryColumn(db, "straight", "stored_shape", "LINESTRING", 2056);
        assertCliSuccess(db, "import", fixture.model, "native", fixture.delivery("native"));
        var output = work.resolve("native-export.xtf");
        assertCliSuccess(db, "export", fixture.model, "native", output);
        fixture.assertDelivery(output, "native");
        try (var sql = db.connect()) {
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("straight") + " WHERE stored_shape IS NULL"));
            execute(sql, "UPDATE " + prop + " SET setting='COMPOUNDCURVE' WHERE tablename='straight'"
                    + " AND columnname='stored_shape' AND tag='ch.ehi.ili2db.geomType'");
        }
        assertExportRejected(db, fixture.model, "native", output, "COLUMN_PROP.geomType for straight.stored_shape");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void chordToleranceAndCoordinateLimitRollback(int version, Backend backend) throws Exception {
        var xml = new StrokeFixtures(work, version);
        var db = POSTGIS.target(work.resolve("arcs.gpkg"), backend);
        String model = "StrokeArc" + version;
        String fine = "Fine" + version;
        var source = xml.source(model, "POLYLINE");
        var fineSource = xml.source(fine, "POLYLINE", "00000000000");
        assertSchemaWithIdentityColumns(db, model, source, "--stroke-arcs", "--model", fine,
                "--model-file", fineSource.toString());

        var nativeInput = xml.transfer(model, "native",
                xml.route(model, "native-major", xml.polyline(xml.coord(600010, 200000) + xml.arc(600000, 200010, 600008, 199994)))
                + xml.route(model, "native-circle", xml.polyline(xml.coord(600010, 200000) + xml.arc(599990, 200000, 600010, 200000)))
                + xml.route(model, "native-collinear", xml.polyline(xml.coord(600000, 200000) + xml.arc(600005, 200000, 600010, 200000))));
        assertCliSuccess(db, "import", model, "native", nativeInput);
        var output = work.resolve("native-export.xtf");
        assertCliSuccess(db, "export", model, "native", output);
        var objects = xml.objects(output, "native");
        assertCircularPath(xml.linePositions(objects.get("native-major")),
                List.of(600010.0, 200000.0), List.of(600000.0, 200010.0), List.of(600008.0, 199994.0));
        assertCircularPath(xml.linePositions(objects.get("native-circle")),
                List.of(600010.0, 200000.0), List.of(599990.0, 200000.0), List.of(600010.0, 200000.0));
        assertEquals(List.of(List.of(600000.0, 200000.0), List.of(600010.0, 200000.0)),
                xml.linePositions(objects.get("native-collinear")));

        var failing = xml.transfer(fine, "rollback", xml.object(fine, "Route", "written", "")
                + xml.route(fine, "over-limit", StrokeGeometryFixtures.ordinaryArc(xml)));
        assertCliFailure(db, "import", fine, "rollback", failing, "100000 stored XY pairs");
        try (var sql = db.connect()) {
            assertEquals("native", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            assertEquals("native-b", scalar(sql, "SELECT t_ili_tid FROM " + db.table("t_ili2db_basket")));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            var names = db.table("t_ili2db_classname");
            var fineTable = scalar(sql, "SELECT sqlname FROM " + names + " WHERE iliname='" + fine + ".Data.Route'");
            var nativeTable = scalar(sql, "SELECT sqlname FROM " + names + " WHERE iliname='" + model + ".Data.Route'");
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(fineTable)));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + db.table(nativeTable)));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsXyzSchema(int version, Backend backend) throws Exception {
        var xml = new StrokeFixtures(work, version);
        var db = POSTGIS.target(work.resolve("xyz.gpkg"), backend);
        String model = "StrokeXyz" + version;
        var source = xml.source(model, "POLYLINE");
        Files.writeString(source, Files.readString(source).replace("0.000 .. 400000.000;",
                "0.000 .. 400000.000, -1000.000 .. 10000.000;"));
        assertCliFailure(db, "schemaimport", model, null, source,
                "strokeArcs requires XY at " + model + ".Data.Route.Shape", "--stroke-arcs");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsWrongPolygonStorageWithoutReplacingExport(int version, Backend backend) throws Exception {
        var fixture = new StrokeGeometryFixtures(work, version);
        var db = POSTGIS.target(work.resolve("polygons.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--stroke-arcs");
        assertCliSuccess(db, "import", fixture.model, "native", fixture.delivery("native"));
        var output = work.resolve("native-export.xtf");
        assertCliSuccess(db, "export", fixture.model, "native", output);
        fixture.assertDelivery(output, "native");
        String diagnostic;
        try (var sql = db.connect()) {
            var parcel = db.table("parcel");
            if (db.postgis()) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + parcel + " WHERE t_ili_tid='native-parcel'"
                        + " AND ST_IsValid(shape) AND ST_NumInteriorRings(shape)=1 AND ST_SRID(shape)=2056"));
                var zone = db.table("district");
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + zone + " a JOIN " + zone
                        + " b ON ST_Touches(a.shape,b.shape) WHERE a.t_ili_tid='native-top' AND b.t_ili_tid='native-bottom'"));
                execute(sql, "ALTER TABLE " + parcel + " ALTER COLUMN shape TYPE geometry(CurvePolygon,2056) USING ST_ForceCurve(shape)");
                diagnostic = "2D POLYGON column at parcel.shape";
            } else {
                execute(sql, "UPDATE " + parcel + " SET shape=CAST(substr(shape,1,9)||X'0000000A'||substr(shape,14) AS BLOB)"
                        + " WHERE t_ili_tid='native-parcel'");
                diagnostic = "2D WKB POLYGON";
            }
        }
        assertExportRejected(db, fixture.model, "native", output, diagnostic);
    }
    private static void assertCircularPath(List<List<Double>> positions, List<Double> start, List<Double> support, List<Double> end) {
        assertEquals(start, positions.getFirst());
        assertEquals(end, positions.getLast());
        assertTrue(positions.contains(support));
        for (int i = 1; i < positions.size(); i++) {
            var a = positions.get(i - 1);
            var b = positions.get(i);
            assertEquals(10, Math.hypot(b.get(0) - 600000, b.get(1) - 200000), 0.000001);
            double midpointRadius = Math.hypot((a.get(0) + b.get(0)) / 2 - 600000, (a.get(1) + b.get(1)) / 2 - 200000);
            assertTrue(10 - midpointRadius <= 0.001000001, "Chord exceeds model tolerance");
        }
    }

}
