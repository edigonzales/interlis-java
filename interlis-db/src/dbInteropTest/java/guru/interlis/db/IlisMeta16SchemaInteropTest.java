package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class IlisMeta16SchemaInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage24() throws Exception { referenceProfiles(null); }
    @Test void postgis24() throws Exception { referenceProfiles(POSTGIS.container()); }

    private void referenceProfiles(PostgreSQLContainer postgres) throws Exception {
        var source = Path.of("../testdata/conformance/metamodel/IlisMeta16.ili");
        var ili = Files.copy(source, work.resolve(source.getFileName()));
        var reference = new ReferenceTool(work, postgres != null);
        for (var profile : List.of("newclass", "smart1", "smart2")) {
            boolean newClass = profile.equals("newclass");
            var db = new TestDatabase(work, postgres, "meta_ref_" + profile);
            var args = new ArrayList<>(List.of("--schemaimport", "--createBasketCol", "--noSmartMapping"));
            if (!newClass) args.add("--" + profile + "Inheritance");
            args.add(ili.toString());
            reference.run(db, work.toString(), args.toArray(String[]::new));
            try (var sql = db.connect()) {
                var inheritance = "select setting from " + db.table("t_ili2db_trafo")
                        + " where tag = 'ch.ehi.ili2db.inheritance' and iliname = 'IlisMeta16.ModelData.";
                assertEquals("embedded", scalar(sql, inheritance + "BasketOID'"), profile);
                assertEquals(newClass ? "newClass" : "subClass", scalar(sql, inheritance + "Factor'"), profile);
                assertEquals(profile.equals("smart2") ? "newAndSubClass" : "newClass",
                        scalar(sql, inheritance + "Model'"), profile);
                String oidColumn = newClass ? "oid" : "oid_texttype";
                assertNull(scalar(sql, "select t_basket, " + oidColumn + " from " + db.table("dataunit") + " where 1 = 0"), profile);
            }
        }
    }
}
