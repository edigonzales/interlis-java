package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class BaseExternalRoleStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void checksTheIdentityOfAnOmittedCatalogueTarget(int version, Backend backend) throws Exception {
        var fixture = BaseExternalRoleFixtures.create(work, version);
        var db = POSTGIS.target(work.resolve("external-base.gpkg"), backend);
        fixture.schema(db);
        assertCliSuccess(db, "import", fixture.derived(), "source", fixture.delivery(), "--model", fixture.parent());
        var output = work.resolve("base.xtf");
        String[] options = {"--export-model", fixture.parent(), "--basket", "region-partial"};
        assertCliSuccess(db, "export", fixture.derived(), null, output, options);
        fixture.assertProjection(output, false, true);

        var catalog = work.resolve("catalog.xtf");
        assertCliSuccess(db, "export", fixture.derived(), null, catalog, "--model", fixture.parent(), "--basket", "catalog-b");
        var duplicate = Files.writeString(work.resolve("duplicate.xtf"),
                Files.readString(catalog).replace("catalog-b", "other-b"));
        assertCliSuccess(db, "import", fixture.derived(), "duplicate", duplicate, "--model", fixture.parent());
        if (version == 23) {
            // The target BID disambiguates an otherwise duplicate TID.
            assertCliSuccess(db, "export", fixture.derived(), null, output, options);
            fixture.assertProjection(output, false, true);
            try (var sql = db.connect()) {
                execute(sql, "UPDATE " + db.table("t_ili2db_basket") + " SET t_ili_tid='catalog-b' WHERE t_ili_tid='other-b'");
            }
        }

        byte[] original = Files.readAllBytes(output);
        assertCliFailure(db, "export", fixture.derived(), null, output, "Ambiguous stored reference target catalog-site", options);
        assertArrayEquals(original, Files.readAllBytes(output));
    }
}
