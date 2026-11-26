package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.assertSharedIds;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModelExtensionInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { exchangeStructures(23, null, "newClass"); }
    @Test void geoPackage24() throws Exception { exchangeStructures(24, null, "newClass"); }
    @Test void postgis23() throws Exception { exchangeStructures(23, POSTGIS.container(), "newClass"); }
    @Test void postgis24() throws Exception { exchangeStructures(24, POSTGIS.container(), "newClass"); }
    @Test void strictGeoPackage23() throws Exception { exchangeStructures(23, null, "strict"); }
    @Test void strictGeoPackage24() throws Exception { exchangeStructures(24, null, "strict"); }
    @Test void strictPostgis23() throws Exception { exchangeStructures(23, POSTGIS.container(), "strict"); }
    @Test void strictPostgis24() throws Exception { exchangeStructures(24, POSTGIS.container(), "strict"); }
    @Test void smart2StructuresGeoPackage23() throws Exception { exchangeStructures(23, null, "smart2"); }
    @Test void smart2StructuresGeoPackage24() throws Exception { exchangeStructures(24, null, "smart2"); }
    @Test void smart2StructuresPostgis23() throws Exception { exchangeStructures(23, POSTGIS.container(), "smart2"); }
    @Test void smart2StructuresPostgis24() throws Exception { exchangeStructures(24, POSTGIS.container(), "smart2"); }
    @Test void smart2GeoPackage23() throws Exception { exchangeSmartModels(23, null, "smart2"); }
    @Test void smart2GeoPackage24() throws Exception { exchangeSmartModels(24, null, "smart2"); }
    @Test void smart2Postgis23() throws Exception { exchangeSmartModels(23, POSTGIS.container(), "smart2"); }
    @Test void smart2Postgis24() throws Exception { exchangeSmartModels(24, POSTGIS.container(), "smart2"); }
    @Test void smart1GeoPackage23() throws Exception { exchangeSmartModels(23, null, "smart1"); }
    @Test void smart1GeoPackage24() throws Exception { exchangeSmartModels(24, null, "smart1"); }
    @Test void smart1Postgis23() throws Exception { exchangeSmartModels(23, POSTGIS.container(), "smart1"); }
    @Test void smart1Postgis24() throws Exception { exchangeSmartModels(24, POSTGIS.container(), "smart1"); }
    @Test void smart2SubtypeGeoPackage23() throws Exception { exchangeSmart2Subtype(23, null); }
    @Test void smart2SubtypeGeoPackage24() throws Exception { exchangeSmart2Subtype(24, null); }
    @Test void smart2SubtypePostgis23() throws Exception { exchangeSmart2Subtype(23, POSTGIS.container()); }
    @Test void smart2SubtypePostgis24() throws Exception { exchangeSmart2Subtype(24, POSTGIS.container()); }
    @Test void smart1RolesGeoPackage23() throws Exception { exchangeExternalRoles(23, null, "smart1"); }
    @Test void smart1RolesGeoPackage24() throws Exception { exchangeExternalRoles(24, null, "smart1"); }
    @Test void smart1RolesPostgis23() throws Exception { exchangeExternalRoles(23, POSTGIS.container(), "smart1"); }
    @Test void smart1RolesPostgis24() throws Exception { exchangeExternalRoles(24, POSTGIS.container(), "smart1"); }
    @Test void smart2RolesGeoPackage23() throws Exception { exchangeExternalRoles(23, null, "smart2"); }
    @Test void smart2RolesGeoPackage24() throws Exception { exchangeExternalRoles(24, null, "smart2"); }
    @Test void smart2RolesPostgis23() throws Exception { exchangeExternalRoles(23, POSTGIS.container(), "smart2"); }
    @Test void smart2RolesPostgis24() throws Exception { exchangeExternalRoles(24, POSTGIS.container(), "smart2"); }
    @Test void smart1SharedSubtypeGeoPackage23() throws Exception { exchangeSmart1SharedSubtype(23, null); }
    @Test void smart1SharedSubtypeGeoPackage24() throws Exception { exchangeSmart1SharedSubtype(24, null); }
    @Test void smart1SharedSubtypePostgis23() throws Exception { exchangeSmart1SharedSubtype(23, POSTGIS.container()); }
    @Test void smart1SharedSubtypePostgis24() throws Exception { exchangeSmart1SharedSubtype(24, POSTGIS.container()); }
    @Test void smart1FirstSubtypeGeoPackage23() throws Exception { exchangeSmart1FirstSubtype(23, null); }
    @Test void smart1FirstSubtypeGeoPackage24() throws Exception { exchangeSmart1FirstSubtype(24, null); }
    @Test void smart1FirstSubtypePostgis23() throws Exception { exchangeSmart1FirstSubtype(23, POSTGIS.container()); }
    @Test void smart1FirstSubtypePostgis24() throws Exception { exchangeSmart1FirstSubtype(24, POSTGIS.container()); }

    private void exchangeSmart1FirstSubtype(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        fixture.leaveOnlyOriginalEntry();
        var source = fixture.specializedSource();
        var db = new TestDatabase(work, postgres, "append_smart1_first_subtype", "v" + version + "_");
        var expected = db.peer("append_first_subtype_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.original, "smart1");
        importDataset(reference, db, "original", fixture.entryInput("original"));
        assertCliSuccess(db, "schemaimport", fixture.specialized, null, source,
                "--add-models", "--model-file", fixture.originalSource.toString());
        schema(reference, expected, work, fixture.original + ";" + fixture.specialized, "smart1");
        assertSubtypeCatalog(db, expected);

        var output = work.resolve("first-subtype.xtf");
        assertCliSuccess(db, "export", fixture.original, "original", output);
        fixture.assertEntryDelivery(output, "original");
        importDataset(reference, db, "legacy", fixture.specializedInput("legacy"));
        assertCliSuccess(db, "export", fixture.specialized, "legacy", output);
        fixture.assertSpecializedDelivery(output, "legacy");
        assertCliSuccess(db, "import", fixture.specialized, "native", fixture.specializedInput("native"));
        exportDataset(reference, db, "native", output);
        fixture.assertSpecializedDelivery(output, "native");
    }

    private void exchangeSmart1SharedSubtype(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var source = fixture.specializedSource();
        String further = "FurtherSmart" + version;
        var furtherSource = fixture.subtypeSource(further, "FurtherEntry", "Entry", "OtherExtra");
        var db = new TestDatabase(work, postgres, "append_smart1_subtype", "v" + version + "_");
        var expected = db.peer("append_smart1_subtype_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.original, "smart1");
        importDataset(reference, db, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        assertCliSuccess(db, "schemaimport", fixture.specialized, null, source,
                "--add-models", "--model-file", fixture.originalSource.toString());
        importDataset(reference, db, "legacy", fixture.specializedInput("legacy"));
        assertCliSuccess(db, "schemaimport", further, null, furtherSource,
                "--add-models", "--model-file", fixture.originalSource.toString());
        schema(reference, expected, work, fixture.original + ";" + fixture.specialized + ";" + further, "smart1");
        assertSubtypeCatalog(db, expected);
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("entry")
                    + " WHERE t_type IN ('entry','alternate') AND extra IS NULL AND otherextra IS NULL"));
        }
        var output = work.resolve("shared-subtype.xtf");
        assertCliSuccess(db, "export", fixture.specialized, "legacy", output);
        fixture.assertSpecializedDelivery(output, "legacy");
        assertCliSuccess(db, "import", fixture.specialized, "native", fixture.specializedInput("native"));
        exportDataset(reference, db, "native", output);
        fixture.assertSpecializedDelivery(output, "native");
        assertCliSuccess(db, "export", fixture.original, "original", output);
        fixture.assertDelivery(output, fixture.original, "original");
        assertEquals(original, fixture.originalRows(db));
    }

    private static void assertSubtypeCatalog(TestDatabase db, TestDatabase expected) throws Exception {
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            var actualCatalog = DbSchemaCatalog.read(sql, db.postgis(), db.schema()).relations();
            var referenceCatalog = DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).relations();
            for (String relation : java.util.List.of("classname", "attrname", "column_prop", "trafo", "inheritance")) {
                assertEquals(referenceCatalog.get(relation), actualCatalog.get(relation), relation);
            }
            var actual = actualCatalog.get("columns").stream().filter(row -> row.getFirst().equals("entry"))
                    .map(row -> row.subList(2, row.size())).collect(java.util.stream.Collectors.toSet());
            var canonical = referenceCatalog.get("columns").stream().filter(row -> row.getFirst().equals("entry"))
                    .map(row -> row.subList(2, row.size())).collect(java.util.stream.Collectors.toSet());
            assertEquals(canonical, actual);
        }
    }

    private void exchangeExternalRoles(int version, PostgreSQLContainer postgres, String profile) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        String survey = "AddedSurvey" + version;
        var source = fixture.externalRoleSource(survey, "BaseAsset");
        var db = new TestDatabase(work, postgres, "append_" + profile + "_roles", "v" + version + "_");
        var expected = db.peer("append_" + profile + "_roles_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.original, profile, "--createFk");
        schema(reference, expected, work, fixture.original, profile, "--createFk");
        importDataset(reference, db, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        schema(reference, expected, work, survey, profile, "--createFk");

        assertCliSuccess(db, "schemaimport", survey, null, source, "--add-models", "--model-file", fixture.originalSource.toString());
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).canonicalReferenceAlternatives()
                    .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()).canonicalReferenceAlternatives());
            assertEquals(foreignKeyColumns(expected, expectedSql), foreignKeyColumns(db, sql));
        }
        var output = work.resolve("survey.xtf");
        importDataset(reference, db, "legacy", fixture.externalRoleInput(survey, "legacy"));
        assertCliSuccess(db, "export", survey, "legacy", output);
        fixture.assertExternalRoleDelivery(output, survey, "legacy");
        assertCliSuccess(db, "import", survey, "native", fixture.externalRoleInput(survey, "native"));
        exportDataset(reference, db, "native", output);
        fixture.assertExternalRoleDelivery(output, survey, "native");
        assertEquals(original, fixture.originalRows(db));
    }

    private void exchangeSmart2Subtype(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        var newSource = fixture.specializedSource();
        var db = new TestDatabase(work, postgres, "append_subtype", "v" + version + "_");
        var expected = db.peer("append_subtype_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.original, "smart2");
        schema(reference, expected, work, fixture.original, "smart2");
        importDataset(reference, db, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        schema(reference, expected, work, fixture.specialized, "smart2");
        assertCliSuccess(db, "schemaimport", fixture.specialized, null, newSource,
                "--add-models", "--model-file", fixture.originalSource.toString());
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema())
                    .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
        }
        var output = work.resolve("subtype.xtf");
        importDataset(reference, db, "legacy", fixture.specializedInput("legacy"));
        assertCliSuccess(db, "export", fixture.specialized, "legacy", output);
        fixture.assertSpecializedDelivery(output, "legacy");
        assertCliSuccess(db, "import", fixture.specialized, "native", fixture.specializedInput("native"));
        exportDataset(reference, db, "native", output);
        fixture.assertSpecializedDelivery(output, "native");
        assertCliSuccess(db, "export", fixture.original, "original", output);
        fixture.assertDelivery(output, fixture.original, "original");
        assertEquals(original, fixture.originalRows(db));
    }

    private void exchangeSmartModels(int version, PostgreSQLContainer postgres, String profile) throws Exception {
        boolean smart1 = profile.equals("smart1");
        var fixture = new SmartModelExtensionFixtures(work, version, !smart1, smart1);
        var db = new TestDatabase(work, postgres, "append_" + profile, "v" + version + "_");
        var expected = db.peer("append_" + profile + "_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.original, profile);
        schema(reference, expected, work, fixture.original, profile);
        importDataset(reference, db, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        schema(reference, expected, work, fixture.added, profile);
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            if (smart1) assertCliSuccess(db, "schemaimport", fixture.added, null, fixture.addedSource, "--add-models");
            else assertCliSuccess(db, "schemaimport", fixture.added, null, fixture.addedSource,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            var after = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertEquals(before.relations().get("settings"), after.relations().get("settings"));
            DbSchemaCatalog.read(expectedSql, expected.postgis(), expected.schema()).assertSameAs(after);
            assertEquals(original, fixture.originalRows(db));
        }

        var output = work.resolve("original-native.xtf");
        assertCliSuccess(db, "export", fixture.original, "original", output);
        fixture.assertDelivery(output, fixture.original, "original");
        importDataset(reference, db, "legacy", fixture.input(fixture.added, "legacy"));
        output = work.resolve("legacy-native.xtf");
        assertCliSuccess(db, "export", fixture.added, "legacy", output);
        fixture.assertDelivery(output, fixture.added, "legacy");
        assertCliSuccess(db, "import", fixture.added, "native", fixture.input(fixture.added, "native"));
        output = work.resolve("native-reference.xtf");
        exportDataset(reference, db, "native", output);
        fixture.assertDelivery(output, fixture.added, "native");
        importDataset(reference, db, "tail", fixture.input(fixture.added, "tail"));
        output = work.resolve("tail-native.xtf");
        assertCliSuccess(db, "export", fixture.added, "tail", output);
        fixture.assertDelivery(output, fixture.added, "tail");
        assertEquals(original, fixture.originalRows(db));
        String originalEntry = tableFor(db, fixture.original + ".Data.Entry");
        String addedEntry = tableFor(db, fixture.added + ".Data.Entry");
        if (smart1) {
            assertSharedIds(db, 16, originalEntry, addedEntry);
            try (var sql = db.connect()) {
                assertEquals(Set.of(addedEntry, tableFor(db, fixture.added + ".Data.Alternate")),
                        catalogKeys(sql, "SELECT DISTINCT t_type FROM " + db.table(addedEntry)));
            }
        } else {
            assertSharedIds(db, 16, originalEntry, tableFor(db, fixture.original + ".Data.Alternate"),
                    addedEntry, tableFor(db, fixture.added + ".Data.Alternate"));
        }
    }

    private void exchangeStructures(int version, PostgreSQLContainer postgres, String profile) throws Exception {
        boolean strict = profile.equals("strict");
        var fixture = new ModelExtensionFixtures(work, version, profile);
        var db = new TestDatabase(work, postgres, "append_" + profile.toLowerCase(Locale.ROOT) + "_models", "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        if (strict) {
            reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                    "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", fixture.original);
        } else {
            schema(reference, db, work, fixture.original, profile);
        }
        importDataset(reference, db, "original", fixture.originalInput);

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var ids = fixture.originalIds(db, sql);
            fixture.extend(db);
            String newTable = fixture.assertExtension(db, sql, before, ids);
            if (profile.equals("smart2")) {
                var expected = db.peer("append_shared_structures_reference");
                schema(reference, expected, work, fixture.original + ";" + fixture.added, profile);
                try (var expectedSql = expected.connect()) {
                    DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).canonicalStructureOwners("detail")
                            .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()).canonicalStructureOwners("detail"));
                }
            }
            fixture.removeSources();

            Path output = work.resolve("original-native.xtf");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertOriginalDelivery(output);
            output = work.resolve("original-reference.xtf");
            exportDataset(reference, db, "original", output);
            fixture.assertOriginalDelivery(output);

            assertCliSuccess(db, "import", fixture.added, "native", fixture.addedDelivery("native"));
            output = work.resolve("added-reference.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertAddedDelivery(output, "native");

            importDataset(reference, db, "tail", fixture.addedDelivery("tail"));
            output = work.resolve("added-native.xtf");
            assertCliSuccess(db, "export", fixture.added, "tail", output);
            fixture.assertAddedDelivery(output, "tail");
            fixture.assertOwnedDetails(db, sql, fixture.original, "report", "fixture");
            fixture.assertOwnedDetails(db, sql, fixture.added, newTable, "native");
            fixture.assertOwnedDetails(db, sql, fixture.added, newTable, "tail");
            assertEquals(ids, fixture.originalIds(db, sql));
            assertSharedIds(db, 24, "report", newTable, "detail");
        }
    }
}
