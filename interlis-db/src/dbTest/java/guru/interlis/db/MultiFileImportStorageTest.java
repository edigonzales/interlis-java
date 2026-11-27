package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiFileImportStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test
    void rejectsDatabaseAsLaterInputFile() throws Exception {
        Path gpkg = work.resolve("target.gpkg");
        Target db = POSTGIS.target(gpkg, Backend.GEOPACKAGE);
        var fixture = new MultiFileImportFixtures(work, 24);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        Path first = fixture.delivery("first", fixture.basket("one", fixture.place("one", "First")));

        assertCliFailure(db, "import", fixture.model, "rejected", gpkg,
                "The XTF and GeoPackage paths must identify different files", first.toString());
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void rejectionRollsBackEarlierFiles(Backend backend, int version) throws Exception {
        Target db = POSTGIS.target(work.resolve("rollback.gpkg"), backend);
        var fixture = new MultiFileImportFixtures(work, version);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        Path control = fixture.delivery("control", fixture.basket("control-basket", fixture.place("control", "Original")));
        assertCliSuccess(db, "import", fixture.model, "control", control);

        Path first = fixture.delivery("first", fixture.basket("duplicate-basket", fixture.place("duplicate-tid", "First")));
        Path malformed = fixture.delivery("malformed", fixture.basket("written-basket", fixture.place("written", "Late")));
        Files.writeString(malformed, Files.readString(malformed) + "<trailing/>");
        assertCliFailure(db, "import", fixture.model, "rejected", malformed,
                version == 23 ? "Malformed XML" : "Malformed XTF 2.4 XML", first.toString());
        assertOnlyControl(db, control);

        Path duplicateBasket = fixture.delivery("duplicate-basket",
                fixture.basket("duplicate-basket", fixture.place("other", "Duplicate basket")));
        assertCliFailure(db, "import", fixture.model, "rejected", duplicateBasket,
                "Duplicate basket ID duplicate-basket", first.toString());
        assertOnlyControl(db, control);

        Path duplicateTid = fixture.delivery("duplicate-tid", fixture.basket("other-basket", fixture.survey("duplicate-tid")));
        assertCliFailure(db, "import", fixture.model, "rejected", duplicateTid,
                "Duplicate stored object TID duplicate-tid", first.toString());
        assertOnlyControl(db, control);

        Path local = fixture.delivery("local", fixture.basket("local-basket", fixture.localSurvey("local-source", "later-target")));
        Path target = fixture.delivery("target", fixture.basket("target-basket", fixture.place("later-target", "Later")));
        assertCliFailure(db, "import", fixture.model, "rejected", target, "later-target", local.toString());
        assertOnlyControl(db, control);
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void batchSelectionAndAuditIncludeFilesWithoutSelectedBaskets(Backend backend, int version) throws Exception {
        Target db = POSTGIS.target(work.resolve("filtered.gpkg"), backend);
        var fixture = new MultiFileImportFixtures(work, version);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        Path empty = fixture.delivery("empty");
        Path ignored = fixture.delivery("ignored", fixture.basket("ignored", fixture.place("picked-object", "Ignored")));
        Path picked = fixture.delivery("picked", fixture.basket("picked", fixture.place("picked-object", "Kept")));

        assertCliSuccess(db, "import", fixture.model, "filtered", picked,
                "--basket", "picked", "--topic", fixture.model + ".Data", empty.toString(), ignored.toString());
        try (var sql = db.connect()) {
            assertEquals("filtered:picked:picked-object:Kept", scalar(sql, """
                    SELECT d.datasetname || ':' || b.t_ili_tid || ':' || p.t_ili_tid || ':' || p.label
                    FROM %s d JOIN %s b ON b.dataset=d.t_id JOIN %s p ON p.t_basket=b.t_id
                    """.formatted(db.table("t_ili2db_dataset"), db.table("t_ili2db_basket"), db.table("place"))));
            assertEquals(Set.of(empty.toString(), ignored.toString(), picked.toString()),
                    catalogKeys(sql, "SELECT importfile FROM " + db.table("t_ili2db_import")));
            assertEquals(picked + ":picked:1", scalar(sql, """
                    SELECT i.importfile || ':' || b.t_ili_tid || ':' || s.objectcount
                    FROM %s s JOIN %s i ON i.t_id=s.importrun JOIN %s b ON b.t_id=s.basket
                    """.formatted(db.table("t_ili2db_import_basket"), db.table("t_ili2db_import"), db.table("t_ili2db_basket"))));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void emptyTransfersCreateDatasetAndAuditPerFile(Backend backend, int version) throws Exception {
        Target db = POSTGIS.target(work.resolve("empty.gpkg"), backend);
        var fixture = new MultiFileImportFixtures(work, version);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        Path single = fixture.delivery("single");
        Path first = fixture.delivery("first");
        Path last = fixture.delivery("last");
        Path malformed = fixture.delivery("malformed-empty");
        Files.writeString(malformed, Files.readString(malformed) + "<trailing/>");

        assertCliFailure(db, "import", fixture.model, "invalid", malformed,
                version == 23 ? "Malformed XML" : "Malformed XTF 2.4 XML");
        assertCliSuccess(db, "import", fixture.model, "single", single);
        assertCliSuccess(db, "import", fixture.model, "batch", last, first.toString());
        try (var sql = db.connect()) {
            String datasets = db.table("t_ili2db_dataset");
            String imports = db.table("t_ili2db_import");
            assertEquals(Set.of("single", "batch"), catalogKeys(sql, "SELECT datasetname FROM " + datasets));
            assertEquals(Set.of("single:" + single, "batch:" + first, "batch:" + last), catalogKeys(sql, """
                    SELECT d.datasetname || ':' || i.importfile FROM %s i JOIN %s d ON i.dataset=d.t_id
                    """.formatted(imports, datasets)));
            for (String table : Set.of("t_ili2db_basket", "t_ili2db_import_basket", "place")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
        }
    }

    private static void assertOnlyControl(Target db, Path control) throws Exception {
        try (var sql = db.connect()) {
            assertEquals("control", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            assertEquals("control-basket", scalar(sql, "SELECT t_ili_tid FROM " + db.table("t_ili2db_basket")));
            assertEquals("control:Original", scalar(sql, "SELECT t_ili_tid || ':' || label FROM " + db.table("place")));
            for (String table : Set.of("survey", "pointer", "localpointer")) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertEquals(control.toString(), scalar(sql, "SELECT importfile FROM " + db.table("t_ili2db_import")));
            assertEquals("1", scalar(sql, "SELECT objectcount FROM " + db.table("t_ili2db_import_basket")));
        }
    }
}
