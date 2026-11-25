package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void mutatesTranslatedBasketWithStableIds(int version, Backend backend) throws Exception {
        var fixture = new TranslationFixtures(work, version);
        var db = POSTGIS.target(work.resolve("translation-mutation.gpkg"), backend);
        createSchema(db, fixture);
        var input = fixture.scalarInput();
        assertCliSuccess(db, "import", fixture.translated, "translation", input);
        var output = work.resolve("translation-mutation-export.xtf");
        try (var sql = db.connect()) {
            var before = ids(db, sql);
            assertCliSuccess(db, "update", fixture.translated, "translation", fixture.updateScalar(input));
            assertEquals(before, ids(db, sql));
            assertEquals("updated", scalar(sql, "SELECT memo FROM " + db.table("station")));
            if (fixture.french) {
                var wrongLanguage = Files.writeString(work.resolve("wrong-language.xtf"),
                        Files.readString(fixture.copy("original23.xtf")).replace("original-", "translated-"));
                assertCliFailure(db, "update", fixture.original, "translation", wrongLanguage, "another dataset or topic");
                assertCliFailure(db, "delete", fixture.original, "translation", null, "outside the selected model");
                assertEquals(before, ids(db, sql));
            }
            assertCliSuccess(db, "export", fixture.translated, "translation", output);
            fixture.assertScalarDelivery(output, fixture.french, fixture.scalarLabel, "updated");

            assertCliSuccess(db, "replace", fixture.translated, "translation", input);
            var replaced = ids(db, sql);
            assertEquals(before.dataset(), replaced.dataset());
            assertNotEquals(before.object(), replaced.object());
            assertNotEquals(before.basket(), replaced.basket());
            assertCliSuccess(db, "export", fixture.translated, "translation", output);
            fixture.assertScalarDelivery(output, fixture.french, fixture.scalarLabel, "text");
            assertCliSuccess(db, "delete", fixture.translated, "translation", null);
            for (var table : List.of("station", "t_ili2db_basket", "t_ili2db_dataset")) {
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table(table)), table);
            }
        }
    }

    private record TranslationIds(String object, String basket, String dataset) { }

    private static TranslationIds ids(Target db, Connection sql) throws Exception {
        var object = scalar(sql, "SELECT t_id FROM " + db.table("station"));
        var basket = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket"));
        var dataset = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='translation'");
        assertNotNull(object);
        assertNotNull(basket);
        assertNotNull(dataset);
        assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + db.table("station")));
        assertEquals(dataset, scalar(sql, "SELECT dataset FROM " + db.table("t_ili2db_basket")));
        return new TranslationIds(object, basket, dataset);
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
