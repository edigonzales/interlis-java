package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.AlternateCrsFixtures.assertDelivery;
import static guru.interlis.db.AlternateCrsFixtures.assertSwitchedTopic;
import static guru.interlis.db.CrsFixtures.assertDeferredStorage;
import static guru.interlis.db.CrsInteropSupport.assertCatalogs;
import static guru.interlis.db.CrsInteropSupport.createReference;
import static guru.interlis.db.CrsInteropSupport.referenceExport;
import static guru.interlis.db.CrsInteropSupport.referenceImport;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BasketCrsInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageAlternate23() throws Exception { alternate(null); }
    @Test void postgisAlternate23() throws Exception { alternate(POSTGIS.container()); }

    @Test void geoPackageDeferredPoints24() throws Exception { deferredPoints(null); }
    @Test void postgisDeferredPoints24() throws Exception { deferredPoints(POSTGIS.container()); }

    @Test void geoPackageDeferredMulti24() throws Exception { deferredMulti(null); }
    @Test void postgisDeferredMulti24() throws Exception { deferredMulti(POSTGIS.container()); }

    @Test void geoPackageDeferredCurves24() throws Exception { deferredCurves(null); }
    @Test void postgisDeferredCurves24() throws Exception { deferredCurves(POSTGIS.container()); }

    private void alternate(PostgreSQLContainer postgres) throws Exception {
        var old = new TestDatabase(work, postgres, "basket_alternate_reference");
        var own = old.peer("basket_alternate_own");
        var reference = new ReferenceTool(work, old.postgis());
        var fixture = new AlternateCrsFixtures(work);
        String model = "Current23";
        Path ili = fixture.modelFile();
        createReference(reference, old, work, ili, 2056, "--models", model, "--multiSrs", "--altSrsModel", "Current23=Old23");
        AlternateCrsFixtures.createOwn(own, ili);
        assertCatalogs(old, own);
        for (var db : List.of(old, own)) {
            referenceImport(reference, db, "legacy", fixture.delivery("legacy"));
            Path output = work.resolve(db.schema() + "-native.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertDelivery(output, "legacy");

            Path input = fixture.delivery("native");
            assertCliSuccess(db, "import", model, "native", input);
            output = work.resolve(db.schema() + "-reference.xtf");
            referenceExport(reference, db, "native", output);
            assertDelivery(output, "native");
            try (var sql = db.connect()) {
                AlternateCrsFixtures.assertStorage(db, sql, "native");
            }
            if (db == own) {
                assertCliSuccess(db, "replace", model, "native", fixture.switchTopic(input));
                output = work.resolve("after-topic-switch.xtf");
                referenceExport(reference, db, "native", output);
                assertSwitchedTopic(output, "native");
            }
        }
    }

    private void deferredPoints(PostgreSQLContainer postgres) throws Exception {
        var old = new TestDatabase(work, postgres, "basket_deferred_reference");
        var own = old.peer("basket_deferred_own");
        var reference = new ReferenceTool(work, old.postgis());
        var fixture = new CrsFixtures(work, 24);
        String model = "DeferredCrs24";
        var ili = fixture.deferredPoints(model);
        createReference(reference, old, work, ili, 2056, "--multiSrs", "--createGeomIdx");
        assertSchemaWithIdentityColumns(own, model, ili, "--multi-srs", "--create-geom-index");
        assertCatalogs(old, own);

        for (var db : List.of(old, own)) {
            referenceImport(reference, db, "legacy", fixture.deferredDelivery(model, "legacy"));
            Path output = work.resolve(db.schema() + "-native.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            fixture.assertDeferred(output, model, "legacy");

            Path input = fixture.deferredDelivery(model, "native");
            assertCliSuccess(db, "import", model, "native", input);
            output = work.resolve(db.schema() + "-reference.xtf");
            referenceExport(reference, db, "native", output);
            fixture.assertDeferred(output, model, "native");
            try (var sql = db.connect()) {
                assertDeferredStorage(db, sql, "legacy");
                assertDeferredStorage(db, sql, "native");
            }
            if (db == own) {
                assertCliSuccess(db, "replace", model, "native", fixture.switchDeferredDomain(input));
                output = work.resolve("after-domain-switch.xtf");
                referenceExport(reference, db, "native", output);
                fixture.assertSwitchedDomain(output, model, "native");
                deferredFollowUp(reference, db, fixture, model);
            }
        }
    }

    private void deferredFollowUp(ReferenceTool reference, TestDatabase db, CrsFixtures fixture, String model)
            throws Exception {
        try (var sql = db.connect()) {
            String stations = db.table("station");
            long maxId = Long.parseLong(scalar(sql, "SELECT max(t_id) FROM " + stations));
            referenceImport(reference, db, "tail", fixture.deferredDelivery(model, "tail"));
            assertTrue(Long.parseLong(scalar(sql, "SELECT min(t_id) FROM " + stations + " WHERE t_ili_tid LIKE '%-tail'")) > maxId);
            assertDeferredStorage(db, sql, "tail");
        }
        Path output = work.resolve("follow-up.xtf");
        assertCliSuccess(db, "export", model, "tail", output);
        fixture.assertDeferred(output, model, "tail");
    }

    private void deferredMulti(PostgreSQLContainer postgres) throws Exception {
        var old = new TestDatabase(work, postgres, "basket_multi_reference");
        new DeferredGeometryInteropCases(work, new ReferenceTool(work, old.postgis()))
                .deferredMulti(old, old.peer("basket_multi_native"));
    }

    private void deferredCurves(PostgreSQLContainer postgres) throws Exception {
        var old = new TestDatabase(work, postgres, "basket_curves_reference");
        new DeferredGeometryInteropCases(work, new ReferenceTool(work, old.postgis()))
                .deferredCurves(old, old.peer("basket_curves_native"));
    }
}
