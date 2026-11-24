package guru.interlis.db;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CrsInteropSupport {
    private CrsInteropSupport() { }

    static void createReference(ReferenceTool reference, TestDatabase db, Path work, Path ili, int epsg, String... extra) throws Exception {
        var args = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG"));
        args.addAll(List.of("--defaultSrsCode", Integer.toString(epsg)));
        args.addAll(List.of(extra));
        args.add(ili.toString());
        reference.run(db, work.toString(), args.toArray(String[]::new));
    }

    static void assertCatalogs(TestDatabase old, TestDatabase own) throws Exception {
        try (var ref = old.connect(); var sql = own.connect()) {
            DbSchemaCatalog.read(ref, old.postgis(), old.schema())
                    .assertSameAs(DbSchemaCatalog.read(sql, own.postgis(), own.schema()));
        }
    }

    static void referenceImport(ReferenceTool reference, TestDatabase db, String dataset, Path file) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", file.toString());
    }

    static void referenceExport(ReferenceTool reference, TestDatabase db, String dataset, Path file) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", file.toString());
    }
}
