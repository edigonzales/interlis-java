package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TranslationStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsBrokenOriginalSources(int version, Backend backend) throws Exception {
        var fixture = new TranslationFixtures(work, version);
        var db = POSTGIS.target(work.resolve("translation-sources.gpkg"), backend);
        createSchema(db, fixture);
        var input = fixture.copy("original" + version + ".xtf");
        assertCliSuccess(db, "import", fixture.original, "original", input, "--model", fixture.translated);
        var output = work.resolve("sources-export.xtf");
        assertCliSuccess(db, "export", fixture.translated, "original", output, "--model", fixture.original);
        fixture.assertScalarDelivery(output, false, "original", "text");
        var published = Files.readAllBytes(output);
        String catalog = db.table("t_ili2db_model");
        try (var sql = db.connect()) {
            assertEquals(fixture.translated + "{ " + fixture.original + "}", scalar(sql, "SELECT modelname FROM " + catalog
                    + " WHERE modelname LIKE '" + fixture.translated + "%'"));
            execute(sql, "UPDATE " + catalog + " SET content=replace(content, 'VERSION \"1\"', 'VERSION \"2\"')"
                    + " WHERE modelname='" + fixture.original + "'");
            assertRejected(db, fixture.translated, "original", output, published, "Cannot compile", "--model", fixture.original);
            execute(sql, "UPDATE " + catalog + " SET content=replace(content, 'VERSION \"2\"', 'VERSION \"1\"')"
                    + " WHERE modelname='" + fixture.original + "'");
            execute(sql, "INSERT INTO " + catalog + " (filename,iliversion,modelname,content,importdate)"
                    + " SELECT filename,iliversion,modelname||' Ambiguous',content,importdate FROM " + catalog
                    + " WHERE modelname='" + fixture.original + "'");
            assertRejected(db, fixture.translated, "original", output, published, "Multiple MODEL sources for " + fixture.original,
                    "--model", fixture.original);
            execute(sql, "DELETE FROM " + catalog + " WHERE modelname LIKE '" + fixture.original + "%'");
            assertRejected(db, fixture.translated, "original", output, published, "No MODEL source for " + fixture.original,
                    "--model", fixture.original);
        }
    }

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsUnavailableOutputLanguage(int version, Backend backend) throws Exception {
        var fixture = new TranslationFixtures(work, version);
        var db = POSTGIS.target(work.resolve("translation-language.gpkg"), backend);
        createSchema(db, fixture);
        assertCliSuccess(db, "import", fixture.translated, "language", fixture.scalarInput());
        var output = work.resolve("language-export.xtf");
        assertCliSuccess(db, "export", fixture.translated, "language", output);
        fixture.assertScalarDelivery(output, fixture.french, fixture.scalarLabel, "text");
        assertRejected(db, fixture.original, "language", output, Files.readAllBytes(output),
                fixture.french ? "outside the selected model" : "require the original model", "--export-model", fixture.translated);
    }

    private static void createSchema(Target db, TranslationFixtures fixture) throws Exception {
        var original = fixture.copy(fixture.original + ".ili");
        var translated = fixture.copy(fixture.translated + ".ili");
        fixture.scalarSchema(db, original, translated);
        Files.delete(original);
        Files.delete(translated);
    }

    private static void assertRejected(Target db, String model, String dataset, Path output, byte[] published,
                                       String diagnostic, String... options) throws Exception {
        assertCliFailure(db, "export", model, dataset, output, diagnostic, options);
        assertArrayEquals(published, Files.readAllBytes(output));
    }
}
