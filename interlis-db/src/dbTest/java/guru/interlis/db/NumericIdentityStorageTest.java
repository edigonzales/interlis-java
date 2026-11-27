package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.NumericIdentityFixtures.INSPECTION_BID;
import static guru.interlis.db.NumericIdentityFixtures.SITE_BID;
import static guru.interlis.db.NumericIdentityFixtures.siteIds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NumericIdentityStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void numericObjectOidWorksWithoutTransferTidColumn(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("numeric-object-oid.gpkg"), backend);
        var fixture = new NumericIdentityFixtures(work, version);
        assertCliSuccess(db, "schemaimport", fixture.model, null, fixture.ili, "--create-basket-col");
        assertCliSuccess(db, "import", fixture.model, "sites", fixture.site("before", SITE_BID, "Before"));
        assertCliSuccess(db, "import", fixture.model, "inspections", fixture.inspection());
        try (var sql = db.connect()) {
            fixture.assertObjectOidMetadata(db, sql);
            fixture.assertStoredSiteAndReference(db, sql, "Before");
            String ids = siteIds(db, sql);
            assertNotNull(ids);
            assertCliSuccess(db, "update", fixture.model, "sites", fixture.site("after", SITE_BID, "After"));
            assertEquals(ids, siteIds(db, sql));
            fixture.assertStoredSiteAndReference(db, sql, "After");
            String otherBid = "755fd3a0-b570-41c9-a757-aae90aada1f1";
            assertCliFailure(db, "update", fixture.model, "sites", fixture.site("relocated", otherBid, "Moved"),
                    "Object OID 0011 is already stored in another basket");
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals(ids, siteIds(db, sql));
            fixture.assertStoredSiteAndReference(db, sql, "After");
        }
        Path output = work.resolve("sites.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", SITE_BID);
        fixture.assertSite(output, "After");
        Path linked = work.resolve("linked-inspection.xtf");
        assertCliSuccess(db, "export", fixture.model, null, linked, "--basket", INSPECTION_BID);
        fixture.assertInspection(linked);
    }
}
