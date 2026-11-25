package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ArrayCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ArrayCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void smart2(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "ArraySmart" + version;
        var ili = source(model, "STRUCTURE Elem = Entry : TEXT*40; END Elem;", """
                CLASS AssetBase (ABSTRACT) =
                  !!@ili2db.mapping=ARRAY
                  Items : LIST OF Elem;
                END AssetBase;
                CLASS FirstAsset EXTENDS AssetBase =
                  Marker : TEXT*20;
                END FirstAsset;
                CLASS OtherAsset EXTENDS AssetBase =
                  Caption : TEXT*20;
                END OtherAsset;
                """);
        schema(baseline, model, "--smart2Inheritance");
        create(own, model, ili, "--inheritance", "smart2", "--coalesce-array");
        assertSameCatalog(baseline, own);

        var initial = List.of("first", "second", "second");
        var updated = List.of("shortened");
        var output = work.resolve("output.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", smartDataset(model, "legacy", initial, true));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertSmart(output, "legacy", initial, true);
            assertCliSuccess(db, "import", model, "native", smartDataset(model, "native", initial, true));
            referenceExport(db, "native", output);
            assertSmart(output, "native", initial, true);

            try (var sql = db.connect()) {
                String first = db.table("firstasset");
                String other = db.table("otherasset");
                String identity = "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text) FROM ";
                String nativeFirst = first + " WHERE t_ili_tid = 'native-first'";
                String nativeOther = other + " WHERE t_ili_tid = 'native-other'";
                String dataset = "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname = 'native'";
                var beforeFirst = scalar(sql, identity + nativeFirst);
                var beforeOther = scalar(sql, identity + nativeOther);
                var beforeDataset = scalar(sql, dataset);
                var legacyFirst = scalar(sql, identity + first + " WHERE t_ili_tid = 'legacy-first'");
                assertCliSuccess(db, "update", model, "native", smartDataset(model, "native", updated, false));
                assertEquals(beforeFirst, scalar(sql, identity + nativeFirst));
                assertEquals(beforeOther, scalar(sql, identity + nativeOther));
                String length = db.postgis() ? "cardinality(items)" : "json_array_length(items)";
                assertEquals("1", scalar(sql, "SELECT " + length + " FROM " + nativeFirst));
                assertNull(scalar(sql, "SELECT items FROM " + nativeOther));
                referenceExport(db, "native", output);
                assertSmart(output, "native", updated, false);

                assertCliSuccess(db, "replace", model, "native", smartDataset(model, "native", List.of(), true));
                assertNotEquals(beforeFirst, scalar(sql, identity + nativeFirst));
                assertNotEquals(beforeOther, scalar(sql, identity + nativeOther));
                assertEquals(beforeDataset, scalar(sql, dataset));
                assertNull(scalar(sql, "SELECT items FROM " + nativeFirst));
                referenceExport(db, "native", output);
                assertSmart(output, "native", List.of(), true);
                assertCliSuccess(db, "delete", model, "native", null);
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + nativeFirst));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + nativeOther));
                assertEquals(legacyFirst, scalar(sql, identity + first + " WHERE t_ili_tid = 'legacy-first'"));
                assertCliSuccess(db, "export", model, "legacy", output);
                assertSmart(output, "legacy", initial, true);
            }
        }
    }

    private Path smartDataset(String model, String prefix, List<String> values, boolean other) throws Exception {
        var entries = values.stream().map(value -> entry(model, value)).collect(Collectors.joining());
        return transfer(model, prefix, object(model, "FirstAsset", prefix + "-first",
                (entries.isEmpty() ? "" : element("Items", entries)) + element("Marker", "primary"))
                + object(model, "OtherAsset", prefix + "-other", (other ? element("Items", entry(model, "other")) : "")
                + element("Caption", "secondary")));
    }

    private void assertSmart(Path output, String prefix, List<String> values, boolean other) throws Exception {
        var rows = objects(output, prefix);
        assertEquals(2, rows.size());
        var firstRow = rows.get(prefix + "-first");
        var otherRow = rows.get(prefix + "-other");
        assertEquals(values, items(firstRow, "Items"));
        assertEquals(other ? List.of("other") : List.of(), items(otherRow, "Items"));
        assertEquals("primary", child(firstRow, "Marker").getTextContent());
        assertEquals("secondary", child(otherRow, "Caption").getTextContent());
    }

    void references(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "ArrayRefs" + version;
        String target = "CLASS Target = Label : TEXT*40; END Target;";
        var ili = source(model, target + (version == 23
                ? " STRUCTURE Link = Entry : REFERENCE TO Target; END Link;" : ""), """
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Links : LIST OF %s;
                END Asset;
                """.formatted(version == 23 ? "Link" : "REFERENCE TO Target"));
        schema(baseline, model);
        create(own, model, ili, "--coalesce-array");
        assertSameCatalog(baseline, own);
        var output = work.resolve("output.xtf");
        var order = List.of("b", "a", "b");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", referenceDataset(model, "legacy", order, true));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertReferences(output, "legacy", order);
            assertCliSuccess(db, "import", model, "native", referenceDataset(model, "native", order, true));
            referenceExport(db, "native", output);
            assertReferences(output, "native", order);
            referenceImport(db, "tail", referenceDataset(model, "tail", List.of("a"), true));
            assertCliSuccess(db, "export", model, "tail", output);
            assertReferences(output, "tail", List.of("a"));

            try (var sql = db.connect()) {
                String assets = db.table("asset");
                String targets = db.table("target");
                String nativeA = scalar(sql, "SELECT t_id FROM " + targets + " WHERE t_ili_tid = 'native-target-a'");
                String nativeB = scalar(sql, "SELECT t_id FROM " + targets + " WHERE t_ili_tid = 'native-target-b'");
                assertEquals(List.of(nativeB, nativeA, nativeB), storedReferences(db, sql, "native-value", 3));
                assertNull(scalar(sql, "SELECT links FROM " + assets + " WHERE t_ili_tid = 'native-missing'"));
                assertSharedIds(db, sql, 18, "asset", "target");

                assertCliFailure(db, "import", model, "broken", referenceDataset(model, "broken", List.of("absent"), true),
                        "Cannot encode array reference to broken-target-absent");
                assertSharedIds(db, sql, 18, "asset", "target");

                storeReference(db, sql, "legacy-missing", nativeA);
                byte[] previous = Files.readAllBytes(output);
                assertCliFailure(db, "export", model, "legacy", output, "Missing same-basket array target T_Id " + nativeA);
                assertArrayEquals(previous, Files.readAllBytes(output));
                assertCliFailure(db, "update", model, "native", referenceDataset(model, "native", List.of("b"), false),
                        "Cannot delete referenced object T_Id " + nativeA);
                assertCliFailure(db, "replace", model, "native", referenceDataset(model, "native", List.of("b"), false),
                        "Cannot replace referenced object T_Id " + nativeA);
                assertCliFailure(db, "delete", model, "native", null, "Cannot delete referenced object T_Id " + nativeA);
                assertEquals(List.of(nativeB, nativeA, nativeB), storedReferences(db, sql, "native-value", 3));
                assertSharedIds(db, sql, 18, "asset", "target");

                execute(sql, "UPDATE " + assets + " SET links = NULL WHERE t_ili_tid = 'legacy-missing'");
                assertCliSuccess(db, "update", model, "native", referenceDataset(model, "native", List.of("b"), false));
                assertEquals(List.of(nativeB), storedReferences(db, sql, "native-value", 1));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + targets + " WHERE t_ili_tid = 'native-target-a'"));
                referenceExport(db, "native", output);
                assertReferences(output, "native", List.of("b"));
                assertCliSuccess(db, "delete", model, "native", null);
                assertSharedIds(db, sql, 12, "asset", "target");
            }
        }
        protectUnselectedArray(own);
    }

    private void protectUnselectedArray(TestDatabase db) throws Exception {
        String model = "ArrayTarget" + version;
        var ili = source(model, "", "CLASS OtherTarget = Label : TEXT*40; END OtherTarget;");
        create(db, model, ili, "--add-models");
        var keep = object(model, "OtherTarget", "kept", element("Label", "Keep"));
        var drop = object(model, "OtherTarget", "referenced", element("Label", "Drop"));
        assertCliSuccess(db, "import", model, "target", transfer(model, "target", keep + drop));
        var smaller = transfer(model, "target", keep);
        try (var sql = db.connect()) {
            String targets = db.table("othertarget");
            String id = scalar(sql, "SELECT t_id FROM " + targets + " WHERE t_ili_tid = 'referenced'");
            // The source model is outside the CLI selection. Simulate a stored reference to the selected target.
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                    + " SET setting = 'othertarget' WHERE tablename = 'asset' AND columnname = 'links'"
                    + " AND tag = 'ch.ehi.ili2db.foreignKey'");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname")
                    + " SET target = 'othertarget' WHERE colowner = 'asset' AND sqlname = 'links'");
            storeReference(db, sql, "legacy-missing", id);
            assertCliFailure(db, "update", model, "target", smaller, "Cannot delete referenced object T_Id " + id);
            assertCliFailure(db, "replace", model, "target", smaller, "Cannot replace referenced object T_Id " + id);
            assertCliFailure(db, "delete", model, "target", null, "Cannot delete referenced object T_Id " + id);
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + targets));
            assertEquals(id, scalar(sql, "SELECT t_id FROM " + targets + " WHERE t_ili_tid = 'referenced'"));
        }
    }

    private static void storeReference(TestDatabase db, Connection sql, String owner, String target) throws Exception {
        String encoded = db.postgis() ? "ARRAY[" + target + "]::bigint[]" : "'[\"" + target + "\"]'";
        execute(sql, "UPDATE " + db.table("asset") + " SET links = " + encoded + " WHERE t_ili_tid = '" + owner + "'");
    }

    private Path referenceDataset(String model, String prefix, List<String> order, boolean includeA) throws Exception {
        String entries = order.stream().map(value -> {
            String tid = prefix + "-target-" + value;
            return version == 23 ? element(tag(model, "Link"), "<Entry REF=\"" + tid + "\"/>")
                    : "<Links i:ref=\"" + tid + "\"/>";
        }).collect(Collectors.joining());
        String contents = version == 23 ? element("Links", entries) : entries;
        return transfer(model, prefix, object(model, "Asset", prefix + "-value", contents)
                + object(model, "Asset", prefix + "-missing", "")
                + (includeA ? object(model, "Target", prefix + "-target-a", element("Label", "A")) : "")
                + object(model, "Target", prefix + "-target-b", element("Label", "B")));
    }

    private void assertReferences(Path output, String prefix, List<String> order) throws Exception {
        var rows = objects(output, prefix);
        assertNull(child(rows.get(prefix + "-missing"), "Links"));
        var value = rows.get(prefix + "-value");
        var refs = version == 23 ? children(child(value, "Links")).stream().map(entry -> child(entry, "Entry"))
                : children(value).stream().filter(field -> field.getLocalName().equals("Links"))
                        .flatMap(field -> id(field, version, "ref").isEmpty() ? children(field).stream() : Stream.of(field));
        assertEquals(order.stream().map(target -> prefix + "-target-" + target).toList(),
                refs.map(field -> id(field, version, "ref")).toList());
        assertEquals("B", child(rows.get(prefix + "-target-b"), "Label").getTextContent());
    }

    private static List<String> storedReferences(TestDatabase db, Connection sql, String tid, int size) throws Exception {
        String from = " FROM " + db.table("asset") + " WHERE t_ili_tid = '" + tid + "'";
        assertEquals(Integer.toString(size), scalar(sql, "SELECT "
                + (db.postgis() ? "cardinality(links)" : "json_array_length(links)") + from));
        var refs = new ArrayList<String>();
        for (int i = 0; i < size; i++) {
            String path = "'$[" + i + "]'";
            if (!db.postgis()) assertEquals("text", scalar(sql, "SELECT json_type(links, " + path + ")" + from));
            refs.add(scalar(sql, "SELECT " + (db.postgis() ? "links[" + (i + 1) + "]" : "json_extract(links, " + path + ")") + from));
        }
        return refs;
    }

    private static void create(TestDatabase db, String model, Path ili, String... options) {
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private static void assertSameCatalog(TestDatabase baseline, TestDatabase db) throws Exception {
        try (var expected = baseline.connect(); var actual = db.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, db.postgis(), db.schema()));
        }
    }

    private static void assertSharedIds(TestDatabase db, Connection sql, int count, String... tables) throws Exception {
        var all = new ArrayList<>(List.of(tables));
        all.addAll(List.of("t_ili2db_dataset", "t_ili2db_basket"));
        var rows = all.stream().map(table -> "SELECT t_id FROM " + db.table(table)).collect(Collectors.joining(" UNION ALL "));
        assertEquals(Integer.toString(count), scalar(sql, "SELECT count(*) FROM (" + rows + ") ids"));
        assertEquals(Integer.toString(count), scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + rows + ") ids"));
    }

    private Path source(String model, String structures, String objects) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, structures, objects, model));
    }

    private Path transfer(String model, String prefix, String objects) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-b", objects));
    }

    private String entry(String model, String value) {
        return element(tag(model, "Elem"), value == null ? "" : element("Entry", xmlText(value)));
    }

    private static String xmlText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;");
    }

    private String object(String model, String type, String tid, String contents) {
        String tag = tag(model, type);
        return "<" + tag + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + contents + "</" + tag + ">";
    }

    private String tag(String model, String type) { return version == 23 ? model + ".Data." + type : type; }

    private static String element(String tag, String contents) { return "<" + tag + ">" + contents + "</" + tag + ">"; }

    private static List<String> items(Element object, String name) {
        return children(object).stream().filter(field -> field.getLocalName().equals(name))
                .flatMap(field -> children(field).stream())
                .map(entry -> child(entry, "Entry")).map(value -> value == null ? null : value.getTextContent()).toList();
    }

    private Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-b", id(baskets.getFirst(), version, "bid"));
        return children(baskets.getFirst()).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }

    private void schema(TestDatabase db, String model, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--coalesceArray", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
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
