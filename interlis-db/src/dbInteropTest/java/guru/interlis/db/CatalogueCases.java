package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CatalogueCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    CatalogueCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void singleTarget(TestDatabase db) throws Exception {
        String model = "CatalogueSurvey" + version;
        fixture(model + ".ili");
        referenceSchema(db, model, false);
        var catalog = fixture(model + "-catalog.xtf");
        var survey = fixture(model + "-survey.xtf");
        var output = work.resolve("survey-output.xtf");
        referenceImport(db, "catalog", catalog);
        referenceImport(db, "legacy", survey);
        try (var sql = db.connect()) {
            assertNotNull(scalar(sql, "SELECT mainmaterial FROM " + db.table("asset") + " WHERE t_ili_tid='survey-a'"),
                    "The reference import must store the external target");
        }
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSurvey(output, model, "survey", "material-a");

        assertCliSuccess(db, "import", model, "native", surveyWithPrefix(survey, "native"));
        referenceExport(db, "native", output);
        assertSurvey(output, model, "native", "material-a");

        try (var sql = db.connect()) {
            String assets = db.table("asset");
            String material = db.table("material");
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + assets + " a JOIN " + material
                    + " m ON m.t_id = a.mainmaterial AND m.t_id = a.requiredmaterial"
                    + " JOIN " + db.table("item") + " i ON i.t_id=m.t_id WHERE i.t_ili_tid = 'material-a'"));
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + assets
                    + " WHERE mainmaterial IS NULL AND requiredmaterial IS NULL"));
            String attr = db.table("t_ili2db_attrname");
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + attr + " WHERE colowner='asset' AND target='material'"
                    + " AND iliname IN ('" + model + ".Survey.Asset.MainMaterial','" + model + ".Survey.Asset.RequiredMaterial')"));
            for (String table : List.of("cataloguereference", "mandatorycataloguereference", "materialref", "materialmandatory")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }
        var broken = surveyWithPrefix(survey, "broken");
        Files.writeString(broken, Files.readString(broken).replace("material-a", "missing"));
        assertCliFailure(db, "import", model, "broken", broken, "Cannot encode reference from broken-a at "
                + model + ".Survey.Asset.MainMaterial to missing");
        assertNoFailedImport(db, "broken");

        var previous = Files.readString(output);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop") + " SET setting='item'"
                    + " WHERE tablename='asset' AND columnname='mainmaterial' AND tag='ch.ehi.ili2db.foreignKey'");
        }
        assertCliFailure(db, "export", model, "legacy", output, "Inconsistent COLUMN_PROP.foreignKey");
        assertEquals(previous, Files.readString(output));
    }

    void directReference(TestDatabase db) throws Exception {
        String model = "CatalogueDirect24";
        fixture(model + ".ili");
        referenceSchema(db, model, false);
        var catalog = fixture(model + "-catalog.xtf");
        var survey = fixture(model + "-survey.xtf");
        var output = work.resolve("direct-output.xtf");
        referenceImport(db, "catalog", catalog);
        referenceImport(db, "legacy", survey);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertDirectReference(output, "survey");

        assertCliSuccess(db, "import", model, "native", surveyWithPrefix(survey, "direct-native"));
        referenceExport(db, "native", output);
        assertDirectReference(output, "direct-native");
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_trafo")
                    + " WHERE tag='ch.ehi.ili2db.catalogueRefTrafo'"));
        }
    }

    private void assertDirectReference(Path output, String prefix) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var asset = children(basket).getFirst();
        assertEquals(prefix + "-a", id(asset, version, "tid"));
        var material = child(asset, "Material");
        assertEquals("material-a", id(material, version, "ref"));
        assertEquals(List.of(), children(material));
    }

    private static void assertNoFailedImport(TestDatabase db, String dataset) throws Exception {
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE t_ili_tid LIKE '" + dataset + "-%'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid = '" + dataset + "-b'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetName = '" + dataset + "'"));
        }
    }

    private void assertSurvey(Path output, String model, String prefix, String mainTarget) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        Map<String, Element> assets = children(basket).stream()
                .collect(Collectors.toMap(asset -> id(asset, version, "tid"), Function.identity()));
        assertEquals(3, assets.size());
        var record = assets.get(prefix + "-a");
        assertReference(record, model, "MainMaterial", "MaterialRef", mainTarget);
        assertReference(record, model, "RequiredMaterial", "MaterialMandatory", "material-a");
        for (var name : List.of("empty", "absent")) {
            assertNull(child(assets.get(prefix + "-" + name), "MainMaterial"));
            assertNull(child(assets.get(prefix + "-" + name), "RequiredMaterial"));
        }
    }

    private void assertReference(Element asset, String model, String field, String structure, String target) {
        var attribute = child(asset, field);
        assertNotNull(attribute, field);
        var wrapper = children(attribute).getFirst();
        assertEquals(version == 23 ? model + ".Catalog." + structure : structure, wrapper.getLocalName());
        var ref = child(wrapper, "Reference");
        assertNotNull(ref);
        assertEquals(target, id(ref, version, "ref"));
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/CatalogueObjects_V1", ref.getNamespaceURI());
    }

    private Path surveyWithPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input)
                .replace("survey-", prefix + "-"));
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var source = getClass().getResourceAsStream("/catalogue/" + name)) {
            Files.copy(source, path);
        }
        return path;
    }

    private void referenceSchema(TestDatabase db, String model, boolean smart2) throws Exception {
        var args = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--coalesceCatalogueRef",
                "--createBasketCol", "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG",
                "--defaultSrsCode", "2056", "--models", model));
        if (smart2) args.add("--smart2Inheritance");
        reference.run(db, work.toString(), args.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, work.toString(), "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
