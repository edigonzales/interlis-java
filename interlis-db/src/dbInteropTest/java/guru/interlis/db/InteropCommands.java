package guru.interlis.db;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class InteropCommands {
    private InteropCommands() { }

    static void schema(ReferenceTool reference, TestDatabase db, Path work, String models, String profile,
                       String... mappings) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--createBasketCol", "--createTidCol", "--sqlEnableNull",
                "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", models));
        if (profile != null) {
            options.add("--noSmartMapping");
            if (!profile.equals("newClass")) options.add("--" + profile + "Inheritance");
        }
        options.addAll(List.of(mappings));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    static void importDataset(ReferenceTool reference, TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    static void exportDataset(ReferenceTool reference, TestDatabase db, String dataset, Path output, String... options)
            throws Exception {
        var args = new ArrayList<>(List.of("--export", "--dataset", dataset, "--exportTid"));
        args.addAll(List.of(options));
        args.add(output.toString());
        reference.run(db, "%ILI_FROM_DB", args.toArray(String[]::new));
    }

    static void assertSharedIds(TestDatabase db, int expected, String... tables) throws Exception {
        var allTables = new ArrayList<>(List.of(tables));
        allTables.addAll(List.of("t_ili2db_basket", "t_ili2db_dataset"));
        String ids = allTables.stream().map(table -> "SELECT t_id FROM " + db.table(table))
                .collect(Collectors.joining(" UNION ALL "));
        try (var sql = db.connect()) {
            assertEquals(Integer.toString(expected), scalar(sql, "SELECT count(*) FROM (" + ids + ") ids"));
            assertEquals(Integer.toString(expected), scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + ids + ") ids"));
        }
    }
}
