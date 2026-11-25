package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertMissingTarget;

final class IlisMeta16SchemaStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource({"GEOPACKAGE, newclass", "GEOPACKAGE, smart1", "GEOPACKAGE, smart2",
            "POSTGIS, newclass", "POSTGIS, smart1", "POSTGIS, smart2"})
    void rejectsUnsupportedSchemaBeforeCreatingTarget(Backend backend, String profile) throws Exception {
        var source = Path.of("../testdata/conformance/metamodel/IlisMeta16.ili");
        var db = POSTGIS.target(work.resolve("ilismeta16.gpkg"), backend);
        var options = new ArrayList<String>();
        if (!profile.equals("newclass")) options.addAll(List.of("--inheritance", profile));
        assertCliFailure(db, "schemaimport", "IlisMeta16", null, source,
                "IlisMeta16.ModelData requires --create-basket-col", options.toArray(String[]::new));
        assertMissingTarget(db);
        options.addAll(List.of("--create-basket-col", "--create-tid-col"));
        assertCliFailure(db, "schemaimport", "IlisMeta16", null, source,
                "Unsupported ili2db mapping (composition in structure inheritance at IlisMeta16.ModelData.UnaryExpr.SubExpression)",
                options.toArray(String[]::new));
        assertMissingTarget(db);
    }
}
