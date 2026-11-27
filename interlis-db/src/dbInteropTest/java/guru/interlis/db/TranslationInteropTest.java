package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.mergeDeliveries;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class TranslationInteropTest {
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

    @Test void geoPackageExtension23() throws Exception { extendAndExport(23, null); }
    @Test void geoPackageExtension24() throws Exception { extendAndExport(24, null); }
    @Test void postgisExtension23() throws Exception { extendAndExport(23, POSTGIS.container()); }
    @Test void postgisExtension24() throws Exception { extendAndExport(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new TranslationFixtures(work, version);
        var original = fixture.copy(fixture.original + ".ili");
        var translated = fixture.copy(fixture.translated + ".ili");
        var nestedOriginal = fixture.copy(fixture.nestedOriginal + ".ili");
        var nestedTranslated = fixture.copy(fixture.nestedTranslated + ".ili");
        var baseline = new TestDatabase(work, postgres, "translation_reference", "v" + version + "_");
        var own = baseline.peer("translation_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.translated + ";" + fixture.nestedTranslated, "newClass");
        fixture.scalarSchema(own, original, translated, "--model", fixture.nestedTranslated,
                "--model-file", nestedOriginal.toString(), "--model-file", nestedTranslated.toString());
        assertSameCatalog(baseline, own);
        for (var source : List.of(original, translated, nestedOriginal, nestedTranslated)) Files.delete(source);

        var scalarInput = fixture.scalarInput();
        var legacy = mergeDeliveries(work.resolve("mixed-legacy.xtf"), version, scalarInput, fixture.copy("nested" + version + ".xtf"));
        var nativeInput = Files.writeString(work.resolve("mixed-native.xtf"), Files.readString(legacy)
                .replace(fixture.scalarLabel + "-", "native-scalar-").replace("nested-", "native-nested-"));
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", legacy);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.translated, "legacy", output, "--model", fixture.nestedTranslated);
            fixture.assertMixedDelivery(output, fixture.scalarLabel, "nested", "text");
            assertCliSuccess(db, "export", fixture.translated, null, output, "--model", fixture.nestedTranslated);
            fixture.assertMixedDelivery(output, fixture.scalarLabel, "nested", "text");

            assertCliSuccess(db, "import", fixture.translated, "native", nativeInput, "--model", fixture.nestedTranslated);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertMixedDelivery(output, "native-scalar", "native-nested", "text");
            assertStoredValues(db, fixture);
        }

        assertCliSuccess(own, "update", fixture.translated, "legacy", fixture.updateScalar(scalarInput));
        var updated = work.resolve("mixed-updated-reference.xtf");
        exportDataset(reference, own, "legacy", updated);
        fixture.assertMixedDelivery(updated, fixture.scalarLabel, "nested", "updated");
    }

    private void extendAndExport(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new TranslationFixtures(work, version);
        var original = fixture.copy(fixture.original + ".ili");
        var translated = fixture.copy(fixture.translated + ".ili");
        var baseline = new TestDatabase(work, postgres, "translation_fresh", "v" + version + "_");
        var extended = baseline.peer("translation_extended");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.translated, "newClass");
        schema(reference, extended, work, fixture.original, "newClass");
        var existing = fixture.copy("original" + version + ".xtf");
        importDataset(reference, extended, "existing", existing);
        var ids = existingIds(extended);
        fixture.scalarSchema(extended, original, translated, "--add-models");
        Files.delete(original);
        Files.delete(translated);
        assertSameCatalog(baseline, extended);
        assertEquals(ids, existingIds(extended));

        var input = fixture.french ? fixture.scalarInput() : existing;
        var legacy = Files.writeString(work.resolve("extension-legacy.xtf"),
                Files.readString(input).replace(fixture.scalarLabel + "-", "legacy-"));
        var nativeInput = Files.writeString(work.resolve("extension-native.xtf"),
                Files.readString(input).replace(fixture.scalarLabel + "-", "native-"));
        importDataset(reference, extended, "legacy", legacy);
        var output = work.resolve("extension-legacy-export.xtf");
        assertCliSuccess(extended, "export", fixture.translated, "legacy", output);
        fixture.assertScalarDelivery(output, fixture.french, "legacy", "text");
        assertCliSuccess(extended, "import", fixture.translated, "native", nativeInput);
        output = work.resolve("extension-native-export.xtf");
        exportDataset(reference, extended, "native", output);
        fixture.assertScalarDelivery(output, fixture.french, "native", "text");

        assertOutputLanguage(fixture, reference, extended);
        assertEquals(ids, existingIds(extended));
    }

    private void assertOutputLanguage(TranslationFixtures fixture, ReferenceTool reference, TestDatabase db) throws Exception {
        String language = fixture.french ? fixture.translated : fixture.original;
        var expected = work.resolve("existing-reference.xtf");
        exportDataset(reference, db, "existing", expected, "--exportModels", language);
        fixture.assertScalarDelivery(expected, fixture.french, "original", "text");

        var output = work.resolve("existing-native.xtf");
        assertCliSuccess(db, "export", fixture.french ? fixture.original : fixture.translated, "existing", output,
                "--export-model", language);
        fixture.assertScalarDelivery(output, fixture.french, "original", "text");
        if (fixture.french) {
            var converted = Files.writeString(work.resolve("converted.xtf"), Files.readString(output).replace("original-", "converted-"));
            importDataset(reference, db, "converted", converted);
            var readBack = work.resolve("converted-native.xtf");
            assertCliSuccess(db, "export", fixture.translated, "converted", readBack);
            fixture.assertScalarDelivery(readBack, true, "converted", "text");

            expected = work.resolve("legacy-original-reference.xtf");
            exportDataset(reference, db, "legacy", expected, "--exportModels", fixture.original);
            fixture.assertScalarDelivery(expected, false, "legacy", "text");
            output = work.resolve("legacy-original-native.xtf");
            assertCliSuccess(db, "export", fixture.translated, "legacy", output, "--export-model", fixture.original);
            fixture.assertScalarDelivery(output, false, "legacy", "text");
        }
    }

    private static List<String> existingIds(TestDatabase db) throws Exception {
        try (var sql = db.connect()) {
            return List.of(scalar(sql, "SELECT t_id FROM " + db.table("station") + " WHERE t_ili_tid='original-o' AND memo='text' AND astate='open'"),
                    scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='original-b'"),
                    scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='existing'"));
        }
    }

    private static void assertStoredValues(TestDatabase db, TranslationFixtures fixture) throws Exception {
        assertEquals("station", tableFor(db, fixture.original + ".Survey.Station"));
        assertNull(tableFor(db, fixture.translated + ".Releve.Poste"));
        String topic = fixture.french ? fixture.translated + ".Releve" : fixture.original + ".Survey";
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("station") + " s JOIN "
                    + db.table("t_ili2db_basket") + " b ON b.t_id=s.t_basket WHERE b.topic='" + topic
                    + "' AND s.astate='open' AND s.t_ili_tid IN ('" + fixture.scalarLabel + "-o','native-scalar-o')"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("reading") + " r JOIN "
                    + db.table("details") + " p ON p.reading_payload=r.t_id AND p.t_basket=r.t_basket JOIN "
                    + db.table("detail") + " d ON d.details_entry=p.t_id AND d.t_basket=r.t_basket JOIN "
                    + db.table("site") + " s ON s.t_id=d.target AND s.t_id=r.atsite AND s.t_basket=r.t_basket"
                    + " WHERE d.memo='inside'"));
            assertEquals("2056", scalar(sql, db.postgis()
                    ? "SELECT DISTINCT ST_SRID(location) FROM " + db.table("reading")
                    : "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name='reading' AND column_name='location'"));
        }
    }

    private static void assertSameCatalog(TestDatabase baseline, TestDatabase own) throws Exception {
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
    }
}
