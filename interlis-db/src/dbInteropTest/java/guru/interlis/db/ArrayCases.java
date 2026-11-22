package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
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

    void bool(TestDatabase db) throws Exception {
        String model = "ArrayBoolean" + version;
        arraySource(model, "BOOLEAN", "LIST");
        var values = List.of("true", "false", "true");
        var output = mixedLists(db, model, values);
        var table = db.table("asset");
        String diagnostic;
        try (var sql = db.connect()) {
            if (db.postgis()) {
                assertEquals("true", scalar(sql, "SELECT CAST(items[1] AS text) FROM " + table + " WHERE t_ili_tid='native-value'"));
                execute(sql, "ALTER TABLE " + table + " ALTER COLUMN items TYPE integer[] USING NULL");
                diagnostic = "Expected _bool for array column";
            } else {
                assertEquals("[true,false,true]", scalar(sql, "SELECT items FROM " + table + " WHERE t_ili_tid='native-value'"));
                execute(sql, """
                        UPDATE %s SET items='["true"]' WHERE t_ili_tid='native-value'
                        """.formatted(table));
                diagnostic = "Expected a boolean array element";
            }
            assertNull(scalar(sql, "SELECT items FROM " + table + " WHERE t_ili_tid='native-missing'"));
        }
        var previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, diagnostic);
        assertEquals(previous, Files.readString(output));
    }

    void integer(TestDatabase db) throws Exception {
        String model = "ArrayInteger" + version;
        arraySource(model, "-20 .. 20", "LIST");
        mixedLists(db, model, List.of("-5", "0", "12", "-5"));

        var first = object(model, "Asset", "rollback-first", element("Items", entry(model, "3")));
        var last = object(model, "Asset", "rollback-last", element("Items", entry(model, "2147483648")));
        assertCliFailure(db, "import", model, "rollback", transfer(model, "rollback", first + last), "32-bit integer encoding");
        try (var sql = db.connect()) {
            var asset = db.table("asset");
            var firstElement = db.postgis() ? "items[1]" : "json_extract(items, '$[0]')";
            assertEquals("-5", scalar(sql, "SELECT " + firstElement + " FROM " + asset + " WHERE t_ili_tid='native-value'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + asset + " WHERE t_ili_tid LIKE 'rollback-%'"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
        }
    }

    void enumeration(TestDatabase db) throws Exception {
        String model = "ArrayEnum" + version;
        source(model, """
                DOMAIN Grade = (Low, Family (High, Higher));
                STRUCTURE Elem = Entry : Grade; END Elem;
                """, """
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Items : LIST OF Elem;
                END Asset;
                """);
        mixedLists(db, model, List.of("Family.High", "Low", "Family.High", "Family.Higher"));
        try (var sql = db.connect()) {
            var first = db.postgis() ? "items[1]" : "json_extract(items,'$[0]')";
            assertEquals("Family.High", scalar(sql, "SELECT " + first + " FROM "
                    + db.table("asset") + " WHERE t_ili_tid='native-value'"));
            assertEquals(model + ".Data.Grade", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                    + " WHERE tablename='asset' AND columnname='items' AND subtype IS NULL AND tag='ch.ehi.ili2db.enumDomain'"));
        }
    }

    void bag(TestDatabase db) throws Exception {
        String model = "ArrayBag" + version;
        arraySource(model, "TEXT*40", "BAG");
        schema(db, model);
        var output = work.resolve("output.xtf");
        var values = List.of("repeat", "other", "repeat");
        referenceImport(db, "legacy", dataset(model, "legacy", values));
        if (version == 24) {
            referenceExport(db, "legacy", output);
            var value = objects(output, "legacy").get("legacy-value");
            var fields = children(value).stream().filter(field -> field.getLocalName().equals("Items")).toList();
            assertEquals(1, fields.size());
            assertEquals(3, children(fields.getFirst()).size());
        }
        assertCliSuccess(db, "export", model, "legacy", output);
        assertBag(output, "legacy");
        assertCliSuccess(db, "import", model, "native", dataset(model, "native", values));
        referenceExport(db, "native", output);
        assertBag(output, "native");
    }

    private void assertBag(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-missing"), "Items"));
        assertEquals(List.of("other", "repeat", "repeat"),
                items(objects.get(prefix + "-value"), "Items").stream().sorted().toList());
    }

    void single(TestDatabase db) throws Exception {
        String model = "ArraySingle" + version;
        source(model, "STRUCTURE Elem = Entry : TEXT*40; END Elem;", """
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Items : Elem;
                END Asset;
                """);
        var output = mixedLists(db, model, List.of("one"));
        try (var sql = db.connect()) {
            String size = db.postgis() ? "cardinality(items)" : "json_array_length(items)";
            var table = db.table("asset");
            assertEquals("1", scalar(sql, "SELECT " + size + " FROM " + table + " WHERE t_ili_tid='native-value'"));
            assertNull(scalar(sql, "SELECT items FROM " + table + " WHERE t_ili_tid='native-missing'"));
            String invalid = db.postgis() ? "'{first,second}'" : "'[\"first\",\"second\"]'";
            execute(sql, "UPDATE " + table + " SET items=" + invalid + " WHERE t_ili_tid='native-value'");
        }
        var previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, "Single coalesced structure has multiple array elements");
        assertEquals(previous, Files.readString(output));
    }

    void direct(TestDatabase db) throws Exception {
        String model = "ArrayDirect" + version;
        source(model, """
                DOMAIN Score = -20 .. 20;
                DOMAIN Grade = (Low, Family (High, Higher));
                """ + (version == 23 ? """
                STRUCTURE LabelElem = Entry : TEXT*40; END LabelElem;
                STRUCTURE FlagElem = Entry : BOOLEAN; END FlagElem;
                STRUCTURE NumberElem = Entry : Score; END NumberElem;
                STRUCTURE GradeElem = Entry : Grade; END GradeElem;
                """ : ""), """
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Labels : LIST OF %s;
                  !!@ili2db.mapping=ARRAY
                  Flags : BAG OF %s;
                  !!@ili2db.mapping=ARRAY
                  Numbers : LIST OF %s;
                  !!@ili2db.mapping=ARRAY
                  Grades : LIST OF %s;
                END Asset;
                """.formatted(version == 23 ? new Object[]{"LabelElem", "FlagElem", "NumberElem", "GradeElem"}
                        : new Object[]{"TEXT*40", "BOOLEAN", "Score", "Grade"}));

        String values = directValues(model, "Labels", "LabelElem", "a & b", "a & b", "last")
                + directValues(model, "Flags", "FlagElem", "true", "false", "true")
                + directValues(model, "Numbers", "NumberElem", "-5", "0", "8")
                + directValues(model, "Grades", "GradeElem", "Family.High", "Low");
        schema(db, model);
        var output = work.resolve("output.xtf");
        referenceImport(db, "legacy", directDataset(model, "legacy", values));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertDirect(output, "legacy");
        assertCliSuccess(db, "import", model, "native", directDataset(model, "native", values));
        referenceExport(db, "native", output);
        assertDirect(output, "native");
        if (version == 24) {
            try (var sql = db.connect()) {
                String invalid = db.postgis() ? "'{value,NULL}'" : """
                        '["value",null]'
                        """.strip();
                execute(sql, "UPDATE " + db.table("asset") + " SET labels=" + invalid + " WHERE t_ili_tid='native-value'");
            }
            String previous = Files.readString(output);
            assertCliFailure(db, "export", model, "native", output, "Null element in direct coalesced array");
            assertEquals(previous, Files.readString(output));
        }
    }

    private String directValues(String model, String attribute, String wrapper, String... values) {
        if (version == 24) {
            return Arrays.stream(values).map(value -> element(attribute, xmlText(value))).collect(Collectors.joining());
        }
        return element(attribute, Arrays.stream(values).map(value -> element(tag(model, wrapper), element("Entry", xmlText(value))))
                .collect(Collectors.joining()));
    }

    private Path directDataset(String model, String prefix, String values) throws Exception {
        return transfer(model, prefix, object(model, "Asset", prefix + "-value", values)
                + object(model, "Asset", prefix + "-missing", ""));
    }

    private void assertDirect(Path output, String prefix) throws Exception {
        var found = objects(output, prefix);
        assertEquals(List.of(), children(found.get(prefix + "-missing")));
        var asset = found.get(prefix + "-value");
        assertEquals(List.of("a & b", "a & b", "last"), scalarItems(asset, "Labels"));
        assertEquals(List.of("false", "true", "true"), scalarItems(asset, "Flags").stream().sorted().toList());
        assertEquals(List.of("-5", "0", "8"), scalarItems(asset, "Numbers"));
        assertEquals(List.of("Family.High", "Low"), scalarItems(asset, "Grades"));
    }

    private List<String> scalarItems(Element object, String name) {
        if (version == 23) return items(object, name);
        return children(object).stream().filter(value -> value.getLocalName().equals(name)).map(Element::getTextContent).toList();
    }

    void createSchema(TestDatabase baseline, TestDatabase db) throws Exception {
        String model = "ArraySchema" + version;
        String structures = """
                DOMAIN Grade = (Low, Family (High, Higher));
                STRUCTURE Elem = Entry : TEXT*40; END Elem;
                STRUCTURE NoteElem = Entry : MTEXT; END NoteElem;
                STRUCTURE NumberElem = Entry : -20 .. 20; END NumberElem;
                STRUCTURE FlagElem = Entry : BOOLEAN; END FlagElem;
                STRUCTURE GradeElem = Entry : Grade; END GradeElem;
                """;
        var ili = source(model, structures, """
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Items : LIST OF Elem;
                  !!@ili2db.mapping=ARRAY
                  Notes : LIST OF NoteElem;
                  !!@ili2db.mapping=ARRAY
                  Numbers : LIST OF NumberElem;
                  !!@ili2db.mapping=ARRAY
                  Flags : LIST OF FlagElem;
                  !!@ili2db.mapping=ARRAY
                  Grades : LIST OF GradeElem;
                  !!@ili2db.mapping=EXPAND
                  Detail : Elem;
                  !!@ili2db.mapping=JSON
                  Doc : Elem;
                END Asset;
                """ + (version == 24 ? """
                CLASS DirectAsset =
                  !!@ili2db.mapping=ARRAY
                  Grades : LIST OF Grade;
                END DirectAsset;
                """ : ""));
        schema(baseline, model, "--expandStruct", "--coalesceJson");
        create(db, model, ili, "--coalesce-array", "--expand-struct", "--coalesce-json");
        assertSameCatalog(baseline, db);
        var output = work.resolve("output.xtf");
        referenceImport(db, "legacy", schemaDataset(model, "legacy"));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSchemaData(output, "legacy");
        assertCliSuccess(db, "import", model, "native", schemaDataset(model, "native"));
        referenceExport(db, "native", output);
        assertSchemaData(output, "native");
        referenceImport(db, "tail", schemaDataset(model, "tail"));
        assertCliSuccess(db, "export", model, "tail", output);
        assertSchemaData(output, "tail");
        try (var sql = db.connect()) {
            assertSharedIds(db, sql, 9, "asset");
        }

        String added = "ArrayAppend" + version;
        var extra = arraySource(added, "TEXT*40", "LIST");
        create(db, added, extra, "--add-models");
        referenceImport(db, "added", dataset(added, "added", List.of("one", "two")));
        assertCliSuccess(db, "export", added, "added", output);
        assertItems(output, "added", List.of("one", "two"));
        try (var sql = db.connect()) {
            assertEquals("coalesce", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_trafo")
                    + " WHERE iliname='" + added + ".Data.Asset.Items' AND tag='ch.ehi.ili2db.arrayTrafo'"));
        }
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

    private Path schemaDataset(String model, String prefix) throws Exception {
        String values = element("Items", entry(model, "some") + entry(model, "words"))
                + element("Notes", element(tag(model, "NoteElem"), element("Entry", "long text")))
                + element("Numbers", element(tag(model, "NumberElem"), element("Entry", "-9")))
                + element("Flags", element(tag(model, "FlagElem"), element("Entry", "false")))
                + element("Grades", element(tag(model, "GradeElem"), element("Entry", "Family.High")))
                + element("Detail", entry(model, "expanded")) + element("Doc", entry(model, "json"));
        return transfer(model, prefix, object(model, "Asset", prefix + "-value", values));
    }

    private void assertSchemaData(Path output, String prefix) throws Exception {
        var asset = objects(output, prefix).get(prefix + "-value");
        assertEquals(List.of("some", "words"), items(asset, "Items"));
        assertEquals(List.of("long text"), items(asset, "Notes"));
        assertEquals(List.of("-9"), items(asset, "Numbers"));
        assertEquals(List.of("false"), items(asset, "Flags"));
        assertEquals(List.of("Family.High"), items(asset, "Grades"));
        assertEquals(List.of("expanded"), items(asset, "Detail"));
        assertEquals(List.of("json"), items(asset, "Doc"));
    }

    private static void create(TestDatabase db, String model, Path ili, String... options) {
        var result = cli(db, "schemaimport", model, null, ili, CancellationToken.none(), options);
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

    private Path mixedLists(TestDatabase db, String model, List<String> expected) throws Exception {
        schema(db, model);
        var output = work.resolve("output.xtf");
        referenceImport(db, "legacy", dataset(model, "legacy", expected));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertItems(output, "legacy", expected);
        assertCliSuccess(db, "import", model, "native", dataset(model, "native", expected));
        referenceExport(db, "native", output);
        assertItems(output, "native", expected);
        return output;
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
