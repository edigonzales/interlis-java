package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        String tag = type(model, "Asset");
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
