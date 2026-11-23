package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseTextCases {
    private static final List<String> SUFFIXES = List.of("", "_de", "_fr", "_rm", "_it", "_en");
    private static final Map<String, String> CAPTIONS = Map.of("", "Default", "de", "Brücke", "fr", "Pont",
            "rm", "Punt", "it", "Ponte", "en", "Bridge");
    private static final String NOTES = "Première ligne\nDeuxième ligne";

    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ChbaseTextCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void multilingual(TestDatabase db) throws Exception {
        String model = "ChbaseMultilingual" + version;
        fixture("Localisation" + version + ".ili");
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        referenceSchema(db, model, "--expandMultilingual");
        referenceImport(db, "legacy", input);

        var output = work.resolve("output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertMultilingual(output, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertMultilingual(output, "native");

        try (var sql = db.connect()) {
            String plaque = db.table("plaque");
            for (var suffix : SUFFIXES) {
                String lang = suffix.isEmpty() ? "" : suffix.substring(1);
                assertEquals(CAPTIONS.get(lang), scalar(sql, "SELECT caption" + suffix + " FROM " + plaque
                        + " WHERE t_ili_tid='native-value'"));
                execute(sql, "ALTER TABLE " + plaque + " RENAME COLUMN caption" + suffix + " TO stored_caption" + suffix);
            }
            String attrs = db.table("t_ili2db_attrname");
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + attrs + " WHERE colowner='plaque' AND sqlname='caption'"));
            execute(sql, "UPDATE " + attrs + " SET sqlname='stored_caption' WHERE colowner='plaque' AND sqlname='caption'");
            assertEquals(NOTES, scalar(sql, "SELECT notes_fr FROM " + plaque + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT notes_de FROM " + plaque + " WHERE t_ili_tid='native-value'"));
            for (String table : List.of("multilingualtext", "localisedtext", "multilingualmtext", "localisedmtext")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }
        assertCliSuccess(db, "import", model, "renamed", withPrefix(input, "renamed"));
        assertCliSuccess(db, "export", model, "renamed", output);
        assertMultilingual(output, "renamed");

        String language = version == 23 ? "Language" : "l:Language";
        for (var code : List.of("de", "es")) {
            var broken = withPrefix(input, code);
            Files.writeString(broken, Files.readString(broken).replace("<" + language + ">en", "<" + language + ">" + code));
            assertCliFailure(db, "import", model, code, broken, code.equals("de")
                    ? "Multiple texts for language de" : "No stored language column for es");
            try (var sql = db.connect()) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("plaque") + " WHERE t_ili_tid LIKE '" + code + "-%'"));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='" + code + "-b'"));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='" + code + "'"));
            }
        }
    }

    void localised(TestDatabase db) throws Exception {
        String model = "ChbaseLocalised" + version;
        fixture("Localisation" + version + ".ili");
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        referenceSchema(db, model, "--expandLocalised");
        referenceImport(db, "legacy", input);
        var output = work.resolve("localised.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertLocalised(output, "fixture", "Ponte");

        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertLocalised(output, "native", "Ponte");

        var empty = withPrefix(input, "empty-text");
        Files.writeString(empty, Files.readString(empty).replace(">Ponte<", "><"));
        assertCliSuccess(db, "import", model, "empty-text", empty);
        assertCliSuccess(db, "export", model, "empty-text", output);
        assertLocalised(output, "empty-text", "");

        try (var sql = db.connect()) {
            var cards = db.table("memocard");
            assertEquals("", scalar(sql, "SELECT label FROM " + cards + " WHERE t_ili_tid='empty-text-value'"));
            assertEquals("it", scalar(sql, "SELECT label_lang FROM " + cards + " WHERE t_ili_tid='empty-text-value'"));
            assertEquals(NOTES, scalar(sql, "SELECT adescription FROM " + cards + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT adescription_lang FROM " + cards + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT label FROM " + cards + " WHERE t_ili_tid='native-empty'"));
            assertNull(scalar(sql, "SELECT label_lang FROM " + cards + " WHERE t_ili_tid='native-empty'"));
            execute(sql, "ALTER TABLE " + cards + " RENAME COLUMN label_lang TO missing_language");
        }
        var previous = Files.readString(output);
        assertCliFailure(db, "export", model, "legacy", output, "Missing physical column memocard.label_lang");
        assertEquals(previous, Files.readString(output));
    }

    void schema(TestDatabase baseline, TestDatabase own) throws Exception {
        String multilingual = "ChbaseMultilingual" + version;
        String localised = "ChbaseLocalised" + version;
        var base = fixture("Localisation" + version + ".ili");
        var source = fixture(multilingual + ".ili");
        var other = fixture(localised + ".ili");
        referenceSchema(baseline, multilingual + ";" + localised, "--expandMultilingual", "--expandLocalised");
        var result = schemaWithIdentityColumns(own, multilingual, source, CancellationToken.none(),
                "--model", localised, "--model-file", other.toString(), "--model-file", base.toString(),
                "--expand-multilingual", "--expand-localised");
        assertEquals(0, result.code(), result::diagnostics);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        for (String model : List.of(multilingual, localised)) {
            var input = fixture(model + ".xtf");
            var output = work.resolve(model + "-output.xtf");
            referenceImport(own, model + "-legacy", input);
            assertCliSuccess(own, "export", model, model + "-legacy", output);
            assertText(output, model, "fixture");
            assertCliSuccess(own, "import", model, model + "-native", withPrefix(input, model + "-native"));
            referenceExport(own, model + "-native", output);
            assertText(output, model, model + "-native");
            referenceImport(own, model + "-tail", withPrefix(input, model + "-tail"));
            assertCliSuccess(own, "export", model, model + "-tail", output);
            assertText(output, model, model + "-tail");
        }
        try (var sql = own.connect()) {
            var ids = List.of("plaque", "memocard", "t_ili2db_basket", "t_ili2db_dataset").stream()
                    .map(table -> "SELECT t_id FROM " + own.table(table)).collect(Collectors.joining(" UNION ALL "));
            assertEquals("24", scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + ids + ") ids"));
        }
    }

    private void assertText(Path output, String model, String prefix) throws Exception {
        if (model.startsWith("ChbaseMultilingual")) assertMultilingual(output, prefix);
        else assertLocalised(output, prefix, "Ponte");
    }

    private void assertLocalised(Path output, String prefix, String label) throws Exception {
        var objects = objects(output, prefix);
        var present = objects.get(prefix + "-value");
        var missing = objects.get(prefix + "-empty");
        var caption = children(child(present, "Label")).getFirst();
        assertStructure(caption, "LocalisedText");
        assertEquals(label, child(caption, "Text").getTextContent());
        assertEquals("it", child(caption, "Language").getTextContent());
        var description = children(child(present, "Description")).getFirst();
        assertStructure(description, "LocalisedMText");
        assertEquals(NOTES, child(description, "Text").getTextContent());
        assertNull(child(description, "Language"));
        assertNull(child(missing, "Label"));
        assertNull(child(missing, "Description"));
    }

    private void assertMultilingual(Path output, String prefix) throws Exception {
        var samples = objects(output, prefix);
        assertNull(child(samples.get(prefix + "-empty"), "Caption"));
        assertNull(child(samples.get(prefix + "-empty"), "Notes"));
        var plaque = samples.get(prefix + "-value");
        assertEquals(CAPTIONS, translations(plaque, "Caption", "Text"));
        assertEquals(Map.of("fr", NOTES), translations(plaque, "Notes", "MText"));
    }

    private Map<String, String> translations(Element object, String field, String type) {
        var wrapper = children(child(object, field)).getFirst();
        assertStructure(wrapper, "Multilingual" + type);
        return children(wrapper).stream().flatMap(bag -> children(bag).stream()).collect(Collectors.toMap(entry -> {
            assertStructure(entry, "Localised" + type);
            var language = child(entry, "Language");
            return language == null ? "" : language.getTextContent();
        }, entry -> child(entry, "Text").getTextContent()));
    }

    private void assertStructure(Element element, String type) {
        assertEquals(version == 23 ? "LocalisationCH_V1." + type : type, element.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/LocalisationCH_V2", element.getNamespaceURI());
    }

    private Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var objects = children(basket).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
        assertEquals(2, objects.size());
        assertNotNull(objects.get(prefix + "-value"));
        assertNotNull(objects.get(prefix + "-empty"));
        return objects;
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var source = getClass().getResourceAsStream("/chbase/" + name)) {
            Files.copy(source, path);
        }
        return path;
    }

    private Path withPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input).replace("fixture-", prefix + "-"));
    }

    private void referenceSchema(TestDatabase db, String model, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
        options.addAll(List.of(flags));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
