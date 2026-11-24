package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CatalogueReferenceStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void missingTargetAndInvalidForeignKeyMetadata(Backend backend, int version) throws Exception {
        var fixture = new CatalogueFixtures(work, "CatalogueSurvey" + version, version);
        var db = POSTGIS.target(work.resolve("catalogue-new-class.gpkg"), backend);
        seed(db, fixture, "newClass", fixture.survey);
        assertMetadata(db, fixture.model, false);

        var broken = fixture.surveyWithPrefix(fixture.survey, "broken");
        Files.writeString(broken, Files.readString(broken).replace("material-a", "missing"));
        assertCliFailure(db, "import", fixture.model, "broken", broken, "Cannot encode reference from broken-a at "
                + fixture.model + ".Survey.Asset.MainMaterial to missing");
        assertNoFailedImport(db, "broken");
        assertRetainsCatalogue(db, fixture, false);

        Path output = work.resolve("output.xtf");
        assertCliSuccess(db, "export", fixture.model, "survey", output);
        fixture.assertSurvey(output, "survey", "material-a");
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop") + " SET setting='item'"
                    + " WHERE tablename='asset' AND columnname='mainmaterial' AND tag='ch.ehi.ili2db.foreignKey'");
        }
        assertAtomicExportFailure(db, fixture, output, "Inconsistent COLUMN_PROP.foreignKey");
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void ambiguousTargetAndMultipleStoredValues(Backend backend, int version) throws Exception {
        var fixture = new CatalogueFixtures(work, "CatalogueSurvey" + version, version);
        var db = POSTGIS.target(work.resolve("catalogue-smart2.gpkg"), backend);
        Path survey = fixture.smart2Survey();
        seed(db, fixture, "smart2", survey);
        assertMetadata(db, fixture.model, true);
        assertRetainsCatalogue(db, fixture, true);

        var other = Files.writeString(work.resolve("other-catalog.xtf"),
                Files.readString(fixture.catalogue).replace("cat-b", "other-b"));
        assertCliSuccess(db, "import", fixture.model, "other-catalog", other);
        var ambiguous = fixture.surveyWithPrefix(survey, "ambiguous");
        Files.writeString(ambiguous, Files.readString(ambiguous).replace(" BID=\"cat-b\"", ""));
        assertCliFailure(db, "import", fixture.model, "ambiguous", ambiguous, "Ambiguous stored reference target secondary-a");
        assertNoFailedImport(db, "ambiguous");
        fixture.assertStoredReferences(db, true, "survey", "survey");
        assertCliSuccess(db, "delete", fixture.model, "other-catalog", null);

        Path output = work.resolve("output.xtf");
        assertCliSuccess(db, "export", fixture.model, "survey", output);
        fixture.assertSurvey(output, "survey", "secondary-a");
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("asset")
                    + " SET mainmaterial_material=requiredmaterial_material WHERE t_ili_tid='survey-a'");
        }
        assertAtomicExportFailure(db, fixture, output, "Multiple stored values for " + fixture.model + ".Survey.Asset.MainMaterial");
    }

    private static void seed(Target db, CatalogueFixtures fixture, String layout, Path survey) {
        fixture.schema(db, layout);
        assertCliSuccess(db, "import", fixture.model, "catalog", fixture.catalogue);
        assertCliSuccess(db, "import", fixture.model, "survey", survey);
    }

    private static void assertRetainsCatalogue(Target db, CatalogueFixtures fixture, boolean smart2) throws Exception {
        assertCliFailure(db, "delete", fixture.model, "catalog", null, "Cannot delete referenced object T_Id");
        fixture.assertStoredReferences(db, smart2, "survey", "survey");
        try (var sql = db.connect()) {
            assertEquals(smart2 ? "1" : "2", scalar(sql, "SELECT count(*) FROM " + db.table(smart2 ? "material" : "item")));
            if (smart2) assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("asecondary")));
        }
    }

    private static void assertMetadata(Target db, String model, boolean smart2) throws Exception {
        String targets = smart2 ? "('material','asecondary')" : "('material')";
        var structures = smart2 ? List.of("materialref", "materialmandatory")
                : List.of("cataloguereference", "mandatorycataloguereference", "materialref", "materialmandatory");
        try (var sql = db.connect()) {
            assertEquals(smart2 ? "4" : "2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_attrname")
                    + " WHERE colowner='asset' AND target IN " + targets + " AND iliname IN ('"
                    + model + ".Survey.Asset.MainMaterial','" + model + ".Survey.Asset.RequiredMaterial')"));
            for (String table : structures) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }
    }

    private static void assertNoFailedImport(Target db, String dataset) throws Exception {
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE t_ili_tid LIKE '" + dataset + "-%'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='" + dataset + "-b'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='" + dataset + "'"));
        }
    }

    private static void assertAtomicExportFailure(Target db, CatalogueFixtures fixture, Path output, String diagnostic) throws Exception {
        byte[] original = Files.readAllBytes(output);
        assertCliFailure(db, "export", fixture.model, "survey", output, diagnostic);
        assertArrayEquals(original, Files.readAllBytes(output));
    }
}
