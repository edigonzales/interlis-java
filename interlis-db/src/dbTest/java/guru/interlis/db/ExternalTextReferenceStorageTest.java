package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;

final class ExternalTextReferenceStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() { POSTGIS.close(); }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void keepsExternalIdentifiersInAppendedModels(Backend backend, int version) throws Exception {
        var fixture = new ExternalTextReferenceFixture(work, version);
        var db = POSTGIS.target(work.resolve("external-text.gpkg"), backend);
        String model = fixture.model();
        assertSchemaWithIdentityColumns(db, model, fixture.source(model), "--sql-ext-ref-cols", "--create-fk");
        assertCliSuccess(db, "import", model, "base", fixture.transfer(model, "base"));
        try (var sql = db.connect()) {
            fixture.assertStored(db, sql, "base");
        }

        String added = model + "Added";
        assertSchemaWithIdentityColumns(db, added, fixture.source(added), "--add-models");
        assertCliSuccess(db, "import", added, "added", fixture.transfer(added, "added"));
        Path output = work.resolve("added-out.xtf");
        assertCliSuccess(db, "export", added, "added", output);
        fixture.assertTransfer(output, "added");
    }
}
