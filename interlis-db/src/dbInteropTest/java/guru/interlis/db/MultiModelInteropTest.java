package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.InteropCommands.assertSharedIds;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiModelInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageMixed23() throws Exception { mixed(23, null); }
    @Test void geoPackageMixed24() throws Exception { mixed(24, null); }
    @Test void postgisMixed23() throws Exception { mixed(23, POSTGIS.container()); }
    @Test void postgisMixed24() throws Exception { mixed(24, POSTGIS.container()); }

    @Test void geoPackageImportedTopic23() throws Exception { importedTopic(23, null); }
    @Test void geoPackageImportedTopic24() throws Exception { importedTopic(24, null); }
    @Test void postgisImportedTopic23() throws Exception { importedTopic(23, POSTGIS.container()); }
    @Test void postgisImportedTopic24() throws Exception { importedTopic(24, POSTGIS.container()); }

    private void mixed(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new MultiModelFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "mixed_models_reference", "v" + version + "_");
        var own = baseline.peer("mixed_models_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.warehouse + ";" + fixture.workshop, "newClass");
        fixture.schema(own);
        Files.delete(fixture.warehouseSource);
        Files.delete(fixture.workshopSource);
        Path nativeInput = fixture.mixedWithPrefix("native");
        Path update = fixture.warehouseUpdate("native");
        for (var db : List.of(baseline, own)) {
            var tables = fixture.tables(db);
            importDataset(reference, db, "legacy", fixture.mixed);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            fixture.run(db, "export", "legacy", output);
            fixture.assertDelivery(output, "mixed", "warehouse");
            fixture.storedIds(db, tables, "legacy", "mixed", "warehouse");

            fixture.run(db, "import", "native", nativeInput);
            var ids = fixture.storedIds(db, tables, "native", "native", "warehouse");
            assertCliSuccess(db, "update", fixture.warehouse, "native", update);
            assertEquals(ids, fixture.storedIds(db, tables, "native", "native", "updated"));
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native", "updated");
        }
        importDataset(reference, own, "tail", fixture.mixedWithPrefix("tail"));
        var output = work.resolve("tail-output.xtf");
        fixture.run(own, "export", "tail", output);
        fixture.assertDelivery(output, "tail", "warehouse");
        var tables = fixture.tables(own);
        assertSharedIds(own, 15, tables.warehouse(), tables.workshop());
    }

    private void importedTopic(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ImportedTopicFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "imported_topic_reference", "v" + version + "_");
        var own = baseline.peer("imported_topic_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.model, "newClass");
        fixture.schema(own);
        try (var refSql = baseline.connect(); var ownSql = own.connect()) {
            DbSchemaCatalog.read(refSql, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(ownSql, own.postgis(), own.schema()));
        }
        fixture.removeSources();
        Path nativeInput = Files.writeString(work.resolve("native.xtf"), Files.readString(fixture.survey).replace("fixture-", "native-"));
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "catalog", fixture.catalog);
            importDataset(reference, db, "legacy", fixture.survey);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", output);
            fixture.assertDelivery(output, "fixture");

            assertCliSuccess(db, "import", fixture.model, "native", nativeInput);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native");
            fixture.assertStoredReferences(db, 2);
        }
    }
}
