package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Test void polymorphicGeoPackage23() throws Exception { exchange(23, null, false); }
    @Test void polymorphicGeoPackage24() throws Exception { exchange(24, null, false); }
    @Test void polymorphicPostgis23() throws Exception { exchange(23, POSTGIS.container(), false); }
    @Test void polymorphicPostgis24() throws Exception { exchange(24, POSTGIS.container(), false); }
    @Test void singleTargetGeoPackage23() throws Exception { exchange(23, null, true); }
    @Test void singleTargetGeoPackage24() throws Exception { exchange(24, null, true); }
    @Test void singleTargetPostgis23() throws Exception { exchange(23, POSTGIS.container(), true); }
    @Test void singleTargetPostgis24() throws Exception { exchange(24, POSTGIS.container(), true); }

    private void exchange(int version, PostgreSQLContainer postgres, boolean singleTarget) throws Exception {
        var fixture = new SmartReferenceExtensionFixtures(work, version);
        var catalog = fixture.catalog;
        if (singleTarget) catalog.leaveOnlyOriginalEntry();
        var subtype = catalog.specializedSource();
        var db = new TestDatabase(work, postgres, singleTarget ? "widen_single_role" : "widen_role", "v" + version + "_");
        var expected = db.peer(singleTarget ? "widen_single_role_reference" : "widen_role_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.survey, "smart2", "--createFk", "--createFkIdx");
        importDataset(reference, db, "original", singleTarget ? catalog.entryInput("original") : catalog.input(catalog.original, "original"));
        importDataset(reference, db, "old-survey", fixture.existingSurvey("old-survey", "original-entry", "original-basket"));
        var original = singleTarget ? List.<String>of() : catalog.originalRows(db);
        assertCliSuccess(db, "schemaimport", catalog.specialized, null, subtype,
                "--add-models", "--model-file", catalog.originalSource.toString());

        if (singleTarget) {
            schema(reference, expected, work, fixture.survey, "smart2", "--createFk", "--createFkIdx");
            schema(reference, expected, work, fixture.survey + ";" + catalog.specialized, "smart2", "--createFk", "--createFkIdx");
            try (var sql = db.connect(); var expectedSql = expected.connect()) {
                var actual = DbSchemaCatalog.read(sql, db.postgis(), db.schema()).relations();
                var widened = DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).relations();
                assertEquals(widened.get("attrname"), actual.get("attrname"));
                assertEquals(Set.of("surveyrecord:site:entry", "surveyrecord:site_specializedentry:specializedentry"),
                        foreignKeyColumns(db, sql).stream().filter(name -> name.startsWith("surveyrecord:site")).collect(Collectors.toSet()));
            }
        } else {
            schema(reference, expected, work, fixture.survey + ";" + catalog.specialized, "smart2", "--createFk", "--createFkIdx");
            try (var sql = db.connect(); var expectedSql = expected.connect()) {
                DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).canonicalReferenceAlternatives()
                        .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()).canonicalReferenceAlternatives());
                assertEquals(foreignKeyColumns(expected, expectedSql), foreignKeyColumns(db, sql));
            }
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
        if (!singleTarget) assertEquals(original, catalog.originalRows(db));
    }
}
