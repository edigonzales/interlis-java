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

    @Test void polymorphicGeoPackage23() throws Exception { exchange(23, null, false, false); }
    @Test void polymorphicGeoPackage24() throws Exception { exchange(24, null, false, false); }
    @Test void polymorphicPostgis23() throws Exception { exchange(23, POSTGIS.container(), false, false); }
    @Test void polymorphicPostgis24() throws Exception { exchange(24, POSTGIS.container(), false, false); }
    @Test void singleTargetGeoPackage23() throws Exception { exchange(23, null, true, false); }
    @Test void singleTargetGeoPackage24() throws Exception { exchange(24, null, true, false); }
    @Test void singleTargetPostgis23() throws Exception { exchange(23, POSTGIS.container(), true, false); }
    @Test void singleTargetPostgis24() throws Exception { exchange(24, POSTGIS.container(), true, false); }
    @Test void attributeGeoPackage23() throws Exception { exchange(23, null, false, true); }
    @Test void attributeGeoPackage24() throws Exception { exchange(24, null, false, true); }
    @Test void attributePostgis23() throws Exception { exchange(23, POSTGIS.container(), false, true); }
    @Test void attributePostgis24() throws Exception { exchange(24, POSTGIS.container(), false, true); }

    private void exchange(int version, PostgreSQLContainer postgres, boolean singleTarget, boolean attribute) throws Exception {
        var fixture = new SmartReferenceExtensionFixtures(work, version, attribute);
        var catalog = fixture.catalog;
        if (singleTarget) catalog.leaveOnlyOriginalEntry();
        var subtype = catalog.specializedSource();
        String name = attribute ? "widen_attribute" : singleTarget ? "widen_single_role" : "widen_role";
        var db = new TestDatabase(work, postgres, name, "v" + version + "_");
        var expected = db.peer(name + "_reference");
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
                        foreignKeyColumns(db, sql).stream().filter(column -> column.startsWith("surveyrecord:site")).collect(Collectors.toSet()));
            }
        } else {
            schema(reference, expected, work, fixture.survey + ";" + catalog.specialized, "smart2", "--createFk", "--createFkIdx");
            try (var sql = db.connect(); var expectedSql = expected.connect()) {
                var actual = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
                var combined = DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema());
                if (attribute && version == 23) {
                    for (String relation : List.of("attrname", "classname", "column_prop", "inheritance", "trafo")) {
                        assertEquals(combined.relations().get(relation), actual.relations().get(relation), relation);
                    }
                    // An added reference follows the old structure back-reference column in physical order.
                    Set<List<String>> actualFields = structureFields(actual, fixture.ownerTable());
                    assertEquals(structureFields(combined, fixture.ownerTable()), actualFields);
                } else {
                    combined.canonicalReferenceAlternatives().assertSameAs(actual.canonicalReferenceAlternatives());
                }
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
        if (attribute) {
            importDataset(reference, db, "empty-reference", fixture.existingSurvey("empty-reference", null, null));
            assertCliSuccess(db, "export", fixture.survey, "empty-reference", output);
            fixture.assertSurvey(output, "empty-reference", null);
        }
        if (!singleTarget) assertEquals(original, catalog.originalRows(db));
    }

    private static Set<List<String>> structureFields(DbSchemaCatalog catalog, String table) {
        return catalog.relations().get("columns").stream().filter(row -> row.getFirst().equals(table))
                .map(row -> row.subList(2, row.size())).collect(Collectors.toSet());
    }
}
