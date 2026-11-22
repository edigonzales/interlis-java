package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TranslationCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;
    private final String original;
    private final String translated;

    TranslationCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
        original = "TownBase" + version;
        translated = "TownFrench" + version;
    }

    void sources(TestDatabase db) throws Exception {
        var originalFile = fixture(original + ".ili");
        var translatedFile = fixture(translated + ".ili");
        createReference(db, translated);
        Files.delete(originalFile);
        Files.delete(translatedFile);
        var input = fixture("original" + version + ".xtf");
        referenceImport(db, "legacy", input);
        var output = work.resolve("output.xtf");
        run(db, "export", translated, "legacy", output, "--model", original);
        assertDelivery(output, false, "original");
        var own = Files.writeString(work.resolve("own.xtf"), Files.readString(input).replace("original-", "own-"));
        run(db, "import", original, "own", own, "--model", translated);
        referenceExport(db, "own", output);
        assertDelivery(output, false, "own");

        String saved = Files.readString(output);
        String catalog = db.table("t_ili2db_model");
        try (var sql = db.connect()) {
            assertEquals(original + ".Survey.Station", scalar(sql, "SELECT iliname FROM " + db.table("t_ili2db_classname")));
            assertEquals(translated + "{ " + original + "}", scalar(sql, "SELECT modelname FROM " + catalog
                    + " WHERE modelname LIKE '" + translated + "%'"));
            execute(sql, "UPDATE " + catalog + " SET content=replace(content, 'VERSION \"1\"', 'VERSION \"2\"')"
                    + " WHERE modelname='" + original + "'");
            assertRejectedExport(db, output, saved, "Cannot compile");
            execute(sql, "UPDATE " + catalog + " SET content=replace(content, 'VERSION \"2\"', 'VERSION \"1\"')"
                    + " WHERE modelname='" + original + "'");
            execute(sql, "INSERT INTO " + catalog + " (filename,iliversion,modelname,content,importdate)"
                    + " SELECT filename,iliversion,modelname||' Ambiguous',content,importdate FROM " + catalog
                    + " WHERE modelname='" + original + "'");
            assertRejectedExport(db, output, saved, "Multiple MODEL sources for " + original);
            execute(sql, "DELETE FROM " + catalog + " WHERE modelname LIKE '" + original + "%'");
            assertRejectedExport(db, output, saved, "No MODEL source for " + original);
        }
    }

    private void assertRejectedExport(TestDatabase db, Path output, String saved, String reason) throws Exception {
        var result = cli(db, "export", translated, "legacy", output, CancellationToken.none(), "--model", original);
        assertEquals(1, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains(reason), result::diagnostics);
        assertEquals(saved, Files.readString(output));
    }

    void scalars(TestDatabase db) throws Exception {
        fixture(original + ".ili");
        fixture(translated + ".ili");
        createReference(db, translated);
        boolean french = version == 23;
        String label = french ? "translated" : "original";
        var input = fixture(label + version + ".xtf");
        referenceImport(db, "legacy", input);
        var output = work.resolve("output.xtf");
        run(db, "export", translated, "legacy", output);
        assertDelivery(output, french, label);
        var own = Files.writeString(work.resolve("import.xtf"), Files.readString(input).replace(label + "-", "own-"));
        run(db, "import", translated, "own", own);
        referenceExport(db, "own", output);
        assertDelivery(output, french, "own");
        try (var sql = db.connect()) {
            String topic = french ? translated + ".Releve" : original + ".Survey";
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("station")
                    + " WHERE astate='open' AND t_ili_tid IN ('" + label + "-o','own-o')"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_basket")
                    + " WHERE topic='" + topic + "' AND t_ili_tid IN ('" + label + "-b','own-b')"));
        }
    }

    void nested(TestDatabase db) throws Exception {
        String translatedModel = "NestedFrench" + version;
        fixture("NestedTown" + version + ".ili");
        fixture(translatedModel + ".ili");
        createReference(db, translatedModel);
        var input = fixture("nested" + version + ".xtf");
        referenceImport(db, "legacy", input);
        var output = work.resolve("nested-output.xtf");
        run(db, "export", translatedModel, "legacy", output);
        assertNested(output, "nested");
        var own = Files.writeString(work.resolve("nested-own.xtf"), Files.readString(input).replace("nested-", "own-"));
        run(db, "import", translatedModel, "own", own);
        referenceExport(db, "own", output);
        assertNested(output, "own");
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("reading") + " r JOIN "
                    + db.table("details") + " p ON p.reading_payload=r.t_id AND p.t_basket=r.t_basket JOIN "
                    + db.table("detail") + " d ON d.details_entry=p.t_id AND d.t_basket=r.t_basket JOIN "
                    + db.table("site") + " s ON s.t_id=d.target AND s.t_id=r.atsite AND s.t_basket=r.t_basket"
                    + " WHERE d.memo='inside'"));
            assertEquals("2056", scalar(sql, db.postgis()
                    ? "SELECT DISTINCT ST_SRID(location) FROM " + db.table("reading")
                    : "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name='reading' AND column_name='location'"));
        }
    }

    private void assertNested(Path path, String prefix) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var reading = children(basket).stream().filter(record -> id(record, version, "tid").equals(prefix + "-r"))
                .findFirst().orElseThrow();
        boolean french = version == 23;
        var outer = children(child(reading, french ? "Infos" : "Payload")).getFirst();
        var inner = children(child(outer, french ? "Entree" : "Entry")).getFirst();
        assertEquals(french ? "NestedFrench23.Releve.Note" : "Detail", inner.getLocalName());
        assertEquals("inside", child(inner, french ? "Texte" : "Memo").getTextContent());
        assertEquals(prefix + "-s", id(child(inner, french ? "Cible" : "Target"), version, "ref"));
        assertEquals(prefix + "-s", id(child(reading, french ? "AuLieu" : "AtSite"), version, "ref"));
        var coordinate = children(child(reading, french ? "Position" : "Location")).getFirst();
        var axes = children(coordinate);
        assertEquals(0, new BigDecimal("2600215.125").compareTo(new BigDecimal(axes.get(0).getTextContent())));
        assertEquals(0, new BigDecimal("1200224.500").compareTo(new BigDecimal(axes.get(1).getTextContent())));
    }

    private void assertDelivery(Path output, boolean french, String prefix) throws Exception {
        var data = readDataSection(output, version);
        String model = french ? translated : original;
        var header = child((Element) data.getParentNode(), version == 23 ? "HEADERSECTION" : "headersection");
        var models = children(child(header, version == 23 ? "MODELS" : "models"));
        assertEquals(List.of(model), models.stream().map(m -> version == 23 ? m.getAttribute("NAME")
                : m.getTextContent().strip()).toList());
        var basket = children(data).getFirst();
        var station = children(basket).getFirst();
        String topic = french ? "Releve" : "Survey";
        String object = french ? "Poste" : "Station";
        assertEquals(version == 23 ? model + "." + topic : topic, basket.getLocalName());
        assertEquals(version == 23 ? model + "." + topic + "." + object : object, station.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + original, station.getNamespaceURI());
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        assertEquals(prefix + "-o", id(station, version, "tid"));
        assertEquals("text", child(station, french ? "Note" : "Memo").getTextContent());
        assertEquals(french ? "ouvert" : "open", child(station, french ? "Statut" : "State").getTextContent());
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var resource = getClass().getResourceAsStream("/translation/" + name)) {
            Files.copy(resource, path);
        }
        return path;
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
