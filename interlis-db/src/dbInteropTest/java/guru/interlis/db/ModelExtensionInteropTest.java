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
