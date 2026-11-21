package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    void sharedSource(TestDatabase db) throws Exception {
        String model = "DbSharedSource" + version;
        Path source = fixture(model + ".ili");
        createReference(db, model, source);
        Files.delete(source);
        try (var sql = db.connect()) {
            assertEquals("DbSourceTypes" + version + " " + model + "{ DbSourceTypes" + version + "}",
                    scalar(sql, "SELECT modelname FROM " + db.table("t_ili2db_model")));
            assertEquals(model + ".Data.Station", scalar(sql, "SELECT iliname FROM " + db.table("t_ili2db_classname")));
        }
        exchangeDomains(db, model, fixture(model + ".xtf"));
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_basket") + " SET topic='DbSourceTypes" + version
                    + ".Unused' WHERE t_ili_tid='fixture-basket'");
            var result = cli(db, "delete", model, "legacy", null, CancellationToken.none());
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("outside the selected model"), result::diagnostics);
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("station")));
        }
    }

    private void exchangeDomains(TestDatabase db, String model, Path input) throws Exception {
        referenceImport(db, "legacy", input);
        Path output = work.resolve("legacy-output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertDomainDelivery(output, model, "fixture");

        Path second = work.resolve("own-input.xtf");
        Files.writeString(second, Files.readString(input).replace("fixture-", "own-"));
        assertCliSuccess(db, "import", model, "own", second);
        output = work.resolve("own-output.xtf");
        referenceExport(db, "own", output);
        assertDomainDelivery(output, model, "own");
        try (var sql = db.connect()) {
            String query = db.postgis()
                    ? "SELECT DISTINCT ST_SRID(location) FROM " + db.table("station")
                    : "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name='station' AND column_name='location'";
            assertEquals("2056", scalar(sql, query));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("station")
                    + " WHERE aname='Imported domains' AND astate='active'"));
        }
    }

    private void assertDomainDelivery(Path file, String model, String prefix) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(model), headerModels(data));
        var basket = children(data).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var station = children(basket).getFirst();
        assertEquals(prefix + "-station", id(station, version, "tid"));
        assertEquals("Imported domains", child(station, "Name").getTextContent());
        assertEquals("active", child(station, "State").getTextContent());
        var point = children(child(station, "Location")).getFirst();
        assertDecimal("2600123.125", children(point).get(0).getTextContent());
        assertDecimal("1200456.375", children(point).get(1).getTextContent());
    }

    private List<String> headerModels(Element data) {
        var header = child((Element) data.getParentNode(), version == 23 ? "HEADERSECTION" : "headersection");
        return children(child(header, version == 23 ? "MODELS" : "models")).stream()
                .map(model -> version == 23 ? model.getAttribute("NAME") : model.getTextContent().strip()).toList();
    }

    private static void assertDecimal(String expected, String actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)));
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
