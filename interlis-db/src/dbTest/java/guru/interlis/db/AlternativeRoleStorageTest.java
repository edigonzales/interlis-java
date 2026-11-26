package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class AlternativeRoleStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() { POSTGIS.close(); }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void selectsOneTargetAndRollsBackAnUnmappedTarget(Backend backend, int version) throws Exception {
        var fixture = new AlternativeRoleFixture(work, version);
        var db = POSTGIS.target(work.resolve("alternatives.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model(), fixture.source(), "--inheritance", "smart1", "--create-fk");
        assertCliSuccess(db, "import", fixture.model(), "kept", fixture.transfer("kept", "station"));
        var output = work.resolve("export.xtf");
        assertCliSuccess(db, "export", fixture.model(), "kept", output);
        fixture.assertTransfer(output, "kept");
        assertCliFailure(db, "import", fixture.model(), "broken", fixture.transfer("broken", "hidden"), "broken-hidden");
        try (var sql = db.connect()) {
            String links = db.table("choicelink");
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + links));
            for (String table : new String[]{"t_ili2db_dataset", "t_ili2db_basket", "ticket"}) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
            }
            assertEquals("kept-station", scalar(sql, "SELECT r.t_ili_tid FROM " + links + " l JOIN "
                    + db.table("registry") + " r ON r.t_id=l.candidate_registry WHERE l.candidate_landmark IS NULL"));
            execute(sql, "UPDATE " + links + " SET candidate_landmark=(SELECT t_id FROM " + db.table("landmark")
                    + " WHERE t_ili_tid='kept-landmark') WHERE candidate_registry IS NOT NULL");
        }
        assertExportRejected(db, fixture.model(), "kept", output, "Multiple stored values", "ChoiceLink.Candidate");
    }
}
