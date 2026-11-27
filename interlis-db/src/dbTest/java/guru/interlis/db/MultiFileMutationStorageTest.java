package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
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
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiFileMutationStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void lateUpdateFailureRestoresBothBasketsAndStatistics(Backend backend, int version) throws Exception {
        Target db = POSTGIS.target(work.resolve("rollback.gpkg"), backend);
        var fixture = new MultiFileImportFixtures(work, version);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        var initial = fixture.delivery("initial", fixture.basket("left", fixture.place("a", "Initially left")),
                fixture.basket("right", fixture.place("b", "Initially right")));
        assertCliSuccess(db, "import", fixture.model, "existing", initial);
        var before = placesWithIds(db);
        assertEquals(2, before.size());

        var first = fixture.delivery("first", fixture.basket("left", fixture.place("a", "Updated left")));
        var last = fixture.delivery("last", fixture.basket("right", fixture.place("b", "Updated right")));
        Files.writeString(last, Files.readString(last) + "<trailing/>");
        assertCliFailure(db, "update", fixture.model, "existing", last,
                version == 23 ? "Malformed XML" : "Malformed XTF 2.4 XML", "--basket", "left", "--basket", "right",
                first.toString());

        assertEquals(before, placesWithIds(db));
        try (var sql = db.connect()) {
            assertEquals(initial.toString(), scalar(sql, "SELECT importfile FROM " + db.table("t_ili2db_import")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_import_basket")));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void lateReplaceFailuresRestorePreviousDataset(Backend backend, int version) throws Exception {
        Target db = POSTGIS.target(work.resolve("replace-rollback.gpkg"), backend);
        var fixture = new MultiFileImportFixtures(work, version);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        var initial = fixture.delivery("initial", fixture.basket("original", fixture.place("a", "Original")));
        var control = fixture.delivery("control", fixture.basket("control-b", fixture.place("control", "Preserve")));
        assertCliSuccess(db, "import", fixture.model, "existing", initial);
        assertCliSuccess(db, "import", fixture.model, "control", control);
        var before = placesWithIds(db);
        assertEquals(2, before.size());

        var first = fixture.delivery("first", fixture.basket("replace", fixture.place("a", "Replacement")));
        var malformed = fixture.delivery("malformed", fixture.basket("last-b", fixture.place("b", "Written")));
        Files.writeString(malformed, Files.readString(malformed) + "<trailing/>");
        assertCliFailure(db, "replace", fixture.model, "existing", malformed,
                version == 23 ? "Malformed XML" : "Malformed XTF 2.4 XML", first.toString());
        assertEquals(before, placesWithIds(db));

        var repeatedBid = fixture.delivery("bid", fixture.basket("replace", fixture.place("b", "Duplicate basket")));
        assertCliFailure(db, "replace", fixture.model, "existing", repeatedBid, "Duplicate basket ID replace", first.toString());
        assertEquals(before, placesWithIds(db));

        var repeatedTid = fixture.delivery("tid", fixture.basket("last-b", fixture.place("a", "Duplicate object")));
        assertCliFailure(db, "replace", fixture.model, "existing", repeatedTid, "Duplicate stored object TID a", first.toString());
        assertEquals(before, placesWithIds(db));
        try (var sql = db.connect()) {
            assertEquals(Set.of(initial.toString(), control.toString()),
                    catalogKeys(sql, "SELECT importfile FROM " + db.table("t_ili2db_import")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_import_basket")));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void repeatedBasketDiscardsIntermediateReferencesAndProtectsItsFinalState(Backend backend, int version) throws Exception {
        Target db = POSTGIS.target(work.resolve("repeated-basket.gpkg"), backend);
        var fixture = new MultiFileImportFixtures(work, version);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source, "--create-import-tabs");
        var initial = fixture.delivery("initial", fixture.basket("left", fixture.place("a", "Original")),
                fixture.basket("right", fixture.place("b", "Preserve")));
        assertCliSuccess(db, "import", fixture.model, "existing", initial);
        var before = placesWithIds(db);
        var first = fixture.delivery("first", fixture.basket("left", fixture.place("intermediate", "Temporary"),
                fixture.externalSurvey("transient-source", "absent", "right")));
        var last = fixture.delivery("last", fixture.basket("left", fixture.place("a", "Updated")));
        assertCliSuccess(db, "update", fixture.model, "existing", last, first.toString());
        var updated = placesWithIds(db);
        assertEquals(before.stream().map(row -> row.replace(":Original", ":Updated")).collect(toSet()), updated);

        var source = fixture.delivery("ref", fixture.basket("right", fixture.place("b", "Rejected"),
                fixture.externalSurvey("remaining-source", "intermediate", "left")));
        assertCliFailure(db, "update", fixture.model, "existing", source, "Cannot delete referenced object", first.toString(),
                last.toString());
        assertEquals(updated, placesWithIds(db));
        assertCliFailure(db, "update", fixture.model, "existing", last, "Cannot encode reference from",
                source.toString(), first.toString());
        assertEquals(updated, placesWithIds(db));

        var duplicate = fixture.delivery("duplicate", fixture.basket("left", fixture.place("intermediate", "Duplicate")));
        assertCliFailure(db, "update", fixture.model, "existing", duplicate, "Duplicate stored object TID intermediate", first.toString());
        assertEquals(updated, placesWithIds(db));
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("survey")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("pointer")));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_import")));
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_import_basket")));
        }
    }

    private static Set<String> placesWithIds(Target db) throws Exception {
        try (var sql = db.connect()) {
            return catalogKeys(sql, """
                    SELECT d.t_id || ':' || b.t_id || ':' || b.t_ili_tid || ':' || p.t_id || ':' || p.t_ili_tid || ':' || p.label
                    FROM %s p JOIN %s b ON p.t_basket=b.t_id JOIN %s d ON b.dataset=d.t_id
                    """.formatted(db.table("place"), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset")));
        }
    }
}
