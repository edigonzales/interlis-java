package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class EnumerationCases {
    private static final String BASE = """
            DOMAIN Grade = (Zulu, Family (Zed, Alpha), Last_Code) ORDERED;
            CLASS Asset =
              GradeValue : Grade;
              DirectValue : (No_Sign, Present);
              Active : BOOLEAN;
            END Asset;
            """;

    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    EnumerationCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void itfCodes(TestDatabase db) throws Exception {
        String model = "EnumItf" + version;
        source(model, BASE);
        schema(db, model, "--createEnumColAsItfCode");
        mixScalars(db, model);
        try (var sql = db.connect()) {
            assertItfRows(db, sql, "legacy");
            assertItfRows(db, sql, "native");
            String assets = db.table("asset");
            Path output = work.resolve("output.xtf");
            byte[] previous = Files.readAllBytes(output);
            execute(sql, "UPDATE " + assets + " SET gradevalue = 99 WHERE t_ili_tid = 'legacy-value'");
            assertCliFailure(db, "export", model, "legacy", output, "Unknown numeric enumeration value 99");
            assertArrayEquals(previous, Files.readAllBytes(output));

            var broken = dataset(model, "broken", object(model, "Asset", "broken-first", "<GradeValue>Zulu</GradeValue>")
                    + object(model, "Asset", "broken-last", "<GradeValue>Family.Missing</GradeValue>"));
            assertCliFailure(db, "import", model, "broken", broken, "Cannot encode enumeration value Family.Missing");
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + assets));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
        }
    }

    private static void assertItfRows(TestDatabase db, Connection sql, String prefix) throws Exception {
        String value = " FROM " + db.table("asset") + " WHERE t_ili_tid = '" + prefix;
        assertEquals("2", scalar(sql, "SELECT gradevalue" + value + "-value'"));
        assertEquals("0", scalar(sql, "SELECT directvalue" + value + "-value'"));
        assertNull(scalar(sql, "SELECT gradevalue" + value + "-missing'"));
    }

    private void mixScalars(TestDatabase db, String model) throws Exception {
        Path output = work.resolve("output.xtf");
        referenceImport(db, "legacy", scalars(model, "legacy"));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertScalars(output, "legacy");
        assertCliSuccess(db, "import", model, "native", scalars(model, "native"));
        referenceExport(db, "native", output);
        assertScalars(output, "native");
    }

    private Path scalars(String model, String prefix) throws Exception {
        return dataset(model, prefix, object(model, "Asset", prefix + "-value", """
                <GradeValue>Family.Alpha</GradeValue><DirectValue>No_Sign</DirectValue><Active>true</Active>
                """) + object(model, "Asset", prefix + "-missing", ""));
    }

    private void assertScalars(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-basket", id(baskets.getFirst(), version, "bid"));
        var objects = children(baskets.getFirst()).stream()
                .collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
        assertEquals(2, objects.size());
        var value = objects.get(prefix + "-value");
        assertEquals("Family.Alpha", child(value, "GradeValue").getTextContent());
        assertEquals("No_Sign", child(value, "DirectValue").getTextContent());
        assertEquals("true", child(value, "Active").getTextContent());
        assertNull(child(objects.get(prefix + "-missing"), "GradeValue"));
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

    private Path dataset(String model, String prefix, String objects) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"),
                referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    private String object(String model, String type, String tid, String fields) {
        String name = version == 23 ? model + ".Data." + type : type;
        return "<" + name + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + fields + "</" + name + ">";
    }

    private void schema(TestDatabase db, String model, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol",
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
