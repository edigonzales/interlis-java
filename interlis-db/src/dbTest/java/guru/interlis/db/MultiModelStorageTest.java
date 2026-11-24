package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ModelFixtures.tableFor;
import static guru.interlis.db.MultiModelFixtures.assertSingleDelivery;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class MultiModelStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void selectionLanguageVersionsAndLateImportFailure(Backend backend, int version) throws Exception {
        var models = new MultiModelFixtures(work, version);
        var db = POSTGIS.target(work.resolve("selection.gpkg"), backend);
        models.schema(db);
        int otherVersion = version == 23 ? 24 : 23;
        String other = "DbWarehouse" + otherVersion;
        assertSchemaWithIdentityColumns(db, other, fixture(work, other + ".ili"), "--add-models");

        String contents = Files.readString(models.mixed);
        String outside = version == 23 ? contents.replace("<" + models.workshop + ".Data BID", "<DbOutside.Data BID")
                : contents.replace("<b:Data i:bid", "<b:Data xmlns:b=\"http://www.interlis.ch/xtf/2.4/DbOutside\" i:bid");
        var invalid = Files.writeString(work.resolve("outside.xtf"), outside);
        assertCliFailure(db, "import", models.warehouse, "mixed", invalid, "DbOutside", "--model", models.workshop);
        try (var sql = db.connect()) {
            var tables = models.tables(db);
            for (String table : List.of(tables.warehouse(), tables.workshop(), "t_ili2db_dataset", "t_ili2db_basket")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }

        models.run(db, "import", "mixed", models.mixed);
        assertCliSuccess(db, "import", other, "other-version", fixture(work, other + ".xtf"));
        assertCliSuccess(db, "import", models.warehouse, "warehouse-only", models.singleWarehouse);
        var output = work.resolve("selection.xtf");
        models.run(db, "export", "mixed", output);
        models.assertDelivery(output, "mixed", "warehouse");
        byte[] published = Files.readAllBytes(output);
        assertCliFailure(db, "export", models.warehouse, "mixed", output, "outside the selected model: " + models.workshop + ".Data");
        assertArrayEquals(published, Files.readAllBytes(output));
        assertCliFailure(db, "export", models.warehouse, "mixed", output, "same INTERLIS language version", "--model", other);
        assertArrayEquals(published, Files.readAllBytes(output));
        models.run(db, "export", "warehouse-only", output);
        assertSingleDelivery(output, version, models.warehouse);
        assertCliSuccess(db, "export", other, "other-version", output);
        assertSingleDelivery(output, otherVersion, other);
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void datasetChangesRespectIncomingReferences(Backend backend, int version) throws Exception {
        var models = new MultiModelFixtures(work, version);
        var db = POSTGIS.target(work.resolve("mutation.gpkg"), backend);
        String incoming = "DbIncoming" + version;
        assertSchemaWithIdentityColumns(db, models.warehouse, models.warehouseSource, "--model", models.workshop,
                "--model", incoming, "--model-file", models.workshopSource.toString(),
                "--model-file", fixture(work, incoming + ".ili").toString());
        models.run(db, "import", "mixed", models.mixed);
        assertCliSuccess(db, "import", incoming, "incoming", fixture(work, incoming + ".xtf"));
        assertCliSuccess(db, "import", models.warehouse, "independent", models.singleWarehouse);

        String contents = Files.readString(models.singleWarehouse).replace("fixture-", "mixed-");
        var update = models.warehouseUpdate("mixed");
        var wrongTopic = Files.writeString(work.resolve("wrong-topic.xtf"),
                contents.replace("mixed-warehouse-basket", "mixed-workshop-basket"));
        var removeTarget = Files.writeString(work.resolve("remove-target.xtf"), contents.lines()
                .filter(line -> !line.contains("mixed-warehouse-entry")).collect(Collectors.joining("\n")));
        var tables = models.tables(db);
        var ids = models.storedIds(db, tables, "mixed", "mixed", "warehouse");
        String link = tableFor(db, incoming + ".Survey.Link");
        var output = work.resolve("mixed-mutated.xtf");
        try (var sql = db.connect()) {
            assertEquals(ids.warehouseObject(), scalar(sql, "SELECT destination FROM " + db.table(link)));
            assertEquals(tables.warehouse(), scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                    + " WHERE tablename='" + link + "' AND columnname='destination' AND tag='ch.ehi.ili2db.foreignKey'"));
            assertCliSuccess(db, "update", models.warehouse, "mixed", update);
            assertEquals(ids, models.storedIds(db, tables, "mixed", "mixed", "updated"));
            models.run(db, "export", "mixed", output);
            models.assertDelivery(output, "mixed", "updated");

            assertCliFailure(db, "update", models.warehouse, "mixed", wrongTopic, "another dataset or topic");
            assertCliFailure(db, "update", models.warehouse, "mixed", removeTarget, "referenced object");
            assertCliFailure(db, "replace", models.warehouse, "mixed", update, "outside the selected model");
            assertCliFailure(db, "delete", models.warehouse, "mixed", null, "outside the selected model");
            assertCliFailure(db, "replace", models.warehouse, "mixed", models.mixed, "referenced object", "--model", models.workshop);
            assertCliFailure(db, "delete", models.warehouse, "mixed", null, "referenced object", "--model", models.workshop);
            assertEquals(ids, models.storedIds(db, tables, "mixed", "mixed", "updated"));
            assertEquals(ids.warehouseObject(), scalar(sql, "SELECT destination FROM " + db.table(link)));
            models.run(db, "export", "mixed", output);
            models.assertDelivery(output, "mixed", "updated");

            assertCliSuccess(db, "delete", incoming, "incoming", null);
            models.run(db, "replace", "mixed", models.mixed);
            var replaced = models.storedIds(db, tables, "mixed", "mixed", "warehouse");
            assertEquals(ids.dataset(), replaced.dataset());
            assertNotEquals(ids.warehouseObject(), replaced.warehouseObject());
            assertNotEquals(ids.workshopObject(), replaced.workshopObject());
            models.run(db, "export", "mixed", output);
            models.assertDelivery(output, "mixed", "warehouse");
            models.run(db, "delete", "mixed", null);
            assertEquals("independent", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(tables.workshop())));
            assertCliSuccess(db, "export", models.warehouse, "independent", output);
            assertSingleDelivery(output, version, models.warehouse);
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void ambiguousTargetRollsBackSurvey(Backend backend, int version) throws Exception {
        var fixture = new ImportedTopicFixtures(work, version);
        var db = POSTGIS.target(work.resolve("target.gpkg"), backend);
        fixture.schema(db);
        seedCatalog(db, fixture.library, "catalog", "target-basket");
        assertCliSuccess(db, "import", fixture.model, "survey", fixture.survey);
        fixture.assertStoredReferences(db, 1);
        seedCatalog(db, fixture.library, "catalog-copy", "duplicate-basket");
        var invalid = Files.writeString(work.resolve("ambiguous.xtf"), Files.readString(fixture.survey)
                .replace("fixture-", "ambiguous-").replace(" BID=\"target-basket\"", ""));
        assertCliFailure(db, "import", fixture.model, "ambiguous", invalid, "Ambiguous stored reference target target-entry");
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='ambiguous'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='ambiguous-basket'"));
            for (String table : List.of("report", "note")) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }
        fixture.assertStoredReferences(db, 1);
    }

    private static void seedCatalog(Target db, String model, String dataset, String bid) throws Exception {
        // The imported topic's tables exist via DEPENDS ON. Its delivery is seeded separately from the survey import.
        try (var database = db.postgis() ? Database.openPostgres(db.url, db.schema, db.user, db.password, true)
                : Database.openGeoPackage(db.file, true)) {
            var sql = database.connection();
            long datasetId = database.nextId();
            long basket = database.nextId();
            execute(sql, "INSERT INTO " + db.table("t_ili2db_dataset") + " (t_id,datasetname) VALUES ("
                    + datasetId + ",'" + dataset + "')");
            execute(sql, "INSERT INTO " + db.table("t_ili2db_basket") + " (t_id,dataset,topic,t_ili_tid,attachmentkey) VALUES ("
                    + basket + "," + datasetId + ",'" + model + ".Catalog','" + bid + "','" + basket + "')");
            execute(sql, "INSERT INTO " + db.table("entry") + " (t_id,t_basket,t_ili_tid) VALUES ("
                    + database.nextId() + "," + basket + ",'target-entry')");
            execute(sql, "INSERT INTO " + db.table("marker") + " (t_id,t_basket,t_ili_tid) VALUES ("
                    + database.nextId() + "," + basket + ",'target-marker')");
            database.commit();
        }
    }
}
