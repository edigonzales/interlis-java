package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.ArraySmart2Fixtures.INITIAL;
import static guru.interlis.db.ArraySmart2Fixtures.SHORTENED;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;

final class ArraySmart2InteropTest {
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
        var fixture = new ArraySmart2Fixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "array_smart2_reference", "v" + version + "_");
        var own = baseline.peer("array_smart2_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.model, "smart2", "--coalesceArray");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        Path legacy = fixture.delivery("legacy-input", "legacy", INITIAL, true);
        Path nativeInput = fixture.delivery("native-input", "native", INITIAL, true);
        Path update = fixture.delivery("update-input", "native", SHORTENED, false);
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", legacy);
            Path output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", output);
            fixture.assertDelivery(output, "legacy", INITIAL, true);

            assertCliSuccess(db, "import", fixture.model, "native", nativeInput);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native", INITIAL, true);

            assertCliSuccess(db, "update", fixture.model, "native", update);
            output = work.resolve(db.schema() + "-updated.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native", SHORTENED, false);
        }
    }
}
