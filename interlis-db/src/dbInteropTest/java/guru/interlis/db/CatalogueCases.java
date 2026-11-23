package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
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

    void smart2(TestDatabase db) throws Exception {
        String model = "CatalogueSurvey" + version;
        fixture(model + ".ili");
        referenceSchema(db, model, true);
        var catalog = fixture(model + "-catalog.xtf");
        var survey = fixture(model + "-survey.xtf");
        Files.writeString(survey, Files.readString(survey).replaceFirst("material-a", "secondary-a"));
        var output = work.resolve("smart2-output.xtf");
        referenceImport(db, "catalog", catalog);
        referenceImport(db, "legacy", survey);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSurvey(output, model, "survey", "secondary-a");

        assertCliSuccess(db, "import", model, "native", surveyWithPrefix(survey, "native"));
        referenceExport(db, "native", output);
        assertSurvey(output, model, "native", "secondary-a");

        try (var sql = db.connect()) {
            String assets = db.table("asset");
            String material = db.table("material");
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + assets + " a JOIN " + material
                    + " m ON m.t_id=a.requiredmaterial_material JOIN " + db.table("asecondary")
                    + " s ON s.t_id=a.mainmaterial_asecondary WHERE m.t_ili_tid='material-a'"
                    + " AND s.t_ili_tid='secondary-a' AND a.mainmaterial_material IS NULL"
                    + " AND a.requiredmaterial_asecondary IS NULL"));
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_attrname")
                    + " WHERE colowner='asset' AND target IN ('material','asecondary') AND iliname IN ('"
                    + model + ".Survey.Asset.MainMaterial','" + model + ".Survey.Asset.RequiredMaterial')"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("materialref")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("materialmandatory")));
            execute(sql, "UPDATE " + assets + " SET mainmaterial_material=requiredmaterial_material WHERE t_ili_tid='native-a'");
        }
        var previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output,
                "Multiple stored values for " + model + ".Survey.Asset.MainMaterial");
        assertEquals(previous, Files.readString(output));

        var other = Files.writeString(work.resolve("other-catalog.xtf"),
                Files.readString(catalog).replace("cat-b", "other-b"));
        assertCliSuccess(db, "import", model, "other-catalog", other);
        var ambiguous = surveyWithPrefix(survey, "ambiguous");
        Files.writeString(ambiguous, Files.readString(ambiguous).replace(" BID=\"cat-b\"", ""));
        assertCliFailure(db, "import", model, "ambiguous", ambiguous, "Ambiguous stored reference target secondary-a");
        assertNoFailedImport(db, "ambiguous");
    }

    void schema(TestDatabase referenceNewClass, TestDatabase ownNewClass,
                TestDatabase referenceSmart2, TestDatabase ownSmart2) throws Exception {
        String model = "CatalogueSurvey" + version;
        var source = fixture(model + ".ili");
        var catalog = fixture(model + "-catalog.xtf");
        var survey = fixture(model + "-survey.xtf");
        createAndCompare(referenceNewClass, ownNewClass, model, source, false);
        exchangeInOwnSchema(ownNewClass, model, catalog, survey, "material-a", false);
        createAndCompare(referenceSmart2, ownSmart2, model, source, true);
        Files.writeString(survey, Files.readString(survey).replaceFirst("material-a", "secondary-a"));
        exchangeInOwnSchema(ownSmart2, model, catalog, survey, "secondary-a", true);
    }

    private void createAndCompare(TestDatabase baseline, TestDatabase own, String model, Path source, boolean smart2)
            throws Exception {
        referenceSchema(baseline, model, smart2);
        String[] options = smart2 ? new String[]{"--coalesce-catalogue-ref", "--inheritance", "smart2"}
                : new String[]{"--coalesce-catalogue-ref"};
        var result = schemaWithIdentityColumns(own, model, source, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var ref = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            var nativeSchema = DbSchemaCatalog.read(actual, own.postgis(), own.schema());
            if (smart2) {
                ref.canonicalReferenceAlternatives().assertSameAs(nativeSchema.canonicalReferenceAlternatives());
            } else {
                ref.assertSameAs(nativeSchema);
            }
        }
    }

    private void exchangeInOwnSchema(TestDatabase db, String model, Path catalog, Path survey, String target, boolean smart2)
            throws Exception {
        var output = work.resolve("schema-output.xtf");
        referenceImport(db, "catalog", catalog);
        referenceImport(db, "legacy", survey);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSurvey(output, model, "survey", target);
        assertCliSuccess(db, "import", model, "native", surveyWithPrefix(survey, "native"));
        referenceExport(db, "native", output);
        assertSurvey(output, model, "native", target);
        referenceImport(db, "tail", surveyWithPrefix(survey, "tail"));
        assertCliSuccess(db, "export", model, "tail", output);
        assertSurvey(output, model, "tail", target);

        assertCliFailure(db, "delete", model, "catalog", null, "Cannot delete referenced object T_Id");
        try (var sql = db.connect()) {
            String targets = smart2 ? "material" : "item";
            var tables = List.of(targets, "asset", "t_ili2db_dataset", "t_ili2db_basket");
            var ids = new ArrayList<>(tables.stream().map(table -> "SELECT t_id FROM " + db.table(table)).toList());
            if (smart2) ids.add("SELECT t_id FROM " + db.table("asecondary"));
            assertEquals("19", scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + String.join(" UNION ALL ", ids) + ") ids"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")
                    + " WHERE datasetName='catalog'"));
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
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
