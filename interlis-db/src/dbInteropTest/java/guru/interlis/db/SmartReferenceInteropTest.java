package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.indexColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.tableFor;
import static guru.interlis.db.Smart2LinkExtensionFixtures.ORIGINAL;
import static guru.interlis.db.Smart2LinkExtensionFixtures.specialized;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmartReferenceInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll static void stopPostgis() { POSTGIS.close(); }

    @Test void embeddedRoleGeoPackage23() throws Exception { exchange(23, null, false); }
    @Test void embeddedRoleGeoPackage24() throws Exception { exchange(24, null, false); }
    @Test void embeddedRolePostgis23() throws Exception { exchange(23, POSTGIS.container(), false); }
    @Test void embeddedRolePostgis24() throws Exception { exchange(24, POSTGIS.container(), false); }
    @Test void attributeGeoPackage23() throws Exception { exchange(23, null, true); }
    @Test void attributeGeoPackage24() throws Exception { exchange(24, null, true); }
    @Test void attributePostgis23() throws Exception { exchange(23, POSTGIS.container(), true); }
    @Test void attributePostgis24() throws Exception { exchange(24, POSTGIS.container(), true); }
    @Test void linkTableGeoPackage23() throws Exception { exchangeLinkTable(23, null); }
    @Test void linkTableGeoPackage24() throws Exception { exchangeLinkTable(24, null); }
    @Test void linkTablePostgis23() throws Exception { exchangeLinkTable(23, POSTGIS.container()); }
    @Test void linkTablePostgis24() throws Exception { exchangeLinkTable(24, POSTGIS.container()); }

    private void exchangeLinkTable(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new Smart2LinkExtensionFixtures(work, version);
        var catalog = fixture.catalog;
        var db = new TestDatabase(work, postgres, "polymorphic_link_table", "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        createComparedSchema(db, db.peer("polymorphic_link_table_reference"), reference, fixture.survey, fixture.surveySource, catalog);
        importDataset(reference, db, "original", catalog.input(catalog.original, "original"));
        importDataset(reference, db, "old-links", fixture.linkInput("old-links", ORIGINAL));
        String link = db.table(tableFor(db, fixture.associationPath()));
        String originalLink = "SELECT t_id || ':' || t_basket || ':' || visits || ':' || sites_entry FROM " + link
                + " WHERE t_basket=(SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='old-links-basket')";
        Path output = work.resolve("links.xtf");
        try (var sql = db.connect()) {
            String row = scalar(sql, originalLink);
            assertCliSuccess(db, "export", fixture.survey, "old-links", output);
            fixture.assertLinks(output, "old-links", true, ORIGINAL);
            importDataset(reference, db, "legacy", fixture.combined("legacy"));
            assertCliSuccess(db, "export", fixture.survey, "legacy", output, "--model", catalog.specialized);
            fixture.assertLinks(output, "legacy", true, ORIGINAL, specialized("legacy-catalog"));
            assertCliSuccess(db, "import", fixture.survey, "native", fixture.combined("native"), "--model", catalog.specialized);
            exportDataset(reference, db, "native", output);
            fixture.assertLinks(output, "native", false, ORIGINAL, specialized("native-catalog"));
            assertEquals(row, scalar(sql, originalLink));
        }
    }

    private void exchange(int version, PostgreSQLContainer postgres, boolean attribute) throws Exception {
        var fixture = new SmartReferenceExtensionFixtures(work, version, attribute);
        var catalog = fixture.catalog;
        String name = attribute ? "polymorphic_attribute" : "polymorphic_role";
        var db = new TestDatabase(work, postgres, name, "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        createComparedSchema(db, db.peer(name + "_reference"), reference, fixture.survey, fixture.surveySource, catalog);
        importDataset(reference, db, "original", catalog.input(catalog.original, "original"));
        importDataset(reference, db, "old-survey", fixture.existingSurvey("old-survey", "original-entry", "original-basket"));
        var original = catalog.originalRows(db);
        Path output = work.resolve("survey.xtf");
        assertCliSuccess(db, "export", fixture.survey, "old-survey", output);
        fixture.assertSurvey(output, "old-survey", "original-entry");
        importDataset(reference, db, "legacy", fixture.combined("legacy"));
        assertCliSuccess(db, "export", fixture.survey, "legacy", output, "--model", catalog.specialized);
        fixture.assertSurvey(output, "legacy", "legacy-catalog-specialized");
        assertCliSuccess(db, "import", fixture.survey, "native", fixture.combined("native"), "--model", catalog.specialized);
        exportDataset(reference, db, "native", output);
        fixture.assertSurvey(output, "native", "native-catalog-specialized");
        if (attribute) {
            importDataset(reference, db, "empty-reference", fixture.existingSurvey("empty-reference", null, null));
            assertCliSuccess(db, "export", fixture.survey, "empty-reference", output);
            fixture.assertSurvey(output, "empty-reference", null);
        }
        assertEquals(original, catalog.originalRows(db));
    }

    private void createComparedSchema(TestDatabase db, TestDatabase expected, ReferenceTool reference, String survey,
                                      Path surveySource, SmartModelExtensionFixtures catalog) throws Exception {
        var subtype = catalog.specializedSource();
        assertSchemaWithIdentityColumns(db, survey, surveySource,
                "--model", catalog.specialized, "--model-file", subtype.toString(),
                "--model-file", catalog.originalSource.toString(), "--inheritance", "smart2", "--create-fk", "--create-fk-idx");
        schema(reference, expected, work, survey + ";" + catalog.specialized, "smart2", "--createFk", "--createFkIdx");
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            DbSchemaCatalog.read(expectedSql, expected.postgis(), expected.schema()).canonicalReferenceAlternatives()
                    .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()).canonicalReferenceAlternatives());
            assertEquals(foreignKeyColumns(expected, expectedSql), foreignKeyColumns(db, sql));
            assertEquals(indexColumns(expected, expectedSql), indexColumns(db, sql));
        }
    }
}
