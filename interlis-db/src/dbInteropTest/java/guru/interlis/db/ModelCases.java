package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertStructures;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModelCases {
    private static final String STORED_MODELS = "%ILI_FROM_DB";
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ModelCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void appendModels(TestDatabase db) throws Exception {
        String original = "DbImportedStruct" + version;
        String added = "DbLaterStruct" + version;
        String library = "DbSharedDetail" + version;
        var existingFile = fixture(original + ".ili");
        var sharedFile = fixture(library + ".ili");
        var newFile = Files.writeString(work.resolve(added + ".ili"), Files.readString(existingFile).replace(original, added));
        var oldTransfer = fixture(original + ".xtf");
        createReference(db, original, existingFile);
        referenceImport(db, "original", oldTransfer);
        var badLibrary = Files.writeString(work.resolve("conflicting-library.ili"),
                Files.readString(sharedFile).replace("TEXT*20", "TEXT*21"));
        assertCliFailure(db, "schemaimport", added, null, newFile, "Stored MODEL source differs for " + library,
                "--add-models", "--model-file", badLibrary.toString());
        assertCliFailure(db, "schemaimport", added, null, newFile, "conflict with the existing ili2db SETTINGS",
                "--add-models", "--model-file", sharedFile.toString(), "--default-epsg", "21781");

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            String oldId = scalar(sql, "SELECT t_id FROM " + db.table("report"));
            var checks = new AtomicInteger();
            var walker = StackWalker.getInstance();
            CancellationToken duringDdl = () -> walker.walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals(SchemaSql.class.getName()) && frame.getMethodName().equals("apply")))
                    && checks.incrementAndGet() == 2;
            var cancelled = schemaWithIdentityColumns(db, added, newFile, duringDdl, "--add-models",
                    "--model-file", sharedFile.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(2, checks.get());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(oldId, scalar(sql, "SELECT t_id FROM " + db.table("report")));

            var extended = schemaWithIdentityColumns(db, added, newFile, CancellationToken.none(), "--add-models",
                    "--model-file", sharedFile.toString());
            assertEquals(0, extended.code(), extended::diagnostics);
            assertEquals(before.relations().get("settings"), DbSchemaCatalog.read(sql, db.postgis(), db.schema())
                    .relations().get("settings"));
            assertEquals(oldId, scalar(sql, "SELECT t_id FROM " + db.table("report")));
            assertEquals("report", tableFor(db, original + ".Data.Report"));
            String newTable = tableFor(db, added + ".Data.Report");
            assertEquals("dblaterstrct" + version + "data_report", newTable);
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_model")));
            assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_classname")
                    + " WHERE iliname='" + library + ".Detail'"));

            for (var file : List.of(existingFile, sharedFile, newFile, badLibrary)) Files.delete(file);
            var output = work.resolve("append-output.xtf");
            assertCliSuccess(db, "export", original, "original", output);
            assertStructures(output, version, original, library, "fixture");
            String addition = Files.readString(oldTransfer).replace(original, added);
            var addInput = Files.writeString(work.resolve("new-model.xtf"), addition.replace("fixture-", "native-"));
            assertCliSuccess(db, "import", added, "native", addInput);
            referenceExport(db, "native", output);
            assertStructures(output, version, added, library, "native");

            var tail = Files.writeString(work.resolve("new-model-tail.xtf"), addition.replace("fixture-", "tail-"));
            referenceImport(db, "tail", tail);
            assertCliSuccess(db, "export", added, "tail", output);
            assertStructures(output, version, added, library, "tail");
            String ids = List.of("report", newTable, "detail", "t_ili2db_dataset", "t_ili2db_basket").stream()
                    .map(table -> "SELECT t_id FROM " + db.table(table)).collect(java.util.stream.Collectors.joining(" UNION ALL "));
            assertEquals("24", scalar(sql, "SELECT COUNT(DISTINCT t_id) FROM (" + ids + ") ids"));
        }
    }

    private Path fixture(String name) throws Exception {
        Path target = work.resolve(name);
        try (var resource = ModelCases.class.getResourceAsStream("/" + name)) {
            Files.copy(resource, target);
        }
        return target;
    }

    private void createReference(TestDatabase db, String models, Path... sources) throws Exception {
        var args = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", models));
        for (var source : sources) args.add(source.toString());
        reference.run(db, work.toString(), args.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path file) throws Exception {
        reference.run(db, STORED_MODELS, "--import", "--dataset", dataset, "--importBid", "--importTid", file.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path file) throws Exception {
        reference.run(db, STORED_MODELS, "--export", "--dataset", dataset, "--exportTid", file.toString());
    }
}
