package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.CrsFixtures.assertDeferredStorage;
import static guru.interlis.db.CrsFixtures.assertHits;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertMissingTarget;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BasketCrsStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF 23")
    @EnumSource(Backend.class)
    void alternateTopicsDuringMutations(Backend backend) throws Exception {
        mutateAlternate(target(backend, "alternate-mutation"), new AlternateCrsFixtures(work));
    }

    @ParameterizedTest(name = "{0}, XTF 24")
    @EnumSource(Backend.class)
    void deferredDomainsDuringMutations(Backend backend) throws Exception {
        mutateDeferred(target(backend, "deferred-mutation"), new CrsFixtures(work, 24));
    }

    @ParameterizedTest(name = "{0}, XTF 23")
    @EnumSource(Backend.class)
    void alternateSchemaAndUnknownTopic(Backend backend) throws Exception {
        var fixtures = new AlternateCrsFixtures(work);
        var db = target(backend, "alternate");
        var model = fixtures.modelFile();
        String content = Files.readString(model);
        int start = content.indexOf("MODEL Old23");
        Path invalid = Files.writeString(work.resolve("Incompatible.ili"), content.substring(0, start)
                + content.substring(start).replace("TEXT*32", "TEXT*16"));
        var absent = target(backend, "alternate-incompatible");
        var failure = schemaWithIdentityColumns(absent, "Current23", invalid, CancellationToken.none(), "--multi-srs",
                "--alt-srs-model", "Current23=Old23");
        assertEquals(1, failure.code(), failure::diagnostics);
        assertTrue(failure.diagnostics().contains("structurally incompatible"), failure::diagnostics);
        assertMissingTarget(absent);

        AlternateCrsFixtures.createOwn(db, model);
        assertCliSuccess(db, "import", "Current23", "native", fixtures.delivery("native"));
        Path output = work.resolve("alternate.xtf");
        assertCliSuccess(db, "export", "Current23", "native", output);
        AlternateCrsFixtures.assertDelivery(output, "native");
        try (var sql = db.connect()) {
            AlternateCrsFixtures.assertStorage(db, sql, "native");
            execute(sql, "UPDATE " + db.table("t_ili2db_basket")
                    + " SET topic='Unknown23.Data' WHERE t_ili_tid='basket-old-native'");
        }
        assertExportRejected(db, "Current23", "native", output, "outside the selected model");
    }

    @ParameterizedTest(name = "{0}, XTF 24")
    @EnumSource(Backend.class)
    void rejectsMissingMultiSrsAndAmbiguousDomains(Backend backend) throws Exception {
        var fixtures = new CrsFixtures(work, 24);
        String model = "DeferredCrs24";
        Path ili = fixtures.deferredPoints(model);
        var absent = target(backend, "missing-option");
        var failure = schemaWithIdentityColumns(absent, model, ili, CancellationToken.none());
        assertEquals(1, failure.code(), failure::diagnostics);
        assertTrue(failure.diagnostics().contains("require --multi-srs"), failure::diagnostics);
        assertMissingTarget(absent);

        Path ambiguous = Files.writeString(work.resolve("ambiguous.ili"),
                Files.readString(ili).replace("EPSG:21781", "EPSG:2056"));
        var ambiguousTarget = target(backend, "ambiguous");
        failure = schemaWithIdentityColumns(ambiguousTarget, model, ambiguous, CancellationToken.none(), "--multi-srs");
        assertEquals(1, failure.code(), failure::diagnostics);
        assertTrue(failure.diagnostics().contains("multiple concrete domains with EPSG:2056"), failure::diagnostics);
        assertMissingTarget(ambiguousTarget);
    }

    @ParameterizedTest(name = "{0}, XTF 24")
    @EnumSource(Backend.class)
    void deferredImportRollbackAndCorruptStoredDomains(Backend backend) throws Exception {
        var fixtures = new CrsFixtures(work, 24);
        var db = target(backend, "deferred");
        String model = "DeferredCrs24";
        assertSchemaWithIdentityColumns(db, model, fixtures.deferredPoints(model), "--multi-srs", "--create-geom-index");
        assertCliSuccess(db, "import", model, "native", fixtures.deferredDelivery(model, "native"));
        assertCliSuccess(db, "import", model, "second", fixtures.deferredDelivery(model, "second"));
        Path source = fixtures.deferredDelivery(model, "invalid");
        Files.writeString(source, Files.readString(source).replace(
                "basket-old-invalid\" i:domains=\"" + model + ".Grid=" + model + ".OldGrid",
                "basket-old-invalid\" i:domains=\"" + model + ".Grid=" + model + ".UnknownGrid"));
        assertCliFailure(db, "import", model, "invalid", source, "permitted assignments");

        try (var sql = db.connect()) {
            assertDeferredStorage(db, sql, "native");
            assertDeferredStorage(db, sql, "second");
            String stations = db.table("station");
            String baskets = db.table("t_ili2db_basket");
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + stations));
            assertEquals("6", scalar(sql, "SELECT count(*) FROM " + baskets));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertHits(db, sql, "station", "location_2056", "2600001.625", "1200002.125", 2056, 2);
            String oldTable = db.postgis() ? "station" : "station_location_21781";
            assertHits(db, sql, oldTable, "location_21781", "600100.125", "200200.375", 21781, 2);

        }

        // The raw geometry mutation uses a schema without spatial index triggers.
        rejectCorruptDeferredExport(target(backend, "corrupt-domains"), fixtures, model);
    }

    private void rejectCorruptDeferredExport(Target db, CrsFixtures fixtures, String model) throws Exception {
        assertSchemaWithIdentityColumns(db, model, fixtures.deferredPoints(model), "--multi-srs");
        assertCliSuccess(db, "import", model, "native", fixtures.deferredDelivery(model, "native"));
        Path output = work.resolve("deferred.xtf");
        assertCliSuccess(db, "export", model, "native", output);
        fixtures.assertDeferred(output, model, "native");
        try (var sql = db.connect()) {
            String stations = db.table("station");
            String baskets = db.table("t_ili2db_basket");
            String where = " WHERE t_ili_tid='basket-current-native'";
            String assignment = model + ".Grid=" + model + ".CurrentGrid";
            for (String corrupt : List.of("", assignment + " " + assignment, model + ".Grid=" + model + ".UnknownGrid")) {
                execute(sql, "UPDATE " + baskets + " SET domains='" + corrupt + "'" + where);
                assertExportRejected(db, model, "native", output, "Invalid domain assignments");
            }
            execute(sql, "UPDATE " + baskets + " SET domains='" + assignment + "'" + where);
            String old = db.table(db.postgis() ? "station" : "station_location_21781");
            String first = " WHERE t_id=(SELECT t_id FROM " + stations + " WHERE t_ili_tid='current-native')";
            String second = " WHERE t_id=(SELECT t_id FROM " + stations + " WHERE t_ili_tid='old-native')";
            execute(sql, "UPDATE " + old + " SET location_21781=(SELECT location_21781 FROM " + old + second + ")" + first);
            assertExportRejected(db, model, "native", output, "inactive EPSG:21781");
        }
    }

    private Target target(Backend backend, String name) {
        return POSTGIS.target(work.resolve(name + ".gpkg"), backend);
    }

    private void mutateAlternate(Target db, AlternateCrsFixtures fixtures) throws Exception {
        AlternateCrsFixtures.createOwn(db, fixtures.modelFile(), "--create-geom-index");
        Path initial = fixtures.delivery("main");
        assertCliSuccess(db, "import", "Current23", "main", initial);
        assertCliSuccess(db, "import", "Current23", "other", fixtures.delivery("other"));

        String start = Files.readString(initial);
        Path updated = Files.writeString(work.resolve("alternate-update.xtf"), start.replace("600100.125", "610000.125"));
        Path switched = fixtures.switchTopic(initial);
        Path invalid = Files.writeString(work.resolve("alternate-replace-invalid.xtf"),
                Files.readString(switched).replace("<C1>2620000.125</C1>", "<C1>invalid</C1>"));
        String object = "SELECT t_id FROM " + db.table("station") + " WHERE t_ili_tid='old-main'";
        String basket = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='basket-old-main'";
        String oldTable = db.postgis() ? "station" : "station_location_21781";
        try (var sql = db.connect()) {
            String oldObject = scalar(sql, object);
            String oldBasket = scalar(sql, basket);
            assertCliSuccess(db, "update", "Current23", "main", updated);
            assertEquals(oldObject, scalar(sql, object));
            assertEquals(oldBasket, scalar(sql, basket));
            assertHits(db, sql, oldTable, "location_21781", "610000.125", "200200.375", 21781, 1);
            Path output = work.resolve("alternate-updated.xtf");
            assertCliSuccess(db, "export", "Current23", "main", output);
            assertEquals("Old23.Data", AlternateCrsFixtures.basket(output, "basket-old-main").getLocalName());
            AlternateCrsFixtures.assertCoordinate(AlternateCrsFixtures.basket(output, "basket-old-main"), "610000.125", "200200.375");

            var denied = cli(db, "update", "Current23", "main", switched, CancellationToken.none());
            assertNotEquals(0, denied.code(), denied::diagnostics);
            assertTrue(denied.diagnostics().contains("another dataset or topic"), denied::diagnostics);
            var rollback = cli(db, "replace", "Current23", "main", invalid, CancellationToken.none());
            assertNotEquals(0, rollback.code(), rollback::diagnostics);
            assertEquals(oldObject, scalar(sql, object));
            assertEquals(oldBasket, scalar(sql, basket));
            assertHits(db, sql, oldTable, "location_21781", "610000.125", "200200.375", 21781, 1);
            assertHits(db, sql, "station", "location_2056", "2620000.125", "1220000.375", 2056, 0);

            assertCliSuccess(db, "replace", "Current23", "main", switched);
            assertNotEquals(oldObject, scalar(sql, object));
            assertHits(db, sql, oldTable, "location_21781", "610000.125", "200200.375", 21781, 0);
            assertHits(db, sql, "station", "location_2056", "2620000.125", "1220000.375", 2056, 1);
            output = work.resolve("alternate-replaced.xtf");
            assertCliSuccess(db, "export", "Current23", "main", output);
            AlternateCrsFixtures.assertSwitchedTopic(output, "main");

            assertCliSuccess(db, "delete", "Current23", "main", null);
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertHits(db, sql, "station", "location_2056", "2620000.125", "1220000.375", 2056, 0);
            assertHits(db, sql, "station", "location_2056", "2600001.625", "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "location_21781", "600100.125", "200200.375", 21781, 1);
        }
    }

    private void mutateDeferred(Target db, CrsFixtures fixtures) throws Exception {
        String model = "DeferredCrs24";
        assertSchemaWithIdentityColumns(db, model, fixtures.deferredPoints(model), "--multi-srs", "--create-geom-index");
        Path original = fixtures.deferredDelivery(model, "main");
        assertCliSuccess(db, "import", model, "main", original);
        assertCliSuccess(db, "import", model, "other", fixtures.deferredDelivery(model, "other"));

        String start = Files.readString(original);
        Path updated = Files.writeString(work.resolve("domains-update.xtf"), start.replace("2600001.625", "2600100.625"));
        Path switched = fixtures.switchDeferredDomain(original);
        Path invalid = Files.writeString(work.resolve("domains-replace-invalid.xtf"), Files.readString(switched).replace(
                "basket-old-main\" i:domains=\"" + model + ".Grid=" + model + ".OldGrid",
                "basket-old-main\" i:domains=\"" + model + ".Grid=" + model + ".MissingGrid"));
        String station = db.table("station");
        String oldTable = db.postgis() ? "station" : "station_location_21781";
        String objectId = "SELECT t_id FROM " + station + " WHERE t_ili_tid='current-main'";
        String basketId = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='basket-current-main'";
        try (var sql = db.connect()) {
            String objectBefore = scalar(sql, objectId);
            String basketBefore = scalar(sql, basketId);
            assertCliSuccess(db, "update", model, "main", updated);
            assertEquals(objectBefore, scalar(sql, objectId));
            assertEquals(basketBefore, scalar(sql, basketId));
            assertHits(db, sql, "station", "location_2056", "2600100.625", "1200002.125", 2056, 1);
            Path output = work.resolve("domains-updated.xtf");
            assertCliSuccess(db, "export", model, "main", output);
            CrsFixtures.assertPoint(fixtures.object(fixtures.basket(output, "current-main"), "current-main"), "Location", "2600100.625", "1200002.125");

            var rejected = cli(db, "update", model, "main", switched, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("Cannot change domain assignments"), rejected::diagnostics);
            var rollback = cli(db, "replace", model, "main", invalid, CancellationToken.none());
            assertNotEquals(0, rollback.code(), rollback::diagnostics);
            assertTrue(rollback.diagnostics().contains("permitted assignments"), rollback::diagnostics);
            assertEquals(objectBefore, scalar(sql, objectId));
            assertEquals(basketBefore, scalar(sql, basketId));
            assertHits(db, sql, "station", "location_2056", "2600100.625", "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "location_21781", "620000.125", "220000.375", 21781, 0);

            assertCliSuccess(db, "replace", model, "main", switched);
            assertNotEquals(objectBefore, scalar(sql, objectId));
            assertHits(db, sql, "station", "location_2056", "2600100.625", "1200002.125", 2056, 0);
            assertHits(db, sql, oldTable, "location_21781", "620000.125", "220000.375", 21781, 1);
            output = work.resolve("domains-replaced.xtf");
            assertCliSuccess(db, "export", model, "main", output);
            fixtures.assertSwitchedDomain(output, model, "main");

            assertCliSuccess(db, "delete", model, "main", null);
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertHits(db, sql, oldTable, "location_21781", "620000.125", "220000.375", 21781, 0);
            assertHits(db, sql, oldTable, "location_21781", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, "station", "location_2056", "2600001.625", "1200002.125", 2056, 1);
        }
    }
}
