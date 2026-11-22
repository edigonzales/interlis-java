package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    void text(TestDatabase db) throws Exception {
        String model = "ArrayText" + version;
        arraySource(model, "TEXT*40", "LIST");
        schema(db, model);
        var values = List.of("é, \"A\"", "duplicate", "duplicate");
        var output = work.resolve("output.xtf");
        referenceImport(db, "legacy", dataset(model, "legacy", values));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertItems(output, "legacy", values);
        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN items TO stored_items");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_items' WHERE sqlname='items'");
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop") + " SET columnname='stored_items' WHERE columnname='items'");
        }
        assertCliSuccess(db, "import", model, "native", dataset(model, "native", values));
        referenceExport(db, "native", output);
        assertItems(output, "native", values);

        var withNull = Arrays.asList("before", null, "");
        referenceImport(db, "legacy-null", dataset(model, "legacy-null", Arrays.asList("before", null, "after")));
        reference.expectFailure(db, "%ILI_FROM_DB", "illegal value null for Entry", "--export", "--dataset", "legacy-null",
                "--exportTid", work.resolve("reference-null.xtf").toString());
        assertCliSuccess(db, "export", model, "legacy-null", output);
        assertItems(output, "legacy-null", Arrays.asList("before", null, "after"));
        assertCliSuccess(db, "import", model, "native-null", dataset(model, "native-null", withNull));
        assertCliSuccess(db, "export", model, "native-null", output);
        assertItems(output, "native-null", withNull);

        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("elem")));
            var table = db.table("asset");
            assertNull(scalar(sql, "SELECT stored_items FROM " + table + " WHERE t_ili_tid='native-missing'"));
            execute(sql, "UPDATE " + table + " SET stored_items=" + (db.postgis() ? "'{}'" : "'[]'")
                    + " WHERE t_ili_tid='native-missing'");
            assertCliSuccess(db, "export", model, "native", output);
            assertItems(output, "native", values);
            execute(sql, "UPDATE " + table + " SET stored_items=" + (db.postgis() ? "'{{a},{b}}'" : "'[{}]'")
                    + " WHERE t_ili_tid='native-value'");
        }
        var previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, db.postgis()
                ? "one-dimensional PostgreSQL array" : "Expected a text array element");
        assertEquals(previous, Files.readString(output));
    }

    private Path arraySource(String model, String scalar, String collection) throws Exception {
        return source(model, "STRUCTURE Elem = Entry : " + scalar + "; END Elem;", """
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Items : %s OF Elem;
                END Asset;
                """.formatted(collection));
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

    private Path dataset(String model, String prefix, List<String> values) throws Exception {
        String entries = values.stream().map(value -> entry(model, value)).collect(Collectors.joining());
        return transfer(model, prefix, object(model, "Asset", prefix + "-value", element("Items", entries))
                + object(model, "Asset", prefix + "-missing", ""));
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

    private void assertItems(Path output, String prefix, List<String> values) throws Exception {
        var found = objects(output, prefix);
        assertNull(child(found.get(prefix + "-missing"), "Items"));
        assertEquals(values, items(found.get(prefix + "-value"), "Items"));
    }

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
