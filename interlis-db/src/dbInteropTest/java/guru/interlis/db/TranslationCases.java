package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class TranslationCases {
    private final Path work;
    private final ReferenceTool reference;
    private final TranslationFixtures fixture;
    private final String original;
    private final String translated;

    TranslationCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        fixture = new TranslationFixtures(work, version);
        original = fixture.original;
        translated = fixture.translated;
    }

    void mutation(TestDatabase db) throws Exception {
        fixture.copy(original + ".ili");
        fixture.copy(translated + ".ili");
        createReference(db, translated);
        boolean french = fixture.french;
        String prefix = fixture.scalarLabel;
        var input = fixture.scalarInput();
        referenceImport(db, "translation", input);
        var changed = Files.writeString(work.resolve("translation-update.xtf"),
                Files.readString(input).replace(">text<", ">updated<"));
        var output = work.resolve("translation-mutation.xtf");
        String object = "SELECT t_id FROM " + db.table("station");
        String basket = "SELECT t_id FROM " + db.table("t_ili2db_basket");
        String dataset = "SELECT t_id FROM " + db.table("t_ili2db_dataset");
        try (var sql = db.connect()) {
            String previousObject = scalar(sql, object);
            String previousBasket = scalar(sql, basket);
            String previousDataset = scalar(sql, dataset);
            run(db, "update", translated, "translation", changed);
            assertEquals(previousObject, scalar(sql, object));
            assertEquals(previousBasket, scalar(sql, basket));
            referenceExport(db, "translation", output);
            fixture.assertScalarDelivery(output, french, prefix, "updated");
            if (french) {
                var fromOriginal = Files.writeString(work.resolve("wrong-language.xtf"),
                        Files.readString(fixture.copy("original23.xtf")).replace("original-", "translated-"));
                assertCliFailure(db, "update", original, "translation", fromOriginal, "another dataset or topic");
                assertCliFailure(db, "delete", original, "translation", null, "outside the selected model");
                assertEquals(previousObject, scalar(sql, object));
                assertEquals(previousBasket, scalar(sql, basket));
                run(db, "export", translated, "translation", output);
                fixture.assertScalarDelivery(output, true, prefix, "updated");
            }
            run(db, "replace", translated, "translation", input);
            assertEquals(previousDataset, scalar(sql, dataset));
            assertNotEquals(previousObject, scalar(sql, object));
            referenceExport(db, "translation", output);
            fixture.assertScalarDelivery(output, french, prefix, "text");
            run(db, "delete", translated, "translation", null);
            for (var table : List.of("station", "t_ili2db_basket", "t_ili2db_dataset")) {
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table(table)), table);
            }
        }
    }

    private void createReference(TestDatabase db, String model) throws Exception {
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--models", model);
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output, String... options) throws Exception {
        var args = new java.util.ArrayList<>(List.of("--export", "--dataset", dataset, "--exportTid"));
        args.addAll(List.of(options));
        args.add(output.toString());
        reference.run(db, "%ILI_FROM_DB", args.toArray(String[]::new));
    }

    private void run(TestDatabase db, String operation, String model, String dataset, Path path, String... options) {
        var result = cli(db, operation, model, dataset, path, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }
}
