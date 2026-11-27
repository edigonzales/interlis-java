package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class InputSelectionStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void malformedSkippedBasketRollsBackSelectedDataAndStatistics(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("selected-import.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        var input = fixture.delivery("malformed",
                fixture.basket("Data", "accepted", fixture.asset("written", "accepted", false)),
                fixture.basket("Data", "excluded", fixture.asset("broken", "excluded", false).replace("</Label>", "</Wrong>")));
        assertCliFailure(db, "import", fixture.model, "selection", input,
                version == 23 ? "Malformed XML" : "Malformed XTF 2.4 XML", "--basket", "accepted");
        try (var sql = db.connect()) {
            for (String table : new String[]{"asset", "t_ili2db_dataset", "t_ili2db_basket",
                    "t_ili2db_import", "t_ili2db_import_basket"}) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void resolvesExternalReferenceFromStorageButNotFromSkippedInput(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("selected-references.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        assertCliSuccess(db, "import", fixture.model, "catalogue",
                fixture.delivery("stored-target", fixture.basket("Data", "existing-b", fixture.place("existing"))));
        assertCliSuccess(db, "import", fixture.model, "selected", fixture.delivery("selected-source",
                fixture.basket("Data", "source-b", fixture.survey("source", "existing", "existing-b")),
                fixture.basket("Data", "excluded-b", fixture.place("excluded"))), "--basket", "source-b");
        assertCliFailure(db, "import", fixture.model, "missing", fixture.delivery("missing-source",
                fixture.basket("Data", "accepted", fixture.survey("missing-source", "incoming", "excluded-b")),
                fixture.basket("Data", "excluded-b", fixture.place("incoming"))), "to incoming", "--basket", "accepted");
        try (var sql = db.connect()) {
            assertEquals("existing", scalar(sql, "SELECT p.t_ili_tid FROM " + db.table("pointer") + " r JOIN "
                    + db.table("place") + " p ON p.t_id=r.destination"));
            for (String table : new String[]{"t_ili2db_dataset", "t_ili2db_basket", "t_ili2db_import", "t_ili2db_import_basket"}) {
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("survey")));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("place")));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void filtersUnnamedImportWithoutBasketColumn(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("unpartitioned-selection.gpkg"), backend);
        assertCliSuccess(db, "schemaimport", fixture.model, null, fixture.source);
        var input = fixture.delivery("unpartitioned",
                fixture.basket("Data", "kept", fixture.asset("one", "chosen", false)),
                fixture.basket("Data", "skipped", fixture.asset("two", "excluded", false)));
        assertCliSuccess(db, "import", fixture.model, null, input, "--basket", "kept", "--topic", fixture.model + ".Data");
        try (var sql = db.connect()) {
            assertEquals("chosen", scalar(sql, "SELECT label FROM " + db.table("asset")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
        }
    }
}
