package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
            assertCliFailure(db, "schemaimport", fixture.specialized, null, fixture.specializedSource(),
                    "would change stored table entry for subtype " + fixture.specialized + ".Data.SpecializedEntry",
                    "--add-models", "--model-file", fixture.originalSource.toString());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));

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

    private static CancellationToken cancelDuringDdl(AtomicInteger checkpoints, int checkpoint) {
        var stack = StackWalker.getInstance();
        return () -> stack.walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().equals(SchemaSql.class.getName()) && frame.getMethodName().equals("apply")))
                && checkpoints.incrementAndGet() == checkpoint;
    }
}
