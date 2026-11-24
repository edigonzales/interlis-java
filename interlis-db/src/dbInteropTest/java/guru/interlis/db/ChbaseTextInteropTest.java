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
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChbaseTextInteropTest {
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
        var fixture = new ChbaseTextFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "chbase_text_reference", "v" + version + "_");
        var own = baseline.peer("chbase_text_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.multilingual + ";" + fixture.localised, "newClass",
                "--expandMultilingual", "--expandLocalised");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        Path nativeInput = withPrefix(work, fixture.input, "native");
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", fixture.input);
            assertNativeExport(db, fixture, "legacy", "fixture");

            var imported = cli(db, "import", fixture.multilingual, "native", nativeInput, CancellationToken.none(),
                    "--model", fixture.localised);
            assertEquals(0, imported.code(), imported::diagnostics);
            Path output = work.resolve(db.schema() + "-reference.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native", "Ponte");
        }

        importDataset(reference, own, "tail", withPrefix(work, fixture.input, "tail"));
        assertNativeExport(own, fixture, "tail", "tail");
        InteropCommands.assertSharedIds(own, 21, "plaque", "memocard");
    }

    private void assertNativeExport(TestDatabase db, ChbaseTextFixtures fixture, String dataset, String prefix) throws Exception {
        Path output = work.resolve(db.schema() + "-" + dataset + ".xtf");
        var exported = cli(db, "export", fixture.multilingual, dataset, output, CancellationToken.none(),
                "--model", fixture.localised);
        assertEquals(0, exported.code(), exported::diagnostics);
        fixture.assertDelivery(output, prefix, "Ponte");
    }
}
