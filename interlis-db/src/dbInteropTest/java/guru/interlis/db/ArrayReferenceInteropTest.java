package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.ArrayReferenceFixtures.ORDER;
import static guru.interlis.db.ArrayReferenceFixtures.assertStoredDelivery;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.InteropCommands.assertSharedIds;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;

final class ArrayReferenceInteropTest {
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
        var fixture = new ArrayReferenceFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "array_refs_reference", "v" + version + "_");
        var own = baseline.peer("array_refs_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.model, "newClass", "--coalesceArray");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        Path legacy = fixture.delivery("legacy-input", "legacy", ORDER, true);
        Path nativeInput = fixture.delivery("native-input", "native", ORDER, true);
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", legacy);
            Path output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", output);
            fixture.assertDelivery(output, "legacy", ORDER, true);

            assertCliSuccess(db, "import", fixture.model, "native", nativeInput);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native", ORDER, true);
            try (var sql = db.connect()) {
                assertStoredDelivery(db, sql, "legacy", ORDER);
                assertStoredDelivery(db, sql, "native", ORDER);
            }
        }
        importDataset(reference, own, "tail", fixture.delivery("tail-input", "tail", List.of("a"), true));
        Path output = work.resolve("tail-output.xtf");
        assertCliSuccess(own, "export", fixture.model, "tail", output);
        fixture.assertDelivery(output, "tail", List.of("a"), true);
        assertSharedIds(own, 18, "asset", "target");
    }
}
