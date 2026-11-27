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
import static guru.interlis.db.StructurePointFixtures.ORIGINAL;

final class StructurePointInteropTest {
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
        var fixture = new StructurePointFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "structure_point_reference", "v" + version + "_");
        var own = baseline.peer("structure_point_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.model, "newClass", "--createGeomIdx");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        var input = fixture.input("delivery", ORIGINAL);
        var output = work.resolve("structure-reference-to-native.xtf");
        importDataset(reference, baseline, "points", input);
        assertCliSuccess(baseline, "export", fixture.model, "points", output);
        fixture.assertTransfer(output, ORIGINAL);
        try (var sql = baseline.connect()) {
            fixture.assertRows(baseline, sql, ORIGINAL);
        }

        output = work.resolve("structure-native-to-reference.xtf");
        assertCliSuccess(own, "import", fixture.model, "points", input);
        exportDataset(reference, own, "points", output);
        fixture.assertTransfer(output, ORIGINAL);
        try (var sql = own.connect()) {
            fixture.assertRows(own, sql, ORIGINAL);
        }
    }
}
