package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;

final class ExternalTextReferenceInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() { POSTGIS.close(); }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ExternalTextReferenceFixture(work, version);
        var old = new TestDatabase(work, postgres, "external_text_ref", "ext_text_v" + version + "_");
        var own = old.peer("external_text_own");
        var reference = new ReferenceTool(work, postgres != null);
        String model = fixture.model();
        var source = fixture.source(model);
        schema(reference, old, work, model, "newClass", "--sqlExtRefCols", "--createFk");
        assertSchemaWithIdentityColumns(own, model, source, "--sql-ext-ref-cols", "--create-fk");
        try (var expected = old.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, old.postgis(), old.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        var legacy = fixture.transfer(model, "legacy");
        var nativeInput = fixture.transfer(model, "native");
        for (var db : List.of(old, own)) {
            importDataset(reference, db, "legacy", legacy);
            Path nativeOutput = work.resolve(db.schema() + "-native.xtf");
            assertCliSuccess(db, "export", model, "legacy", nativeOutput);
            fixture.assertTransfer(nativeOutput, "legacy");
            assertCliSuccess(db, "import", model, "native", nativeInput);
            Path referenceOutput = work.resolve(db.schema() + "-reference.xtf");
            exportDataset(reference, db, "native", referenceOutput);
            fixture.assertTransfer(referenceOutput, "native");
            try (var sql = db.connect()) {
                fixture.assertStored(db, sql, "legacy");
                fixture.assertStored(db, sql, "native");
            }
        }
    }
}
