package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.CustomRowIdFixtures.KEY;
import static guru.interlis.db.CustomRowIdFixtures.SETTING;
import static guru.interlis.db.CustomRowIdFixtures.assertRelation;
import static guru.interlis.db.CustomRowIdFixtures.assertSpatialIndex;
import static guru.interlis.db.CustomRowIdFixtures.assetId;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CustomRowIdInteropTest {
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
        var db = new TestDatabase(work, postgres, "custom_id", "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        var fixtures = new CustomRowIdFixtures(work, version);
        fixtures.modelFile();
        var options = new ArrayList<>(List.of("--t_id_Name", KEY, "--createGeomIdx", "--createFk"));
        if (db.postgis()) options.add("--oneGeomPerTable");
        InteropCommands.schema(reference, db, work, fixtures.model, "newClass", options.toArray(String[]::new));
        var oldInput = fixtures.delivery("reference", "reference", "2601100.125", "from ili2db");
        InteropCommands.importDataset(reference, db, "reference", oldInput);
        var ownOutput = work.resolve("native-export.xtf");
        assertCliSuccess(db, "export", fixtures.model, "reference", ownOutput);
        fixtures.assertDelivery(ownOutput, "reference", "2601100.125", "from ili2db");

        var ownInput = fixtures.delivery("native", "native", "2601200.125", "from interlis-db");
        assertCliSuccess(db, "import", fixtures.model, "native", ownInput);
        var oldOutput = work.resolve("reference-export.xtf");
        InteropCommands.exportDataset(reference, db, "native", oldOutput);
        fixtures.assertDelivery(oldOutput, "native", "2601200.125", "from interlis-db");
        try (var sql = db.connect()) {
            assertEquals(KEY, scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_settings")
                    + " WHERE tag='" + SETTING + "'"));
            assertRelation(db, sql, "native");
            assertSpatialIndex(db, sql, assetId(db, sql, "native"), "2601200.125");
        }

        var following = fixtures.delivery("following", "following", "2601300.125", "continued");
        InteropCommands.importDataset(reference, db, "following", following);
        try (var sql = db.connect()) {
            assertRelation(db, sql, "reference");
            assertRelation(db, sql, "following");
        }
    }
}
