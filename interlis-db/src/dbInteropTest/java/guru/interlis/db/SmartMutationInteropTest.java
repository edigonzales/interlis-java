package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SmartMutationInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixtures = new SmartFixtures(work, version);
        var reference = new ReferenceTool(work, postgres != null);
        String model = "TownChild" + version;
        fixtures.fixture("/topics/", "TownParent" + version + ".ili");
        fixtures.fixture("/topics/", model + ".ili");
        Path input = fixtures.fixture("/topics/", "region" + version + ".xtf");
        Path updated = fixtures.changedRegion(input);
        for (String layout : List.of("smart1", "smart2")) {
            var db = new TestDatabase(work, postgres, "mutation_" + layout, "v" + version + "_");
            schema(reference, db, work, model, layout);
            importDataset(reference, db, "region", input);
            String table = db.table(layout.equals("smart1") ? "station" : "localstation");
            String row = " FROM " + table + " WHERE t_ili_tid='region-s'";
            String basket = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='region-b'";
            try (var sql = db.connect()) {
                String oldId = scalar(sql, "SELECT t_id" + row);
                String oldBasket = scalar(sql, basket);
                assertNotNull(oldId);
                assertNotNull(oldBasket);

                assertCliSuccess(db, "update", model, "region", updated);
                assertEquals(oldId, scalar(sql, "SELECT t_id" + row));
                assertEquals(oldBasket, scalar(sql, basket));
                assertEquals("updated", scalar(sql, "SELECT localmemo" + row));
                assertEquals("base", scalar(sql, "SELECT memo" + row));
            }
            Path output = work.resolve(layout + "-after-update.xtf");
            exportDataset(reference, db, "region", output);
            fixtures.assertRegion(output, "updated");
        }
    }
}
