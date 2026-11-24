package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.ChbaseFixtures.withPrefix;
import static guru.interlis.db.ChbaseTextFixtures.CAPTIONS;
import static guru.interlis.db.ChbaseTextFixtures.NOTES;
import static guru.interlis.db.ChbaseTextFixtures.SUFFIXES;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseTextStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void storedLanguageColumnsAndRollback(Backend backend, int version) throws Exception {
        var fixture = new ChbaseTextFixtures(work, version);
        var db = POSTGIS.target(work.resolve("chbase-text.gpkg"), backend);
        fixture.schema(db);
        run(db, fixture, "import", "original", fixture.input);
        String plaque = db.table("plaque");
        String cards = db.table("memocard");
        try (var sql = db.connect()) {
            for (String suffix : SUFFIXES) {
                String lang = suffix.isEmpty() ? "" : suffix.substring(1);
                assertEquals(CAPTIONS.get(lang), scalar(sql, "SELECT caption" + suffix + " FROM " + plaque
                        + " WHERE t_ili_tid='fixture-ml-value'"));
                execute(sql, "ALTER TABLE " + plaque + " RENAME COLUMN caption" + suffix + " TO stored_caption" + suffix);
            }
            String attrs = db.table("t_ili2db_attrname");
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + attrs + " WHERE colowner='plaque' AND sqlname='caption'"));
            execute(sql, "UPDATE " + attrs + " SET sqlname='stored_caption' WHERE colowner='plaque' AND sqlname='caption'");
            assertEquals(NOTES, scalar(sql, "SELECT notes_fr FROM " + plaque + " WHERE t_ili_tid='fixture-ml-value'"));
            assertNull(scalar(sql, "SELECT notes_de FROM " + plaque + " WHERE t_ili_tid='fixture-ml-value'"));
            assertEquals(NOTES, scalar(sql, "SELECT adescription FROM " + cards + " WHERE t_ili_tid='fixture-loc-value'"));
            assertNull(scalar(sql, "SELECT adescription_lang FROM " + cards + " WHERE t_ili_tid='fixture-loc-value'"));
            for (String table : List.of("multilingualtext", "localisedtext", "multilingualmtext", "localisedmtext")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }

        Path output = work.resolve("output.xtf");
        run(db, fixture, "import", "renamed", withPrefix(work, fixture.input, "renamed"));
        run(db, fixture, "export", "renamed", output);
        fixture.assertDelivery(output, "renamed", "Ponte");

        String language = version == 23 ? "Language" : "l:Language";
        for (String code : List.of("de", "es")) {
            var broken = withPrefix(work, fixture.input, code);
            Files.writeString(broken, Files.readString(broken)
                    .replace("<" + language + ">en", "<" + language + ">" + code));
            assertCliFailure(db, "import", fixture.multilingual, code, broken, code.equals("de")
                    ? "Multiple texts for language de" : "No stored language column for es", "--model", fixture.localised);
            try (var sql = db.connect()) {
                for (String table : List.of(plaque, cards, db.table("t_ili2db_basket"))) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM " + table + " WHERE t_ili_tid LIKE '" + code + "-%'"), table);
                }
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='" + code + "'"));
            }
        }

        var empty = withPrefix(work, fixture.input, "empty-text");
        // Only change the localised label. The multilingual caption also contains "Ponte".
        String text = Files.readString(empty);
        int caption = text.indexOf("<Caption>");
        Files.writeString(empty, text.substring(0, caption).replace(">Ponte<", "><") + text.substring(caption));
        run(db, fixture, "import", "empty-text", empty);
        run(db, fixture, "export", "empty-text", output);
        fixture.assertDelivery(output, "empty-text", "");

        try (var sql = db.connect()) {
            assertEquals("", scalar(sql, "SELECT label FROM " + cards + " WHERE t_ili_tid='empty-text-loc-value'"));
            assertEquals("it", scalar(sql, "SELECT label_lang FROM " + cards + " WHERE t_ili_tid='empty-text-loc-value'"));
            assertNull(scalar(sql, "SELECT label FROM " + cards + " WHERE t_ili_tid='empty-text-loc-empty'"));
            assertNull(scalar(sql, "SELECT label_lang FROM " + cards + " WHERE t_ili_tid='empty-text-loc-empty'"));
            execute(sql, "ALTER TABLE " + cards + " RENAME COLUMN label_lang TO missing_language");
        }
        byte[] previous = Files.readAllBytes(output);
        assertCliFailure(db, "export", fixture.multilingual, "original", output, "Missing physical column memocard.label_lang",
                "--model", fixture.localised);
        assertArrayEquals(previous, Files.readAllBytes(output));
    }

    private static void run(Target db, ChbaseTextFixtures fixture, String operation, String dataset, Path inputOrOutput) {
        var result = cli(db, operation, fixture.multilingual, dataset, inputOrOutput, CancellationToken.none(),
                "--model", fixture.localised);
        assertEquals(0, result.code(), result::diagnostics);
    }
}
