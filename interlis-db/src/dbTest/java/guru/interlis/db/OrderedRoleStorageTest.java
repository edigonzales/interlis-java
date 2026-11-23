package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.OrderedRoleFixtures.assertEmbeddedOrderOwner;
import static guru.interlis.db.OrderedRoleFixtures.assertOrderColumn;
import static guru.interlis.db.OrderedRoleFixtures.assertOrderedTransfer;
import static guru.interlis.db.OrderedRoleFixtures.assertStoredOrderedPositions;
import static guru.interlis.db.OrderedRoleFixtures.copyOrderedFixture;
import static guru.interlis.db.OrderedRoleFixtures.orderedMemberships;
import static guru.interlis.db.TransferCatalogAssertions.assertAssociationCatalog;
import static guru.interlis.db.TransferCatalogAssertions.assertTypeKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderedRoleStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void orderedRolesAndInvalidPositions(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("ordered.gpkg"), backend);
        String model = "OrderedInterop" + version;
        Path ili = copyOrderedFixture(work, model + ".ili");
        String fixture = Files.readString(copyOrderedFixture(work, model + ".xtf"));
        assertSchemaWithIdentityColumns(db, model, ili);
        String order = version == 23 ? "ORDER_POS" : "i:order_pos";
        Path unpositioned = Files.writeString(work.resolve("ordered-missing.xtf"),
                fixture.replaceAll(" " + order + "=\"[0-9]+\"", ""));
        Path nativeInput = Files.writeString(work.resolve("ordered-native.xtf"), fixture.replace("ordered-", "native-"));
        Path nativeOutput = work.resolve("ordered-export.xtf");
        assertCliSuccess(db, "import", model, "ordered", unpositioned);
        try (var sql = db.connect()) {
            assertOrderColumn(db, sql, "membership", "amember");
            assertOrderColumn(db, sql, "membership", "team");
            assertOrderColumn(db, sql, "entry", "runner");
            assertOrderColumn(db, sql, "document", "writtenin");
            assertOrderColumn(db, sql, "placement", "operatives");
            assertEmbeddedOrderOwner(db, sql, model);
            assertAssociationCatalog(db, sql, model, "Placement", Map.of("Site", "dutyarea", "Operatives", "operative"));
            assertTypeKind(db, sql, "placement", "memo", "TEXT");
            assertStoredOrderedPositions(db, sql, "ordered", false);
            assertCliSuccess(db, "export", model, "ordered", nativeOutput);
            assertOrderedTransfer(nativeOutput, model, version, "ordered", false);
            seedStandalonePositions(db, sql);
            execute(sql, "update " + db.table("document") + " set writtenin_t_seq=21 where t_ili_tid='ordered-doc-two'");
            execute(sql, "update " + db.table("placement") + " set operatives_t_seq=case when memo is null then 18 else 34 end");
            assertCliSuccess(db, "export", model, "ordered", nativeOutput);
            assertOrderedTransfer(nativeOutput, model, version, "ordered", true);
            assertCliSuccess(db, "import", model, "native", nativeInput);
            assertStoredOrderedPositions(db, sql, "native", true);
            assertCliSuccess(db, "export", model, "native", nativeOutput);
            assertOrderedTransfer(nativeOutput, model, version, "native", true);
            assertOrderOverflowRollback(db, sql, model, fixture, version);
            String orphan = " where t_ili_tid='native-doc-loose'";
            execute(sql, "update " + db.table("document") + " set writtenin_t_seq=6" + orphan);
            assertExportRejected(db, model, "native", nativeOutput, "Filing.WrittenIn");
            execute(sql, "update " + db.table("document") + " set writtenin_t_seq=null" + orphan);
            execute(sql, "update " + db.table("entry") + " set runner_t_seq=0");
            assertExportRejected(db, model, "native", nativeOutput, "Entry.Runner");
        }
    }

    private void assertOrderOverflowRollback(Target db, Connection sql, String model, String fixture, int version)
            throws Exception {
        String order = version == 23 ? "ORDER_POS" : "i:order_pos";
        String broken = fixture.replace(order + "=\"4\"", order + "=\"9223372036854775808\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve("order-overflow.xtf"), broken.replace("ordered-", "overflow-"));
        var rejected = cli(db, "import", model, "overflow", input, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("Entry.Runner"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("64-bit"), rejected::diagnostics);
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_dataset") + " where datasetname='overflow'"));
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_basket") + " where t_ili_tid='overflow-basket'"));
        assertEquals("6", scalar(sql, "select count(*) from " + db.table("membership")));
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("amember") + " where t_ili_tid like 'overflow-%'"));
    }

    private static void seedStandalonePositions(Target db, Connection sql) throws SQLException {
        for (var pair : orderedMemberships("ordered", true)) {
            execute(sql, "update " + db.table("membership") + " set amember_t_seq=" + nullIfEmpty(pair.memberPosition())
                    + ", team_t_seq=" + nullIfEmpty(pair.teamPosition()) + " where amember=(select t_id from " + db.table("amember")
                    + " where t_ili_tid='" + pair.member() + "') and team=(select t_id from " + db.table("team")
                    + " where t_ili_tid='" + pair.team() + "')");
        }
        execute(sql, "update " + db.table("entry") + " set runner_t_seq=4");
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? "null" : value;
    }
}
