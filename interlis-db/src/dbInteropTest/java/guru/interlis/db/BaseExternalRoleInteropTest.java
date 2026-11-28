package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;

final class BaseExternalRoleInteropTest {
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
        var fixture = BaseExternalRoleFixtures.create(work, version);
        var baseline = new TestDatabase(work, postgres, "external_base_reference", "v" + version + "_");
        var own = baseline.peer("external_base_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.derived() + ";" + fixture.parent(), "newClass");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        importDataset(reference, baseline, "delivery", fixture.delivery());
        assertCliSuccess(own, "import", fixture.derived(), "delivery", fixture.delivery(), "--model", fixture.parent());

        var referenceTarget = baseline.peer("external_projected_reference");
        var nativeTarget = own.peer("external_projected_native");
        schema(reference, referenceTarget, work, fixture.parent(), "newClass");
        assertSchemaWithIdentityColumns(nativeTarget, fixture.parent(), fixture.parentSource());
        for (String part : List.of("full", "partial")) {
            var nativeOutput = work.resolve("native-" + part + ".xtf");
            var referenceOutput = work.resolve("reference-" + part + ".xtf");
            boolean full = part.equals("full");
            String bid = "region-" + part;
            if (full) {
                assertCliSuccess(baseline, "export", fixture.derived(), null, nativeOutput,
                        "--model", fixture.parent(), "--export-model", fixture.parent(), "--basket", "catalog-b", "--basket", bid);
            } else {
                assertCliSuccess(baseline, "export", fixture.derived(), null, nativeOutput,
                        "--export-model", fixture.parent(), "--basket", bid);
            }
            reference.run(own, "%ILI_FROM_DB", "--export", "--baskets", full ? "catalog-b;" + bid : bid,
                    "--exportModels", fixture.parent(), "--exportTid", referenceOutput.toString());
            fixture.assertProjection(nativeOutput, full, true);
            fixture.assertProjection(referenceOutput, full, false);
            importDataset(reference, referenceTarget, part, nativeOutput);
            assertCliSuccess(nativeTarget, "import", fixture.parent(), part, referenceOutput);
        }
        fixture.assertReimportedRoles(referenceTarget);
        fixture.assertReimportedRoles(nativeTarget);
    }
}
