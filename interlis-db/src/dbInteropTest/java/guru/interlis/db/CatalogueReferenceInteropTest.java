package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CatalogueReferenceInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageNewClass23() throws Exception { exchange(23, null, "newClass"); }
    @Test void geoPackageNewClass24() throws Exception { exchange(24, null, "newClass"); }
    @Test void postgisNewClass23() throws Exception { exchange(23, POSTGIS.container(), "newClass"); }
    @Test void postgisNewClass24() throws Exception { exchange(24, POSTGIS.container(), "newClass"); }

    @Test void geoPackageSmart223() throws Exception { exchange(23, null, "smart2"); }
    @Test void geoPackageSmart224() throws Exception { exchange(24, null, "smart2"); }
    @Test void postgisSmart223() throws Exception { exchange(23, POSTGIS.container(), "smart2"); }
    @Test void postgisSmart224() throws Exception { exchange(24, POSTGIS.container(), "smart2"); }

    @Test void geoPackageDirect24() throws Exception { directReference(null); }
    @Test void postgisDirect24() throws Exception { directReference(POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres, String layout) throws Exception {
        var fixture = new CatalogueFixtures(work, "CatalogueSurvey" + version, version);
        String scope = "v" + version + "_" + layout.toLowerCase(java.util.Locale.ROOT) + "_";
        var baseline = new TestDatabase(work, postgres, "catalogue_reference", scope);
        var own = baseline.peer("catalogue_native");
        var reference = new ReferenceTool(work, own.postgis());
        schema(reference, baseline, work, fixture.model, layout, "--coalesceCatalogueRef");
        fixture.schema(own, layout);
        boolean smart2 = layout.equals("smart2");
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var ref = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            var calculated = DbSchemaCatalog.read(actual, own.postgis(), own.schema());
            if (smart2) {
                ref.canonicalReferenceAlternatives().assertSameAs(calculated.canonicalReferenceAlternatives());
            } else {
                ref.assertSameAs(calculated);
            }
        }

        Path survey = smart2 ? fixture.smart2Survey() : fixture.survey;
        String target = smart2 ? "secondary-a" : "material-a";
        Path nativeInput = fixture.surveyWithPrefix(survey, "native");
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "catalog", fixture.catalogue);
            importDataset(reference, db, "legacy", survey);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", output);
            fixture.assertSurvey(output, "survey", target);
            fixture.assertStoredReferences(db, smart2, "legacy", "survey");

            assertCliSuccess(db, "import", fixture.model, "native", nativeInput);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertSurvey(output, "native", target);
            fixture.assertStoredReferences(db, smart2, "native", "native");
        }

        importDataset(reference, own, "tail", fixture.surveyWithPrefix(survey, "tail"));
        var output = work.resolve("tail-output.xtf");
        assertCliSuccess(own, "export", fixture.model, "tail", output);
        fixture.assertSurvey(output, "tail", target);
        fixture.assertStoredReferences(own, smart2, "tail", "tail");
        InteropCommands.assertSharedIds(own, 19, smart2
                ? new String[]{"material", "asecondary", "asset"} : new String[]{"item", "asset"});
    }

    private void directReference(PostgreSQLContainer postgres) throws Exception {
        var fixture = new CatalogueFixtures(work, "CatalogueDirect24", 24);
        var db = new TestDatabase(work, postgres, "catalogue_direct24");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.model, "newClass", "--coalesceCatalogueRef");
        importDataset(reference, db, "catalog", fixture.catalogue);
        importDataset(reference, db, "legacy", fixture.survey);
        var output = work.resolve("direct-legacy.xtf");
        assertCliSuccess(db, "export", fixture.model, "legacy", output);
        assertDirectReference(output, "survey");

        assertCliSuccess(db, "import", fixture.model, "native", fixture.surveyWithPrefix(fixture.survey, "direct-native"));
        output = work.resolve("direct-native-output.xtf");
        exportDataset(reference, db, "native", output);
        assertDirectReference(output, "direct-native");
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_trafo")
                    + " WHERE tag='ch.ehi.ili2db.catalogueRefTrafo'"));
        }
    }

    private static void assertDirectReference(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, 24));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(prefix + "-b", id(basket, 24, "bid"));
        var asset = children(basket).getFirst();
        assertEquals(prefix + "-a", id(asset, 24, "tid"));
        var material = child(asset, "Material");
        assertEquals("material-a", id(material, 24, "ref"));
        assertEquals(List.of(), children(material));
    }
}
