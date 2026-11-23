package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class StructureMappingCases {
    private static final String VALUES = "<Label>é &amp; a</Label><Flag>true</Flag><Code>high</Code><Quantity>-2</Quantity>";

    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    StructureMappingCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void single(TestDatabase db) throws Exception {
        String model = "ExpandSingle" + version;
        source(model, """
                    !!@ili2db.mapping=EXPAND
                    Detail : Entry;
                """);
        referenceSchema(db, model, "--expandStruct");
        Path output = work.resolve("out.xtf");
        referenceImport(db, "legacy", singleDataset(model, "legacy", "Detail"));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSingle(output, "legacy", "Detail");

        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN detail0_label TO stored_label");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_label' WHERE sqlname='detail0_label'");
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop") + " SET columnname='stored_label' WHERE columnname='detail0_label'");
        }
        assertCliSuccess(db, "import", model, "native", singleDataset(model, "native", "Detail"));
        referenceExport(db, "native", output);
        assertSingle(output, "native", "Detail");
        try (var sql = db.connect()) {
            String asset = db.table("asset");
            assertEquals("entry", scalar(sql, "SELECT detail0_t_type FROM " + asset + " WHERE t_ili_tid='native-empty'"));
            assertNull(scalar(sql, "SELECT stored_label FROM " + asset + " WHERE t_ili_tid='native-empty'"));
            assertNull(scalar(sql, "SELECT detail0_t_type FROM " + asset + " WHERE t_ili_tid='native-missing'"));
            assertEquals("é & a", scalar(sql, "SELECT stored_label FROM " + asset + " WHERE t_ili_tid='native-value'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("entry")));
            execute(sql, "UPDATE " + asset + " SET detail0_t_type='unknown' WHERE t_ili_tid='native-value'");
        }
        String previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, "Unexpected expanded structure type unknown");
        assertEquals(previous, Files.readString(output));
    }

    void list(TestDatabase db) throws Exception {
        String model = "ExpandList" + version;
        source(model, """
                    !!@ili2db.mapping=EXPAND
                    Items : LIST {0..5} OF Entry;
                    !!@ili2db.mapping=EXPAND
                    Larger : LIST {0..6} OF Entry;
                """);
        referenceSchema(db, model, "--expandStruct");
        String entries = entry(model, field("Label", "c")) + entry(model, field("Label", "a")) + entry(model, "")
                + entry(model, field("Label", "d")) + entry(model, field("Label", "b"));
        String payload = field("Items", entries) + field("Larger", entry(model, field("Label", "separate row")));
        var output = work.resolve("out.xtf");
        referenceImport(db, "legacy", dataset(model, "legacy", asset(model, "legacy-value", payload)));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertList(output, "legacy");
        assertCliSuccess(db, "import", model, "native", dataset(model, "native", asset(model, "native-value", payload)));
        referenceExport(db, "native", output);
        assertList(output, "native");
        try (var sql = db.connect()) {
            String table = db.table("asset");
            assertEquals("b", scalar(sql, "SELECT items4_label FROM " + table + " WHERE t_ili_tid='native-value'"));
            assertEquals("entry", scalar(sql, "SELECT items2_t_type FROM " + table + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT items2_label FROM " + table + " WHERE t_ili_tid='native-value'"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("entry") + " WHERE asset_larger IS NOT NULL"));
        }
        var failing = dataset(model, "rollback", asset(model, "first-row", field("Items", entry(model, "")))
                + asset(model, "last-row", field("Items", entries + entry(model, ""))));
        assertCliFailure(db, "import", model, "rollback", failing, "Expanded collection exceeds 5 stored positions");
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertNull(scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='rollback'"));
        }
    }

    private void assertList(Path output, String prefix) throws Exception {
        var object = objects(output, prefix).get(prefix + "-value");
        assertEquals(List.of("c", "a", "", "d", "b"), members(object, "Items").stream()
                .map(item -> item.getTextContent().strip()).toList());
        assertEquals("separate row", child(members(object, "Larger").getFirst(), "Label").getTextContent());
    }

    private static List<Element> members(Element object, String field) {
        return children(object).stream().filter(value -> value.getLocalName().equals(field))
                .flatMap(value -> children(value).stream()).toList();
    }

    void bag(TestDatabase db) throws Exception {
        String model = "ExpandBag" + version;
        source(model, """
                    !!@ili2db.mapping=EXPAND
                    Items : BAG {0..4} OF Entry;
                """);
        referenceSchema(db, model, "--expandStruct");
        Path output = work.resolve("out.xtf");
        referenceImport(db, "legacy", bagDataset(model, "legacy"));
        referenceExport(db, "legacy", output);
        if (version == 24) {
            var object = objects(output, "legacy").get("legacy-value");
            assertEquals(3, children(child(object, "Items")).size());
        }
        assertCliSuccess(db, "export", model, "legacy", output);
        assertBag(output, "legacy");

        assertCliSuccess(db, "import", model, "native", bagDataset(model, "native"));
        referenceExport(db, "native", output);
        assertBag(output, "native");
        try (var sql = db.connect()) {
            String asset = db.table("asset");
            assertEquals("entry", scalar(sql, "SELECT items2_t_type FROM " + asset + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT items2_label FROM " + asset + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT items3_t_type FROM " + asset + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT items0_t_type FROM " + asset + " WHERE t_ili_tid='native-missing'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("entry")));
        }
    }

    private Path bagDataset(String model, String prefix) throws Exception {
        String duplicate = entry(model, field("Label", "duplicate"));
        return dataset(model, prefix, asset(model, prefix + "-value", field("Items", duplicate + duplicate + entry(model, "")))
                + asset(model, prefix + "-missing", ""));
    }

    private void assertBag(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-missing"), "Items"));
        var values = members(objects.get(prefix + "-value"), "Items");
        assertEquals(List.of("", "duplicate", "duplicate"), values.stream().map(value -> value.getTextContent().strip()).sorted().toList());
    }

    void nested(TestDatabase db) throws Exception {
        String model = "ExpandNested" + version;
        source(model, """
                    STRUCTURE Wrapper =
                      !!@ili2db.mapping=EXPAND
                      Detail : Entry;
                    END Wrapper;
                """, """
                    CLASS Asset =
                      !!@ili2db.mapping=EXPAND
                      Nest : Wrapper;
                    END Asset;
                """);
        referenceSchema(db, model, "--expandStruct");
        var output = work.resolve("out.xtf");
        referenceImport(db, "legacy", nestedDataset(model, "legacy", "Nest"));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertNested(output, "legacy", "Nest");
        assertCliSuccess(db, "import", model, "native", nestedDataset(model, "native", "Nest"));
        referenceExport(db, "native", output);
        assertNested(output, "native", "Nest");
        try (var sql = db.connect()) {
            var asset = db.table("asset");
            var wrapper = scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname")
                    + " WHERE iliname='" + model + ".Data.Wrapper'");
            assertEquals(wrapper, scalar(sql, "SELECT nest0_t_type FROM " + asset + " WHERE t_ili_tid='native-without-inner'"));
            assertNull(scalar(sql, "SELECT nest0_detail0_t_type FROM " + asset + " WHERE t_ili_tid='native-without-inner'"));
            assertEquals("entry", scalar(sql, "SELECT nest0_detail0_t_type FROM " + asset + " WHERE t_ili_tid='native-empty'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(wrapper)));
            execute(sql, "UPDATE " + asset + " SET nest0_t_type=NULL WHERE t_ili_tid='native-value'");
        }
        String previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, "Missing expanded structure type at " + model + ".Data.Asset.Nest[0]");
        assertEquals(previous, Files.readString(output));
    }

    private Path nestedDataset(String model, String prefix, String field) throws Exception {
        String wrapper = type(model, "Wrapper");
        return dataset(model, prefix, asset(model, prefix + "-without-inner", field(field, field(wrapper, "")))
                + asset(model, prefix + "-empty", field(field, field(wrapper, field("Detail", entry(model, "")))))
                + asset(model, prefix + "-missing", "")
                + asset(model, prefix + "-value", field(field, field(wrapper, field("Detail", entry(model, VALUES))))));
    }

    private void assertNested(Path output, String prefix, String field) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-missing"), field));
        assertNull(child(members(objects.get(prefix + "-without-inner"), field).getFirst(), "Detail"));
        assertEquals(List.of(), children(members(members(objects.get(prefix + "-empty"), field).getFirst(), "Detail").getFirst()));
        assertEntry(members(members(objects.get(prefix + "-value"), field).getFirst(), "Detail").getFirst());
    }

    void schema(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "ExpandSchema" + version;
        var ili = source(model, """
                    STRUCTURE Wrapper =
                      !!@ili2db.mapping=EXPAND
                      Detail : Entry;
                    END Wrapper;
                """, """
                    CLASS Asset =
                      Detail0_Label : TEXT*31;
                      !!@ili2db.mapping=EXPAND
                      Detail : Entry;
                      !!@ili2db.mapping=EXPAND
                      Items : LIST {0..2} OF Entry;
                      !!@ili2db.mapping=EXPAND
                      Bagged : BAG {0..2} OF Entry;
                      !!@ili2db.mapping=EXPAND
                      Larger : LIST {0..6} OF Entry;
                      !!@ili2db.mapping=EXPAND
                      Nest : Wrapper;
                    END Asset;
                """);
        referenceSchema(baseline, model, "--expandStruct");
        createSchema(own, model, ili, "--expand-struct");
        assertSameCatalog(baseline, own);
        var output = work.resolve("out.xtf");
        referenceImport(own, "legacy", schemaDataset(model, "legacy"));
        assertCliSuccess(own, "export", model, "legacy", output);
        assertSchemaData(output, "legacy");
        assertCliSuccess(own, "import", model, "native", schemaDataset(model, "native"));
        referenceExport(own, "native", output);
        assertSchemaData(output, "native");
        referenceImport(own, "tail", schemaDataset(model, "tail"));
        assertCliSuccess(own, "export", model, "tail", output);
        assertSchemaData(output, "tail");
        try (var sql = own.connect()) {
            assertEquals("ordinary", scalar(sql, "SELECT detail0_label FROM " + own.table("asset") + " WHERE t_ili_tid='native-value'"));
            assertEquals("é & a", scalar(sql, "SELECT detail0_label1 FROM " + own.table("asset") + " WHERE t_ili_tid='native-value'"));
            assertSharedIds(own, sql, "15", "asset", "entry");
        }

        var appended = "ExpandAppend" + version;
        var extra = source(appended, """
                    !!@ili2db.mapping=EXPAND
                    Detail : Entry;
                """);
        createSchema(own, appended, extra, "--add-models");
        referenceImport(own, "added", singleDataset(appended, "added", "Detail"));
        assertCliSuccess(own, "export", appended, "added", output);
        assertSingle(output, "added", "Detail");
        try (var sql = own.connect()) {
            assertEquals("expand", scalar(sql, "SELECT setting FROM " + own.table("t_ili2db_trafo")
                    + " WHERE iliname='" + appended + ".Data.Asset.Detail' AND tag='ch.ehi.ili2db.structTrafo'"));
        }
    }

    private Path schemaDataset(String model, String prefix) throws Exception {
        var entry = entry(model, VALUES);
        String payload = field("Detail0_Label", "ordinary") + field("Detail", entry)
                + field("Items", entry + entry(model, "")) + field("Bagged", entry + entry)
                + field("Larger", entry) + field("Nest", field(type(model, "Wrapper"), field("Detail", entry)));
        return dataset(model, prefix, asset(model, prefix + "-value", payload) + asset(model, prefix + "-absent", ""));
    }

    private void assertSchemaData(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        var value = objects.get(prefix + "-value");
        assertEquals("ordinary", child(value, "Detail0_Label").getTextContent());
        assertEntry(members(value, "Detail").getFirst());
        assertEntry(members(value, "Larger").getFirst());
        assertEntry(members(members(value, "Nest").getFirst(), "Detail").getFirst());
        assertEquals(2, members(value, "Bagged").size());
        assertEquals(2, members(value, "Items").size());
        assertEquals(List.of(), children(members(value, "Items").getLast()));
        assertEquals(List.of(), children(objects.get(prefix + "-absent")));
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
        var ili = source(model, "", """
                    CLASS AssetBase (ABSTRACT) =
                      !!@ili2db.mapping=EXPAND
                      Items : LIST {0..2} OF Entry;
                    END AssetBase;
                    CLASS FirstAsset EXTENDS AssetBase =
                      Marker : TEXT*20;
                    END FirstAsset;
                    CLASS OtherAsset EXTENDS AssetBase =
                      Caption : TEXT*20;
                    END OtherAsset;
                """);
        referenceSchema(baseline, model, "--smart2Inheritance", "--expandStruct");
        createSchema(own, model, ili, "--inheritance", "smart2", "--expand-struct");
        assertSameCatalog(baseline, own);
        Path output = work.resolve("out.xtf");
        var initial = List.of("first", "second");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", smartDataset(model, "legacy", initial));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertSmart(output, "legacy", initial);
            assertCliSuccess(db, "import", model, "native", smartDataset(model, "native", initial));
            referenceExport(db, "native", output);
            assertSmart(output, "native", initial);
            assertCliSuccess(db, "delete", model, "native", null);
            try (var sql = db.connect()) {
                String first = db.table("firstasset");
                String other = db.table("otherasset");
                String identity = "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text) FROM ";
                var beforeFirst = scalar(sql, identity + first);
                var beforeOther = scalar(sql, identity + other);
                var beforeDataset = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset"));
                assertEquals("second", scalar(sql, "SELECT items1_label FROM " + first));
                assertEquals("entry", scalar(sql, "SELECT items1_t_type FROM " + first));
                assertEquals("other", scalar(sql, "SELECT items0_label FROM " + other));

                var updated = List.of("updated");
                assertCliSuccess(db, "update", model, "legacy", smartDataset(model, "legacy", updated));
                assertEquals(beforeFirst, scalar(sql, identity + first));
                assertEquals(beforeOther, scalar(sql, identity + other));
                assertNull(scalar(sql, "SELECT items1_label FROM " + first));
                assertNull(scalar(sql, "SELECT items1_t_type FROM " + first));
                referenceExport(db, "legacy", output);
                assertSmart(output, "legacy", updated);

                assertCliSuccess(db, "replace", model, "legacy", smartDataset(model, "legacy", List.of()));
                assertNotEquals(beforeFirst, scalar(sql, identity + first));
                assertNotEquals(beforeOther, scalar(sql, identity + other));
                assertEquals(beforeDataset, scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset")));
                assertNull(scalar(sql, "SELECT items0_t_type FROM " + first));
                referenceExport(db, "legacy", output);
                assertSmart(output, "legacy", List.of());
                assertCliSuccess(db, "delete", model, "legacy", null);
                for (var table : List.of(first, other, db.table("entry"), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"))) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM " + table), table);
                }
            }
        }
    }

    private Path smartDataset(String model, String prefix, List<String> values) throws Exception {
        var entries = values.stream().map(value -> entry(model, field("Label", value))).collect(Collectors.joining());
        return dataset(model, prefix, object(model, "FirstAsset", prefix + "-first",
                (entries.isEmpty() ? "" : field("Items", entries)) + field("Marker", "primary"))
                + object(model, "OtherAsset", prefix + "-other", field("Items", entry(model, field("Label", "other")))
                + field("Caption", "secondary")));
    }

    private void assertSmart(Path output, String prefix, List<String> labels) throws Exception {
        var rows = objects(output, prefix);
        assertEquals(2, rows.size());
        assertEquals(labels, members(rows.get(prefix + "-first"), "Items").stream()
                .map(value -> child(value, "Label").getTextContent()).toList());
        assertEquals("other", child(members(rows.get(prefix + "-other"), "Items").getFirst(), "Label").getTextContent());
        assertEquals("primary", child(rows.get(prefix + "-first"), "Marker").getTextContent());
        assertEquals("secondary", child(rows.get(prefix + "-other"), "Caption").getTextContent());
    }

    void jsonSingle(TestDatabase db) throws Exception {
        String model = "JsonStructure" + version;
        source(model, """
                    !!@ili2db.mapping=JSON
                    Doc : Entry;
                """);
        referenceSchema(db, model, "--coalesceJson");
        var output = work.resolve("out.xtf");
        referenceImport(db, "legacy", singleDataset(model, "legacy", "Doc"));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSingle(output, "legacy", "Doc");

        assertCliSuccess(db, "import", model, "native", singleDataset(model, "native", "Doc"));
        referenceExport(db, "native", output);
        assertSingle(output, "native", "Doc");
        String previous = Files.readString(output);
        try (var sql = db.connect()) {
            var asset = db.table("asset");
            for (var prefix : List.of("legacy", "native")) {
                var value = " FROM " + asset + " WHERE t_ili_tid='" + prefix + "-value'";
                assertEquals("é & a", scalar(sql, "SELECT " + jsonKey(db, "doc", "Label") + value));
                assertEquals("high", scalar(sql, "SELECT " + jsonKey(db, "doc", "Code") + value));
                assertEquals(model + ".Data.Entry", scalar(sql, "SELECT " + jsonKey(db, "doc", "@type") + value));
                String quantityKind = db.postgis() ? "jsonb_typeof(doc->'Quantity')" : "json_type(doc,'$.Quantity')";
                assertEquals(db.postgis() ? "number" : "integer", scalar(sql, "SELECT " + quantityKind + value));
                String flagKind = db.postgis() ? "jsonb_typeof(doc->'Flag')" : "json_type(doc,'$.Flag')";
                assertEquals(db.postgis() ? "boolean" : "true", scalar(sql, "SELECT " + flagKind + value));
                assertNull(scalar(sql, "SELECT doc FROM " + asset + " WHERE t_ili_tid='" + prefix + "-missing'"));
                assertEquals(model + ".Data.Entry", scalar(sql, "SELECT " + jsonKey(db, "doc", "@type")
                        + " FROM " + asset + " WHERE t_ili_tid='" + prefix + "-empty'"));
            }
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("entry")));
            execute(sql, """
                    UPDATE %s SET doc='{"@type":"Unknown"}' WHERE t_ili_tid='native-value'
                    """.formatted(asset));
            assertCliFailure(db, "export", model, "native", output, "Unexpected JSON structure type Unknown");
            assertEquals(previous, Files.readString(output));
            execute(sql, "UPDATE " + asset + " SET doc='" + (db.postgis() ? "[]" : "{") + "' WHERE t_ili_tid='native-value'");
        }
        assertCliFailure(db, "export", model, "native", output, "Invalid ili2db JSON");
        assertEquals(previous, Files.readString(output));
    }

    private static String jsonKey(TestDatabase db, String column, String key) {
        return db.postgis() ? column + "->>'" + key + "'" : "json_extract(" + column + ",'$." + key + "')";
    }

    void jsonCollections(TestDatabase db) throws Exception {
        String model = "JsonCollections" + version;
        source(model, "STRUCTURE Wrapper = Detail : Entry; END Wrapper;", """
                    CLASS Asset =
                      !!@ili2db.mapping=JSON
                      Items : LIST {0..3} OF Entry;
                      !!@ili2db.mapping=JSON
                      Bagged : BAG {0..3} OF Entry;
                      !!@ili2db.mapping=JSON
                      Nest : Wrapper;
                    END Asset;
                """);
        referenceSchema(db, model, "--coalesceJson");
        Path output = work.resolve("out.xtf");
        referenceImport(db, "legacy", jsonCollectionDataset(model, "legacy", true));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertJsonCollections(output, "legacy", true);
        assertCliSuccess(db, "import", model, "native", jsonCollectionDataset(model, "native", true));
        assertCliSuccess(db, "export", model, "native", output);
        assertJsonCollections(output, "native", true);

        // ili2db 5.5.2 writes a nested object but its JSON parser fails to advance the parent token.
        reference.expectFailure(db, "%ILI_FROM_DB", "unexpected json token END_OBJECT; '}' expected",
                "--export", "--dataset", "legacy", "--exportTid", work.resolve("nested-reference.xtf").toString());
        assertCliSuccess(db, "import", model, "mixed", jsonCollectionDataset(model, "mixed", false));
        referenceExport(db, "mixed", output);
        assertJsonCollections(output, "mixed", false);
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
            execute(sql, "UPDATE " + asset + " SET items='[]' WHERE t_ili_tid='native-one'");
        }
        assertCliSuccess(db, "export", model, "native", output);
        assertNull(child(objects(output, "native").get("native-one"), "Items"));
    }

    private Path jsonCollectionDataset(String model, String prefix, boolean includeNested) throws Exception {
        var first = entry(model, field("Label", "z"));
        var last = entry(model, field("Label", "a"));
        var empty = entry(model, "");
        var nested = field(type(model, "Wrapper"), field("Detail", empty));
        return dataset(model, prefix, asset(model, prefix + "-many", field("Items", first + empty + last)
                + field("Bagged", first + first + empty) + (includeNested ? field("Nest", nested) : ""))
                + asset(model, prefix + "-one", field("Items", entry(model, VALUES)))
                + asset(model, prefix + "-missing", ""));
    }

    private void assertJsonCollections(Path output, String prefix, boolean includeNested) throws Exception {
        var objects = objects(output, prefix);
        var many = objects.get(prefix + "-many");
        assertEquals(List.of("z", "", "a"), members(many, "Items").stream().map(item -> item.getTextContent().strip()).toList());
        assertEquals(List.of("", "z", "z"), members(many, "Bagged").stream().map(item -> item.getTextContent().strip()).sorted().toList());
        if (includeNested) {
            assertEquals(List.of(), children(members(members(many, "Nest").getFirst(), "Detail").getFirst()));
        } else {
            assertNull(child(many, "Nest"));
        }
        assertEntry(members(objects.get(prefix + "-one"), "Items").getFirst());
        assertEquals(List.of(), children(objects.get(prefix + "-missing")));
    }

    void jsonSchema(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "JsonSchema" + version;
        String fields = """
                    !!@ili2db.mapping=EXPAND
                    Items : LIST {0..2} OF Entry;
                    !!@ili2db.mapping=JSON
                    Doc : Entry;
                    !!@ili2db.mapping=JSON
                    Copies : BAG {0..2} OF Entry;
                """;
        var ili = source(model, fields);
        referenceSchema(baseline, model, "--expandStruct", "--coalesceJson");
        createSchema(own, model, ili, "--expand-struct", "--coalesce-json");
        assertSameCatalog(baseline, own);
        Path output = work.resolve("out.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", mixedDataset(model, "legacy", false));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertMixed(output, "legacy", false);
            assertCliSuccess(db, "import", model, "native", mixedDataset(model, "native", false));
            referenceExport(db, "native", output);
            assertMixed(output, "native", false);
            try (var sql = db.connect()) {
                var asset = db.table("asset");
                var row = " FROM " + asset + " WHERE t_ili_tid='native-value'";
                String identity = "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text)";
                String before = scalar(sql, identity + row);
                assertEquals("entry", scalar(sql, "SELECT items1_t_type" + row));
                assertEquals("é & a", scalar(sql, "SELECT " + jsonKey(db, "doc", "Label") + row));
                assertCliSuccess(db, "update", model, "native", mixedDataset(model, "native", true));
                assertEquals(before, scalar(sql, identity + row));
                assertNull(scalar(sql, "SELECT doc" + row));
                assertNull(scalar(sql, "SELECT items1_t_type" + row));
                assertNull(scalar(sql, "SELECT items1_label" + row));
            }
            referenceExport(db, "native", output);
            assertMixed(output, "native", true);
            referenceImport(db, "tail", mixedDataset(model, "tail", false));
            assertCliSuccess(db, "export", model, "tail", output);
            assertMixed(output, "tail", false);
            try (var sql = db.connect()) {
                assertSharedIds(db, sql, "9", "asset");
            }
        }

        var appended = "JsonAppend" + version;
        createSchema(own, appended, source(appended, fields), "--add-models");
        assertCliSuccess(own, "import", appended, "added", mixedDataset(appended, "added", false));
        referenceExport(own, "added", output);
        assertMixed(output, "added", false);
        try (var sql = own.connect()) {
            var metadata = own.table("t_ili2db_trafo");
            assertEquals("coalesce", scalar(sql, "SELECT setting FROM " + metadata
                    + " WHERE iliname='" + appended + ".Data.Asset.Doc' AND tag='ch.ehi.ili2db.jsonTrafo'"));
            assertEquals("expand", scalar(sql, "SELECT setting FROM " + metadata
                    + " WHERE iliname='" + appended + ".Data.Asset.Items' AND tag='ch.ehi.ili2db.structTrafo'"));
        }
    }

    private Path mixedDataset(String model, String prefix, boolean updated) throws Exception {
        String first = entry(model, field("Label", updated ? "updated" : "first"));
        String second = entry(model, field("Label", "second"));
        String payload = field("Items", first + (updated ? "" : second))
                + (updated ? "" : field("Doc", entry(model, VALUES))) + field("Copies", first + first);
        return dataset(model, prefix, asset(model, prefix + "-value", payload));
    }

    private void assertMixed(Path output, String prefix, boolean updated) throws Exception {
        var value = objects(output, prefix).get(prefix + "-value");
        assertEquals(updated ? List.of("updated") : List.of("first", "second"), members(value, "Items").stream()
                .map(item -> child(item, "Label").getTextContent()).toList());
        if (updated) assertNull(child(value, "Doc"));
        else assertEntry(members(value, "Doc").getFirst());
        assertEquals(updated ? List.of("updated", "updated") : List.of("first", "first"), members(value, "Copies").stream()
                .map(item -> child(item, "Label").getTextContent()).toList());
    }

    private Path singleDataset(String model, String prefix, String field) throws Exception {
        return dataset(model, prefix, asset(model, prefix + "-empty", field(field, entry(model, "")))
                + asset(model, prefix + "-missing", "")
                + asset(model, prefix + "-value", field(field, entry(model, VALUES))));
    }

    private void assertSingle(Path output, String prefix, String field) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-missing"), field));
        assertEquals(List.of(), children(children(child(objects.get(prefix + "-empty"), field)).getFirst()));
        var item = children(child(objects.get(prefix + "-value"), field)).getFirst();
        assertEntry(item);
    }

    private static void assertEntry(Element item) {
        assertEquals("é & a", child(item, "Label").getTextContent());
        assertEquals("true", child(item, "Flag").getTextContent());
        assertEquals("high", child(item, "Code").getTextContent());
        assertEquals("-2", child(item, "Quantity").getTextContent());
    }

    private Path source(String model, String fields) throws Exception {
        return source(model, "", "CLASS Asset =\n" + fields + "\nEND Asset;");
    }

    private Path source(String model, String structures, String classes) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    STRUCTURE Entry =
                      Label : TEXT*30;
                      Flag : BOOLEAN;
                      Code : (low, high);
                      Quantity : -20 .. 20;
                    END Entry;
                %s
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, structures, classes, model));
    }

    private Path dataset(String model, String prefix, String contents) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-b", contents));
    }

    private String type(String model, String type) { return version == 23 ? model + ".Data." + type : type; }

    private String asset(String model, String tid, String contents) {
        return object(model, "Asset", tid, contents);
    }

    private String object(String model, String name, String tid, String contents) {
        String tag = type(model, name);
        return "<" + tag + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + contents + "</" + tag + ">";
    }

    private String entry(String model, String fields) { return field(type(model, "Entry"), fields); }

    private static String field(String name, String contents) { return "<" + name + ">" + contents + "</" + name + ">"; }

    private Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-b", id(baskets.getFirst(), version, "bid"));
        return children(baskets.getFirst()).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
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
