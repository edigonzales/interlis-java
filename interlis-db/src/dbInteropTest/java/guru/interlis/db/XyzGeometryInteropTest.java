package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;

final class XyzGeometryInteropTest {
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
        var fixture = new XyzGeometryFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "xyz_reference", "v" + version + "_");
        var own = baseline.peer("xyz_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.model, "newClass", "--createGeomIdx");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var catalog = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            if (!baseline.postgis()) {
                catalog = catalog.expectGeoPackageGeometryExtension("track", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("plot", "outline", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("parcel", "perimeter", "CURVEPOLYGON");
                if (version == 24) {
                    catalog = catalog.expectGeoPackageGeometryExtension("network", "routes", "MULTICURVE")
                            .expectGeoPackageGeometryExtension("park", "grounds", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("district", "territory", "MULTISURFACE");
                }
            }
            catalog.assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        var input = fixture.input();
        importDataset(reference, baseline, "xyz", input);
        var output = work.resolve("xyz-native-export.xtf");
        assertCliSuccess(baseline, "export", fixture.model, "xyz", output);
        fixture.assertTransfer(output);

        assertCliSuccess(own, "import", fixture.model, "xyz", input);
        output = work.resolve("xyz-reference-export.xtf");
        exportDataset(reference, own, "xyz", output);
        fixture.assertTransfer(output);
    }
}
