package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeometryLayoutInteropTest {
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
        var fixture = new GeometryLayoutFixtures(work, version);
        var shared = new TestDatabase(work, postgres, "geometry_shared_reference", "v" + version + "_");
        var split = shared.peer("geometry_split_reference");
        var reference = new ReferenceTool(work, shared.postgis());
        exchangeShared(reference, shared, shared.peer("geometry_shared_native"), fixture);
        exchangeSplit(reference, split, split.peer("geometry_split_native"), fixture);
    }

    private void exchangeShared(ReferenceTool reference, TestDatabase baseline, TestDatabase own, GeometryLayoutFixtures fixture)
            throws Exception {
        schemas(reference, baseline, own, fixture, false);
        var input = fixture.mixedInput();
        importDataset(reference, baseline, "mixed", input);
        var output = work.resolve("shared-native-export.xtf");
        assertCliSuccess(baseline, "export", fixture.model, "mixed", output);
        fixture.assertMixed(output, false);

        assertCliSuccess(own, "import", fixture.model, "mixed", input);
        output = work.resolve("shared-reference-export.xtf");
        exportDataset(reference, own, "mixed", output);
        fixture.assertMixed(output, false);

        assertCliSuccess(own, "update", fixture.model, "mixed", fixture.assetUpdate());
        output = work.resolve("shared-reference-updated.xtf");
        exportDataset(reference, own, "mixed", output);
        fixture.assertMixed(output, true);
    }

    private void exchangeSplit(ReferenceTool reference, TestDatabase baseline, TestDatabase own, GeometryLayoutFixtures fixture)
            throws Exception {
        schemas(reference, baseline, own, fixture, true);
        var assets = fixture.assetInput();
        importDataset(reference, baseline, "assets", assets);
        var output = work.resolve("split-native-assets.xtf");
        assertCliSuccess(baseline, "export", fixture.model, "assets", output);
        fixture.assertAssetDelivery(output, false);

        assertCliSuccess(own, "import", fixture.model, "assets", assets);
        output = work.resolve("split-reference-assets.xtf");
        exportDataset(reference, own, "assets", output);
        fixture.assertAssetDelivery(output, false);

        reference.run(baseline, "%ILI_FROM_DB", "--update", "--dataset", "assets", "--importTid", "--importBid",
                fixture.assetUpdate().toString());
        output = work.resolve("split-reference-updated.xtf");
        assertCliSuccess(baseline, "export", fixture.model, "assets", output);
        fixture.assertAssetDelivery(output, true);
        if (!baseline.postgis()) {
            try (var sql = baseline.connect()) {
                assertEquals("2", scalar(sql, "SELECT count(*) FROM asset_surveyed"));
                assertCliSuccess(baseline, "delete", fixture.model, "assets", null);
                assertEquals("0", scalar(sql, "SELECT count(*) FROM asset_surveyed"));
            }
        }

        var hierarchy = fixture.hierarchyInput();
        importDataset(reference, baseline, "hierarchy", hierarchy);
        output = work.resolve("split-reference-hierarchy-failure.xtf");
        // The reference joins the Track fragment twice even for a delivery it imported itself.
        reference.expectFailure(baseline, "%ILI_FROM_DB", "max one POLYLINE value allowed (Track)",
                "--export", "--dataset", "hierarchy", "--exportTid", output.toString());
        output = work.resolve("split-native-reference-hierarchy.xtf");
        assertCliSuccess(baseline, "export", fixture.model, "hierarchy", output);
        fixture.assertHierarchyDelivery(output, false);

        importDataset(reference, own, "hierarchy", hierarchy);
        output = work.resolve("split-native-own-hierarchy.xtf");
        assertCliSuccess(own, "export", fixture.model, "hierarchy", output);
        fixture.assertHierarchyDelivery(output, false);
    }

    private void schemas(ReferenceTool reference, TestDatabase baseline, TestDatabase own, GeometryLayoutFixtures fixture,
                         boolean split) throws Exception {
        var options = new ArrayList<>(List.of("--createGeomIdx"));
        if (baseline.postgis() && split) options.add("--oneGeomPerTable");
        if (!baseline.postgis() && !split) options.add("--gpkgMultiGeomPerTable");
        schema(reference, baseline, work, fixture.model, "newClass", options.toArray(String[]::new));
        fixture.schema(own, split);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var catalog = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            if (!baseline.postgis()) {
                catalog = catalog.expectGeoPackageGeometryExtension(split ? "asset_trace" : "asset", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension(split ? "recorder_track" : "recorder", "track", "COMPOUNDCURVE");
            }
            catalog.assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
    }
}
