package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.text;
import static guru.interlis.db.InteropCommands.assertSharedIds;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ModelExtensionInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void sharedStructuresGeoPackage23() throws Exception { exchangeStructures(23, null, "newClass"); }
    @Test void sharedStructuresGeoPackage24() throws Exception { exchangeStructures(24, null, "newClass"); }
    @Test void sharedStructuresPostgis23() throws Exception { exchangeStructures(23, POSTGIS.container(), "newClass"); }
    @Test void sharedStructuresPostgis24() throws Exception { exchangeStructures(24, POSTGIS.container(), "newClass"); }
    @Test void strictStructuresGeoPackage23() throws Exception { exchangeStructures(23, null, "strict"); }
    @Test void strictStructuresGeoPackage24() throws Exception { exchangeStructures(24, null, "strict"); }
    @Test void strictStructuresPostgis23() throws Exception { exchangeStructures(23, POSTGIS.container(), "strict"); }
    @Test void strictStructuresPostgis24() throws Exception { exchangeStructures(24, POSTGIS.container(), "strict"); }
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
    @Test void smart1MetadataSubtypeGeoPackage23() throws Exception { exchangeSmart1MetadataSubtype(23, null); }
    @Test void smart1MetadataSubtypeGeoPackage24() throws Exception { exchangeSmart1MetadataSubtype(24, null); }
    @Test void smart1MetadataSubtypePostgis23() throws Exception { exchangeSmart1MetadataSubtype(23, POSTGIS.container()); }
    @Test void smart1MetadataSubtypePostgis24() throws Exception { exchangeSmart1MetadataSubtype(24, POSTGIS.container()); }
    @Test void smart1SharedSubtypeGeoPackage23() throws Exception { exchangeSmart1SharedSubtype(23, null); }
    @Test void smart1SharedSubtypeGeoPackage24() throws Exception { exchangeSmart1SharedSubtype(24, null); }
    @Test void smart1SharedSubtypePostgis23() throws Exception { exchangeSmart1SharedSubtype(23, POSTGIS.container()); }
    @Test void smart1SharedSubtypePostgis24() throws Exception { exchangeSmart1SharedSubtype(24, POSTGIS.container()); }
    @Test void smart1FragmentGeoPackage23() throws Exception { exchangeSmart1Fragments(23, null); }
    @Test void smart1FragmentGeoPackage24() throws Exception { exchangeSmart1Fragments(24, null); }
    @Test void smart1FragmentPostgis23() throws Exception { exchangeSmart1Fragments(23, POSTGIS.container()); }
    @Test void smart1FragmentPostgis24() throws Exception { exchangeSmart1Fragments(24, POSTGIS.container()); }
    @Test void smart1SubtypeStructuresGeoPackage23() throws Exception { exchangeSmart1SubtypeStructures(23, null); }
    @Test void smart1SubtypeStructuresGeoPackage24() throws Exception { exchangeSmart1SubtypeStructures(24, null); }
    @Test void smart1SubtypeStructuresPostgis23() throws Exception { exchangeSmart1SubtypeStructures(23, POSTGIS.container()); }
    @Test void smart1SubtypeStructuresPostgis24() throws Exception { exchangeSmart1SubtypeStructures(24, POSTGIS.container()); }
    @Test void strictIndependentGeoPackage23() throws Exception { exchangeStrictIndependent(23, null); }
    @Test void strictIndependentGeoPackage24() throws Exception { exchangeStrictIndependent(24, null); }
    @Test void strictIndependentPostgis23() throws Exception { exchangeStrictIndependent(23, POSTGIS.container()); }
    @Test void strictIndependentPostgis24() throws Exception { exchangeStrictIndependent(24, POSTGIS.container()); }

    private void exchangeSmart1SubtypeStructures(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new Smart1StructureExtensionFixtures(work, version);
        var db = new TestDatabase(work, postgres, "smart1_structure", "v" + version + "_");
        var expected = db.peer("smart1_structure_reference");
        var reference = new ReferenceTool(work, db.postgis());
        assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource,
                "--model", fixture.subtype, "--model-file", fixture.subtypeSource.toString(),
                "--inheritance", "smart1", "--create-fk", "--create-fk-idx");
        importDataset(reference, db, "original", fixture.originalInput());
        schema(reference, expected, work, fixture.original + ";" + fixture.subtype, "smart1", "--createFk", "--createFkIdx");
        assertSubtypeCatalog(db, expected, "memopart");
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            assertEquals(foreignKeyColumns(expected, expectedSql), foreignKeyColumns(db, sql));
        }

        var output = work.resolve("subtype-memos.xtf");
        var entries = List.of("before", "after");
        importDataset(reference, db, "legacy", fixture.subtypeInput("legacy", entries));
        assertCliSuccess(db, "export", fixture.subtype, "legacy", output);
        fixture.assertSubtype(output, "legacy", entries);
        assertCliSuccess(db, "import", fixture.subtype, "native", fixture.subtypeInput("native", entries));
        exportDataset(reference, db, "native", output);
        fixture.assertSubtype(output, "native", entries);
        assertCliSuccess(db, "export", fixture.original, "original", output);
        fixture.assertOriginal(output);
    }

    private void exchangeSmart1Fragments(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        fixture.leaveOnlyOriginalEntry();
        String firstType = "SpecializedEntry";
        String next = "FurtherSmart" + version;
        String nextType = "FurtherEntry";
        var firstSource = fixture.fragmentSource(fixture.specialized, firstType);
        var nextSource = fixture.fragmentSource(next, nextType);
        var db = new TestDatabase(work, postgres, "smart1_fragments", "v" + version + "_");
        var expected = db.peer("smart1_fragments_reference");
        var reference = new ReferenceTool(work, db.postgis());
        String[] options = {"--createFk", "--createTypeConstraint"};
        assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource,
                "--model", fixture.specialized, "--model-file", firstSource.toString(),
                "--model", next, "--model-file", nextSource.toString(),
                "--inheritance", "smart1", "--create-fk", "--create-type-constraint");
        schema(reference, expected, work, fixture.original + ";" + fixture.specialized + ";" + next, "smart1", options);
        // ili2gpkg 5.5.2 omits T_Type when importing only the base model into this joined schema.
        assertCliSuccess(db, "import", fixture.original, "original", fixture.entryInput("original"));
        importDataset(reference, db, "legacy", fixture.fragmentInput(fixture.specialized, firstType, "legacy"));
        var output = work.resolve("fragments.xtf");
        try (var sql = db.connect()) {
            String first = tableFor(db, fixture.specialized + ".Data." + firstType);
            String second = tableFor(db, next + ".Data." + nextType);
            assertSubtypeCatalog(db, expected, first, second);
            try (var expectedSql = expected.connect()) {
                assertEquals(foreignKeyColumns(expected, expectedSql), foreignKeyColumns(db, sql));
            }
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertEntryDelivery(output, "original");
            assertCliSuccess(db, "export", fixture.specialized, "legacy", output);
            fixture.assertFragmentDelivery(output, fixture.specialized, firstType, "legacy");
            assertCliSuccess(db, "import", next, "native", fixture.fragmentInput(next, nextType, "native"));
            exportDataset(reference, db, "native", output);
            fixture.assertFragmentDelivery(output, next, nextType, "native");
            assertFragmentIdentity(db, sql, first, "legacy");
            assertFragmentIdentity(db, sql, second, "native");
        }
    }

    private static void assertFragmentIdentity(TestDatabase db, Connection sql, String fragment, String prefix)
            throws Exception {
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("entry") + " root JOIN "
                + db.table(fragment) + " fragment ON root.t_id=fragment.t_id AND root.t_basket=fragment.t_basket"
                + " WHERE root.t_type='" + fragment + "' AND root.t_ili_tid='" + prefix + "-fragment'"));
    }

    private void exchangeSmart1MetadataSubtype(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var source = fixture.specializedSourceWithoutFields();
        var db = new TestDatabase(work, postgres, "append_smart1_metadata", "v" + version + "_");
        var expected = db.peer("append_smart1_metadata_reference");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.original, "smart1");
        schema(reference, expected, work, fixture.original, "smart1");
        importDataset(reference, db, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        schema(reference, expected, work, fixture.specialized, "smart1");
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema()).relations();
            assertCliSuccess(db, "schemaimport", fixture.specialized, null, source,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            var after = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertEquals(before.get("columns"), after.relations().get("columns"));
            assertEquals(before.get("column_prop"), after.relations().get("column_prop"));
            DbSchemaCatalog.read(expectedSql, expected.postgis(), expected.schema()).assertSameAs(after);
        }

        var output = work.resolve("metadata-subtype.xtf");
        importDataset(reference, db, "legacy", fixture.specializedInputWithoutFields("legacy"));
        assertCliSuccess(db, "export", fixture.specialized, "legacy", output);
        fixture.assertSpecializedDeliveryWithoutFields(output, "legacy");
        assertCliSuccess(db, "import", fixture.specialized, "native", fixture.specializedInputWithoutFields("native"));
        exportDataset(reference, db, "native", output);
        fixture.assertSpecializedDeliveryWithoutFields(output, "native");
        importDataset(reference, db, "tail", fixture.specializedInputWithoutFields("tail"));
        assertCliSuccess(db, "export", fixture.specialized, "tail", output);
        fixture.assertSpecializedDeliveryWithoutFields(output, "tail");
        assertEquals(original, fixture.originalRows(db));
        assertSharedIds(db, 13, "entry");
        try (var sql = db.connect()) {
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM " + db.table("entry")
                    + " WHERE t_type='specializedentry' AND label='inherited' AND quantity=17"));
        }
    }

    private void exchangeStrictIndependent(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ProfileFixtures(work, version);
        String original = "StrictOriginal" + version;
        String added = "StrictAdded" + version;
        fixture.source(original, "CLASS Entry = Label : MANDATORY TEXT*20; END Entry;");
        var source = fixture.source(added, "CLASS Checkpoint = RankNo : MANDATORY 0..100; Label : TEXT*20; END Checkpoint;");
        var db = new TestDatabase(work, postgres, "append_strict", "v" + version + "_");
        var expected = db.peer("append_strict_reference");
        var reference = new ReferenceTool(work, db.postgis());
        for (var target : List.of(db, expected)) {
            reference.run(target, work.toString(), "--schemaimport", "--models", original, "--noSmartMapping",
                    "--createBasketCol", "--createTidCol", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056");
        }
        importDataset(reference, db, "original", fixture.data(original, "original",
                fixture.object(original, "Entry", "original-entry", "<Label>retained</Label>")));
        reference.run(expected, work.toString(), "--schemaimport", "--models", added);
        String addedTable;
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            String row = "SELECT t_id || ':' || t_basket || ':' || label FROM " + db.table("entry")
                    + " WHERE t_ili_tid='original-entry'";
            String originalRow = scalar(sql, row);
            assertCliSuccess(db, "schemaimport", added, null, source, "--add-models");
            addedTable = tableFor(db, added + ".Data.Checkpoint");
            var actual = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            DbSchemaCatalog.read(expectedSql, expected.postgis(), expected.schema()).assertSameAs(actual);
            assertEquals(db.postgis() ? "NO" : "1", actual.relations().get("columns").stream()
                    .filter(column -> column.getFirst().equals(addedTable) && column.get(2).equals("rankno"))
                    .findFirst().orElseThrow().get(db.postgis() ? 5 : 4));
            var output = work.resolve("checkpoint.xtf");
            for (String prefix : List.of("legacy", "native", "tail")) {
                Path input = fixture.data(added, prefix,
                        fixture.object(added, "Checkpoint", prefix + "-checkpoint", "<RankNo>17</RankNo>"));
                if (prefix.equals("native")) {
                    assertCliSuccess(db, "import", added, prefix, input);
                    exportDataset(reference, db, prefix, output);
                } else {
                    importDataset(reference, db, prefix, input);
                    assertCliSuccess(db, "export", added, prefix, output);
                }
                var checkpoint = fixture.rows(output, prefix, 1).getFirst();
                ModelFixtures.assertName(checkpoint, version, added + ".Data.Checkpoint");
                assertEquals(prefix + "-checkpoint", id(checkpoint, version, "tid"));
                assertEquals("17", text(checkpoint, "RankNo"));
                assertNull(text(checkpoint, "Label"));
            }
            assertEquals(originalRow, scalar(sql, row));
        }
        assertSharedIds(db, 12, "entry", addedTable);
    }

    private void exchangeSmart1SharedSubtype(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var source = fixture.specializedSource();
        String further = "FurtherSmart" + version;
        var furtherSource = fixture.subtypeSource(further, "FurtherEntry", "Entry", "OtherExtra");
        var db = new TestDatabase(work, postgres, "smart1_shared_subtypes", "v" + version + "_");
        var expected = db.peer("smart1_shared_subtypes_reference");
        var reference = new ReferenceTool(work, db.postgis());
        assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource,
                "--model", fixture.specialized, "--model-file", source.toString(),
                "--model", further, "--model-file", furtherSource.toString(),
                "--inheritance", "smart1", "--create-type-constraint");
        schema(reference, expected, work, fixture.original + ";" + fixture.specialized + ";" + further, "smart1",
                "--createTypeConstraint");
        importDataset(reference, db, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        importDataset(reference, db, "legacy", fixture.specializedInput("legacy"));
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

    private static void assertSubtypeCatalog(TestDatabase db, TestDatabase expected, String... fragmentTables) throws Exception {
        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            var actualCatalog = DbSchemaCatalog.read(sql, db.postgis(), db.schema()).relations();
            var referenceCatalog = DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).relations();
            for (String relation : List.of("classname", "attrname", "column_prop", "trafo", "inheritance")) {
                assertEquals(referenceCatalog.get(relation), actualCatalog.get(relation), relation);
            }
            var tables = new HashSet<>(List.of(fragmentTables));
            tables.add("entry");
            for (String table : tables) {
                var actual = actualCatalog.get("columns").stream().filter(row -> row.getFirst().equals(table))
                        .map(row -> row.subList(2, row.size())).collect(Collectors.toSet());
                var canonical = referenceCatalog.get("columns").stream().filter(row -> row.getFirst().equals(table))
                        .map(row -> row.subList(2, row.size())).collect(Collectors.toSet());
                assertEquals(canonical, actual, table);
            }
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
        String name = profile.toLowerCase(Locale.ROOT) + "_structure_models";
        var db = new TestDatabase(work, postgres, name, "v" + version + "_");
        var expected = db.peer(name + "_reference");
        var reference = new ReferenceTool(work, db.postgis());
        fixture.combinedSchema(db);
        String models = fixture.original + ";" + fixture.added;
        if (strict) {
            reference.run(expected, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                    "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", models);
        } else {
            schema(reference, expected, work, models, profile);
        }
        importDataset(reference, db, "original", fixture.originalInput);

        try (var sql = db.connect(); var expectedSql = expected.connect()) {
            var ids = fixture.originalIds(db, sql);
            String newTable = fixture.assertCombinedSchema(db, sql);
            DbSchemaCatalog.read(expectedSql, db.postgis(), expected.schema()).canonicalStructureOwners("detail")
                    .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()).canonicalStructureOwners("detail"));
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
