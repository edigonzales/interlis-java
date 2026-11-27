package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExportSelectionInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageBaskets23() throws Exception { exchangeBaskets(23, null); }
    @Test void geoPackageBaskets24() throws Exception { exchangeBaskets(24, null); }
    @Test void postgisBaskets23() throws Exception { exchangeBaskets(23, POSTGIS.container()); }
    @Test void postgisBaskets24() throws Exception { exchangeBaskets(24, POSTGIS.container()); }
    @Test void geoPackageTopics23() throws Exception { compareTopics(23, null); }
    @Test void geoPackageTopics24() throws Exception { compareTopics(24, null); }
    @Test void postgisTopics23() throws Exception { compareTopics(23, POSTGIS.container()); }
    @Test void postgisTopics24() throws Exception { compareTopics(24, POSTGIS.container()); }

    @Test void geoPackageModels23() throws Exception { exchangeModels(23, null); }
    @Test void geoPackageModels24() throws Exception { exchangeModels(24, null); }
    @Test void postgisModels23() throws Exception { exchangeModels(23, POSTGIS.container()); }
    @Test void postgisModels24() throws Exception { exchangeModels(24, POSTGIS.container()); }

    private void exchangeModels(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var other = new ProfileFixtures(work, version);
        other.source("ForeignModel", "CLASS ExportedElsewhere = END ExportedElsewhere;");
        var db = new TestDatabase(work, postgres, "model_export", "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        schema(reference, db, work, fixture.model + ";ForeignModel", "newClass", "--createDatasetCol");

        Path input = fixture.delivery("incoming delivery",
                fixture.basket("Data", "reference", fixture.asset("a", "reference import", true)));
        reference.run(db, "%ILI_FROM_DB", "--import", "--importBid", "--importTid", input.toString());
        assertCliSuccess(db, "import", fixture.model, null, fixture.delivery("incoming delivery",
                fixture.basket("Data", "native", fixture.asset("b", "native import", true))));
        assertCliSuccess(db, "import", fixture.model, "detached", fixture.delivery("detached",
                fixture.basket("Data", "detached", fixture.asset("c", "unassigned", false))));
        importDataset(reference, db, "other", other.data("ForeignModel", "other", other.object(
                "ForeignModel", "ExportedElsewhere", "elsewhere", "")));

        try (var sql = db.connect()) {
            String dataset = db.table("t_ili2db_dataset");
            String basket = db.table("t_ili2db_basket");
            assertEquals(Set.of("reference", "native"), catalogKeys(sql, """
                    SELECT b.t_ili_tid FROM %s b JOIN %s d ON d.t_id=b.dataset
                    JOIN %s a ON a.t_basket=b.t_id JOIN %s n ON n.asset_detail=a.t_id
                    WHERE d.datasetname='incomingdelivery.xtf-' || d.t_id
                        AND a.t_datasetname=d.datasetname AND n.t_datasetname=d.datasetname
                    """.formatted(basket, dataset, db.table("asset"), db.table("note"))));
            execute(sql, "UPDATE " + basket + " SET dataset=NULL WHERE t_ili_tid='detached'");
        }

        Path nativeOutput = work.resolve("native-model.xtf");
        Path referenceOutput = work.resolve("reference-model.xtf");
        assertCliSuccess(db, "export", fixture.model, null, nativeOutput);
        reference.run(db, "%ILI_FROM_DB", "--export", "--models", fixture.model, "--exportTid", referenceOutput.toString());
        var expected = List.of("Data:reference:a:reference import", "Data:native:b:native import", "Data:detached:c:unassigned");
        assertEquals(expected, fixture.exportedAssets(nativeOutput));
        assertEquals(Set.copyOf(expected), Set.copyOf(fixture.exportedAssets(referenceOutput)));
    }

    private void exchangeBaskets(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var source = new TestDatabase(work, postgres, "basket_export", "v" + version + "_");
        var reference = new ReferenceTool(work, source.postgis());
        schema(reference, source, work, fixture.model, "newClass");
        importDataset(reference, source, "first", fixture.delivery("first",
                fixture.basket("Data", "picked", fixture.asset("a", "selected", false)),
                fixture.basket("Data", "ignored", fixture.asset("b", "ignored", false))));
        assertCliSuccess(source, "import", fixture.model, "second", fixture.delivery("second",
                fixture.basket("Data", "unnamed", fixture.asset("c", "detached", false)),
                fixture.basket("Data", "picked-too", fixture.asset("d", "second", false))));
        try (var sql = source.connect()) {
            execute(sql, "UPDATE " + source.table("t_ili2db_basket") + " SET dataset=NULL WHERE t_ili_tid='unnamed'");
        }
        var nativeOutput = work.resolve("native-selection.xtf");
        var referenceOutput = work.resolve("reference-selection.xtf");
        assertCliSuccess(source, "export", fixture.model, null, nativeOutput,
                "--basket", "picked-too", "--basket", "unnamed", "--basket", "picked");
        reference.run(source, "%ILI_FROM_DB", "--export", "--baskets", "picked;unnamed;picked-too", "--exportTid",
                referenceOutput.toString());
        var expected = List.of("Data:picked:a:selected", "Data:unnamed:c:detached", "Data:picked-too:d:second");
        assertEquals(expected, fixture.exportedAssets(nativeOutput));
        assertEquals(Set.copyOf(expected), Set.copyOf(fixture.exportedAssets(referenceOutput)));
        var nativeTarget = source.peer("basket_native_import");
        var referenceTarget = source.peer("basket_reference_import");
        assertSchemaWithIdentityColumns(nativeTarget, fixture.model, fixture.source);
        assertSchemaWithIdentityColumns(referenceTarget, fixture.model, fixture.source);
        assertCliSuccess(nativeTarget, "import", fixture.model, "selected", referenceOutput);
        importDataset(reference, referenceTarget, "selected", nativeOutput);
        var expectedSql = Set.of(fixture.model + ".Data:picked:a:selected", fixture.model + ".Data:unnamed:c:detached",
                fixture.model + ".Data:picked-too:d:second");
        fixture.assertImportedAssets(nativeTarget, expectedSql);
        fixture.assertImportedAssets(referenceTarget, expectedSql);
    }

    private void compareTopics(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        fixture.addTopics();
        var source = new TestDatabase(work, postgres, "topic_export", "v" + version + "_");
        var reference = new ReferenceTool(work, source.postgis());
        schema(reference, source, work, fixture.model, "newClass");
        importDataset(reference, source, "first", fixture.delivery("first",
                fixture.basket("Data", "picked", fixture.asset("a", "selected", false)),
                fixture.basket("District", "ignored", fixture.asset("b", "ignored", false))));
        assertCliSuccess(source, "import", fixture.model, "second", fixture.delivery("second",
                fixture.basket("Data", "picked-too", fixture.asset("c", "second", false))));
        String topic = fixture.model + ".Data";
        var nativeOutput = work.resolve("native-topics.xtf");
        var referenceOutput = work.resolve("reference-topics.xtf");
        assertCliSuccess(source, "export", fixture.model, null, nativeOutput, "--topic", topic);
        reference.run(source, "%ILI_FROM_DB", "--export", "--topics", topic, "--exportTid", referenceOutput.toString());
        var expected = List.of("Data:picked:a:selected", "Data:picked-too:c:second");
        assertEquals(expected, fixture.exportedAssets(nativeOutput));
        assertEquals(Set.copyOf(expected), Set.copyOf(fixture.exportedAssets(referenceOutput)));

        String empty = fixture.model + ".EmptyTopic";
        assertCliSuccess(source, "export", fixture.model, null, nativeOutput, "--topic", empty);
        var emptyOutput = work.resolve("reference-empty-topic.xtf");
        reference.run(source, "%ILI_FROM_DB", "--export", "--topics", empty, "--exportTid", emptyOutput.toString());
        assertEquals(List.of(), fixture.baskets(nativeOutput));
        assertEquals(List.of(), fixture.baskets(emptyOutput));
    }
}
