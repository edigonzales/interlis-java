package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.SpatialIndexFixtures.assertCollectionOrder;
import static guru.interlis.db.SpatialIndexFixtures.assertIndex;
import static guru.interlis.db.SpatialIndexFixtures.assertSearch;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SpatialIndexInteropTest {
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
        var old = new TestDatabase(work, postgres, "spatial_reference", "v" + version + "_");
        var own = old.peer("spatial_own");
        var reference = new ReferenceTool(work, old.postgis());
        var fixtures = new SpatialIndexFixtures(work, version);
        String model = "DbSpatial" + version;
        var ili = fixtures.copy(model + ".ili");
        referenceSchema(reference, old, ili);
        assertSchemaWithIdentityColumns(own, model, ili, "--create-geom-index");
        try (var oldSql = old.connect(); var ownSql = own.connect()) {
            var expected = DbSchemaCatalog.read(oldSql, old.postgis(), old.schema());
            if (!old.postgis()) {
                expected = expected.expectGeoPackageGeometryExtension("indexedline", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("indexedplot", "footprint", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("indexedarea", "boundary", "CURVEPOLYGON");
            }
            expected.assertSameAs(DbSchemaCatalog.read(ownSql, own.postgis(), own.schema()));
            assertEquals(indexDefinitions(old, oldSql), indexDefinitions(own, ownSql));
        }

        String straight = fixtures.straight(model);
        String curves = fixtures.curves(model);
        var original = fixtures.delivery("original", model, "shapes", straight + curves);
        var changed = fixtures.delivery("updated", model, "shapes",
                straight.replace("2601000.125", "2601200.125").replace("2601020.250", "2601220.250") + curves);

        for (var db : List.of(old, own)) {
            String suffix = db == old ? "old" : "own";
            assertCliSuccess(db, "import", model, "native", original);
            try (var sql = db.connect()) {
                assertIndex(db, sql, "indexedline", "trace");
                assertIndex(db, sql, "indexedplot", "footprint");
                assertIndex(db, sql, "indexedarea", "boundary");
                assertSearch(db, sql, "indexedline", "trace", "2601005", "1201005", true);
                assertSearch(db, sql, "indexedplot", "footprint", "2601062", "1201062", true);
                assertSearch(db, sql, "indexedarea", "boundary", "2601110", "1201110", true);
                assertSearch(db, sql, "indexedline", "trace", "2602399", "1202300", true);

                Path exported = work.resolve("spatial-reference-" + suffix + ".xtf");
                reference.run(db, "%ILI_FROM_DB", "--export", "--exportTid", "--dataset", "native", exported.toString());
                assertTransfer(fixtures, exported, version, "2601000.125");

                reference.run(db, "%ILI_FROM_DB", "--update", "--dataset", "native", "--importTid", "--importBid",
                        changed.toString());
                assertSearch(db, sql, "indexedline", "trace", "2601005", "1201005", false);
                assertSearch(db, sql, "indexedline", "trace", "2601205", "1201005", true);
                assertSearch(db, sql, "indexedplot", "footprint", "2602399", "1202300", true);
                Path ownOutput = work.resolve("spatial-native-" + suffix + ".xtf");
                assertCliSuccess(db, "export", model, "native", ownOutput);
                assertTransfer(fixtures, ownOutput, version, "2601200.125");
            }
        }
        if (version == 24) collections(reference, old.peer("collections_reference"), fixtures);
    }

    private void collections(ReferenceTool reference, TestDatabase db, SpatialIndexFixtures fixtures) throws Exception {
        String model = "DbInterop24";
        referenceSchema(reference, db, fixtures.copy(model + ".ili"));
        Path input = fixtures.copy(model + ".xtf");
        assertCliSuccess(db, "import", model, "native", input);
        Path legacy = work.resolve("collections-reference.xtf");
        reference.run(db, "%ILI_FROM_DB", "--export", "--exportTid", "--dataset", "native", legacy.toString());
        assertCollectionOrder(legacy);
        assertCliSuccess(db, "delete", model, "native", null);

        reference.run(db, "%ILI_FROM_DB", "--import", "--importTid", "--importBid", "--dataset", "reference", input.toString());
        Path own = work.resolve("collections-native.xtf");
        assertCliSuccess(db, "export", model, "reference", own);
        assertCollectionOrder(own);
    }

    private static void assertTransfer(SpatialIndexFixtures fixtures, Path file, int version, String expectedX)
            throws Exception {
        var objects = children(children(readDataSection(file, version)).getFirst());
        assertEquals(5, objects.size());
        var line = objects.stream().filter(o -> "line".equals(id(o, version, "tid"))).findFirst().orElseThrow();
        var x = children(children(children(child(line, "Trace")).getFirst()).getFirst()).getFirst().getTextContent();
        assertEquals(0, new BigDecimal(expectedX).compareTo(new BigDecimal(x)));
        var surface = objects.stream().filter(o -> "plot".equals(id(o, version, "tid"))).findFirst().orElseThrow();
        assertEquals(2, children(children(child(surface, "Footprint")).getFirst()).size());
        fixtures.assertCurvesInXtf(file);
    }

    private void referenceSchema(ReferenceTool reference, TestDatabase db, Path ili) throws Exception {
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--createGeomIdx", ili.toString());
    }

    private static List<String> indexDefinitions(TestDatabase db, Connection sql) throws SQLException {
        String query = db.postgis() ? "SELECT replace(indexdef,'" + db.schema() + ".','<schema>.')"
                + " FROM pg_indexes WHERE schemaname='" + db.schema() + "' AND indexdef LIKE '%USING gist%' ORDER BY indexname"
                : "SELECT name||':'||tbl_name FROM sqlite_schema WHERE type='trigger'"
                  + " AND name LIKE 'rtree_%' ORDER BY name";
        var result = new java.util.ArrayList<String>();
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) result.add(rows.getString(1));
        }
        assertEquals(db.postgis() ? 3 : 18, result.size());
        return result;
    }

}
