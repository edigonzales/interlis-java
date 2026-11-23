package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalRows;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalTopicRows;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalTopicXtf;
import static guru.interlis.db.ExternalReferenceFixtures.assertExternalXtf;
import static guru.interlis.db.ExternalReferenceFixtures.assertReferenceDatasetRows;
import static guru.interlis.db.ExternalReferenceFixtures.assertReferenceDatasetXtf;
import static guru.interlis.db.ExternalReferenceFixtures.assertSplitTopicLinks;
import static guru.interlis.db.ExternalReferenceFixtures.assertSplitTopicLinksXtf;
import static guru.interlis.db.ExternalReferenceFixtures.catalogInput;
import static guru.interlis.db.ExternalReferenceFixtures.modelText;
import static guru.interlis.db.ExternalReferenceFixtures.splitInput;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExternalReferenceStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void basketAndDatasetReferences(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("external.gpkg"), backend);
        String model = "DbExternal" + version;
        String fixture = modelText(model + ".xtf");
        createSchema(db, model);
        assertCliSuccess(db, "import", model, "external", Files.writeString(work.resolve("external.xtf"), fixture));
        assertCliSuccess(db, "import", model, "own", Files.writeString(work.resolve("own.xtf"), fixture.replace("external-", "own-")));
        Path ownOutput = work.resolve("external-out.xtf");
        try (var sql = db.connect()) {
            assertExternalRows(db, sql, version, "external");
            assertExternalRows(db, sql, version, "own");
            assertCliSuccess(db, "export", model, "external", ownOutput);
            assertExternalXtf(ownOutput, model, version, "external", true);
            assertBasketFailures(db, sql, model, version, fixture, ownOutput);
            assertDatasetReferences(db, sql, model, version);
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void topicAndSplitDatasetReferences(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("topics.gpkg"), backend);
        String model = "DbExternalTopics" + version;
        String fixture = modelText(model + ".xtf");
        createSchema(db, model);
        assertCliSuccess(db, "import", model, "fixture", Files.writeString(work.resolve("topics.xtf"), fixture));
        Path output = work.resolve("topics-out.xtf");
        assertCliSuccess(db, "export", model, "fixture", output);
        try (var sql = db.connect()) {
            assertExternalTopicRows(db, sql, model, version, "fixture");
            assertExternalTopicXtf(output, model, version, "fixture", true);
            assertWrongFlatTargetTopicRejected(db, sql, model, output);
            assertSplitDatasetReferences(db, sql, model, version, fixture);
        }
    }

    private void createSchema(Target db, String model) throws IOException {
        assertSchemaWithIdentityColumns(db, model, Files.writeString(work.resolve(model + ".ili"), modelText(model + ".ili")));
    }

    private void assertBasketFailures(Target db, Connection sql, String model, int version, String fixture, Path ownOutput)
            throws Exception {
        String wrongDataset = fixture.replace("external-", "failed-")
                .replace((version == 23 ? "REF" : "ref") + "=\"failed-late\"",
                        (version == 23 ? "REF" : "ref") + "=\"absent-late\"");
        assertExternalRollback(db, sql, model, wrongDataset, "absent-late", "dataset");
        if (version == 23) {
            String wrongBid = fixture.replace("external-", "failed-")
                    .replace("REF=\"failed-late\" BID=\"failed-last\"", "REF=\"failed-late\" BID=\"failed-first\"");
            assertExternalRollback(db, sql, model, wrongBid, "Wrong target BID", "failed-late");
        }
        String baseSite = version == 23 ? "BaseSite REF" : "BaseSite ili:ref";
        String wrongRoleTarget = fixture.replace("external-", "failed-")
                .replace(baseSite + "=\"failed-late\"", baseSite + "=\"failed-sibling\"");
        assertExternalRollback(db, sql, model, wrongRoleTarget, "ReportPlace.BaseSite", "failed-sibling");
        String pairObject = version == 23 ? "PairObject REF" : "PairObject ili:ref";
        String wrongLocalBasket = fixture.replace("external-", "failed-")
                .replace(pairObject + "=\"failed-backward\"", pairObject + "=\"failed-forward\"");
        assertExternalRollback(db, sql, model, wrongLocalBasket, "ExternalPair.PairObject", "failed-forward", "basket");

        String where = " where note='forward' and t_basket=(select t_basket from " + db.table("report")
                + " where t_ili_tid='external-forward')";
        String detail = db.table("detail");
        String target = scalar(sql, "select targetref from " + detail + where);
        String foreign = scalar(sql, "select t_id from " + db.table("targetroot") + " where t_ili_tid='own-sibling'");
        try {
            execute(sql, "update " + detail + " set targetref=" + foreign + where);
            assertExportRejected(db, model, "external", ownOutput, "same-dataset reference target TID", "Detail.TargetRef");
        } finally {
            execute(sql, "update " + detail + " set targetref=" + target + where);
        }
        String review = db.table("externalreview");
        execute(sql, "insert into " + review + " (t_id,t_basket,reviewowner,reviewsite,reviewmemo) "
                + "select -1,t_basket,reviewowner,reviewsite,reviewmemo from " + review
                + " where reviewowner=(select t_id from " + db.table("report") + " where t_ili_tid='external-forward')");
        try {
            assertExportRejected(db, model, "external", ownOutput, "Multiple link rows", "ExternalReview.ReviewSite");
        } finally {
            execute(sql, "delete from " + review + " where t_id=-1");
        }
    }

    private void assertDatasetReferences(Target db, Connection sql, String model, int version) throws Exception {
        var inputs = catalogInput(work, model, version);
        assertCliSuccess(db, "import", model, "catalog", inputs.target());
        assertCliSuccess(db, "import", model, "catalog-source", inputs.source());
        assertReferenceDatasetRows(db, sql, version, "report-id");
        Path output = work.resolve("catalog-out.xtf");
        assertCliSuccess(db, "export", model, "catalog-source", output);
        assertReferenceDatasetXtf(output, version, true);
        Path replay = Files.writeString(work.resolve("catalog-replay.xtf"),
                Files.readString(output).replace("report-id", "replay-report").replace("catalog-source", "replay-basket"));
        assertCliSuccess(db, "import", model, "catalog-replay", replay);
        assertReferenceDatasetRows(db, sql, version, "replay-report");
        // Match the external reference form emitted by ili2db 5.5.2 in XTF 2.3.
        Path referenceReplay = Files.writeString(work.resolve("catalog-reference-replay.xtf"),
                Files.readString(replay).replace("replay-report", "reference-report")
                        .replace("replay-basket", "reference-basket").replace(" BID=\"catalog-basket\"", ""));
        assertCliSuccess(db, "import", model, "catalog-reference-replay", referenceReplay);
        assertReferenceDatasetRows(db, sql, version, "reference-report");
        if (version == 23) {
            Path wrongBid = Files.writeString(work.resolve("catalog-bad-bid.xtf"),
                    Files.readString(replay).replace("BID=\"catalog-basket\"", "BID=\"wrong-bid\""));
            assertReferenceDatasetRollback(db, sql, model, wrongBid, "catalog-leaf");
        }
        assertCliSuccess(db, "import", model, "other-catalog", inputs.duplicate());
        assertReferenceDatasetRollback(db, sql, model, referenceReplay, "Ambiguous stored reference target catalog-leaf");
        if (version == 23) {
            assertCliSuccess(db, "export", model, "catalog-source", output);
            assertReferenceDatasetXtf(output, version, true);
        } else {
            assertExportRejected(db, model, "catalog-source", output, "Ambiguous stored reference target catalog-leaf");
        }
    }

    private void assertSplitDatasetReferences(Target db, Connection sql, String model, int version, String fixture) throws Exception {
        var inputs = splitInput(work, model, version, fixture);
        assertCliSuccess(db, "import", model, "split-catalog", inputs.target());
        assertCliSuccess(db, "import", model, "split", inputs.source());
        assertSplitTopicLinks(db, sql, "split");
        assertCliSuccess(db, "import", model, "split-own", inputs.ownSource());
        assertSplitTopicLinks(db, sql, "split-own");
        Path output = work.resolve("topics-split-out.xtf");
        assertCliSuccess(db, "export", model, "split-own", output);
        assertSplitTopicLinksXtf(output, model, version, "split-own", true);
        String role = version == 23 ? "MatchedRecord REF" : "MatchedRecord i:ref";
        Path broken = Files.writeString(work.resolve("topics-split-local-error.xtf"), Files.readString(inputs.ownSource())
                .replace(role + "=\"split-own-survey\"", role + "=\"split-survey\""));
        var rejected = cli(db, "import", model, "split-failed", broken, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("MatchedRecord") && rejected.diagnostics().contains("basket"), rejected::diagnostics);
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='split-failed'"));
    }

    private void assertExternalRollback(Target db, Connection sql, String model, String fixture, String... messages)
            throws Exception {
        var tables = List.of("t_ili2db_dataset", "t_ili2db_basket", "report", "detail", "targetroot", "targetleaf",
                "externalpair", "externalreview");
        var counts = new ArrayList<String>();
        for (String table : tables) counts.add(scalar(sql, "select count(*) from " + db.table(table)));
        Path input = Files.writeString(work.resolve("external-failed.xtf"), fixture);
        var result = cli(db, "import", model, "failed", input, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        for (String message : messages) assertTrue(result.diagnostics().contains(message), result::diagnostics);
        for (int index = 0; index < tables.size(); index++) {
            assertEquals(counts.get(index), scalar(sql, "select count(*) from " + db.table(tables.get(index))));
        }
    }

    private static void assertReferenceDatasetRollback(Target db, Connection sql, String model, Path input,
                                                       String expected) throws SQLException {
        var result = cli(db, "import", model, "failed-catalog", input, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains(expected), result::diagnostics);
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='failed-catalog'"));
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("report") + " where t_ili_tid='replay-report'"));
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("report") + " where t_ili_tid='reference-report'"));
    }

    private static void assertWrongFlatTargetTopicRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String update = "update " + db.table("landmark") + " set t_basket=(select t_id from "
                + db.table("t_ili2db_basket") + " where t_ili_tid='fixture-%s') where t_ili_tid='fixture-landmark'";
        execute(sql, update.formatted("surveys"));
        try {
            assertExportRejected(db, model, "fixture", output, "SiteAnnotation.FlatSite");
        } finally {
            execute(sql, update.formatted("catalog"));
        }
    }
}
