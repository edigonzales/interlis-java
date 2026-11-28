package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;

final class BaseRoleInteropTest {
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
        var fixture = new BaseRoleFixtures(work, version);
        var legacy = new TestDatabase(work, postgres, "roles_reference", "v" + version + "_");
        var own = legacy.peer("roles_native");
        var reference = new ReferenceTool(work, legacy.postgis());
        schema(reference, legacy, work, fixture.extension, "newClass");
        fixture.createExtension(own);
        try (var expected = legacy.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, legacy.postgis(), legacy.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        importDataset(reference, legacy, "roles", fixture.delivery);
        var nativeOutput = work.resolve("native-base.xtf");
        assertCliSuccess(legacy, "export", fixture.extension, "roles", nativeOutput, "--export-model", fixture.base);
        fixture.assertProjection(nativeOutput);

        assertCliSuccess(own, "import", fixture.extension, "roles", fixture.delivery);
        var referenceOutput = work.resolve("reference-base.xtf");
        exportDataset(reference, own, "roles", referenceOutput, "--exportModels", fixture.base);
        fixture.assertProjection(referenceOutput);

        var referenceTarget = legacy.peer("roles_base_reference");
        var nativeTarget = legacy.peer("roles_base_native");
        schema(reference, referenceTarget, work, fixture.base, "newClass");
        assertSchemaWithIdentityColumns(nativeTarget, fixture.base, fixture.baseModel);
        importDataset(reference, referenceTarget, "base", nativeOutput);
        assertCliSuccess(nativeTarget, "import", fixture.base, "base", referenceOutput);
        fixture.assertStoredRoles(referenceTarget);
        fixture.assertStoredRoles(nativeTarget);
    }
}
