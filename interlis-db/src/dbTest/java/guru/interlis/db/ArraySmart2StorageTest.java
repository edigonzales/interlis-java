package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.ArraySmart2Fixtures.INITIAL;
import static guru.interlis.db.ArraySmart2Fixtures.SHORTENED;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class ArraySmart2StorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void mutationsOfInheritedArraysPreserveIndependentDataset(Backend backend, int version) throws Exception {
        var fixture = new ArraySmart2Fixtures(work, version);
        var db = POSTGIS.target(work.resolve("array-smart2.gpkg"), backend);
        fixture.schema(db);
        assertCliSuccess(db, "import", fixture.model, "independent", fixture.delivery("independent-input", "independent", INITIAL, true));
        assertCliSuccess(db, "import", fixture.model, "native", fixture.delivery("native-input", "native", INITIAL, true));

        try (var sql = db.connect()) {
            var independent = fixture.assertStoredDelivery(db, sql, "independent", "independent", INITIAL, true);
            var ids = fixture.assertStoredDelivery(db, sql, "native", "native", INITIAL, true);
            assertCliSuccess(db, "update", fixture.model, "native", fixture.delivery("update-input", "native", SHORTENED, false));
            assertEquals(ids, fixture.assertStoredDelivery(db, sql, "native", "native", SHORTENED, false));

            assertCliSuccess(db, "replace", fixture.model, "native", fixture.delivery("replace-input", "native", List.of(), true));
            var replaced = fixture.assertStoredDelivery(db, sql, "native", "native", List.of(), true);
            assertEquals(ids.dataset(), replaced.dataset());
            assertNotEquals(ids.firstObject(), replaced.firstObject());
            assertNotEquals(ids.otherObject(), replaced.otherObject());
            Path output = work.resolve("replaced.xtf");
            assertCliSuccess(db, "export", fixture.model, "native", output);
            fixture.assertDelivery(output, "native", List.of(), true);

            assertCliSuccess(db, "delete", fixture.model, "native", null);
            for (String table : List.of("firstasset", "otherasset", "t_ili2db_basket", "t_ili2db_dataset")) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertEquals(independent, fixture.assertStoredDelivery(db, sql, "independent", "independent", INITIAL, true));
            output = work.resolve("independent.xtf");
            assertCliSuccess(db, "export", fixture.model, "independent", output);
            fixture.assertDelivery(output, "independent", INITIAL, true);
        }
    }
}
