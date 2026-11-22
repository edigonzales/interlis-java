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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    void hierarchyKeys(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "SmartPoly" + version;
        Path ili = fixture(model + ".ili");
        Files.writeString(ili, Files.readString(ili).replace("  END Data;", """
                    DOMAIN Position = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                    CLASS Marker = MainPoint : Position; CheckPoint : Position; END Marker;
                    ASSOCIATION Calibration =
                      Applying -- {0..1} Terminal;
                      Applied -- {0..*} Marker;
                      Score : 0..9;
                    END Calibration;
                    STRUCTURE PointerValue = Destination : REFERENCE TO (EXTERNAL) Instrument; END PointerValue;
                    CLASS Pointer = Link : PointerValue; END Pointer;
                  END Data;"""));
        Path input = fixture(model + ".xtf");
        String endBasket = version == 23 ? "</" + model + ".Data>" : "</Data>";
        String marker = object(model, "Marker", "fixture-point", "<MainPoint>" + point("12.000", "13.000")
                + "</MainPoint><CheckPoint>" + point("14.000", "15.000") + "</CheckPoint>"
                + reference("Applying", "fixture-t"));
        Files.writeString(input, Files.readString(input).replace(endBasket, marker + endBasket));
        Path nativeData = Files.writeString(work.resolve("hierarchy-native.xtf"), Files.readString(input).replace("fixture-", "native-"));
        Path update = Files.writeString(work.resolve("hierarchy-update.xtf"), Files.readString(nativeData).replace(">smart<", ">changed<"));
        String structure = version == 23 ? model + ".Data.PointerValue" : "PointerValue";
        String destination = version == 23 ? "<Destination REF=\"native-s\" BID=\"native-b\"/>"
                : "<Destination i:ref=\"native-s\"/>";
        Path incoming = data(model, "incoming", object(model, "Pointer", "incoming-p",
                "<Link><" + structure + ">" + destination + "</" + structure + "></Link>"));
        for (boolean smart : List.of(false, true)) {
            var baseline = smart ? expected.peer("profile_smart_keys_ref") : expected;
            var created = smart ? own.peer("profile_smart_keys_own") : own;
            var legacyOptions = new ArrayList<>(List.of("--sqlEnableNull", "--createFk"));
            var nativeOptions = new ArrayList<>(List.of("--create-fk"));
            if (own.postgis()) {
                legacyOptions.add("--oneGeomPerTable");
                nativeOptions.add("--one-geom-per-table");
            }
            if (smart) {
                legacyOptions.add("--smart2Inheritance");
                nativeOptions.addAll(List.of("--inheritance", "smart2"));
            }
            referenceSchema(baseline, ili, legacyOptions.toArray(String[]::new));
            schema(created, model, ili, nativeOptions.toArray(String[]::new));
            try (var left = baseline.connect(); var right = created.connect()) {
                var keys = foreignKeyColumns(baseline, left);
                assertTrue(keys.contains("marker_checkpoint:t_id:marker"), keys.toString());
                assertTrue(keys.contains(smart ? "crosscheck:checkedinstrument_terminal:terminal"
                        : "meter:t_id:instrument"), keys.toString());
                assertEquals(keys, foreignKeyColumns(created, right));
                for (var catalog : List.of(DbSchemaCatalog.read(left, baseline.postgis(), baseline.schema()),
                        DbSchemaCatalog.read(right, created.postgis(), created.schema()))) {
                    assertTrue(catalog.relations().get("unique keys").contains(List.of("calibration", "applied")));
                }
            }
            for (var db : List.of(baseline, created)) {
                Path output = work.resolve("hierarchy-output.xtf");
                referenceImport(db, "legacy", input);
                assertCliSuccess(db, "export", model, "legacy", output);
                assertHierarchy(basketRows(output, "fixture-b", 7), "fixture", "smart");
                assertCliSuccess(db, "import", model, "native", nativeData);
                referenceExport(db, "native", output);
                assertHierarchy(basketRows(output, "native-b", 7), "native", "smart");
                try (var sql = db.connect()) {
                    String row = " FROM " + db.table(smart ? "smartunit" : "instrument") + " WHERE t_ili_tid='native-s'";
                    String original = scalar(sql, "SELECT t_id" + row);
                    assertCliSuccess(db, "update", model, "native", update);
                    assertEquals(original, scalar(sql, "SELECT t_id" + row));
                    assertCliSuccess(db, "import", model, "incoming", incoming);
                    assertCliFailure(db, "delete", model, "native", null, "referenced object");
                    assertCliFailure(db, "replace", model, "native", nativeData, "referenced object");
                    referenceExport(db, "native", output);
                    assertHierarchy(basketRows(output, "native-b", 7), "native", "changed");
                    assertCliSuccess(db, "delete", model, "incoming", null);
                    assertCliSuccess(db, "replace", model, "native", nativeData);
                    assertNotEquals(original, scalar(sql, "SELECT t_id" + row));
                    assertCliSuccess(db, "delete", model, "native", null);
                    assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("marker_checkpoint")));
                }
            }
        }
    }

    private void assertHierarchy(List<Element> rows, String prefix, String memo) {
        var smart = rows.stream().filter(row -> (prefix + "-s").equals(id(row, version, "tid"))).findFirst().orElseThrow();
        assertEquals(memo, child(smart, "Memo").getTextContent());
        var marker = rows.stream().filter(row -> (prefix + "-point").equals(id(row, version, "tid"))).findFirst().orElseThrow();
        var coordinate = child(children(child(marker, "CheckPoint")).getFirst(), version == 23 ? "C1" : "c1");
        assertEquals(0, new java.math.BigDecimal("14").compareTo(new java.math.BigDecimal(coordinate.getTextContent())));
        assertEquals(prefix + "-t", id(child(marker, "Applying"), version, "ref"));
        var links = rows.stream().filter(row -> row.getLocalName().endsWith("CrossCheck")).toList();
        assertEquals(Set.of(prefix + "-s", prefix + "-t"), links.stream()
                .map(row -> id(child(row, "CheckedInstrument"), version, "ref")).collect(java.util.stream.Collectors.toSet()));
    }

    private String point(String x, String y) {
        return version == 23 ? "<COORD><C1>" + x + "</C1><C2>" + y + "</C2></COORD>"
                : "<g:coord xmlns:g=\"http://www.interlis.ch/geometry/1.0\"><g:c1>" + x + "</g:c1><g:c2>" + y + "</g:c2></g:coord>";
    }

    private Path fixture(String name) throws Exception {
        Path destination = work.resolve(name);
        try (var input = SchemaProfileCases.class.getResourceAsStream("/smart/" + name)) {
            Files.copy(java.util.Objects.requireNonNull(input, name), destination);
        }
        return destination;
    }

    void discriminator(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ProfileType" + version;
        Path ili = source(model, """
                STRUCTURE Note = Message : TEXT*24; END Note;
                CLASS Asset = Label : TEXT*24; Notes : LIST OF Note; END Asset;
                CLASS Base (ABSTRACT) = Caption : TEXT*24; END Base;
                CLASS Leaf EXTENDS Base = NumberValue : 0..12; END Leaf;
                ASSOCIATION Survey =
                  AssetTarget -- {0..1} Leaf;
                  Surveyed -- {0..*} Asset;
                  Detail : TEXT*20;
                END Survey;
                """);
        referenceSchema(expected, ili, "--sqlEnableNull", "--createTypeDiscriminator", "--createTypeConstraint");
        schema(own, model, ili, "--create-type-discriminator", "--create-type-constraint");
        sameCatalog(expected, own);
        for (var db : List.of(expected, own)) {
            referenceImport(db, "legacy", typedData(model, "legacy"));
            Path output = work.resolve("type-output.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertTypedRows(rows(output, "legacy", 2), "legacy");
            assertCliSuccess(db, "import", model, "native", typedData(model, "native"));
            referenceExport(db, "native", output);
            assertTypedRows(rows(output, "native", 2), "native");
            String extra = "ProfileTypeExtra" + version;
            schema(db, extra, source(extra, "CLASS Other = Label : TEXT*24; END Other;"), "--add-models");
            try (var sql = db.connect()) {
                assertEquals("asset", scalar(sql, "SELECT t_type FROM " + db.table("asset") + " WHERE t_ili_tid='native-asset'"));
                assertEquals("leaf", scalar(sql, "SELECT t_type FROM " + db.table("base") + " WHERE t_ili_tid='native-leaf'"));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("survey") + " WHERE t_type='survey'"));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("note") + " WHERE t_type='note'"));
                assertThrows(SQLException.class, () -> execute(sql, "UPDATE " + db.table("asset") + " SET t_type='unknown'"));
                assertThrows(SQLException.class, () -> execute(sql, "INSERT INTO " + db.table("other")
                        + " (t_id,t_basket,t_type) VALUES (999,999,'unknown')"));
            }
        }
    }

    private Path typedData(String model, String prefix) throws Exception {
        String note = version == 23 ? model + ".Data.Note" : "Note";
        return data(model, prefix, object(model, "Asset", prefix + "-asset",
                "<Label>typed</Label><Notes><" + note + "><Message>present</Message></" + note + "></Notes>"
                        + reference("AssetTarget", prefix + "-leaf"))
                + object(model, "Leaf", prefix + "-leaf", "<Caption>subtype</Caption><NumberValue>7</NumberValue>"));
    }

    private void assertTypedRows(List<Element> rows, String prefix) {
        assertEquals("typed", child(rows.getFirst(), "Label").getTextContent());
        assertEquals(prefix + "-leaf", id(child(rows.getFirst(), "AssetTarget"), version, "ref"));
        assertEquals("7", child(rows.getLast(), "NumberValue").getTextContent());
    }

    void datasetColumn(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ProfileDataset" + version;
        Path ili = linkedModel(model);
        referenceSchema(expected, ili, "--sqlEnableNull", "--createDatasetCol");
        schema(own, model, ili, "--create-dataset-col");
        sameCatalog(expected, own);
        for (var db : List.of(expected, own)) {
            Path output = work.resolve("dataset-output.xtf");
            referenceImport(db, "legacy", linkedData(model, "legacy"));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertLink(rows(output, "legacy", 2).getFirst(), "legacy");
            Path nativeData = linkedData(model, "native");
            assertCliSuccess(db, "import", model, "native", nativeData);
            referenceExport(db, "native", output);
            assertLink(rows(output, "native", 2).getFirst(), "native");
            try (var sql = db.connect()) {
                assertEquals(Set.of("asset:t_datasetname", "target:t_datasetname", "note:t_datasetname"), indexColumns(db, sql));
                assertDatasetColumns(db, sql);
                Path changed = Files.writeString(work.resolve("changed.xtf"), Files.readString(nativeData).replace(">linked<", ">changed<"));
                String row = " FROM " + db.table("asset") + " WHERE t_datasetname = 'native'";
                String id = scalar(sql, "SELECT t_id" + row);
                assertCliSuccess(db, "update", model, "native", changed);
                assertEquals(id, scalar(sql, "SELECT t_id" + row));
                assertEquals("changed", scalar(sql, "SELECT label" + row));
                assertDatasetColumns(db, sql);
                assertCliSuccess(db, "replace", model, "native", nativeData);
                assertNotEquals(id, scalar(sql, "SELECT t_id" + row));
                assertEquals("linked", scalar(sql, "SELECT label" + row));
                assertDatasetColumns(db, sql);
            }
            String add = "ProfileDatasetExtra" + version;
            schema(db, add, source(add, "CLASS Extra = Label : TEXT*24; END Extra;"), "--add-models");
            try (var sql = db.connect()) {
                assertTrue(indexColumns(db, sql).contains("extra:t_datasetname"));
            }
        }
    }

    private static void assertDatasetColumns(TestDatabase db, Connection sql) throws Exception {
        for (String table : List.of("asset", "target", "note")) {
            assertEquals(Set.of("legacy:legacy", "native:native"), catalogKeys(sql,
                    "SELECT a.t_datasetname || ':' || d.datasetname FROM " + db.table(table) + " a JOIN "
                            + db.table("t_ili2db_basket") + " b ON b.t_id = a.t_basket JOIN "
                            + db.table("t_ili2db_dataset") + " d ON d.t_id = b.dataset"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(table)));
        }
    }

    void standardColumns(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ProfileAudit" + version;
        Path ili = linkedModel(model);
        Files.writeString(ili, Files.readString(ili).replace("CLASS Asset = Label", """
                DOMAIN Position = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                CLASS Asset = MainPoint : Position; CheckPoint : Position; Label"""));
        var legacyOptions = new ArrayList<>(List.of("--sqlEnableNull", "--createStdCols", "--createDatasetCol"));
        var nativeOptions = new ArrayList<>(List.of("--create-std-cols", "--create-dataset-col"));
        if (own.postgis()) {
            legacyOptions.add("--oneGeomPerTable");
            nativeOptions.add("--one-geom-per-table");
        }
        referenceSchema(expected, ili, legacyOptions.toArray(String[]::new));
        schema(own, model, ili, nativeOptions.toArray(String[]::new));
        sameCatalog(expected, own);
        for (var db : List.of(expected, own)) {
            Path output = work.resolve("audit-output.xtf");
            referenceImport(db, "legacy", auditData(model, "legacy"));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertLink(rows(output, "legacy", 2).getFirst(), "legacy");
            Path data = auditData(model, "native");
            assertCliSuccess(db, "import", model, "native", data);
            referenceExport(db, "native", output);
            assertLink(rows(output, "native", 2).getFirst(), "native");
            try (var sql = db.connect()) {
                String user = db.postgis() ? sql.getMetaData().getUserName() : System.getProperty("user.name");
                var tables = List.of("asset", "asset_checkpoint", "note", "target");
                for (String table : tables) {
                    assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(table)
                            + " WHERE t_createdate = t_lastchange AND t_user = '" + user.replace("'", "''") + "'"
                            + (db.postgis() ? "" : " AND typeof(t_createdate) = 'integer'")));
                }
                String old = db.postgis() ? "TIMESTAMP '2000-02-03 00:00:00'" : "949536000000";
                for (String table : List.of("asset", "asset_checkpoint")) {
                    execute(sql, "UPDATE " + db.table(table) + " SET t_createdate=" + old + ", t_lastchange=" + old
                            + ", t_user='previous' WHERE t_datasetname='native'");
                }
                assertCliSuccess(db, "update", model, "native", data);
                for (String table : List.of("asset", "asset_checkpoint")) {
                    assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)
                            + " WHERE t_datasetname='native' AND t_createdate=" + old + " AND t_lastchange>" + old
                            + " AND t_user='" + user.replace("'", "''") + "'"));
                }
            }
            String extra = "ProfileAuditExtra" + version;
            schema(db, extra, source(extra, "CLASS Extra = Label : TEXT*24; END Extra;"), "--add-models");
            try (var sql = db.connect()) {
                assertThrows(SQLException.class, () -> execute(sql, "INSERT INTO " + db.table("extra")
                        + " (t_id,t_basket,t_datasetname) VALUES (999,999,'native')"));
            }
        }
    }

    private Path auditData(String model, String prefix) throws Exception {
        Path data = linkedData(model, prefix);
        return Files.writeString(data, Files.readString(data).replace("<Label>linked</Label>",
                "<MainPoint>" + point("12.000", "13.000") + "</MainPoint><CheckPoint>" + point("14.000", "15.000")
                        + "</CheckPoint><Label>linked</Label>"));
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
        return basketRows(path, prefix + "-basket", count);
    }

    private List<Element> basketRows(Path path, String bid, int count) throws Exception {
        var baskets = children(readDataSection(path, version));
        assertEquals(1, baskets.size());
        assertEquals(bid, id(baskets.getFirst(), version, "bid"));
        var rows = children(baskets.getFirst()).stream().sorted(Comparator.comparing(row -> id(row, version, "tid"))).toList();
        assertEquals(count, rows.size());
        return rows;
    }

    @FunctionalInterface
    private interface CheckedRows {
        void check(List<Element> rows) throws Exception;
    }
}
