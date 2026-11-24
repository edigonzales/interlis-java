package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.StructureMappingFixtures.field;
import static guru.interlis.db.StructureMappingFixtures.jsonKey;
import static guru.interlis.db.StructureMappingFixtures.members;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StructureMappingCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;
    private final StructureMappingFixtures fixtures;

    StructureMappingCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
        fixtures = new StructureMappingFixtures(work, version);
    }

    void schema(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "ExpandSchema" + version;
        var ili = fixtures.expandModel(model);
        referenceSchema(baseline, model, "--expandStruct");
        createSchema(own, model, ili, "--expand-struct");
        assertSameCatalog(baseline, own);
        Path output = work.resolve("out.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", fixtures.expandDataset(model, "legacy"));
            assertCliSuccess(db, "export", model, "legacy", output);
            fixtures.assertExpanded(output, "legacy");
            assertCliSuccess(db, "import", model, "native", fixtures.expandDataset(model, "native"));
            referenceExport(db, "native", output);
            fixtures.assertExpanded(output, "native");
            if (version == 24) {
                var value = fixtures.objects(output, "native").get("native-value");
                assertEquals(3, children(child(value, "Bagged")).size());
            }
            if (db == own) {
                referenceImport(db, "tail", fixtures.expandDataset(model, "tail"));
                assertCliSuccess(db, "export", model, "tail", output);
                fixtures.assertExpanded(output, "tail");
            }
            try (var sql = db.connect()) {
                String row = " FROM " + db.table("asset") + " WHERE t_ili_tid='native-value'";
                assertEquals("ordinary", scalar(sql, "SELECT detail0_label" + row));
                assertEquals("é & a", scalar(sql, "SELECT detail0_label1" + row));
                assertEquals("b", scalar(sql, "SELECT items4_label" + row));
                assertEquals("entry", scalar(sql, "SELECT bagged2_t_type" + row));
                assertEquals(db == own ? "3" : "2", scalar(sql, "SELECT count(*) FROM " + db.table("entry")
                        + " WHERE asset_larger IS NOT NULL"));
                assertSharedIds(db, sql, db == own ? "24" : "16", "asset", "entry");
            }
        }

        String appended = "ExpandAppend" + version;
        var addedModel = fixtures.source(appended, """
                    !!@ili2db.mapping=EXPAND
                    Detail : Entry;
                """);
        createSchema(own, appended, addedModel, "--add-models");
        Path input = fixtures.dataset(appended, "added", fixtures.asset(appended, "added-value",
                field("Detail", fixtures.entry(appended, field("Label", "appended")))));
        referenceImport(own, "added", input);
        assertCliSuccess(own, "export", appended, "added", output);
        var row = fixtures.objects(output, "added").get("added-value");
        assertEquals("appended", child(members(row, "Detail").getFirst(), "Label").getTextContent());
        try (var sql = own.connect()) {
            assertEquals("expand", scalar(sql, "SELECT setting FROM " + own.table("t_ili2db_trafo")
                    + " WHERE iliname='" + appended + ".Data.Asset.Detail' AND tag='ch.ehi.ili2db.structTrafo'"));
        }
    }

    private static void createSchema(TestDatabase db, String model, Path ili, String... options) {
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private static void assertSameCatalog(TestDatabase baseline, TestDatabase own) throws Exception {
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
    }

    private static void assertSharedIds(TestDatabase db, Connection sql, String count, String... tables) throws Exception {
        var allTables = new ArrayList<>(List.of(tables));
        allTables.addAll(List.of("t_ili2db_basket", "t_ili2db_dataset"));
        var ids = allTables.stream().map(table -> "SELECT t_id FROM " + db.table(table)).collect(Collectors.joining(" UNION ALL "));
        assertEquals(count, scalar(sql, "SELECT count(*) FROM (" + ids + ") ids"));
        assertEquals(count, scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + ids + ") ids"));
    }

    void smart2(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "ExpandSmart" + version;
        var ili = fixtures.smartModel(model);
        referenceSchema(baseline, model, "--smart2Inheritance", "--expandStruct");
        createSchema(own, model, ili, "--inheritance", "smart2", "--expand-struct");
        assertSameCatalog(baseline, own);
        Path output = work.resolve("out.xtf");
        var initial = List.of("first", "second");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", fixtures.smartDataset(model, "legacy", initial));
            assertCliSuccess(db, "export", model, "legacy", output);
            fixtures.assertSmart(output, "legacy", initial);
            assertCliSuccess(db, "import", model, "native", fixtures.smartDataset(model, "native", initial));
            referenceExport(db, "native", output);
            fixtures.assertSmart(output, "native", initial);
            if (db == own) {
                var updated = List.of("updated");
                assertCliSuccess(db, "update", model, "native", fixtures.smartDataset(model, "native", updated));
                referenceExport(db, "native", output);
                fixtures.assertSmart(output, "native", updated);
            }
        }
    }

    void jsonCollections(TestDatabase db) throws Exception {
        String model = "JsonCollections" + version;
        fixtures.collectionsModel(model);
        referenceSchema(db, model, "--coalesceJson");
        Path output = work.resolve("out.xtf");
        referenceImport(db, "legacy", fixtures.jsonCollectionDataset(model, "legacy", true));
        assertCliSuccess(db, "export", model, "legacy", output);
        fixtures.assertJsonCollections(output, "legacy", true);
        assertCliSuccess(db, "import", model, "native", fixtures.jsonCollectionDataset(model, "native", true));
        assertCliSuccess(db, "export", model, "native", output);
        fixtures.assertJsonCollections(output, "native", true);

        // ili2db 5.5.2 writes a nested object but its JSON parser fails to advance the parent token.
        reference.expectFailure(db, "%ILI_FROM_DB", "unexpected json token END_OBJECT; '}' expected",
                "--export", "--dataset", "legacy", "--exportTid", work.resolve("nested-reference.xtf").toString());
        assertCliSuccess(db, "import", model, "mixed", fixtures.jsonCollectionDataset(model, "mixed", false));
        referenceExport(db, "mixed", output);
        fixtures.assertJsonCollections(output, "mixed", false);
        try (var sql = db.connect()) {
            String itemsType = db.postgis() ? "jsonb_typeof(items)" : "json_type(items)";
            String count = db.postgis() ? "jsonb_array_length(items)" : "json_array_length(items)";
            String asset = db.table("asset");
            for (String prefix : List.of("legacy", "native")) {
                var one = " FROM " + asset + " WHERE t_ili_tid='" + prefix + "-one'";
                assertEquals("array", scalar(sql, "SELECT " + itemsType + one));
                assertEquals("1", scalar(sql, "SELECT " + count + one));
                assertEquals("3", scalar(sql, "SELECT " + count + " FROM " + asset + " WHERE t_ili_tid='" + prefix + "-many'"));
            }
            String detailType = db.postgis() ? "nest->'Detail'->>'@type'" : "json_extract(nest, '$.Detail.@type')";
            assertEquals(model + ".Data.Entry", scalar(sql, "SELECT " + detailType + " FROM " + asset + " WHERE t_ili_tid='legacy-many'"));
            assertEquals(model + ".Data.Entry", scalar(sql, "SELECT " + detailType + " FROM " + asset + " WHERE t_ili_tid='native-many'"));
            assertEquals(scalar(sql, "SELECT nest FROM " + asset + " WHERE t_ili_tid='legacy-many'"),
                    scalar(sql, "SELECT nest FROM " + asset + " WHERE t_ili_tid='native-many'"));
        }
    }

    void jsonSchema(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "JsonSchema" + version;
        var ili = fixtures.mixedModel(model);
        referenceSchema(baseline, model, "--expandStruct", "--coalesceJson");
        createSchema(own, model, ili, "--expand-struct", "--coalesce-json");
        assertSameCatalog(baseline, own);
        Path output = work.resolve("out.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", fixtures.mixedDataset(model, "legacy", false));
            assertCliSuccess(db, "export", model, "legacy", output);
            fixtures.assertMixed(output, "legacy", false);
            assertCliSuccess(db, "import", model, "native", fixtures.mixedDataset(model, "native", false));
            referenceExport(db, "native", output);
            fixtures.assertMixed(output, "native", false);
            try (var sql = db.connect()) {
                var asset = db.table("asset");
                var row = " FROM " + asset + " WHERE t_ili_tid='native-value'";
                assertEquals("entry", scalar(sql, "SELECT items1_t_type" + row));
                assertEquals("é & a", scalar(sql, "SELECT " + jsonKey(db, "doc", "Label") + row));
                assertNull(scalar(sql, "SELECT doc FROM " + asset + " WHERE t_ili_tid='native-absent'"));
                assertEquals(model + ".Data.Entry", scalar(sql, "SELECT " + jsonKey(db, "doc", "@type")
                        + " FROM " + asset + " WHERE t_ili_tid='native-empty-doc'"));
            }
            if (db == own) {
                referenceImport(db, "tail", fixtures.mixedDataset(model, "tail", false));
                assertCliSuccess(db, "export", model, "tail", output);
                fixtures.assertMixed(output, "tail", false);
                try (var sql = db.connect()) {
                    assertSharedIds(db, sql, "15", "asset");
                }
            }
        }

        var appended = "JsonAppend" + version;
        createSchema(own, appended, fixtures.mixedModel(appended), "--add-models");
        assertCliSuccess(own, "import", appended, "added", fixtures.mixedDataset(appended, "added", false));
        referenceExport(own, "added", output);
        fixtures.assertMixed(output, "added", false);
        try (var sql = own.connect()) {
            var metadata = own.table("t_ili2db_trafo");
            assertEquals("coalesce", scalar(sql, "SELECT setting FROM " + metadata
                    + " WHERE iliname='" + appended + ".Data.Asset.Doc' AND tag='ch.ehi.ili2db.jsonTrafo'"));
            assertEquals("expand", scalar(sql, "SELECT setting FROM " + metadata
                    + " WHERE iliname='" + appended + ".Data.Asset.Items' AND tag='ch.ehi.ili2db.structTrafo'"));
        }
    }

    private void referenceSchema(TestDatabase db, String models, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", models));
        options.addAll(List.of(flags));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
