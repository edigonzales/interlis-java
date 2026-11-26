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
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void addModelsIsTransactionalAndCanBeRetried(Backend backend, int version, String profile) throws Exception {
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
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(ids, fixture.originalIds(db, sql));

            var checkpoints = new AtomicInteger();
            int checkpoint = smart2 ? 3 : 2;
            var cancelled = schemaWithIdentityColumns(db, fixture.added, fixture.addedSource, cancelDuringDdl(checkpoints, checkpoint),
                    "--add-models", "--model-file", fixture.sharedSource.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(checkpoint, checkpoints.get());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(ids, fixture.originalIds(db, sql));

            fixture.extend(db);
            String table = fixture.assertExtension(db, sql, before, ids);
            fixture.removeSources();
            assertCliSuccess(db, "import", fixture.added, "added", fixture.addedDelivery("native"));
            Path output = work.resolve("original.xtf");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertOriginalDelivery(output);
            output = work.resolve("added.xtf");
            assertCliSuccess(db, "export", fixture.added, "added", output);
            fixture.assertAddedDelivery(output, "native");
            fixture.assertOwnedDetails(db, sql, fixture.original, "report", "fixture");
            fixture.assertOwnedDetails(db, sql, fixture.added, table, "native");
            assertEquals(ids, fixture.originalIds(db, sql));
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
    void smart2RejectsSubtypeThatWouldChangeStoredReference(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        var db = POSTGIS.target(work.resolve("referenced-hierarchy.gpkg"), backend);
        String survey = "ExistingSurvey" + version;
        DbTestSupport.assertSchemaWithIdentityColumns(db, survey, fixture.externalRoleSource(survey, "BaseAsset"),
                "--inheritance", "smart2", "--model-file", fixture.originalSource.toString());
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var rows = fixture.originalRows(db);
            assertCliFailure(db, "schemaimport", fixture.specialized, null, fixture.specializedSource(),
                    "would change stored reference " + survey + ".Data.AssetLink.Site",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(rows, fixture.originalRows(db));
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
    void smart1SharedSubtypeKeepsOriginalRowsAndRollsBackAddedColumn(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var db = POSTGIS.target(work.resolve("shared-subtype.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart1");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        var source = fixture.specializedSource();
        var rows = fixture.originalRows(db);
        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            var checkpoints = new AtomicInteger();
            var cancelled = schemaWithIdentityColumns(db, fixture.specialized, source,
                    cancelDuringDdl(checkpoints, 2), "--add-models", "--model-file", fixture.originalSource.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

            assertCliSuccess(db, "schemaimport", fixture.specialized, null, source,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("entry") + " WHERE extra IS NULL"));
            assertCliSuccess(db, "import", fixture.specialized, "added", fixture.specializedInput("added"));
            var output = work.resolve("shared-subtype.xtf");
            assertCliSuccess(db, "export", fixture.specialized, "added", output);
            fixture.assertSpecializedDelivery(output, "added");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertDelivery(output, fixture.original, "original");
            assertEquals(rows, fixture.originalRows(db));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1AddsScalarChecksToNewColumnsInTheSharedTable(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var db = POSTGIS.target(work.resolve("shared-scalar-checks.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource,
                "--inheritance", "smart1", "--create-num-checks", "--create-text-checks");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        var source = fixture.specializedSource();
        Files.writeString(source, Files.readString(source).replace("Extra : TEXT*20;", "Extra : TEXT*20; Score : 0..9;"));
        assertCliSuccess(db, "schemaimport", fixture.specialized, null, source,
                "--add-models", "--model-file", fixture.originalSource.toString());
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
    void smart1BackfillsFirstDiscriminatorAtomically(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        fixture.leaveOnlyOriginalEntry();
        var db = POSTGIS.target(work.resolve("first-discriminator.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource, "--inheritance", "smart1");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.entryInput("original"));
        var source = fixture.specializedSource();
        String originalRow = " FROM " + db.table("entry") + " WHERE t_ili_tid = 'original-entry'";
        try (var sql = db.connect(); var statement = sql.createStatement()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            String id = scalar(sql, "SELECT t_id || ':' || t_basket" + originalRow);
            var checkpoints = new AtomicInteger();
            var cancelled = schemaWithIdentityColumns(db, fixture.specialized, source,
                    cancelDuringDdl(checkpoints, 3), "--add-models", "--model-file", fixture.originalSource.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

            assertCliSuccess(db, "schemaimport", fixture.specialized, null, source,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            assertEquals(id, scalar(sql, "SELECT t_id || ':' || t_basket" + originalRow));
            assertEquals("entry", scalar(sql, "SELECT t_type" + originalRow));
            String table = db.table("entry");
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO " + table
                    + " (t_id, t_basket) SELECT t_id + 100, t_basket" + originalRow));
            var output = work.resolve("first-discriminator.xtf");
            assertCliSuccess(db, "export", fixture.original, "original", output);
            fixture.assertEntryDelivery(output, "original");
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1ExtendsOnlyTheNamedTypeCheck(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, true);
        var db = POSTGIS.target(work.resolve("checked-subtype.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource,
                "--inheritance", "smart1", "--create-type-constraint");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.input(fixture.original, "original"));
        var source = fixture.specializedSource();
        String table = db.table("entry");
        try (var sql = db.connect(); var statement = sql.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT keep_quantity CHECK (quantity IS NULL OR quantity >= 0)");
            var checkpoints = new AtomicInteger();
            var cancelled = schemaWithIdentityColumns(db, fixture.specialized, source,
                    cancelDuringDdl(checkpoints, 2), "--add-models", "--model-file", fixture.originalSource.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET t_type = 'specializedentry'"));

            assertCliSuccess(db, "schemaimport", fixture.specialized, null, source,
                    "--add-models", "--model-file", fixture.originalSource.toString());
            assertCliSuccess(db, "import", fixture.specialized, "added", fixture.specializedInput("added"));
            assertEquals("specializedentry", scalar(sql, "SELECT t_type FROM " + table + " WHERE t_ili_tid='added-specialized'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET t_type = 'unmapped'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET quantity = -1"));

            if (!db.postgis()) {
                statement.execute("ALTER TABLE " + table + " DROP CONSTRAINT entry_t_type_check");
                statement.execute("ALTER TABLE " + table + " ADD CHECK (t_type IN ('alternate','entry','specializedentry'))");
                var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
                var further = fixture.subtypeSource("FurtherSmart" + version, "FurtherEntry", "Entry", "OtherExtra");
                assertCliFailure(db, "schemaimport", "FurtherSmart" + version, null, further, "Expected one named T_Type check for entry",
                        "--add-models", "--model-file", fixture.originalSource.toString());
                before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            }
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void smart1ExtendsANamedTypeCheckWithOneInitialType(Backend backend, int version) throws Exception {
        var fixture = new SmartModelExtensionFixtures(work, version, false, false);
        fixture.leaveOnlyOriginalEntry();
        var db = POSTGIS.target(work.resolve("checked-single-type.gpkg"), backend);
        DbTestSupport.assertSchemaWithIdentityColumns(db, fixture.original, fixture.originalSource,
                "--inheritance", "smart1", "--create-type-discriminator", "--create-type-constraint");
        assertCliSuccess(db, "import", fixture.original, "original", fixture.entryInput("original"));
        assertCliSuccess(db, "schemaimport", fixture.specialized, null, fixture.specializedSource(),
                "--add-models", "--model-file", fixture.originalSource.toString());
        assertCliSuccess(db, "import", fixture.specialized, "added", fixture.specializedInput("added"));
        try (var sql = db.connect(); var statement = sql.createStatement()) {
            String table = db.table("entry");
            assertEquals("entry", scalar(sql, "SELECT t_type FROM " + table + " WHERE t_ili_tid='original-entry'"));
            assertEquals("specializedentry", scalar(sql, "SELECT t_type FROM " + table + " WHERE t_ili_tid='added-specialized'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET t_type = 'unmapped'"));
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
