package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void changesAndClearsEmbeddedTextWithoutReplacingTheObject(Backend backend, int version) throws Exception {
        var fixture = new ExternalTextReferenceFixture(work, version);
        var db = POSTGIS.target(work.resolve("embedded-text.gpkg"), backend);
        String model = fixture.model();
        assertSchemaWithIdentityColumns(db, model, fixture.source(model), "--sql-ext-ref-cols");
        var input = fixture.transfer(model, "mutable");
        assertCliSuccess(db, "import", model, "mutable", input);
        String original = Files.readString(input);
        String role = version == 23 ? "SiteMark REF" : "SiteMark i:ref";
        var changed = Files.writeString(work.resolve("changed.xtf"),
                original.replace(role + "=\"" + ExternalTextReferenceFixture.REMOTE + "\"", role + "=\"another-site\""));
        var cleared = Files.writeString(work.resolve("cleared.xtf"), original.replaceFirst("<SiteMark [^>]*/>", ""));
        String row = " FROM " + db.table("report") + " WHERE t_ili_tid='mutable-report'";
        try (var sql = db.connect()) {
            String id = scalar(sql, "SELECT t_id" + row);
            assertEquals(ExternalTextReferenceFixture.REMOTE, scalar(sql, "SELECT sitemark" + row));
            assertCliSuccess(db, "update", model, "mutable", changed);
            assertEquals("another-site", scalar(sql, "SELECT sitemark" + row));
            assertEquals(id, scalar(sql, "SELECT t_id" + row));
            assertCliSuccess(db, "update", model, "mutable", cleared);
            assertNull(scalar(sql, "SELECT sitemark" + row));
            assertEquals(id, scalar(sql, "SELECT t_id" + row));
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void rollsBackMissingLocalLinkAndKeepsTextAfterTargetDeletion(Backend backend, int version) throws Exception {
        var fixture = new ExternalTextReferenceFixture(work, version);
        var db = POSTGIS.target(work.resolve("text-links.gpkg"), backend);
        String model = fixture.model();
        assertSchemaWithIdentityColumns(db, model, fixture.source(model), "--sql-ext-ref-cols", "--create-fk");
        assertCliSuccess(db, "import", model, "kept", fixture.transfer(model, "kept"));
        var broken = fixture.transfer(model, "broken");
        String attribute = version == 23 ? "REF" : "i:ref";
        Files.writeString(broken, Files.readString(broken)
                .replace("<ReportSide " + attribute + "=\"broken-report\"", "<ReportSide " + attribute + "=\"missing\""));
        assertCliFailure(db, "import", model, "broken", broken, "ExternalLink.ReportSide");
        try (var sql = db.connect()) {
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("externallink")));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + db.table("report")));
        }
        assertCliSuccess(db, "import", model, "catalog", fixture.catalog(model));
        assertCliSuccess(db, "delete", model, "catalog", null);
        var out = work.resolve("kept.xtf");
        assertCliSuccess(db, "export", model, "kept", out);
        fixture.assertTransfer(out, "kept");
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void rejectsMultipleEmbeddedTextLinksAndPreservesThePreviousExport(Backend backend, int version) throws Exception {
        var fixture = new ExternalTextReferenceFixture(work, version);
        var db = POSTGIS.target(work.resolve("embedded-link.gpkg"), backend);
        String model = fixture.model();
        assertSchemaWithIdentityColumns(db, model, fixture.source(model), "--sql-ext-ref-cols");
        assertCliSuccess(db, "import", model, "kept", fixture.transfer(model, "kept"));
        var out = work.resolve("existing.xtf");
        assertCliSuccess(db, "export", model, "kept", out);
        fixture.assertTransfer(out, "kept");
        try (var sql = db.connect()) {
            String links = db.table("sitereview");
            execute(sql, "INSERT INTO " + links + " (t_id,t_basket,reviewowner,reviewedat,reviewmemo)"
                    + " SELECT 900001,t_basket,reviewowner,'other-site',NULL FROM " + links + " WHERE reviewmemo='seen'");
        }
        assertExportRejected(db, model, "kept", out, "Multiple link rows at " + model + ".Data.SiteReview.ReviewedAt");
    }
}
