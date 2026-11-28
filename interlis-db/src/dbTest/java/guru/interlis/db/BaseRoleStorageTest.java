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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class BaseRoleStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsOneToOneRoleWhenProjectionDropsTarget(int version, Backend backend) throws Exception {
        var fixture = new BaseRoleFixtures(work, version);
        var db = POSTGIS.target(work.resolve("roles.gpkg"), backend);
        fixture.createExtension(db);
        assertCliSuccess(db, "import", fixture.extension, "roles", fixture.delivery);
        var output = work.resolve("base.xtf");
        assertCliSuccess(db, "export", fixture.extension, "roles", output, "--export-model", fixture.base);
        fixture.assertProjection(output);
        var published = Files.readAllBytes(output);

        assertCliSuccess(db, "replace", fixture.extension, "roles", fixture.deliveryWithDroppedMarker());
        assertCliFailure(db, "export", fixture.extension, "roles", output,
                "Missing projected target mark at " + fixture.base + ".Data.Assignment.Mark of object survey in basket work",
                "--export-model", fixture.base);
        assertArrayEquals(published, Files.readAllBytes(output));
    }
}
