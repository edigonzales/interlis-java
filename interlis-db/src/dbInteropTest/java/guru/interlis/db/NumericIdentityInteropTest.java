package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NumericIdentityInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageObjectOid23() throws Exception { exchangeObjectOid(23, null); }
    @Test void geoPackageObjectOid24() throws Exception { exchangeObjectOid(24, null); }
    @Test void postgisObjectOid23() throws Exception { exchangeObjectOid(23, POSTGIS.container()); }
    @Test void postgisObjectOid24() throws Exception { exchangeObjectOid(24, POSTGIS.container()); }

    @Test void geoPackageBasketOid23() throws Exception { exchange(NumericIdentityFixtures.forBasketOids(work, 23), null); }
    @Test void geoPackageBasketOid24() throws Exception { exchange(NumericIdentityFixtures.forBasketOids(work, 24), null); }
    @Test void postgisBasketOid23() throws Exception { exchange(NumericIdentityFixtures.forBasketOids(work, 23), POSTGIS.container()); }
    @Test void postgisBasketOid24() throws Exception { exchange(NumericIdentityFixtures.forBasketOids(work, 24), POSTGIS.container()); }

    private void exchangeObjectOid(int version, PostgreSQLContainer postgres) throws Exception {
        exchange(new NumericIdentityFixtures(work, version), postgres);
    }

    private void exchange(NumericIdentityFixtures fixture, PostgreSQLContainer postgres) throws Exception {
        var legacy = new TestDatabase(work, postgres, "reference", fixture.model.toLowerCase(Locale.ROOT) + "_");
        var own = legacy.peer("own");
        var reference = new ReferenceTool(work, legacy.postgis());
        reference.run(legacy, work.toString(), "--schemaimport", "--createBasketCol", "--sqlEnableNull",
                "--noSmartMapping", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", fixture.ili.toString());
        assertCliSuccess(own, "schemaimport", fixture.model, null, fixture.ili, "--create-basket-col");
        try (var expected = legacy.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, legacy.postgis(), legacy.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        var before = fixture.site("before", fixture.siteBid, "Before");
        var after = fixture.site("after", fixture.siteBid, "After");
        var inspection = fixture.inspection();
        for (var db : List.of(legacy, own)) {
            reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", "sites", before.toString());
            Path siteOutput = work.resolve("site-native.xtf");
            assertCliSuccess(db, "export", fixture.model, "sites", siteOutput);
            fixture.assertSite(siteOutput, "Before");

            assertCliSuccess(db, "import", fixture.model, "inspections", inspection);
            Path noteOutput = work.resolve("inspection-reference.xtf");
            reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", "inspections", noteOutput.toString());
            fixture.assertInspection(noteOutput);
            try (var sql = db.connect()) {
                fixture.assertObjectOidMetadata(db, sql);
                fixture.assertStoredSiteAndReference(db, sql, "Before");
                String initial = fixture.siteIds(db, sql);
                assertNotNull(initial);
                if (db == legacy) {
                    assertCliSuccess(db, "update", fixture.model, "sites", after);
                } else {
                    reference.run(db, "%ILI_FROM_DB", "--update", "--dataset", "sites", after.toString());
                }
                assertEquals(initial, fixture.siteIds(db, sql));
                fixture.assertStoredSiteAndReference(db, sql, "After");
            }
        }
    }
}
