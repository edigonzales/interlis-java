package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.ChbaseFixtures.withPrefix;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChbaseGeometryInteropTest {
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

    @Test void geoPackageSmart223() throws Exception { exchangeSmart2(23, null); }
    @Test void geoPackageSmart224() throws Exception { exchangeSmart2(24, null); }
    @Test void postgisSmart223() throws Exception { exchangeSmart2(23, POSTGIS.container()); }
    @Test void postgisSmart224() throws Exception { exchangeSmart2(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ChbaseGeometryFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "chbase_geometry_reference", "v" + version + "_");
        var own = baseline.peer("chbase_geometry_native");
        var reference = new ReferenceTool(work, own.postgis());
        schema(reference, baseline, work, fixture.referenceModels(), "newClass",
                "--coalesceMultiPoint", "--coalesceMultiLine", "--coalesceMultiSurface");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var catalog = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            if (!baseline.postgis()) {
                catalog = catalog.expectGeoPackageGeometryExtension("piperoute", "shape", "MULTICURVE")
                        .expectGeoPackageGeometryExtension("flowroute", "shape", "MULTICURVE")
                        .expectGeoPackageGeometryExtension("linestructure", "line", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("directedlinestructure", "line", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("landcover", "shape", "MULTISURFACE")
                        .expectGeoPackageGeometryExtension("surfacestructure", "surface", "CURVEPOLYGON");
            }
            catalog.assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        Path nativeInput = withPrefix(work, fixture.input, "native");
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", fixture.input);
            assertNativeExport(db, fixture, "legacy", "fixture");

            var result = cli(db, "import", fixture.model(), "native", nativeInput, CancellationToken.none(), fixture.extraModels());
            assertEquals(0, result.code(), result::diagnostics);
            Path output = work.resolve(db.schema() + "-reference.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native");
        }

        importDataset(reference, own, "tail", withPrefix(work, fixture.input, "tail"));
        assertNativeExport(own, fixture, "tail", "tail");
        InteropCommands.assertSharedIds(own, 33, "pinset", "piperoute", "flowroute", "landcover");
    }

    private void assertNativeExport(TestDatabase db, ChbaseGeometryFixtures fixture, String dataset, String prefix) throws Exception {
        Path output = work.resolve(db.schema() + "-" + dataset + ".xtf");
        var result = cli(db, "export", fixture.model(), dataset, output, CancellationToken.none(), fixture.extraModels());
        assertEquals(0, result.code(), result::diagnostics);
        fixture.assertDelivery(output, prefix);
    }

    private void exchangeSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ChbaseSmartFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "chbase_smart2_reference", "v" + version + "_");
        var own = baseline.peer("chbase_smart2_native");
        var reference = new ReferenceTool(work, own.postgis());
        var options = new ArrayList<>(List.of("--coalesceMultiPoint", "--expandMultilingual", "--createGeomIdx"));
        if (own.postgis()) options.add("--oneGeomPerTable");
        schema(reference, baseline, work, fixture.model, "smart2", options.toArray(String[]::new));
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        Path nativeInput = withPrefix(work, fixture.input, "native");
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", fixture.input);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", output);
            fixture.assertDelivery(output, "fixture", "Alt", "Pont", 2600100, 1200100);

            assertCliSuccess(db, "import", fixture.model, "native", nativeInput);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native", "Alt", "Pont", 2600100, 1200100);

            assertCliSuccess(db, "update", fixture.model, "legacy", fixture.update);
            output = work.resolve(db.schema() + "-updated.xtf");
            exportDataset(reference, db, "legacy", output);
            fixture.assertDelivery(output, "fixture", "Neu", null, 2600300, 1200300);
            try (var sql = db.connect()) {
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("surveyasset_shape")));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("surveyasset") + " a JOIN "
                        + db.table("surveyasset_shape") + " s ON a.t_id=s.t_id AND a.t_basket=s.t_basket WHERE s.shape IS NOT NULL"));
            }
        }
    }
}
