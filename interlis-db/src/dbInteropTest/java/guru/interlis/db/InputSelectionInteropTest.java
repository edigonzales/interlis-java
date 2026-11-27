package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class InputSelectionInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageImport23() throws Exception { exchangeImport(23, null); }
    @Test void geoPackageImport24() throws Exception { exchangeImport(24, null); }
    @Test void postgisImport23() throws Exception { exchangeImport(23, POSTGIS.container()); }
    @Test void postgisImport24() throws Exception { exchangeImport(24, POSTGIS.container()); }

    private void exchangeImport(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        fixture.addTopics();
        var legacy = new TestDatabase(work, postgres, "input_import_legacy", "v" + version + "_");
        var own = legacy.peer("input_import_own");
        var reference = new ReferenceTool(work, legacy.postgis());
        schema(reference, legacy, work, fixture.model, "newClass", "--createImportTabs");
        assertSchemaWithIdentityColumns(own, fixture.model, fixture.source, "--create-import-tabs");

        String topic = fixture.model + ".Data";
        Path combined = importInput(fixture, "combined");
        referenceWrite(reference, legacy, "import", "combined", combined,
                "--baskets", "combined-left;combined-district", "--topics", topic);
        assertCliSuccess(own, "import", fixture.model, "combined", combined,
                "--basket", "combined-left", "--basket", "combined-district", "--topic", topic);

        Path byBid = importInput(fixture, "bid");
        referenceWrite(reference, legacy, "import", "by_bid", byBid, "--baskets", "bid-left;bid-district");
        assertCliSuccess(own, "import", fixture.model, "by_bid", byBid, "--basket", "bid-left", "--basket", "bid-district");

        Path byTopic = importInput(fixture, "topic");
        referenceWrite(reference, legacy, "import", "by_topic", byTopic, "--topics", topic);
        assertCliSuccess(own, "import", fixture.model, "by_topic", byTopic, "--topic", topic);

        Path noMatch = importInput(fixture, "empty");
        referenceWrite(reference, legacy, "import", "empty", noMatch,
                "--baskets", "empty-district", "--topics", topic);
        assertCliSuccess(own, "import", fixture.model, "empty", noMatch, "--basket", "empty-district", "--topic", topic);

        var selected = Set.of("Data:combined-left:combined-a:Left");
        for (var db : List.of(legacy, own)) {
            try (var sql = db.connect()) {
                assertSelection(db, sql, fixture.model, "combined", selected, 1, Set.of("combined-left:1:1"));
                assertSelection(db, sql, fixture.model, "by_bid",
                        Set.of("Data:bid-left:bid-a:Left", "District:bid-district:bid-c:District"),
                        1, Set.of("bid-left:1:1", "bid-district:1:1"));
                assertSelection(db, sql, fixture.model, "by_topic",
                        Set.of("Data:topic-left:topic-a:Left", "Data:topic-right:topic-b:Right"),
                        1, Set.of("topic-left:1:1", "topic-right:1:1"));
                assertSelection(db, sql, fixture.model, "empty", Set.of(), 1, Set.of());
            }
        }

        var fromReference = work.resolve("reference-import-native-export.xtf");
        var fromOwn = work.resolve("native-import-reference-export.xtf");
        assertCliSuccess(legacy, "export", fixture.model, "combined", fromReference);
        exportDataset(reference, own, "combined", fromOwn);
        assertEquals(selected, Set.copyOf(fixture.exportedAssets(fromReference)));
        assertEquals(selected, Set.copyOf(fixture.exportedAssets(fromOwn)));
    }

    private static Path importInput(ExportSelectionFixtures fixture, String prefix) throws Exception {
        return fixture.delivery(prefix,
                fixture.basket("Data", prefix + "-left", fixture.asset(prefix + "-a", "Left", false)),
                fixture.basket("Data", prefix + "-right", fixture.asset(prefix + "-b", "Right", false)),
                fixture.basket("District", prefix + "-district", fixture.asset(prefix + "-c", "District", false)));
    }

    private static void referenceWrite(ReferenceTool reference, TestDatabase db, String operation,
                                       String dataset, Path input, String... filters) throws Exception {
        var args = new ArrayList<>(List.of("--" + operation, "--dataset", dataset, "--importBid", "--importTid"));
        args.addAll(List.of(filters));
        args.add(input.toString());
        reference.run(db, "%ILI_FROM_DB", args.toArray(String[]::new));
    }

    private static void assertSelection(TestDatabase db, Connection sql, String model, String dataset,
                                        Set<String> assets, int runs, Set<String> stats) throws Exception {
        String datasets = db.table("t_ili2db_dataset");
        String baskets = db.table("t_ili2db_basket");
        String imports = db.table("t_ili2db_import");
        String statsTable = db.table("t_ili2db_import_basket");
        String where = " WHERE d.datasetname='" + dataset + "'";
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + datasets + " d" + where), dataset);
        assertEquals(Integer.toString(stats.size()), scalar(sql, "SELECT count(*) FROM " + datasets + " d JOIN "
                + baskets + " b ON b.dataset=d.t_id" + where), dataset);
        assertEquals(assets.stream().map(row -> model + "." + row).collect(Collectors.toSet()),
                catalogKeys(sql, """
                        SELECT b.topic || ':' || b.t_ili_tid || ':' || a.t_ili_tid || ':' || a.label
                        FROM %s d JOIN %s b ON d.t_id=b.dataset JOIN %s a ON a.t_basket=b.t_id
                        """.formatted(datasets, baskets, db.table("asset")) + where), dataset);
        assertEquals(Integer.toString(runs), scalar(sql, "SELECT count(*) FROM " + datasets + " d JOIN "
                + imports + " r ON r.dataset=d.t_id" + where), dataset);
        assertEquals(stats, catalogKeys(sql, """
                SELECT b.t_ili_tid || ':' || count(*) || ':' || sum(s.objectcount)
                FROM %s d JOIN %s r ON r.dataset=d.t_id JOIN %s s ON s.importrun=r.t_id JOIN %s b ON b.t_id=s.basket
                """.formatted(datasets, imports, statsTable, baskets) + where + " GROUP BY b.t_ili_tid"), dataset);
    }
}
