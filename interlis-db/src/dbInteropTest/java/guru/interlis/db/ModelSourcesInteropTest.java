package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModelSourcesInteropTest {
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
        var fixture = new ModelSourceFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "model_sources_reference", "v" + version + "_");
        var own = baseline.peer("model_sources_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.referenceModels(), "newClass");
        fixture.schema(own);
        try (var refSql = baseline.connect(); var ownSql = own.connect()) {
            DbSchemaCatalog.read(refSql, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(ownSql, own.postgis(), own.schema()));
        }
        fixture.removeSources();

        Path input = Files.writeString(work.resolve("native.xtf"), Files.readString(fixture.input).replace("fixture-", "native-"));
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", fixture.input);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            fixture.run(db, "export", "legacy", output);
            fixture.assertDelivery(output, "fixture");

            fixture.run(db, "import", "native", input);
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertDelivery(output, "native");
            assertStorage(db, version, fixture);
        }
    }

    private static void assertStorage(TestDatabase db, int version, ModelSourceFixtures fixture) throws Exception {
        try (var sql = db.connect()) {
            String models = db.table("t_ili2db_model");
            assertEquals("6", scalar(sql, "SELECT count(*) FROM " + models));
            String types = "DbSourceTypes" + version;
            assertEquals(types + " " + fixture.shared + "{ " + types + "}", scalar(sql,
                    "SELECT modelname FROM " + models + " WHERE modelname LIKE '" + types + " %'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + models + " WHERE modelname='" + fixture.types + "'"));
            String mapping = db.table("t_ili2db_classname");
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + mapping + " WHERE iliname LIKE '%.Unused.%'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + mapping + " WHERE iliname='DbSharedDetail"
                    + version + ".Detail' AND sqlname='detail'"));
            for (String table : List.of("station", "importedstation")) {
                String srid = db.postgis() ? "SELECT DISTINCT ST_SRID(location) FROM " + db.table(table)
                        : "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name='" + table + "' AND column_name='location'";
                assertEquals("2056", scalar(sql, srid));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(table)
                        + " WHERE aname='Imported domains' AND astate='active'"));
            }
            for (String field : List.of("summary", "entries", "observations")) {
                String query = "SELECT count(*) FROM " + db.table("detail") + " d JOIN " + db.table("report")
                        + " r ON d.report_" + field + "=r.t_id AND d.t_basket=r.t_basket";
                assertEquals(field.equals("summary") ? "2" : "4", scalar(sql, query), field);
                assertEquals("2", scalar(sql, query + " WHERE d.t_seq=0"), field);
            }
        }
    }
}
