package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.StrokeFixtures.assertGeometryColumn;

final class StrokeSmart2InteropTest {
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
        var fixture = new StrokeSmart2Fixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "stroke_smart_reference", "v" + version + "_");
        var own = baseline.peer("stroke_smart_native");
        var reference = new ReferenceTool(work, own.postgis());
        var options = new ArrayList<>(List.of("--strokeArcs", "--createGeomIdx"));
        if (own.postgis()) options.add("--oneGeomPerTable");
        schema(reference, baseline, work, fixture.model, "smart2", options.toArray(String[]::new));
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        var legacy = fixture.delivery("legacy", "legacy", 600000, "first");
        var nativeInput = fixture.delivery("native", "native", 600200, "second");
        var update = fixture.delivery("update", "legacy", 600100, "updated");
        for (var db : List.of(baseline, own)) {
            assertGeometryColumn(db, "surveyasset_shape", "shape", "LINESTRING", 2056);
            importDataset(reference, db, "legacy", legacy);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", output);
            fixture.assertAsset(output, "legacy", 600000, "first");

            assertCliSuccess(db, "import", fixture.model, "native", nativeInput);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertAsset(output, "native", 600200, "second");

            assertCliSuccess(db, "update", fixture.model, "legacy", update);
            output = work.resolve(db.schema() + "-updated.xtf");
            exportDataset(reference, db, "legacy", output);
            fixture.assertAsset(output, "legacy", 600100, "updated");
        }
    }
}
