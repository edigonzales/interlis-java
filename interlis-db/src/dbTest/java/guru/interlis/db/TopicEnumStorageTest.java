package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TopicEnumStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();
    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void updatesExtendedCodeInBaseColumn(int version, Backend backend) throws Exception {
        var fixture = new TopicEnumFixtures(work, version);
        var db = POSTGIS.target(work.resolve("topic-enum.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.derived, fixture.derivedSource, "--model-file", fixture.baseSource.toString());
        assertCliSuccess(db, "import", fixture.derived, "input", fixture.delivery("source", "open.weekend"));
        var row = "SELECT t_id || ':' || t_basket FROM " + db.table("station") + " WHERE t_ili_tid='one'";
        try (var sql = db.connect()) {
            fixture.assertMapping(db, sql);
            fixture.assertStored(db, sql, "open.weekend");
            var before = scalar(sql, row);
            assertNotNull(before);
            assertCliSuccess(db, "update", fixture.derived, "input", fixture.delivery("changed", "open.daily"));
            assertEquals(before, scalar(sql, row));
            fixture.assertStored(db, sql, "open.daily");
        }
        var output = work.resolve("stored.xtf");
        assertCliSuccess(db, "export", fixture.derived, "input", output);
        fixture.assertValues(output, "open.daily");
    }
}
