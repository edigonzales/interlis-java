package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.BaseDbFixture.assertRows;
import static guru.interlis.db.BaseTransferAssertions.assertTransfer;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BaseMappingInteropTest {
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
        var fixture = new BaseDbFixture(work, version);
        var baseline = new TestDatabase(work, postgres, "base_reference", "v" + version + "_");
        var own = baseline.peer("base_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        String model = fixture.model();
        Path source = fixture.source();
        schema(reference, baseline, work, model, "newClass");
        assertSchemaWithIdentityColumns(own, model, source);
        compareCatalogs(baseline, own, version);
        Files.delete(source);

        String transfer = fixture.transfer();
        var legacy = Files.writeString(work.resolve("legacy.xtf"), transfer);
        var nativeInput = Files.writeString(work.resolve("native.xtf"), transfer.replace("legacy-", "native-"));
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", legacy);
            assertReferenceEmptyTextAndRestoreSqlEmptyStrings(db);
            var output = work.resolve(db.schema() + "-native-export.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertTransfer(output, version, "legacy", true);

            assertCliSuccess(db, "import", model, "native", nativeInput);
            output = work.resolve(db.schema() + "-reference-export.xtf");
            exportDataset(reference, db, "native", output);
            assertTransfer(output, version, "native", false);
        }
        try (var sql = baseline.connect()) {
            assertRows(baseline, sql, version, 2);
        }

        var tail = Files.writeString(work.resolve("tail.xtf"), transfer.replace("legacy-", "tail-"));
        importDataset(reference, own, "tail", tail);
        try (var sql = own.connect()) {
            assertRows(own, sql, version, 3);
            assertEquals(Set.of("tail-basket-1", "tail-basket-2"), catalogKeys(sql,
                    "select b.t_ili_tid from " + own.table("t_ili2db_basket") + " b join "
                            + own.table("t_ili2db_dataset") + " d on b.dataset=d.t_id where d.datasetname='tail'"));
            assertEquals("first", scalar(sql, """
                    select m.aselect from %s l
                    join %s m on m.t_id=l.linkedmarker and m.t_basket=l.t_basket
                    join %s h on h.t_id=l.linkedhub and h.t_basket=l.t_basket
                    join %s b on b.t_id=l.t_basket
                    where b.t_ili_tid='tail-basket-1' and m.t_ili_tid='tail-marker-1' and h.t_ili_tid='tail-hub-1'
                    """.formatted(own.table("markerlink"), own.table("marker"), own.table("hub"),
                    own.table("t_ili2db_basket"))));
        }
    }

    private static void compareCatalogs(TestDatabase baseline, TestDatabase own, int version) throws Exception {
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var catalog = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            if (baseline.postgis()) {
                assertTiltPrecision(expected, baseline.schema());
                assertTiltPrecision(actual, own.schema());
            } else {
                catalog = catalog.expectGeoPackageGeometryExtension("route", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("tower", "alignment", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("plot", "footprint", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("parcel", "boundary", "CURVEPOLYGON");
                if (version == 24) {
                    catalog = catalog.expectGeoPackageGeometryExtension("park", "coverage", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("district", "coverage", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("routegroup", "traces", "MULTICURVE");
                }
            }
            catalog.assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
    }

    private static void assertTiltPrecision(Connection sql, String schema) throws SQLException {
        assertEquals("4:2", scalar(sql, """
                select numeric_precision || ':' || numeric_scale from information_schema.columns
                where table_schema = '%s' and table_name = 'tower' and column_name = 'tilt'
                """.formatted(schema)), schema + ".tower.tilt precision and scale");
    }

    private static void assertReferenceEmptyTextAndRestoreSqlEmptyStrings(TestDatabase db) throws Exception {
        try (var sql = db.connect()) {
            String place = db.table("place") + " where t_ili_tid='legacy-place-2'";
            String link = db.table("annotatedlink") + " where consultedmarker=(select t_id from "
                    + db.table("marker") + " where t_ili_tid='legacy-marker-2')";
            assertEquals("1", scalar(sql, "select count(*) from " + place + " and aselect is null"));
            assertEquals("1", scalar(sql, "select count(*) from " + link + " and memo is null"));
            execute(sql, "update " + db.table("place") + " set aselect='' where t_ili_tid='legacy-place-2'");
            execute(sql, "update " + db.table("annotatedlink") + " set memo='' where consultedmarker=(select t_id from "
                    + db.table("marker") + " where t_ili_tid='legacy-marker-2')");
        }
    }
}
