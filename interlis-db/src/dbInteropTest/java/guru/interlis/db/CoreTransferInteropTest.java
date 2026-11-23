package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

final class CoreTransferInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageExternal23() throws Exception { external(23, null); }
    @Test void geoPackageExternal24() throws Exception { external(24, null); }
    @Test void postgisExternal23() throws Exception { external(23, POSTGIS.container()); }
    @Test void postgisExternal24() throws Exception { external(24, POSTGIS.container()); }

    private void external(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "external_reference", "ext_" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        var cases = new CoreExternalCases(work);
        cases.exchangeExternal(version, db, reference);
        cases.exchangeExternalTopics(version, db.peer("topics_reference"), reference);
    }
}
