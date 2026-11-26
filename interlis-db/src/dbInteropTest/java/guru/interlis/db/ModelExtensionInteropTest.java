package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.InteropCommands.assertSharedIds;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModelExtensionInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { exchange(23, null, false); }
    @Test void geoPackage24() throws Exception { exchange(24, null, false); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container(), false); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container(), false); }
    @Test void strictGeoPackage23() throws Exception { exchange(23, null, true); }
    @Test void strictGeoPackage24() throws Exception { exchange(24, null, true); }
    @Test void strictPostgis23() throws Exception { exchange(23, POSTGIS.container(), true); }
    @Test void strictPostgis24() throws Exception { exchange(24, POSTGIS.container(), true); }
    @Test void smart2GeoPackage23() throws Exception { exchangeSmart2(23, null); }
    @Test void smart2GeoPackage24() throws Exception { exchangeSmart2(24, null); }
    @Test void smart2Postgis23() throws Exception { exchangeSmart2(23, POSTGIS.container()); }
    @Test void smart2Postgis24() throws Exception { exchangeSmart2(24, POSTGIS.container()); }

    private void exchangeSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new Smart2ExtensionFixtures(work, version);
        var db = new TestDatabase(work, postgres, "append_smart2", "v" + version + "_");
        var expected = db.peer("append_smart2_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.original, "smart2");
        schema(reference, expected, work, fixture.original, "smart2");
        importDataset(reference, db, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        schema(reference, expected, work, fixture.added, "smart2");
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertCliSuccess(db, "schemaimport", fixture.added, null, fixture.addedSource, "--add-models");
            var after = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertEquals(before.relations().get("settings"), after.relations().get("settings"));
            DbSchemaCatalog.read(expectedSql, expected.postgis(), expected.schema()).assertSameAs(after);
            assertEquals(original, fixture.originalRows(db));
        }

        var output = work.resolve("original-native.xtf");
        assertCliSuccess(db, "export", fixture.original, "original", output);
        fixture.assertDelivery(output, fixture.original, "original");
        importDataset(reference, db, "legacy", fixture.input(fixture.added, "legacy"));
        output = work.resolve("legacy-native.xtf");
        assertCliSuccess(db, "export", fixture.added, "legacy", output);
        fixture.assertDelivery(output, fixture.added, "legacy");
        assertCliSuccess(db, "import", fixture.added, "native", fixture.input(fixture.added, "native"));
        output = work.resolve("native-reference.xtf");
        exportDataset(reference, db, "native", output);
        fixture.assertDelivery(output, fixture.added, "native");
        importDataset(reference, db, "tail", fixture.input(fixture.added, "tail"));
        output = work.resolve("tail-native.xtf");
        assertCliSuccess(db, "export", fixture.added, "tail", output);
        fixture.assertDelivery(output, fixture.added, "tail");
        assertEquals(original, fixture.originalRows(db));
        assertSharedIds(db, 16, tableFor(db, fixture.original + ".Data.Entry"), tableFor(db, fixture.original + ".Data.Alternate"),
                tableFor(db, fixture.added + ".Data.Entry"), tableFor(db, fixture.added + ".Data.Alternate"));
    }

    private void exchange(int version, PostgreSQLContainer postgres, boolean strict) throws Exception {
        var fixture = new ModelExtensionFixtures(work, version, strict);
        var db = new TestDatabase(work, postgres, strict ? "append_strict_models" : "append_models", "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        if (strict) {
            reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                    "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", fixture.original);
        } else {
            schema(reference, db, work, fixture.original, "newClass");
        }
        importDataset(reference, db, "original", fixture.originalInput);

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var ids = fixture.originalIds(db, sql);
            fixture.extend(db);
            String newTable = fixture.assertExtension(db, sql, before, ids);
            fixture.removeSources();

            Path output = work.resolve("original-native.xtf");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertOriginalDelivery(output);
            output = work.resolve("original-reference.xtf");
            exportDataset(reference, db, "original", output);
            fixture.assertOriginalDelivery(output);

            assertCliSuccess(db, "import", fixture.added, "native", fixture.addedDelivery("native"));
            output = work.resolve("added-reference.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertAddedDelivery(output, "native");

            importDataset(reference, db, "tail", fixture.addedDelivery("tail"));
            output = work.resolve("added-native.xtf");
            assertCliSuccess(db, "export", fixture.added, "tail", output);
            fixture.assertAddedDelivery(output, "tail");
            fixture.assertOwnedDetails(db, sql, fixture.original, "report", "fixture");
            fixture.assertOwnedDetails(db, sql, fixture.added, newTable, "native");
            fixture.assertOwnedDetails(db, sql, fixture.added, newTable, "tail");
            assertEquals(ids, fixture.originalIds(db, sql));
            assertSharedIds(db, 24, "report", newTable, "detail");
        }
    }
}
