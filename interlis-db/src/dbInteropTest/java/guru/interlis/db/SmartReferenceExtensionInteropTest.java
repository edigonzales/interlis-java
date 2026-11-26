package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmartReferenceExtensionInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll static void stopPostgis() { POSTGIS.close(); }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartReferenceExtensionFixtures(work, version);
        var catalog = fixture.catalog;
        var subtype = catalog.specializedSource();
        var db = new TestDatabase(work, postgres, "widen_role", "v" + version + "_");
        var expected = db.peer("widen_role_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.survey, "smart2", "--createFk", "--createFkIdx");
        importDataset(reference, db, "original", catalog.input(catalog.original, "original"));
        importDataset(reference, db, "old-survey", fixture.existingSurvey("old-survey", "original-entry", "original-basket"));
        var original = catalog.originalRows(db);
        assertCliSuccess(db, "schemaimport", catalog.specialized, null, subtype,
                "--add-models", "--model-file", catalog.originalSource.toString());

        schema(reference, expected, work, fixture.survey + ";" + catalog.specialized, "smart2", "--createFk", "--createFkIdx");
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).canonicalReferenceAlternatives()
                    .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()).canonicalReferenceAlternatives());
            assertEquals(foreignKeyColumns(expected, expectedSql), foreignKeyColumns(db, sql));
        }
        Path output = work.resolve("survey.xtf");
        assertCliSuccess(db, "export", fixture.survey, "old-survey", output);
        fixture.assertSurvey(output, "old-survey", "original-entry");
        importDataset(reference, db, "legacy", fixture.combined("legacy"));
        assertCliSuccess(db, "export", fixture.survey, "legacy", output, "--model", catalog.specialized);
        fixture.assertSurvey(output, "legacy", "legacy-catalog-specialized");
        assertCliSuccess(db, "import", fixture.survey, "native", fixture.combined("native"), "--model", catalog.specialized);
        exportDataset(reference, db, "native", output);
        fixture.assertSurvey(output, "native", "native-catalog-specialized");
        assertEquals(original, catalog.originalRows(db));
    }
}
