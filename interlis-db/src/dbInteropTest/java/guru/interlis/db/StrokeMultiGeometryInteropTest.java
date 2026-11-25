package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.ChbaseFixtures.withPrefix;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.StrokeFixtures.assertGeometryColumn;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StrokeMultiGeometryInteropTest {
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
        var fixture = new StrokeMultiGeometryFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "stroke_collections_reference", "v" + version + "_");
        var own = baseline.peer("stroke_collections_native");
        var reference = new ReferenceTool(work, own.postgis());
        schema(reference, baseline, work, fixture.referenceModels(), "newClass",
                "--strokeArcs", "--coalesceMultiLine", "--coalesceMultiSurface");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        assertGeometryColumn(own, "piperoute", "shape", "MULTILINESTRING", 2056);
        assertGeometryColumn(own, "flowroute", "shape", "MULTILINESTRING", 2056);
        assertGeometryColumn(own, "landcover", "shape", "MULTIPOLYGON", 21781);
        if (version == 24) {
            assertGeometryColumn(own, "routegroup", "shape", "MULTILINESTRING", 2056);
            assertGeometryColumn(own, "parcelgroup", "shape", "MULTIPOLYGON", 2056);
            assertGeometryColumn(own, "districtgroup", "shape", "MULTIPOLYGON", 2056);
        }

        var nativeInput = withPrefix(work, fixture.input, "native");
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", fixture.input);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            var exported = cli(db, "export", fixture.lines, "legacy", output, CancellationToken.none(), fixture.extraModels());
            assertEquals(0, exported.code(), exported::diagnostics);
            fixture.assertDelivery(output, "fixture");

            var imported = cli(db, "import", fixture.lines, "native", nativeInput, CancellationToken.none(), fixture.extraModels());
            assertEquals(0, imported.code(), imported::diagnostics);
            output = work.resolve(db.schema() + "-reference.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native");
        }
    }
}
