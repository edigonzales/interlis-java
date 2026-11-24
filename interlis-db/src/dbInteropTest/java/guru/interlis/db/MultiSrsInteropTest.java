package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.CrsFixtures.assertMixed;
import static guru.interlis.db.CrsFixtures.assertMixedSrs;
import static guru.interlis.db.CrsFixtures.nativeLayout;
import static guru.interlis.db.CrsInteropSupport.assertCatalogs;
import static guru.interlis.db.CrsInteropSupport.createReference;
import static guru.interlis.db.CrsInteropSupport.referenceExport;
import static guru.interlis.db.CrsInteropSupport.referenceImport;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;

final class MultiSrsInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageMixedSrid23() throws Exception { exchange(23, null, false); }
    @Test void geoPackageMixedSrid24() throws Exception { exchange(24, null, false); }
    @Test void postgisMixedSrid23() throws Exception { exchange(23, POSTGIS.container(), false); }
    @Test void postgisMixedSrid24() throws Exception { exchange(24, POSTGIS.container(), false); }

    @Test void geoPackageFixedMultiSrs23() throws Exception { exchange(23, null, true); }
    @Test void geoPackageFixedMultiSrs24() throws Exception { exchange(24, null, true); }
    @Test void postgisFixedMultiSrs23() throws Exception { exchange(23, POSTGIS.container(), true); }
    @Test void postgisFixedMultiSrs24() throws Exception { exchange(24, POSTGIS.container(), true); }

    private void exchange(int version, PostgreSQLContainer postgres, boolean suffix) throws Exception {
        var reference = new ReferenceTool(work, postgres != null);
        var fixtures = new CrsFixtures(work, version);
        String model = "MixedSrid" + version;
        var ili = fixtures.fixture(model);
        for (boolean split : List.of(true, false)) {
            String scope = (suffix ? "suffix" : "mixed") + "_v" + version + "_";
            var old = new TestDatabase(work, postgres, split ? "split_reference" : "wide_reference", scope);
            var own = old.peer(split ? "split_own" : "wide_own");
            var referenceOptions = new ArrayList<>(List.of(suffix ? "--multiSrs" : "--createGeomIdx"));
            if (split && old.postgis()) referenceOptions.add("--oneGeomPerTable");
            if (!split && !old.postgis()) referenceOptions.add("--gpkgMultiGeomPerTable");
            var options = new ArrayList<>(nativeLayout(own, split));
            options.add(suffix ? "--multi-srs" : "--create-geom-index");
            createReference(reference, old, work, ili, 2056, referenceOptions.toArray(String[]::new));
            assertSchemaWithIdentityColumns(own, model, ili, options.toArray(String[]::new));
            assertCatalogs(old, own);
            for (var db : List.of(old, own)) {
                Path input = fixtures.delivery(model, "basket-legacy-mixed",
                        fixtures.mixedObject(model, "legacy-mixed", "2600001.625", true));
                referenceImport(reference, db, "legacy", input);
                Path output = work.resolve(db.schema() + "-native.xtf");
                assertCliSuccess(db, "export", model, "legacy", output);
                assertTransfer(fixtures, output, "legacy-mixed", "2600001.625", true);

                input = fixtures.delivery(model, "basket-own-mixed", fixtures.mixedObject(model, "own-mixed", "2600001.625", true));
                assertCliSuccess(db, "import", model, "own", input);
                boolean update = db == own && !suffix;
                try (var sql = db.connect()) {
                    assertMixedSrs(db, sql, "legacy-mixed", split, suffix, true);
                    assertMixedSrs(db, sql, "own-mixed", split, suffix, true);
                    if (update) {
                        var change = fixtures.delivery(model, "basket-own-mixed", fixtures.mixedObject(model, "own-mixed", "2600800.125", false));
                        assertCliSuccess(db, "update", model, "own", change);
                        assertMixedSrs(db, sql, "own-mixed", split, false, false);
                    }
                }
                output = work.resolve(db.schema() + "-reference.xtf");
                referenceExport(reference, db, "own", output);
                assertTransfer(fixtures, output, "own-mixed", update ? "2600800.125" : "2600001.625", !update);
            }
        }
    }

    private static void assertTransfer(CrsFixtures fixtures, Path output, String tid, String x, boolean withOld) throws Exception {
        assertMixed(fixtures.object(fixtures.basket(output, tid), tid), x, withOld);
    }
}
