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

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void addModelsIsTransactionalAndCanBeRetried(Backend backend, int version) throws Exception {
        var fixture = new ModelExtensionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("extension.gpkg"), backend);
        fixture.initialSchema(db);
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
            var stack = StackWalker.getInstance();
            CancellationToken duringDdl = () -> stack.walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals(SchemaSql.class.getName()) && frame.getMethodName().equals("apply")))
                    && checkpoints.incrementAndGet() == 2;
            var cancelled = schemaWithIdentityColumns(db, fixture.added, fixture.addedSource, duringDdl,
                    "--add-models", "--model-file", fixture.sharedSource.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(2, checkpoints.get());
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
}
