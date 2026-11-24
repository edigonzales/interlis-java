package guru.interlis.db;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class SmartInteropSupport {
    private SmartInteropSupport() { }

    static void schema(ReferenceTool reference, TestDatabase db, Path work, String model, String profile) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--createBasketCol", "--createTidCol", "--sqlEnableNull",
                "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
        if (profile != null) options.addAll(List.of("--noSmartMapping", "--" + profile + "Inheritance"));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    static void importDataset(ReferenceTool reference, TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    static void exportDataset(ReferenceTool reference, TestDatabase db, String dataset, Path output) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
