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
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExportSelectionStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void basketExportBoundsOwnerChecksAndRequiresUniqueBids(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("basket-checks.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        var input = fixture.delivery("primary",
                fixture.basket("Data", "keep", fixture.asset("keep-asset", "keep", false)),
                fixture.basket("Data", "other", fixture.asset("other-asset", "other", true)));
        assertCliSuccess(db, "import", fixture.model, "primary", input);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("note") + " SET asset_detail=NULL");
        }
        var output = work.resolve("selected.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", "keep");
        assertEquals(List.of("Data:keep:keep-asset:keep"), fixture.exportedAssets(output));

        assertRejected(db, fixture.model, output, "Expected exactly one structure owner", "--basket", "other");
        assertRejected(db, fixture.model, output, "Basket does not exist: missing",
                "--basket", "keep", "--basket", "missing");
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("note") + " SET asset_detail=(SELECT t_id FROM " + db.table("asset")
                    + " WHERE t_ili_tid='other-asset'), t_basket=(SELECT t_id FROM " + db.table("t_ili2db_basket")
                    + " WHERE t_ili_tid='keep')");
        }
        assertRejected(db, fixture.model, output, "owner are in different baskets", "--basket", "other");

        var copy = fixture.delivery("repeat-bid", fixture.basket("Data", "keep", fixture.asset("copy-asset", "copy", false)));
        assertCliSuccess(db, "import", fixture.model, "duplicate", copy);
        assertRejected(db, fixture.model, output, "Ambiguous basket BID: keep", "--basket", "keep");
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void omittedTargetsUseExternalResolutionAcrossTheActualSelection(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("basket-references.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        var input = fixture.delivery("references",
                fixture.basket("Data", "source", fixture.survey("survey", "destination", "target")),
                fixture.basket("Data", "target", fixture.place("destination")));
        assertCliSuccess(db, "import", fixture.model, "references", input);
        var collision = fixture.delivery("collision", fixture.basket("Data", "collision", fixture.asset("destination", "other", false)));
        assertCliSuccess(db, "import", fixture.model, "collision", collision);

        var output = work.resolve("selected.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", "source");
        var baskets = fixture.baskets(output);
        assertEquals(List.of("source"), baskets.stream().map(basket -> id(basket, version, "bid")).toList());
        var survey = children(baskets.getFirst()).getFirst();
        assertEquals("survey", id(survey, version, "tid"));
        var reference = child(children(child(survey, "PointerValue")).getFirst(), "Destination");
        assertEquals("destination", id(reference, version, "ref"));
        if (version == 23) assertEquals("target", id(reference, version, "bid"));
        assertRejected(db, fixture.model, output, "outside the selected baskets",
                "--basket", "source", "--basket", "collision");

        var duplicate = fixture.delivery("duplicate", fixture.basket("Data", "target", fixture.place("destination")));
        assertCliSuccess(db, "import", fixture.model, "other-target", duplicate);
        assertRejected(db, fixture.model, output, "Ambiguous stored reference target destination", "--basket", "source");
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void topicFiltersQualifiedBasketsAcrossDatasets(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        fixture.addTopics();
        var db = POSTGIS.target(work.resolve("topic-selection.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        var first = fixture.delivery("one",
                fixture.basket("Data", "first", fixture.asset("a", "original", false)),
                fixture.basket("District", "derived", fixture.asset("b", "derived", false)));
        assertCliSuccess(db, "import", fixture.model, "one", first);
        assertCliSuccess(db, "import", fixture.model, "two", fixture.delivery("two",
                fixture.basket("Data", "second", fixture.asset("c", "second", false))));
        String data = fixture.model + ".Data";
        String district = fixture.model + ".District";
        var output = work.resolve("topics.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--topic", data);
        assertEquals(List.of("Data:first:a:original", "Data:second:c:second"), fixture.exportedAssets(output));
        assertCliSuccess(db, "export", fixture.model, null, output, "--topic", district, "--topic", data);
        assertEquals(List.of("Data:first:a:original", "District:derived:b:derived", "Data:second:c:second"),
                fixture.exportedAssets(output));
        var published = Files.readAllBytes(output);
        var mixedFilters = cli(db, "export", fixture.model, null, output, CancellationToken.none(),
                "--topic", data, "--basket", "first");
        assertEquals(2, mixedFilters.code(), mixedFilters::diagnostics);
        assertTrue(mixedFilters.diagnostics().contains("Choose one of --dataset, --basket or --topic"), mixedFilters::diagnostics);
        var mixedDataset = cli(db, "export", fixture.model, "one", output, CancellationToken.none(), "--topic", data);
        assertEquals(2, mixedDataset.code(), mixedDataset::diagnostics);
        assertTrue(mixedDataset.diagnostics().contains("Choose one of --dataset, --basket or --topic"), mixedDataset::diagnostics);
        var replaceFilter = cli(db, "replace", fixture.model, "one", first, CancellationToken.none(), "--topic", data);
        assertEquals(2, replaceFilter.code(), replaceFilter::diagnostics);
        assertTrue(replaceFilter.diagnostics().contains("--topic is only valid with export"), replaceFilter::diagnostics);
        assertArrayEquals(published, Files.readAllBytes(output));
        assertRejected(db, fixture.model, output, "Unknown topic", "--topic", "Data");
        assertRejected(db, fixture.model, output, "Unknown topic", "--topic", fixture.model + ".Absent");

        assertCliSuccess(db, "import", fixture.model, "collision", fixture.delivery("duplicate-tid",
                fixture.basket("Data", "duplicate-tid", fixture.asset("a", "collision", false))));
        assertRejected(db, fixture.model, output, "Duplicate object ID a", "--topic", data);
        assertCliSuccess(db, "import", fixture.model, "reused-bid", fixture.delivery("duplicate-bid",
                fixture.basket("District", "derived", fixture.asset("new", "collision", false))));
        assertRejected(db, fixture.model, output, "Duplicate basket ID derived", "--topic", district);
        assertCliSuccess(db, "export", fixture.model, null, output, "--topic", fixture.model + ".EmptyTopic");
        assertTrue(fixture.baskets(output).isEmpty());
        assertCliSuccess(db, "import", fixture.model, "empty", output);
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void modelExportAndUnnamedImportRespectSelectionAndTransactions(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var other = new ProfileFixtures(work, version);
        Path foreign = other.source("ForeignModel", "CLASS Elsewhere = END Elsewhere;");
        var db = POSTGIS.target(work.resolve("model-selection.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--model", "ForeignModel", "--model-file",
                foreign.toString(), "--create-dataset-col");
        assertCliSuccess(db, "import", "ForeignModel", "foreign", other.data("ForeignModel", "foreign",
                other.object("ForeignModel", "Elsewhere", "elsewhere", "")));
        Path output = Files.writeString(work.resolve("selected.xtf"), "preserve");
        assertRejected(db, fixture.model, output, "No baskets in the selected models: " + fixture.model);

        assertCliSuccess(db, "import", fixture.model, null, fixture.delivery("drop in",
                fixture.basket("Data", "first", fixture.asset("a", "one", false))));
        assertCliSuccess(db, "import", fixture.model, null, fixture.delivery("drop in",
                fixture.basket("Data", "second", fixture.asset("b", "two", false))));
        assertCliSuccess(db, "export", fixture.model, null, output);
        assertEquals(List.of("Data:first:a:one", "Data:second:b:two"), fixture.exportedAssets(output));

        Path late = fixture.delivery("late", fixture.basket("Data", "late", fixture.asset("c", "rollback", true)));
        Files.writeString(late, Files.readString(late) + "<trailing/>");
        var result = cli(db, "import", fixture.model, null, late, CancellationToken.none());
        assertEquals(1, result.code(), result::diagnostics);
        try (var sql = db.connect()) {
            String dataset = db.table("t_ili2db_dataset");
            String basket = db.table("t_ili2db_basket");
            assertEquals(Set.of("first", "second"), catalogKeys(sql, """
                    SELECT b.t_ili_tid FROM %s b JOIN %s d ON d.t_id=b.dataset
                    JOIN %s a ON a.t_basket=b.t_id
                    WHERE d.datasetname='dropin.xtf-' || d.t_id AND a.t_datasetname=d.datasetname
                    """.formatted(basket, dataset, db.table("asset"))));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + dataset));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + basket));
            assertEquals(Set.of("a", "b"), catalogKeys(sql, "SELECT t_ili_tid FROM " + db.table("asset")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("note")));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void syntheticBidIsExportOnlyAndPreservesAtomicity(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("synthetic-bid.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        assertCliSuccess(db, "import", fixture.model, "target", fixture.delivery("target",
                fixture.basket("Data", "forgotten", fixture.place("destination"))));
        String generated;
        try (var sql = db.connect()) {
            String baskets = db.table("t_ili2db_basket");
            execute(sql, "UPDATE " + baskets + " SET t_ili_tid='' WHERE t_ili_tid='forgotten'");
            generated = scalar(sql, "SELECT t_id FROM " + baskets + " WHERE t_ili_tid=''");
        }
        assertCliSuccess(db, "import", fixture.model, "source", fixture.delivery("source",
                fixture.basket("Data", "source", fixture.survey("survey", "destination", null))));
        Path output = work.resolve("synthesized.xtf");
        assertCliSuccess(db, "export", fixture.model, "target", output);
        assertEquals(generated, id(fixture.baskets(output).getFirst(), version, "bid"));
        assertRejected(db, fixture.model, output, "Basket does not exist: " + generated, "--basket", generated);
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", "source");
        assertEquals("", id(fixture.surveyReference(fixture.baskets(output).getFirst()), version, "bid"));

        assertCliSuccess(db, "import", fixture.model, "collision", fixture.delivery("duplicate-target",
                fixture.basket("Data", "duplicate", fixture.place("destination"))));
        assertRejected(db, fixture.model, output, "Ambiguous stored reference target destination", "--basket", "source");
        assertCliSuccess(db, "delete", fixture.model, "collision", null);

        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_basket") + " SET t_ili_tid=" + generated + " WHERE t_ili_tid='source'");
        }
        assertRejected(db, fixture.model, output, "Duplicate basket ID " + generated);
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void missingStableBasketOidPreservesPublishedFile(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        Files.writeString(fixture.source, Files.readString(fixture.source)
                .replace("TOPIC Data =", "TOPIC Data = BASKET OID AS INTERLIS.UUIDOID;"));
        var db = POSTGIS.target(work.resolve("missing-oid.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        var input = fixture.delivery("stable", fixture.basket("Data", "832615d2-21be-4fbc-b7d4-e53ccf0ccaaf",
                fixture.asset("a", "label", false)));
        assertCliSuccess(db, "import", fixture.model, "stable", input);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_basket") + " SET t_ili_tid=NULL");
        }
        Path output = Files.writeString(work.resolve("published.xtf"), "preserve");
        assertRejected(db, fixture.model, output, "Missing stable basket OID in " + fixture.model + ".Data");
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void datasetUnionChecksNamesAndIdentitiesBeforePublishing(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("dataset-union.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        var first = fixture.delivery("first", fixture.basket("Data", "a-b", fixture.asset("shared", "first", false)));
        assertCliSuccess(db, "import", fixture.model, "a", first);
        assertCliSuccess(db, "import", fixture.model, "b",
                fixture.delivery("second", fixture.basket("Data", "b-b", fixture.asset("different", "second", false))));
        var output = work.resolve("published.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--dataset", "b", "--dataset", "a");
        assertEquals(List.of("Data:a-b:shared:first", "Data:b-b:different:second"), fixture.exportedAssets(output));
        assertRejected(db, fixture.model, output, "Dataset does not exist: unknown", "--dataset", "a", "--dataset", "unknown");

        byte[] published = Files.readAllBytes(output);
        var filters = cli(db, "export", fixture.model, "a", output, CancellationToken.none(),
                "--dataset", "b", "--basket", "b-b");
        assertEquals(2, filters.code(), filters::diagnostics);
        assertTrue(filters.diagnostics().contains("Choose one of --dataset, --basket or --topic"), filters::diagnostics);
        assertArrayEquals(published, Files.readAllBytes(output));
        var write = cli(db, "import", fixture.model, "a", first, CancellationToken.none(), "--dataset", "b");
        assertEquals(2, write.code(), write::diagnostics);
        assertTrue(write.diagnostics().contains("Repeated option --dataset"), write::diagnostics);

        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("asset") + " SET t_ili_tid='shared' WHERE t_ili_tid='different'");
        }
        assertRejected(db, fixture.model, output, "Duplicate object ID shared", "--dataset", "a", "--dataset", "b");
        String datasets = db.table("t_ili2db_dataset");
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("asset") + " SET t_ili_tid='different' WHERE label='second'");
            execute(sql, "UPDATE " + db.table("t_ili2db_basket") + " SET t_ili_tid='a-b' WHERE t_ili_tid='b-b'");
        }
        assertRejected(db, fixture.model, output, "Duplicate basket ID a-b", "--dataset", "a", "--dataset", "b");
        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + datasets + " RENAME TO original_dataset");
            execute(sql, "CREATE TABLE " + datasets + " (t_id "
                    + (db.postgis() ? "BIGINT" : "INTEGER") + " PRIMARY KEY, datasetname VARCHAR(200))");
            execute(sql, "INSERT INTO " + datasets + " SELECT * FROM " + db.table("original_dataset"));
            execute(sql, "INSERT INTO " + datasets + " (t_id, datasetname) VALUES (10000,'b')");
        }
        assertRejected(db, fixture.model, output, "Dataset name is ambiguous: b", "--dataset", "a", "--dataset", "b");
    }

    private static void assertRejected(Target db, String model, Path output, String diagnostic, String... options) throws Exception {
        var original = Files.readAllBytes(output);
        var result = cli(db, "export", model, null, output, CancellationToken.none(), options);
        assertEquals(1, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains(diagnostic), result::diagnostics);
        assertArrayEquals(original, Files.readAllBytes(output));
    }
}
