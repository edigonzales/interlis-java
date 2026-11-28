package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;

final class BaseGeometryStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void projectsCurrentInheritedGeometry(int version, Backend backend) throws Exception {
        var db = POSTGIS.target(work.resolve("terrain.gpkg"), backend);
        var fixture = new BaseGeometryFixtures(work, version);
        fixture.schema(db, false);
        assertCliSuccess(db, "import", fixture.regional, "terrain", fixture.delivery);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("track") + " SET trace=NULL WHERE t_ili_tid='straight'");
        }
        var output = work.resolve("terrain-base.xtf");
        assertCliSuccess(db, "export", fixture.regional, "terrain", output, "--export-model", fixture.base);
        fixture.assertDelivery(output, true);
    }
}
