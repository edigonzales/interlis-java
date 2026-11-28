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

final class BaseGeometryInteropTest {
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
        var fixture = new BaseGeometryFixtures(work, version);
        var referenceSource = new TestDatabase(work, postgres, "terrain_reference", "v" + version + "_");
        var nativeSource = referenceSource.peer("terrain_native");
        var reference = new ReferenceTool(work, referenceSource.postgis());

        schema(reference, referenceSource, work, fixture.regional, "newClass");
        fixture.schema(nativeSource, false);
        importDataset(reference, referenceSource, "terrain", fixture.delivery);
        assertCliSuccess(nativeSource, "import", fixture.regional, "terrain", fixture.delivery);

        var nativeOutput = work.resolve("native-projection.xtf");
        var referenceOutput = work.resolve("reference-projection.xtf");
        assertCliSuccess(referenceSource, "export", fixture.regional, "terrain", nativeOutput, "--export-model", fixture.base);
        exportDataset(reference, nativeSource, "terrain", referenceOutput, "--exportModels", fixture.base);
        fixture.assertDelivery(nativeOutput, false);
        fixture.assertDelivery(referenceOutput, false);

        var referenceTarget = referenceSource.peer("terrain_base_reference");
        var nativeTarget = referenceSource.peer("terrain_base_native");
        schema(reference, referenceTarget, work, fixture.base, "newClass");
        fixture.schema(nativeTarget, true);
        importDataset(reference, referenceTarget, "base", nativeOutput);
        assertCliSuccess(nativeTarget, "import", fixture.base, "base", referenceOutput);
        fixture.assertStored(referenceTarget);
        fixture.assertStored(nativeTarget);
    }
}
