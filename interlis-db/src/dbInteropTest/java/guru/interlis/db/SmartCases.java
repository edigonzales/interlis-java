package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SmartCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    SmartCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void smart1Scalars(TestDatabase db, TestDatabase defaults) throws Exception {
        String model = "SmartMeter" + version;
        var source = fixture(model + ".ili");
        referenceSchema(db, model, "smart1");
        referenceSchema(defaults, model, null);
        Files.delete(source);
        var input = fixture(model + ".xtf");
        var nativeInput = transferWithPrefix(input, "native");
        var output = work.resolve("output.xtf");
        referenceImport(defaults, "default", input);
        restoreEmptyMemo(defaults);
        assertCliSuccess(defaults, "export", model, "default", output);
        assertScalarObjects(output, model, "fixture", true);
        referenceImport(db, "legacy", input);
        restoreEmptyMemo(db);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertScalarObjects(output, model, "fixture", true);
        assertCliSuccess(db, "import", model, "native", nativeInput);
        referenceExport(db, "native", output);
        assertScalarObjects(output, model, "native", false);
        try (var sql = db.connect()) {
            String table = db.table("meter");
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + table + " WHERE t_type='meter' AND memo='' AND badge IS NULL"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + table + " WHERE t_type='smartunit' AND memo='base' AND badge='tag'"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("terminal") + " WHERE memo IS NULL"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_attrname")
                    + " WHERE iliname='" + model + ".Data.Instrument.Memo' AND colowner IN ('meter','terminal')"));
            execute(sql, "UPDATE " + table + " SET t_type='bogus' WHERE t_ili_tid='native-s'");
        }
        var previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, "Unknown or abstract T_Type bogus");
        assertEquals(previous, Files.readString(output));
    }

    private static void restoreEmptyMemo(TestDatabase db) throws Exception {
        // ili2db normalizes empty text. Store an empty SQL value for the native export.
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("meter") + " SET memo='' WHERE t_ili_tid='fixture-m'");
        }
    }

    private void assertScalarObjects(Path path, String model, String prefix, boolean emptyMemo) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        assertEquals(3, children(basket).size());
        var meter = object(basket, model, "Meter", prefix + "-m");
        var smart = object(basket, model, "SmartUnit", prefix + "-s");
        var terminal = object(basket, model, "Terminal", prefix + "-t");
        if (emptyMemo) {
            assertEquals("", child(meter, "Memo").getTextContent());
        } else {
            assertNull(child(meter, "Memo"));
        }
        assertEquals("idle", child(meter, "State").getTextContent());
        assertEquals("5", child(meter, "Counter").getTextContent());
        assertNull(child(meter, "Badge"));
        assertEquals("base", child(smart, "Memo").getTextContent());
        assertEquals("active", child(smart, "State").getTextContent());
        assertEquals("7", child(smart, "Counter").getTextContent());
        assertEquals("tag", child(smart, "Badge").getTextContent());
        assertNull(child(terminal, "Memo"));
        assertEquals("p1", child(terminal, "Socket").getTextContent());
    }

    private Element object(Element basket, String model, String name, String tid) {
        var object = children(basket).stream().filter(element -> tid.equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertEquals(version == 23 ? model + ".Data." + name : name, object.getLocalName());
        return object;
    }

    private Path transferWithPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input).replace("fixture-", prefix + "-"));
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var source = getClass().getResourceAsStream("/smart/" + name)) {
            Files.copy(source, path);
        }
        return path;
    }

    private void referenceSchema(TestDatabase db, String model, String profile) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--createBasketCol", "--createTidCol", "--sqlEnableNull",
                "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
        if (profile != null) options.addAll(List.of("--noSmartMapping", "--" + profile + "Inheritance"));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
