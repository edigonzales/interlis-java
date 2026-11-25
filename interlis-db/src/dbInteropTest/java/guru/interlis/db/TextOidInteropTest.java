package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ObjectIdFixtures.assertTextOidTransfer;

final class TextOidInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage24() throws Exception { compare(null); }
    @Test void postgis24() throws Exception { compare(POSTGIS.container()); }

    private void compare(PostgreSQLContainer postgres) throws Exception {
        String model = "TextOid24";
        var source = fixture(work, model + ".ili");
        var input = fixture(work, model + ".xtf");
        var reference = new ReferenceTool(work, postgres != null);
        for (var profile : List.of("newClass", "smart1", "smart2")) {
            String suffix = profile.toLowerCase(java.util.Locale.ROOT);
            var legacy = new TestDatabase(work, postgres, "textoid_ref_" + suffix);
            var own = legacy.peer("textoid_own_" + suffix);
            schema(reference, legacy, work, model, profile);
            var options = profile.equals("newClass") ? new String[0] : new String[]{"--inheritance", profile};
            assertSchemaWithIdentityColumns(own, model, source, options);
            try (var expected = legacy.connect(); var actual = own.connect()) {
                DbSchemaCatalog.read(expected, legacy.postgis(), legacy.schema())
                        .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
            }
            var output = work.resolve(suffix + "-export.xtf");
            assertCliSuccess(legacy, "import", model, "text-oid", input);
            assertCliSuccess(legacy, "export", model, "text-oid", output);
            assertTextOidTransfer(output, 24);
        }
    }
}
