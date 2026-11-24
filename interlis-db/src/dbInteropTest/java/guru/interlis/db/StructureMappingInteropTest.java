package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

final class StructureMappingInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageExpandSchema23() throws Exception { exchangeExpandSchema(23, null); }
    @Test void geoPackageExpandSchema24() throws Exception { exchangeExpandSchema(24, null); }
    @Test void postgisExpandSchema23() throws Exception { exchangeExpandSchema(23, POSTGIS.container()); }
    @Test void postgisExpandSchema24() throws Exception { exchangeExpandSchema(24, POSTGIS.container()); }

    private void exchangeExpandSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "expand_baseline", "v" + version + "_");
        var own = new TestDatabase(work, postgres, "expand_own", "v" + version + "_");
        new StructureMappingCases(work, new ReferenceTool(work, own.postgis()), version).schema(baseline, own);
    }

    @Test void geoPackageExpandSmart223() throws Exception { exchangeExpandSmart2(23, null); }
    @Test void geoPackageExpandSmart224() throws Exception { exchangeExpandSmart2(24, null); }
    @Test void postgisExpandSmart223() throws Exception { exchangeExpandSmart2(23, POSTGIS.container()); }
    @Test void postgisExpandSmart224() throws Exception { exchangeExpandSmart2(24, POSTGIS.container()); }

    private void exchangeExpandSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "expand_smart_baseline", "v" + version + "_");
        var own = new TestDatabase(work, postgres, "expand_smart_own", "v" + version + "_");
        new StructureMappingCases(work, new ReferenceTool(work, own.postgis()), version).smart2(baseline, own);
    }

    @Test void geoPackageJsonCollections23() throws Exception { exchangeJsonCollections(23, null); }
    @Test void geoPackageJsonCollections24() throws Exception { exchangeJsonCollections(24, null); }
    @Test void postgisJsonCollections23() throws Exception { exchangeJsonCollections(23, POSTGIS.container()); }
    @Test void postgisJsonCollections24() throws Exception { exchangeJsonCollections(24, POSTGIS.container()); }

    private void exchangeJsonCollections(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "json_collections", "v" + version + "_");
        new StructureMappingCases(work, new ReferenceTool(work, db.postgis()), version).jsonCollections(db);
    }

    @Test void geoPackageJsonSchema23() throws Exception { exchangeJsonSchema(23, null); }
    @Test void geoPackageJsonSchema24() throws Exception { exchangeJsonSchema(24, null); }
    @Test void postgisJsonSchema23() throws Exception { exchangeJsonSchema(23, POSTGIS.container()); }
    @Test void postgisJsonSchema24() throws Exception { exchangeJsonSchema(24, POSTGIS.container()); }

    private void exchangeJsonSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "json_schema_baseline", "v" + version + "_");
        var own = new TestDatabase(work, postgres, "json_schema_own", "v" + version + "_");
        new StructureMappingCases(work, new ReferenceTool(work, own.postgis()), version).jsonSchema(baseline, own);
    }
}
