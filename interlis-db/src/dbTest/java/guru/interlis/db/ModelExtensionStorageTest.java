package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.tableFor;
import static guru.interlis.db.Smart2LinkExtensionFixtures.ORIGINAL;
import static guru.interlis.db.Smart2LinkExtensionFixtures.specialized;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelExtensionStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }


    @ParameterizedTest(name = "{0}, XTF {1}, {2}")
    @CsvSource({"GEOPACKAGE,23,newClass", "GEOPACKAGE,24,newClass", "POSTGIS,23,newClass", "POSTGIS,24,newClass",
            "GEOPACKAGE,23,strict", "GEOPACKAGE,24,strict", "POSTGIS,23,strict", "POSTGIS,24,strict",
            "GEOPACKAGE,23,smart2", "GEOPACKAGE,24,smart2", "POSTGIS,23,smart2", "POSTGIS,24,smart2"})
    void sharedStructureRejectsNewOwnerColumn(Backend backend, int version, String profile) throws Exception {
        boolean smart2 = profile.equals("smart2");
        var fixture = new ModelExtensionFixtures(work, version, profile);
        var db = POSTGIS.target(work.resolve("extension.gpkg"), backend);
        fixture.initialSchema(db, smart2);
        assertCliSuccess(db, "import", fixture.original, "original", fixture.originalInput);

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var ids = fixture.originalIds(db, sql);
            var badLibrary = Files.writeString(work.resolve("conflicting-library.ili"),
                    Files.readString(fixture.sharedSource).replace("TEXT*20", "TEXT*21"));
            assertCliFailure(db, "schemaimport", fixture.added, null, fixture.addedSource,
                    "Stored MODEL source differs for " + fixture.library, "--add-models", "--model-file", badLibrary.toString());
            assertCliFailure(db, "schemaimport", fixture.added, null, fixture.addedSource,
                    "conflict with the existing ili2db SETTINGS", "--add-models", "--model-file", fixture.sharedSource.toString(),
                    "--default-epsg", "21781");

            var result = DbTestSupport.cli(db, "schemaimport", fixture.added, null, fixture.addedSource,
                    cancelDuringDdl(new AtomicInteger(), 1), "--add-models", "--model-file", fixture.sharedSource.toString());
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("would change existing table detail for " + fixture.added + ".Data."),
                    result::diagnostics);
            assertTrue(result.diagnostics().contains("requires a structure owner column"), result::diagnostics);
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(ids, fixture.originalIds(db, sql));
            fixture.assertOwnedDetails(db, sql, fixture.original, "report", "fixture");
            Path output = work.resolve("original.xtf");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertOriginalDelivery(output);
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void strictSchemaAddsNewTableWithMandatoryColumns(Backend backend, int version) throws Exception {
        var fixture = new ProfileFixtures(work, version);
        var db = POSTGIS.target(work.resolve("strict-addition.gpkg"), backend);
        String original = "OriginalStrict";
        String addition = "AddedStrict";
        DbTestSupport.assertSchemaWithIdentityColumns(db, original,
                fixture.source(original, "CLASS Entry = Label : MANDATORY TEXT*20; END Entry;"), "--sql-not-null");
        assertCliSuccess(db, "import", original, "original", fixture.data(original, "original",
                fixture.object(original, "Entry", "original-entry", "<Label>original</Label>")));

        var source = fixture.source(addition, "CLASS Survey = Label : MANDATORY TEXT*20; Notes : TEXT*20; END Survey;");
        try (var sql = db.connect(); var statement = sql.createStatement()) {
            String originalSql = "SELECT t_id || ':' || t_basket || ':' || label FROM " + db.table("entry");
            String existing = scalar(sql, originalSql);
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var checkpoints = new AtomicInteger();
            var cancelled = schemaWithIdentityColumns(db, addition, source, cancelDuringDdl(checkpoints, 2), "--add-models");
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(2, checkpoints.get());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(existing, scalar(sql, originalSql));

            assertCliSuccess(db, "schemaimport", addition, null, source, "--add-models");
            assertCliSuccess(db, "import", addition, "added", fixture.data(addition, "added",
                    fixture.object(addition, "Survey", "added-survey", "<Label>added</Label>")));
            String table = db.table("survey");
            assertEquals("added", scalar(sql, "SELECT label FROM " + table + " WHERE notes IS NULL"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET label = NULL"));
            assertEquals(existing, scalar(sql, originalSql));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2ReusesDomainSourcesAndRollsBackDdl(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, true, false);
        var db = POSTGIS.target(work.resolve("smart2-extension.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart2");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var ids = fixture.originalRows(db);
            var changedSource = Files.writeString(work.resolve("changed.ili"),
                    Files.readString(fixture.originalSource).replace("TEXT*30", "TEXT*31"));
            assertCliFailure(db, "schemaimport", fixture.added, null, fixture.addedSource,
                    "Stored MODEL source differs for " + fixture.original,
                    "--add-models", "--model-file", changedSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            var checkpoints = new AtomicInteger();
            var cancelled = schemaWithIdentityColumns(db, fixture.added, fixture.addedSource,
                    cancelDuringDdl(checkpoints, 2), "--add-models", "--model-file", fixture.originalSource.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(2, checkpoints.get());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(ids, fixture.originalRows(db));

            assertCliSuccess(db, "schemaimport", fixture.added, null, fixture.addedSource,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            assertCliSuccess(db, "import", fixture.added, "added", fixture.input(fixture.added, "added"));
            var output = work.resolve("added.xtf");
            assertCliSuccess(db, "export", fixture.added, "added", output);
            fixture.assertDelivery(output, fixture.added, "added");
            assertEquals(ids, fixture.originalRows(db));
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2RejectsExpansionOfEmbeddedLinkWithAttributes(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        var db = POSTGIS.target(work.resolve("referenced-hierarchy.gpkg"), backend);
        String survey = "ExistingSurvey" + version;
        var linkSource = fixture.externalRoleSource(survey, "Alternate");
        Files.writeString(linkSource, Files.readString(linkSource)
                .replace("END ExactAssetLink;", "CommentText : TEXT*20; END ExactAssetLink;"));
        DbTestSupport.assertSchemaWithIdentityColumns(db, survey, linkSource,
                "--inheritance", "smart2", "--model-file", fixture.originalSource.toString());
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var rows = fixture.originalRows(db);
            assertCliFailure(db, "schemaimport", fixture.specialized, null, fixture.specializedSource(),
                    "would change existing table exactassetlink for " + survey + ".Data.ExactAssetLink.ConcreteSite",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(rows, fixture.originalRows(db));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2RejectsNewOwnerOfInheritedStructure(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        Files.writeString(fixture.originalSource, Files.readString(fixture.originalSource)
                .replace("TOPIC Data =", "STRUCTURE Detail = Caption : TEXT*20; END Detail;\n  TOPIC Data =")
                .replace("CLASS BaseAsset (ABSTRACT) = Label", "CLASS BaseAsset (ABSTRACT) = Memo : Detail; Label"));
        var db = POSTGIS.target(work.resolve("inherited-structure.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart2");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var rows = fixture.originalRows(db);
            assertCliFailure(db, "schemaimport", fixture.specialized, null, fixture.specializedSource(),
                    "would change existing table detail for " + fixture.original + ".Data.BaseAsset.Memo",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(rows, fixture.originalRows(db));
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2LinkTableRejectsNewTargetOutsideModelSelection(Backend backend, int version) throws Exception {
        var fixture = new Smart2LinkExtensionFixtures(work, version);
        var catalog = fixture.catalog;
        var db = POSTGIS.target(work.resolve("stored-link.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.survey, fixture.surveySource, "--inheritance", "smart2",
                "--model-file", catalog.originalSource.toString(), "--create-fk", "--create-fk-idx");
        assertCliSuccess(db, "import", catalog.original, "original", catalog.input(catalog.original, "original"));
        assertCliSuccess(db, "import", fixture.survey, "old", fixture.linkInput("old", ORIGINAL));
        var source = catalog.specializedSource();
        String linkTable = tableFor(db, fixture.associationPath());
        String oldLink = "SELECT t_id || ':' || t_basket || ':' || visits || ':' || sites_entry FROM " + db.table(linkTable);
        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var foreignKeys = DbTestSupport.foreignKeyColumns(db, sql);
            var indexes = DbTestSupport.indexColumns(db, sql);
            String original = scalar(sql, oldLink);
            assertCliFailure(db, "schemaimport", catalog.specialized, null, source,
                    "would change existing table " + linkTable + " for " + fixture.associationPath()
                            + ".Sites (requires a reference column for specializedentry)",
                    "--add-models", "--model-file", catalog.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(foreignKeys, DbTestSupport.foreignKeyColumns(db, sql));
            assertEquals(indexes, DbTestSupport.indexColumns(db, sql));
            assertEquals(original, scalar(sql, oldLink));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2LinkExportRejectsMultipleStoredTargets(Backend backend, int version) throws Exception {
        var fixture = new Smart2LinkExtensionFixtures(work, version);
        var catalog = fixture.catalog;
        var db = POSTGIS.target(work.resolve("polymorphic-link.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.survey, fixture.surveySource, "--inheritance", "smart2",
                "--model", catalog.specialized, "--model-file", catalog.originalSource.toString(),
                "--model-file", catalog.specializedSource().toString(), "--create-fk");
        assertCliSuccess(db, "import", catalog.original, "original", catalog.input(catalog.original, "original"));
        assertCliSuccess(db, "import", catalog.specialized, "catalog", catalog.specializedInput("catalog"));
        var target = specialized("catalog");
        assertCliSuccess(db, "import", fixture.survey, "new", fixture.linkInput("new", target));
        String link = db.table(tableFor(db, fixture.associationPath()));
        try (var sql = db.connect(); var edit = sql.createStatement()) {
            String targetId = scalar(sql, "SELECT t_id FROM " + db.table("specializedentry") + " WHERE t_ili_tid='catalog-specialized'");
            assertEquals(targetId, scalar(sql, "SELECT sites_specializedentry FROM " + link
                    + " WHERE sites_entry IS NULL AND sites_alternate IS NULL"));
            Path output = work.resolve("links.xtf");
            assertCliSuccess(db, "export", fixture.survey, "new", output);
            fixture.assertLinks(output, "new", true, target);
            byte[] published = Files.readAllBytes(output);

            assertEquals(1, edit.executeUpdate("UPDATE " + link + " SET sites_entry=(SELECT t_id FROM "
                    + db.table("entry") + " WHERE t_ili_tid='original-entry') WHERE sites_specializedentry IS NOT NULL"));
            assertCliFailure(db, "export", fixture.survey, "new", output, "Multiple stored values for " + fixture.associationPath() + ".Sites");
            assertArrayEquals(published, Files.readAllBytes(output));
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2RetargetsRoleWithLegacyUnsuffixedColumn(Backend backend, int version) throws Exception {
        var fixture = new SmartReferenceExtensionFixtures(work, version);
        var catalog = fixture.catalog;
        catalog.leaveOnlyOriginalEntry();
        var db = POSTGIS.target(work.resolve("legacy-role.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.survey, fixture.surveySource, "--inheritance", "smart2",
                "--model", catalog.specialized, "--model-file", catalog.originalSource.toString(),
                "--model-file", catalog.specializedSource().toString(), "--create-fk", "--create-fk-idx");
        try (var sql = db.connect(); var edit = sql.createStatement()) {
            String table = db.table("surveyrecord");
            edit.executeUpdate("ALTER TABLE " + table + " RENAME COLUMN site_entry TO site");
            assertEquals(1, edit.executeUpdate("UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='site'"
                    + " WHERE colowner='surveyrecord' AND sqlname='site_entry'"));
            assertEquals(1, edit.executeUpdate("UPDATE " + db.table("t_ili2db_column_prop") + " SET columnname='site'"
                    + " WHERE tablename='surveyrecord' AND columnname='site_entry'"));
            assertEquals(Set.of("surveyrecord:site", "surveyrecord:site_specializedentry"),
                    DbTestSupport.indexColumns(db, sql).stream().filter(index -> index.startsWith("surveyrecord:site"))
                            .collect(Collectors.toSet()));

            assertCliSuccess(db, "import", catalog.original, "original", catalog.entryInput("original"));
            assertCliSuccess(db, "import", fixture.survey, "survey", fixture.existingSurvey("survey", "original-entry", "original-basket"));
            String row = " FROM " + table + " WHERE t_ili_tid='survey-record'";
            String before = scalar(sql, "SELECT t_id" + row);
            assertEquals(scalar(sql, "SELECT t_id FROM " + db.table("entry") + " WHERE t_ili_tid='original-entry'"),
                    scalar(sql, "SELECT site" + row));

            assertCliSuccess(db, "import", catalog.specialized, "new-target", catalog.specializedInput("new-target"));
            assertCliSuccess(db, "update", fixture.survey, "survey",
                    fixture.existingSurvey("survey", "new-target-specialized", "new-target-basket"));
            assertEquals(before, scalar(sql, "SELECT t_id" + row));
            assertEquals("1", scalar(sql, "SELECT COUNT(*)" + row + " AND site IS NULL"));
            var output = work.resolve("retargeted.xtf");
            assertCliSuccess(db, "export", fixture.survey, "survey", output);
            fixture.assertSurvey(output, "survey", "new-target-specialized");
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2RejectsTwoPopulatedAlternativesOfReferenceAttribute(Backend backend, int version) throws Exception {
        var fixture = new SmartReferenceExtensionFixtures(work, version, true);
        var catalog = fixture.catalog;
        var db = POSTGIS.target(work.resolve("widen-attribute.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.survey, fixture.surveySource, "--inheritance", "smart2",
                "--model", catalog.specialized, "--model-file", catalog.originalSource.toString(),
                "--model-file", catalog.specializedSource().toString(), "--create-fk");
        assertCliSuccess(db, "import", catalog.original, "original", catalog.input(catalog.original, "original"));
        assertCliSuccess(db, "import", fixture.survey, "native", fixture.combined("native"), "--model", catalog.specialized);
        var output = work.resolve("attribute.xtf");
        assertCliSuccess(db, "export", fixture.survey, "native", output, "--model", catalog.specialized);
        var before = Files.readString(output);
        try (var sql = db.connect(); var edit = sql.createStatement()) {
            edit.executeUpdate("UPDATE " + db.table(fixture.ownerTable()) + " SET site_entry=(SELECT t_id FROM "
                    + db.table("entry") + " WHERE t_ili_tid='original-entry') WHERE site_specializedentry IS NOT NULL");
            assertCliFailure(db, "export", fixture.survey, "native", output,
                    "Multiple stored values for " + fixture.referencePath(), "--model", catalog.specialized);
        }
        assertEquals(before, Files.readString(output));
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart2RejectsWideningAndReadsStoredRole(Backend backend, int version) throws Exception {
        var fixture = new SmartReferenceExtensionFixtures(work, version);
        var catalog = fixture.catalog;
        var db = POSTGIS.target(work.resolve("stored-role.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.survey, fixture.surveySource, "--inheritance", "smart2",
                "--model-file", catalog.originalSource.toString(), "--create-fk", "--create-fk-idx");
        assertCliSuccess(db, "import", catalog.original, "original", catalog.input(catalog.original, "original"));
        assertCliSuccess(db, "import", fixture.survey, "survey", fixture.existingSurvey("survey", "original-entry", "original-basket"));
        var source = catalog.specializedSource();
        try (var sql = db.connect(); var edit = sql.createStatement()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertCliFailure(db, "schemaimport", catalog.specialized, null, source,
                    "would change existing table surveyrecord for " + fixture.referencePath(),
                    "--add-models", "--model-file", catalog.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

            var output = work.resolve("old-survey.xtf");
            assertCliSuccess(db, "export", fixture.survey, "survey", output);
            fixture.assertSurvey(output, "survey", "original-entry");
            byte[] published = Files.readAllBytes(output);
            String properties = db.table("t_ili2db_column_prop");
            edit.executeUpdate("UPDATE " + properties + " SET setting='wrong'"
                    + " WHERE tablename='surveyrecord' AND columnname='site_entry'");
            assertCliFailure(db, "export", fixture.survey, "survey", output,
                    "Inconsistent COLUMN_PROP.foreignKey for " + fixture.referencePath());
            assertArrayEquals(published, Files.readAllBytes(output));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1AddsIndependentHierarchyAndPreservesStoredTable(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var db = POSTGIS.target(work.resolve("smart1-extension.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart1");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var rows = fixture.originalRows(db);
            var checkpoints = new AtomicInteger();
            var cancelled = schemaWithIdentityColumns(db, fixture.added, fixture.addedSource,
                    cancelDuringDdl(checkpoints, 2), "--add-models");
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(2, checkpoints.get());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

            assertCliSuccess(db, "schemaimport", fixture.added, null, fixture.addedSource, "--add-models");
            assertCliSuccess(db, "import", fixture.added, "added", fixture.input(fixture.added, "added"));
            var output = work.resolve("independent.xtf");
            assertCliSuccess(db, "export", fixture.added, "added", output);
            fixture.assertDelivery(output, fixture.added, "added");
            assertEquals(rows, fixture.originalRows(db));
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1SharedTableRejectsNewSubtypeField(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var db = POSTGIS.target(work.resolve("new-shared-column.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart1");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        var source = fixture.specializedSource();
        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var rows = fixture.originalRows(db);
            assertCliFailure(db, "schemaimport", fixture.specialized, null, source,
                    "would change existing table entry for " + fixture.specialized + ".Data.SpecializedEntry.Extra",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(rows, fixture.originalRows(db));
            var output = work.resolve("shared-subtype.xtf");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertDelivery(output, fixture.original, "original");
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1SharedTableAppliesScalarChecksToSubtypeFields(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var db = POSTGIS.target(work.resolve("shared-scalar-checks.gpkg"), backend);
        var source = fixture.specializedSource();
        Files.writeString(source, Files.readString(source).replace("Extra : TEXT*20;", "Extra : TEXT*20; Score : 0..9;"));
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--model", fixture.specialized,
                "--model-file", source.toString(), "--inheritance", "smart1", "--create-num-checks", "--create-text-checks");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        assertCliSuccess(db, "import", fixture.specialized, "added", fixture.specializedInput("added"));
        try (var sql = db.connect(); var statement = sql.createStatement()) {
            String table = db.table("entry");
            String added = " WHERE t_ili_tid='added-specialized'";
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + table + " WHERE extra IS NULL AND score IS NULL"));
            statement.executeUpdate("UPDATE " + table + " SET score = 9" + added);
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET score = 10" + added));
            if (db.postgis()) {
                assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET extra = '  '" + added));
            }
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1RejectsNewFragmentWhenRootLacksDiscriminator(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        fixture.leaveOnlyOriginalEntry();
        var db = POSTGIS.target(work.resolve("missing-discriminator.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart1");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.entryInput("original"));
        var source = fixture.fragmentSource(fixture.specialized, "SpecializedEntry");
        String originalRow = " FROM " + db.table("entry") + " WHERE t_ili_tid='original-entry'";
        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            String rowSql = "SELECT t_id || ':' || t_basket || ':' || label || ':' || quantity" + originalRow;
            String row = scalar(sql, rowSql);
            assertCliFailure(db, "schemaimport", fixture.specialized, null, source,
                    "would change existing table entry for " + fixture.specialized
                            + ".Data.SpecializedEntry (requires T_Type as a non-null discriminator)",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(row, scalar(sql, rowSql));
            var output = work.resolve("original-entry.xtf");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertEntryDelivery(output, "original");
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void newClassRejectsHierarchyExtensionWhenRootLacksDiscriminator(Backend backend, int version) throws Exception {
        var fixture = new ProfileFixtures(work, version);
        var original = fixture.source("Original", "CLASS Entry = Label : TEXT*20; END Entry;");
        var added = Files.writeString(work.resolve("Added.ili"), """
                INTERLIS 2.%d;
                MODEL Added (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS Original;
                  TOPIC Data EXTENDS Original.Data =
                    CLASS AdditionalEntry (ABSTRACT) EXTENDS Original.Data.Entry = END AdditionalEntry;
                    CLASS ConcreteEntry EXTENDS AdditionalEntry = END ConcreteEntry;
                  END Data;
                END Added.
                """.formatted(version - 20));
        var db = POSTGIS.target(work.resolve("abstract-subtype.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, "Original", original);
        var input = fixture.data("Original", "original",
                fixture.object("Original", "Entry", "original-entry", "<Label>retained</Label>"));
        assertCliSuccess(db, "import", "Original", "original", input);
        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            String entry = "SELECT t_id || ':' || label FROM " + db.table("entry");
            var row = scalar(sql, entry);
            var result = schemaWithIdentityColumns(db, "Added", added, CancellationToken.none(),
                    "--add-models", "--model-file", original.toString());
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("would change existing table entry for Added.Data."),
                    result::diagnostics);
            assertTrue(result.diagnostics().contains("requires T_Type as a non-null discriminator"), result::diagnostics);
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(row, scalar(sql, entry));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1FragmentSharesIdentityWithRoot(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        fixture.leaveOnlyOriginalEntry();
        var db = POSTGIS.target(work.resolve("fragment.gpkg"), backend);
        var source = fixture.fragmentSource(fixture.specialized, "SpecializedEntry");
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart1",
                "--model", fixture.specialized, "--model-file", source.toString());
        assertCliSuccess(db, "import", fixture.original, "original", fixture.entryInput("original"));
        assertCliSuccess(db, "import", fixture.specialized, "new", fixture.fragmentInput(fixture.specialized, "SpecializedEntry", "new"));
        String entry = db.table("entry");
        try (var sql = db.connect()) {
            assertEquals("entry", scalar(sql, "SELECT t_type FROM " + entry + " WHERE t_ili_tid='original-entry'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + entry + " e JOIN " + db.table("specializedentry")
                    + " s ON s.t_id=e.t_id AND s.t_basket=e.t_basket WHERE e.t_type='specializedentry' AND s.score=5"));
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1SubtypeStructureUpdatePreservesExistingOwners(Backend backend, int version) throws Exception {
        var fixture = new Smart1StructureExtensionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("subtype-memos.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart1",
                "--model", fixture.subtype, "--model-file", fixture.subtypeSource.toString());
        assertCliSuccess(db, "import", fixture.original, "original", fixture.originalInput());
        String notes = db.table("memopart");
        String originalSql = "SELECT t_id || ':' || t_basket || ':' || entry_ownmemo || ':' || caption FROM " + notes
                + " WHERE entry_ownmemo IS NOT NULL";
        try (var sql = db.connect()) {
            String original = scalar(sql, originalSql);
            assertCliSuccess(db, "import", fixture.subtype, "new", fixture.subtypeInput("new", List.of("first", "second")));
            String parent = "SELECT t_id FROM " + db.table("entry") + " WHERE t_ili_tid='new-entry'";
            String parentId = scalar(sql, parent);
            String listRows = " FROM " + notes + " WHERE entry_entries=" + parentId;
            assertEquals("2", scalar(sql, "SELECT count(*)" + listRows));

            assertCliSuccess(db, "update", fixture.subtype, "new", fixture.subtypeInput("new", List.of("second")));
            assertEquals(parentId, scalar(sql, parent));
            assertEquals("1", scalar(sql, "SELECT count(*)" + listRows));
            assertEquals("second", scalar(sql, "SELECT caption" + listRows + " AND t_seq=0 AND entry_ownmemo IS NULL"));
            assertEquals(original, scalar(sql, originalSql));
            Path output = work.resolve("updated-subtype-memos.xtf");
            assertCliSuccess(db, "export", fixture.subtype, "new", output);
            fixture.assertSubtype(output, "new", List.of("second"));
        }
    }


    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1RejectsChangingExistingTypeCheck(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var db = POSTGIS.target(work.resolve("checked-subtype.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource,
                "--inheritance", "smart1", "--create-type-constraint");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        var source = fixture.specializedSource();
        Files.writeString(source, Files.readString(source).replace("Extra : TEXT*20;", ""));
        String table = db.table("entry");
        try (var sql = db.connect(); var statement = sql.createStatement()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var rows = fixture.originalRows(db);
            assertCliFailure(db, "schemaimport", fixture.specialized, null, source,
                    "would change existing table entry for " + fixture.specialized + ".Data.SpecializedEntry"
                            + " (requires a new T_Type check value)",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(rows, fixture.originalRows(db));
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET t_type = 'specializedentry'"));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}, {2}")
    @CsvSource({"GEOPACKAGE,23,smart1", "GEOPACKAGE,24,smart1", "POSTGIS,23,smart1", "POSTGIS,24,smart1",
            "GEOPACKAGE,23,smart2", "GEOPACKAGE,24,smart2", "POSTGIS,23,smart2", "POSTGIS,24,smart2"})
    void smartExtensionsRequireStoredRoleTargetMappings(Backend backend, int version, String profile) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        var db = POSTGIS.target(work.resolve("external-role-targets.gpkg"), backend);
        String survey = "NewSurvey" + version;
        var newSource = fixture.externalRoleSource(survey, "BaseAsset");
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", profile);
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        String type = fixture.original + ".Data.Alternate";
        String target = tableFor(db, type);
        String classNames = db.table("t_ili2db_classname");
        try (var sql = db.connect(); var edit = sql.createStatement()) {
            var rows = fixture.originalRows(db);
            edit.executeUpdate("DELETE FROM " + classNames + " WHERE iliname='" + type + "'");
            var missing = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertCliFailure(db, "schemaimport", survey, null, newSource, "Missing stored CLASSNAME mapping for " + type,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            missing.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

            edit.executeUpdate("INSERT INTO " + classNames + " (iliname, sqlname) VALUES ('" + type + "', 'absent_target')");
            var invalid = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertCliFailure(db, "schemaimport", survey, null, newSource, "Missing or ambiguous physical table absent_target",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            invalid.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

            String otherTarget = tableFor(db, fixture.original + ".Data.Entry");
            edit.executeUpdate("UPDATE " + classNames + " SET sqlname='" + otherTarget + "' WHERE iliname='" + type + "'");
            var conflicting = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            assertCliFailure(db, "schemaimport", survey, null, newSource, "Conflicting stored CLASSNAME SQL name " + otherTarget,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            conflicting.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

            edit.executeUpdate("UPDATE " + classNames + " SET sqlname='" + target + "' WHERE iliname='" + type + "'");
            assertEquals(rows, fixture.originalRows(db));
            assertCliSuccess(db, "schemaimport", survey, null, newSource, "--add-models", "--model-file", fixture.originalSource.toString());
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}, {2}")
    @CsvSource({"GEOPACKAGE,23,smart1", "GEOPACKAGE,24,smart1", "POSTGIS,23,smart1", "POSTGIS,24,smart1",
            "GEOPACKAGE,23,smart2", "GEOPACKAGE,24,smart2", "POSTGIS,23,smart2", "POSTGIS,24,smart2"})
    void smartParentExportIgnoresCopiesOfInheritedFieldsInAnotherModel(Backend backend, int version, String profile)
            throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        Files.writeString(fixture.originalSource, Files.readString(fixture.originalSource).replace("  END Data;", """
                    CLASS Service = END Service;
                    ASSOCIATION Servicing =
                      Assets -- {0..*} BaseAsset;
                      UsedService -- {0..1} Service;
                    END Servicing;
                  END Data;"""));
        var db = POSTGIS.target(work.resolve("inherited-fields.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", profile);
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        var original = fixture.originalRows(db);
        assertCliSuccess(db, "schemaimport", fixture.specialized, null, fixture.specializedSource("BaseAsset"),
                "--add-models", "--model-file", fixture.originalSource.toString());
        try (var sql = db.connect()) {
            assertEquals("service", scalar(sql, "SELECT target FROM " + db.table("t_ili2db_attrname") + " WHERE iliname='"
                    + fixture.original + ".Data.Servicing.UsedService' AND colowner='specializedentry'"));
        }
        var output = work.resolve("parent.xtf");
        assertCliSuccess(db, "export", fixture.original, "original", output);
        fixture.assertDelivery(output, fixture.original, "original");
        assertEquals(original, fixture.originalRows(db));
    }

    private static CancellationToken cancelDuringDdl(AtomicInteger checkpoints, int checkpoint) {
        var stack = StackWalker.getInstance();
        return () -> stack.walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().equals(SchemaSql.class.getName()) && frame.getMethodName().equals("apply")))
                && checkpoints.incrementAndGet() == checkpoint;
    }
}
