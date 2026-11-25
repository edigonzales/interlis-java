package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.assertSharedIds;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.tableFor;
import static guru.interlis.db.StrokeFixtures.assertGeometryColumn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StrokeGeometryInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new StrokeGeometryFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "stroke_reference", "v" + version + "_");
        var own = baseline.peer("stroke_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.model, "newClass", "--strokeArcs");
        assertSchemaWithIdentityColumns(own, fixture.model, fixture.source, "--stroke-arcs");
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        assertGeometryColumn(own, "straight", "shape", "LINESTRING", 2056);
        assertGeometryColumn(own, "route", "shape", "LINESTRING", 2056);
        assertGeometryColumn(own, "parcel", "shape", "POLYGON", 2056);
        assertGeometryColumn(own, "district", "shape", "POLYGON", 2056);
        assertGeometryColumn(own, "marker", "anchor", "POINT", 2056);
        try (var sql = own.connect()) {
            var prop = own.table("t_ili2db_column_prop");
            assertEquals("AREA", scalar(sql, "SELECT setting FROM " + prop
                    + " WHERE tablename='district' AND columnname='shape' AND tag='ch.ehi.ili2db.typeKind'"));
            assertEquals("SURFACE", scalar(sql, "SELECT setting FROM " + prop
                    + " WHERE tablename='parcel' AND columnname='shape' AND tag='ch.ehi.ili2db.typeKind'"));
        }

        var legacy = fixture.delivery("legacy");
        var nativeInput = fixture.delivery("native");
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", legacy);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", output);
            var expected = fixture.assertDelivery(output, "legacy");

            assertCliSuccess(db, "import", fixture.model, "native", nativeInput);
            output = work.resolve(db.schema() + "-native.xtf");
            assertCliSuccess(db, "export", fixture.model, "native", output);
            assertPositions(expected, fixture.assertDelivery(output, "native"), 0.000001);
            output = work.resolve(db.schema() + "-reference.xtf");
            exportDataset(reference, db, "native", output);
            assertPositions(expected, fixture.assertDelivery(output, "native"), 0.000500001);
        }

        importDataset(reference, own, "tail", fixture.delivery("tail"));
        var tail = work.resolve("tail-export.xtf");
        assertCliSuccess(own, "export", fixture.model, "tail", tail);
        fixture.assertDelivery(tail, "tail");
        appendModel(reference, own, fixture.xml);
    }

    private void appendModel(ReferenceTool reference, TestDatabase own, StrokeFixtures xml) throws Exception {
        String model = "StrokeAppend" + xml.version;
        var source = xml.source(model, "SURFACE");
        assertSchemaWithIdentityColumns(own, model, source, "--add-models");
        var table = tableFor(own, model + ".Data.Route");
        assertGeometryColumn(own, table, "shape", "POLYGON", 2056);
        var polygon = xml.surfaceValue(xml.polyline(xml.coord(600000, 200000) + xml.arc(600005, 199995, 600010, 200000)
                + xml.coord(600010, 200010) + xml.coord(600000, 200000)));
        assertCliSuccess(own, "import", model, "appended", xml.dataset(model, "appended", polygon));
        var output = work.resolve("appended-export.xtf");
        exportDataset(reference, own, "appended", output);
        var objects = xml.objects(output, "appended");
        assertNull(child(objects.get("appended-null"), "Shape"));
        var ring = xml.rings(objects.get("appended-value")).getFirst();
        assertEquals(83, ring.size());
        assertEquals(List.of(600005.0, 199995.0), ring.get(40));
        assertSharedIds(own, 37, "straight", "route", "parcel", "district", "marker", table);
    }

    private static void assertPositions(List<List<Double>> expected, List<List<Double>> actual, double tolerance) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).get(0), actual.get(i).get(0), tolerance, "X at " + i);
            assertEquals(expected.get(i).get(1), actual.get(i).get(1), tolerance, "Y at " + i);
        }
    }
}
