package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;

final class AlternativeRoleInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() { POSTGIS.close(); }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new AlternativeRoleFixture(work, version);
        var reference = new ReferenceTool(work, postgres != null);
        var source = fixture.source();
        var legacy = fixture.transfer("legacy", "station");
        var ownInput = fixture.transfer("own", "station");
        for (String layout : List.of("newClass", "smart1", "smart2")) {
            var old = new TestDatabase(work, postgres, layout.toLowerCase(java.util.Locale.ROOT) + "_ref", "or_v" + version + "_");
            var own = old.peer(layout.toLowerCase(java.util.Locale.ROOT) + "_own");
            schema(reference, old, work, fixture.model(), layout);
            if (layout.equals("newClass")) assertSchemaWithIdentityColumns(own, fixture.model(), source);
            else assertSchemaWithIdentityColumns(own, fixture.model(), source, "--inheritance", layout);
            try (var expected = old.connect(); var actual = own.connect()) {
                DbSchemaCatalog.read(expected, old.postgis(), old.schema()).canonicalReferenceAlternatives()
                        .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()).canonicalReferenceAlternatives());
                fixture.assertTargets(old, expected, layout);
                fixture.assertTargets(own, actual, layout);
            }
            for (var db : List.of(old, own)) {
                importDataset(reference, db, "legacy", legacy);
                Path nativeOutput = work.resolve(db.schema() + "-native.xtf");
                assertCliSuccess(db, "export", fixture.model(), "legacy", nativeOutput);
                fixture.assertTransfer(nativeOutput, "legacy");
                assertCliSuccess(db, "import", fixture.model(), "own", ownInput);
                Path refOutput = work.resolve(db.schema() + "-ref.xtf");
                exportDataset(reference, db, "own", refOutput);
                fixture.assertTransfer(refOutput, "own");
            }
        }
    }
}
