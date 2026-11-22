package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SchemaProfileCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    SchemaProfileCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void modelNullability(TestDatabase db) throws Exception {
        String model = "ProfileNullability" + version;
        Path ili = source(model, "CLASS Asset = Label : MANDATORY TEXT*24; Remark : TEXT*20; END Asset;");
        referenceSchema(db, ili);
        mixed(db, model, List.of("<Label>first</Label>", "<Label>second</Label><Remark>present</Remark>"), rows -> {
            assertEquals("first", child(rows.getFirst(), "Label").getTextContent());
            assertNull(child(rows.getFirst(), "Remark"));
            assertEquals("second", child(rows.getLast(), "Label").getTextContent());
            assertEquals("present", child(rows.getLast(), "Remark").getTextContent());
        });
        assertCliFailure(db, "schemaimport", model, null, ili, "--add-models requires a schema with SqlNull=enable",
                "--add-models");
        try (var sql = db.connect()) {
            assertNull(scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_settings")
                    + " WHERE tag = 'ch.ehi.ili2db.SqlNull'"));
            Path broken = assetData(model, "broken", List.of("<Label>valid</Label>", "<Remark>no label</Remark>"));
            assertCliFailure(db, "import", model, "broken", broken,
                    db.postgis() ? "SQL code 23502" : "Database operation failed (SQL code ");
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
        }
    }

    void foreignKeyIndexes(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ProfileFkIndex" + version;
        Path ili = linkedModel(model);
        referenceSchema(expected, ili, "--sqlEnableNull", "--createFkIdx");
        schema(own, model, ili, "--create-fk-idx");
        sameCatalog(expected, own);
        Set<String> indexes = Set.of("asset:t_basket", "asset:refasset", "target:t_basket",
                "note:t_basket", "note:asset_notes", "t_ili2db_basket:dataset");
        for (var db : List.of(expected, own)) {
            try (var sql = db.connect()) {
                assertEquals(indexes, indexColumns(db, sql));
                assertTrue(foreignKeyColumns(db, sql).isEmpty());
            }
            mixed(db, model, List.of("<Label>indexed</Label>"), rows ->
                    assertEquals("indexed", child(rows.getFirst(), "Label").getTextContent()));
            String next = "ProfileFkExtra" + version;
            Path extra = source(next, "CLASS Extra = Label : TEXT*24; END Extra;");
            schema(db, next, extra, "--add-models");
            try (var sql = db.connect()) {
                var extended = new HashSet<>(indexes);
                extended.add("extra:t_basket");
                assertEquals(extended, indexColumns(db, sql));
            }
        }
    }

    void foreignKeys(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ProfileFk" + version;
        Path ili = linkedModel(model);
        referenceSchema(expected, ili, "--sqlEnableNull", "--createFk");
        schema(own, model, ili, "--create-fk");
        sameCatalog(expected, own);
        var keys = Set.of("asset:t_basket:t_ili2db_basket", "asset:refasset:target", "target:t_basket:t_ili2db_basket",
                "note:t_basket:t_ili2db_basket", "note:asset_notes:asset", "t_ili2db_basket:dataset:t_ili2db_dataset");
        for (var db : List.of(expected, own)) {
            String legacy = "legacy";
            referenceImport(db, legacy, linkedData(model, legacy));
            Path output = work.resolve("output.xtf");
            assertCliSuccess(db, "export", model, legacy, output);
            assertLink(rows(output, legacy, 2).getFirst(), legacy);
            String dataset = "native";
            assertCliSuccess(db, "import", model, dataset, linkedData(model, dataset));
            referenceExport(db, dataset, output);
            assertLink(rows(output, dataset, 2).getFirst(), dataset);
            try (var sql = db.connect()) {
                assertEquals(keys, foreignKeyColumns(db, sql));
                if (!db.postgis()) execute(sql, "PRAGMA foreign_keys = ON");
                sql.setAutoCommit(false);
                execute(sql, "UPDATE " + db.table("asset") + " SET refasset = -123 WHERE t_ili_tid = 'native-asset'");
                assertThrows(SQLException.class, sql::commit);
                sql.rollback();
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " a JOIN "
                        + db.table("target") + " t ON t.t_id = a.refasset"));
                sql.setAutoCommit(true);
                Path bad = data(model, "broken", object(model, "Asset", "broken-asset", reference("RefAsset", "missing")));
                assertCliFailure(db, "import", model, "broken", bad, "Cannot encode reference from broken-asset");
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            }
            String more = "ProfileFkMore" + version;
            schema(db, more, source(more, "CLASS Extra = Label : TEXT*12; END Extra;"), "--add-models");
            try (var sql = db.connect()) {
                var extended = new HashSet<>(keys);
                extended.add("extra:t_basket:t_ili2db_basket");
                assertEquals(extended, foreignKeyColumns(db, sql));
            }
        }
    }

    private Path linkedData(String model, String prefix) throws Exception {
        String note = version == 23 ? model + ".Data.Note" : "Note";
        return data(model, prefix, object(model, "Asset", prefix + "-asset",
                "<Label>linked</Label><Notes><" + note + "><Message>present</Message></" + note + "></Notes>"
                        + reference("RefAsset", prefix + "-target"))
                + object(model, "Target", prefix + "-target", "<Label>target</Label>"));
    }

    private String reference(String field, String tid) {
        return "<" + field + (version == 23 ? " REF=\"" : " i:ref=\"") + tid + "\"/>";
    }

    private void assertLink(Element row, String prefix) {
        assertEquals(prefix + "-asset", id(row, version, "tid"));
        var role = child(row, "RefAsset");
        assertEquals(prefix + "-target", id(role, version, "ref"));
        assertEquals("present", child(children(child(row, "Notes")).getFirst(), "Message").getTextContent());
    }

    private Path linkedModel(String model) throws Exception {
        return source(model, """
                STRUCTURE Note = Message : TEXT*24; END Note;
                CLASS Target = Label : TEXT*24; END Target;
                CLASS Asset = Label : TEXT*24; Notes : LIST OF Note; END Asset;
                ASSOCIATION Assignment =
                  RefAsset -- {0..1} Target;
                  Sources -- {0..*} Asset;
                END Assignment;
                """);
    }

    private static Set<String> indexColumns(TestDatabase db, Connection sql) throws Exception {
        return catalogKeys(sql, db.postgis() ? """
                SELECT t.relname || ':' || a.attname FROM pg_index i
                JOIN pg_class t ON t.oid = i.indrelid JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(i.indkey)
                WHERE n.nspname = '%s' AND NOT i.indisunique
                """.formatted(db.schema()) : """
                SELECT lower(t.name || ':' || col.name) FROM sqlite_schema t
                JOIN pragma_index_list(t.name) idx JOIN pragma_index_info(idx.name) col
                WHERE t.type = 'table' AND NOT idx."unique"
                """);
    }

    private static Set<String> foreignKeyColumns(TestDatabase db, Connection sql) throws Exception {
        return catalogKeys(sql, db.postgis() ? """
                SELECT t.relname || ':' || a.attname || ':' || target.relname FROM pg_constraint fk
                JOIN pg_class t ON t.oid = fk.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_class target ON target.oid = fk.confrelid
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(fk.conkey)
                WHERE n.nspname = '%s' AND fk.contype = 'f'
                """.formatted(db.schema()) : """
                SELECT lower(t.name || ':' || fk."from" || ':' || fk."table") FROM sqlite_schema t
                JOIN pragma_foreign_key_list(t.name) fk WHERE t.type = 'table' AND substr(t.name, 1, 5) <> 'gpkg_'
                """);
    }

    private static Set<String> catalogKeys(Connection sql, String query) throws Exception {
        var keys = new HashSet<String>();
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) keys.add(rows.getString(1));
        }
        return keys;
    }

    private static void schema(TestDatabase db, String model, Path ili, String... options) {
        var result = cli(db, "schemaimport", model, null, ili, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private static void sameCatalog(TestDatabase expected, TestDatabase own) throws Exception {
        try (var left = expected.connect(); var right = own.connect()) {
            DbSchemaCatalog.read(left, expected.postgis(), expected.schema())
                    .assertSameAs(DbSchemaCatalog.read(right, own.postgis(), own.schema()));
        }
    }

    private Path source(String model, String declarations) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, declarations, model));
    }

    private void referenceSchema(TestDatabase db, Path ili, String... extra) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056"));
        options.addAll(List.of(extra));
        options.add(ili.toString());
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private String object(String model, String type, String tid, String fields) {
        String tag = version == 23 ? model + ".Data." + type : type;
        String attribute = version == 23 ? "TID" : "i:tid";
        return "<" + tag + " " + attribute + "=\"" + tid + "\">" + fields + "</" + tag + ">";
    }

    private Path assetData(String model, String prefix, List<String> fields) throws Exception {
        var objects = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) objects.append(object(model, "Asset", prefix + "-" + i, fields.get(i)));
        return data(model, prefix, objects.toString());
    }

    private Path data(String model, String prefix, String objects) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    private void referenceImport(TestDatabase db, String dataset, Path path) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", path.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path path) throws Exception {
        Files.deleteIfExists(path);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", path.toString());
    }

    private void mixed(TestDatabase db, String model, List<String> fields, CheckedRows check) throws Exception {
        Path output = work.resolve("output.xtf");
        referenceImport(db, "legacy", assetData(model, "legacy", fields));
        assertCliSuccess(db, "export", model, "legacy", output);
        check.check(rows(output, "legacy", fields.size()));
        assertCliSuccess(db, "import", model, "native", assetData(model, "native", fields));
        referenceExport(db, "native", output);
        check.check(rows(output, "native", fields.size()));
    }

    private List<Element> rows(Path path, String prefix, int count) throws Exception {
        var baskets = children(readDataSection(path, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-basket", id(baskets.getFirst(), version, "bid"));
        var rows = children(baskets.getFirst()).stream().sorted(Comparator.comparing(row -> id(row, version, "tid"))).toList();
        assertEquals(count, rows.size());
        return rows;
    }

    @FunctionalInterface
    private interface CheckedRows {
        void check(List<Element> rows) throws Exception;
    }
}
