package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class TopicInheritanceInteropTest {
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
        var fixture = new TopicInheritanceFixtures(work, version);
        var sources = fixture.copySources();
        var baseline = new TestDatabase(work, postgres, "topic_reference", "v" + version + "_");
        var own = baseline.peer("topic_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, String.join(";", fixture.models), "newClass");
        fixture.mixedSchema(own, sources);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        for (var source : sources) Files.delete(source);

        var legacy = fixture.mixedInput();
        var nativeInput = fixture.nativeInput(legacy);
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", legacy);
            var output = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.child, "legacy", output, fixture.modelsAfterChild());
            fixture.assertMixedDelivery(output, "", "text", false);

            assertCliSuccess(db, "import", fixture.child, "native", nativeInput, fixture.modelsAfterChild());
            output = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", output);
            fixture.assertMixedDelivery(output, "native-", "updated", false);
            assertStoredValues(db, fixture);
        }

        exchangeBaseProjection(fixture, reference, baseline, own);

        var updated = fixture.updateRegion(work.resolve("region" + version + ".xtf"));
        assertCliSuccess(own, "update", fixture.child, "legacy", updated);
        var output = work.resolve("topic-updated-reference.xtf");
        exportDataset(reference, own, "legacy", output);
        fixture.assertMixedDelivery(output, "", "text", true);
    }

    private void exchangeBaseProjection(TopicInheritanceFixtures fixture, ReferenceTool reference,
                                        TestDatabase baseline, TestDatabase own) throws Exception {
        var nativeOutput = work.resolve("projected-native.xtf");
        assertCliSuccess(baseline, "export", fixture.child, null, nativeOutput, "--model", fixture.flat,
                "--basket", "region-b", "--basket", "flat-b", "--export-model", fixture.town);
        fixture.assertBaseDelivery(nativeOutput, "", true);

        var referenceOutput = work.resolve("projected-reference.xtf");
        reference.run(own, "%ILI_FROM_DB", "--export", "--baskets", "native-region-b;native-flat-b",
                "--exportModels", fixture.town, "--exportTid", referenceOutput.toString());
        fixture.assertBaseDelivery(referenceOutput, "native-", true);

        var source = fixture.source(fixture.town);
        var referenceTarget = baseline.peer("base_reference");
        var nativeTarget = own.peer("base_native");
        schema(reference, referenceTarget, work, fixture.town, "newClass");
        assertSchemaWithIdentityColumns(nativeTarget, fixture.town, source);
        importDataset(reference, referenceTarget, "projected", nativeOutput);
        assertCliSuccess(nativeTarget, "import", fixture.town, "projected", referenceOutput);
        assertProjectedRows(referenceTarget, fixture, "");
        assertProjectedRows(nativeTarget, fixture, "native-");
    }

    private static void assertProjectedRows(TestDatabase db, TopicInheritanceFixtures fixture, String prefix) throws Exception {
        try (var sql = db.connect()) {
            String baskets = db.table("t_ili2db_basket");
            assertEquals(Set.of(prefix + "region-b", prefix + "flat-b"),
                    catalogKeys(sql, "SELECT t_ili_tid FROM " + baskets + " WHERE topic='" + fixture.town + ".Core'"));
            assertEquals(prefix + "region-s:base", scalar(sql, "SELECT t_ili_tid || ':' || memo FROM " + db.table("station")));
            CrsFixtures.assertStoredEpsg(db, sql, "station", "center", prefix + "region-s", 2056);
            var entries = db.table("entry");
            var stations = db.table("station");
            assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + entries + " e JOIN " + stations
                    + " s ON s.t_id=e.station_detail AND s.t_basket=e.t_basket WHERE e.note IS NULL"));
            assertEquals(Set.of("0:alpha", "1:bravo"), catalogKeys(sql, "SELECT e.t_seq || ':' || e.note FROM "
                    + entries + " e JOIN " + stations + " s ON s.t_id=e.station_history AND s.t_basket=e.t_basket"));
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + entries + " WHERE marker_detail IS NOT NULL"));
            assertEquals(Set.of(prefix + "region-m:inherited", prefix + "flat-m:inherited"),
                    catalogKeys(sql, "SELECT t_ili_tid || ':' || memo FROM " + db.table("marker")));
        }
    }

    private static void assertStoredValues(TestDatabase db, TopicInheritanceFixtures fixture) throws Exception {
        var marker = tableFor(db, fixture.town + ".Core.Marker");
        var desk = tableFor(db, fixture.flat + ".Region.Desk");
        var station = tableFor(db, fixture.town + ".Core.Station");
        var local = tableFor(db, fixture.child + ".Region.LocalStation");
        var item = tableFor(db, fixture.abstractTown + ".Core.Item");
        var refined = tableFor(db, fixture.refined + ".Region.Item");
        assertNotEquals(item, refined);
        String baskets = db.table("t_ili2db_basket");
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_classname")
                    + " WHERE iliname LIKE '" + fixture.town + ".Other.%'"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table(marker) + " m JOIN "
                    + db.table(desk) + " d ON d.t_basket=m.t_basket JOIN " + baskets
                    + " b ON b.t_id=m.t_basket WHERE b.topic='" + fixture.flat + ".Region' AND m.memo='inherited'"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table(station) + " s JOIN "
                    + db.table(local) + " l ON l.t_id=s.t_id AND l.t_basket=s.t_basket JOIN " + baskets
                    + " b ON b.t_id=s.t_basket WHERE b.topic='" + fixture.child + ".Region' AND s.t_type='" + local
                    + "' AND s.memo='base' AND l.localmemo='child'"));
            String attrs = db.table("t_ili2db_attrname");
            assertEquals("label", scalar(sql, "SELECT sqlname FROM " + attrs + " WHERE iliname='"
                    + fixture.abstractTown + ".Core.Item.Label' AND colowner='" + item + "'"));
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + attrs + " WHERE iliname='"
                    + fixture.refined + ".Region.Item.Label'"));
            assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + db.table(item) + " i JOIN "
                    + db.table(refined) + " r ON r.t_id=i.t_id AND r.t_basket=i.t_basket"
                    + " WHERE i.t_ili_tid='native-refine-i' AND i.t_type='" + refined
                    + "' AND i.label='updated' AND i.asize=4 AND r.extra='added'"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("note") + " n JOIN "
                    + db.table("delivery") + " d ON d.t_id=n.delivery_details AND d.t_basket=n.t_basket JOIN "
                    + db.table("depot") + " p ON p.t_id=n.receivingdepot AND p.t_id=d.deliveredat AND p.t_basket=n.t_basket JOIN "
                    + db.table("crate") + " c ON c.stockedat=p.t_id AND c.t_basket=p.t_basket JOIN "
                    + db.table("registerentry") + " e ON e.t_id=n.entry AND e.t_basket<>n.t_basket"));
        }
    }
}
