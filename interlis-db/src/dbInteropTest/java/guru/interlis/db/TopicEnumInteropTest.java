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

final class TopicEnumInteropTest {
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
        var fixture = new TopicEnumFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "enum_topic_reference", "v" + version + "_");
        var own = baseline.peer("enum_topic_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.derived, "newClass");
        assertSchemaWithIdentityColumns(own, fixture.derived, fixture.derivedSource, "--model-file", fixture.baseSource.toString());
        var input = fixture.delivery("source", "open.weekend");
        importDataset(reference, baseline, "input", input);
        assertCliSuccess(own, "import", fixture.derived, "input", input);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
            fixture.assertMapping(baseline, expected);
            fixture.assertMapping(own, actual);
            fixture.assertStored(baseline, expected, "open.weekend");
            fixture.assertStored(own, actual, "open.weekend");
        }
        var nativeOutput = work.resolve("native.xtf");
        assertCliSuccess(baseline, "export", fixture.derived, "input", nativeOutput);
        fixture.assertValues(nativeOutput, "open.weekend");
        var referenceOutput = work.resolve("reference.xtf");
        exportDataset(reference, own, "input", referenceOutput);
        fixture.assertValues(referenceOutput, "open.weekend");

        assertCliSuccess(baseline, "export", fixture.derived, "input", nativeOutput, "--export-model", fixture.base);
        fixture.assertProjected(nativeOutput);
        reference.run(own, "%ILI_FROM_DB", "--export", "--dataset", "input",
                "--exportModels", fixture.base, "--exportTid", referenceOutput.toString());
        fixture.assertProjected(referenceOutput);

        var referenceBase = baseline.peer("base_reference");
        schema(reference, referenceBase, work, fixture.base, "newClass");
        importDataset(reference, referenceBase, "reduced", nativeOutput);
        fixture.assertBaseStored(referenceBase);
        var nativeBase = own.peer("base_native");
        assertSchemaWithIdentityColumns(nativeBase, fixture.base, fixture.baseSource);
        assertCliSuccess(nativeBase, "import", fixture.base, "reduced", referenceOutput);
        fixture.assertBaseStored(nativeBase);
    }
}
