package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
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
        assertCliSuccess(db, "import", fixture.model, "sites", fixture.site("before", fixture.siteBid, "Before"));
        assertCliSuccess(db, "import", fixture.model, "inspections", fixture.inspection());
        try (var sql = db.connect()) {
            fixture.assertObjectOidMetadata(db, sql);
            fixture.assertStoredSiteAndReference(db, sql, "Before");
            String ids = fixture.siteIds(db, sql);
            assertNotNull(ids);
            assertCliSuccess(db, "update", fixture.model, "sites", fixture.site("after", fixture.siteBid, "After"));
            assertEquals(ids, fixture.siteIds(db, sql));
            fixture.assertStoredSiteAndReference(db, sql, "After");
            String otherBid = "755fd3a0-b570-41c9-a757-aae90aada1f1";
            assertCliFailure(db, "update", fixture.model, "sites", fixture.site("relocated", otherBid, "Moved"),
                    "Object OID 0011 is already stored in another basket");
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals(ids, fixture.siteIds(db, sql));
            fixture.assertStoredSiteAndReference(db, sql, "After");
        }
        Path output = work.resolve("sites.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", fixture.siteBid);
        fixture.assertSite(output, "After");
        Path linked = work.resolve("linked-inspection.xtf");
        assertCliSuccess(db, "export", fixture.model, null, linked, "--basket", fixture.inspectionBid);
        fixture.assertInspection(linked);
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void numericBasketOidUsesExactBidForSelectionAndUpdate(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("numeric-basket-oid.gpkg"), backend);
        var fixture = NumericIdentityFixtures.forBasketOids(work, version);
        assertCliSuccess(db, "schemaimport", fixture.model, null, fixture.ili, "--create-basket-col");
        assertCliSuccess(db, "import", fixture.model, "sites", fixture.site("before", fixture.siteBid, "Before"));
        assertCliSuccess(db, "import", fixture.model, "inspections", fixture.inspection());
        String otherBid = "10";
        String otherOid = "755fd3a0-b570-41c9-a757-aae90aada1f1";
        assertCliSuccess(db, "import", fixture.model, "other", fixture.site("other", otherBid, otherOid, "Other"));
        Path output = work.resolve("selected-basket.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", otherBid);
        fixture.assertSite(output, otherBid, otherOid, "Other");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", fixture.siteBid);
        fixture.assertSite(output, "Before");

        try (var sql = db.connect()) {
            String ids = fixture.siteIds(db, sql);
            assertNotNull(ids);
            assertCliSuccess(db, "update", fixture.model, "sites", fixture.site("after", fixture.siteBid, "After"));
            assertEquals(ids, fixture.siteIds(db, sql));
            fixture.assertStoredSiteAndReference(db, sql, "After");
            assertCliFailure(db, "update", fixture.model, "sites", fixture.site("foreign", otherBid, "Rejected"),
                    "BID 10 belongs to another dataset");
            assertEquals(ids, fixture.siteIds(db, sql));
            fixture.assertStoredSiteAndReference(db, sql, "After");
            assertEquals("Other", scalar(sql, "SELECT label FROM " + db.table("site") + " WHERE t_ili_tid='" + otherOid + "'"));

            execute(sql, "UPDATE " + db.table("t_ili2db_basket") + " SET t_ili_tid=NULL WHERE t_ili_tid='0010'");
            assertExportRejected(db, fixture.model, "sites", output, "Missing stable basket OID");
            assertCliFailure(db, "update", fixture.model, "sites", fixture.site("repair", fixture.siteBid, "Rejected"),
                    "Missing stored BID");
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals(ids, fixture.siteIds(db, sql));
            fixture.assertStoredSiteAndReference(db, sql, "After");
        }
    }
}
