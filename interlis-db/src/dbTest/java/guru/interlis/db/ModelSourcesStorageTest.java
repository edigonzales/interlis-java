package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelSourcesStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void modelSourcesAndUnselectedTopic(Backend backend, int version) throws Exception {
        var fixture = new ModelSourceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("sources.gpkg"), backend);
        var failed = schemaWithIdentityColumns(db, fixture.stored, fixture.storedFile, CancellationToken.none(),
                "--model-file", fixture.middleFile.toString());
        assertEquals(1, failed.code(), failed::diagnostics);
        assertTrue(failed.diagnostics().contains(fixture.types), failed::diagnostics);
        fixture.schema(db);
        fixture.removeSources();
        fixture.run(db, "import", "native", fixture.input);
        Path output = work.resolve("published.xtf");
        fixture.run(db, "export", "native", output);
        fixture.assertDelivery(output, "fixture");
        byte[] published = Files.readAllBytes(output);

        try (var sql = db.connect()) {
            String baskets = db.table("t_ili2db_basket");
            execute(sql, "UPDATE " + baskets + " SET topic='DbSourceTypes" + version
                    + ".Unused' WHERE t_ili_tid='fixture-shared-basket'");
            assertCliFailure(db, "delete", fixture.shared, "native", null,
                    "outside the selected model: DbSourceTypes" + version + ".Unused", fixture.extraModels());
            for (String table : List.of("station", "importedstation", "report", "t_ili2db_dataset")) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + baskets));
            execute(sql, "UPDATE " + baskets + " SET topic='" + fixture.shared + ".Data' WHERE t_ili_tid='fixture-shared-basket'");

            String models = db.table("t_ili2db_model");
            execute(sql, "INSERT INTO " + models + " (filename,iliversion,modelname,content,importdate)"
                    + " SELECT filename,iliversion,modelname||' DbDuplicate',content,importdate FROM " + models
                    + " WHERE modelname='" + fixture.types + "'");
            assertCliFailure(db, "export", fixture.shared, "native", output,
                    "Multiple MODEL sources for " + fixture.types, fixture.extraModels());
            assertArrayEquals(published, Files.readAllBytes(output));
            execute(sql, "DELETE FROM " + models + " WHERE modelname LIKE '" + fixture.types + "%'");
            assertCliFailure(db, "export", fixture.shared, "native", output,
                    "No MODEL source for " + fixture.types, fixture.extraModels());
            assertArrayEquals(published, Files.readAllBytes(output));
        }
    }
}
