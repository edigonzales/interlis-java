package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ObjectIdFixtures.REFERENCE_ID;
import static guru.interlis.db.ObjectIdFixtures.assertOidReferenceRow;
import static guru.interlis.db.ObjectIdFixtures.assertOidReferenceXtf;
import static guru.interlis.db.ObjectIdFixtures.assertOidRows;
import static guru.interlis.db.ObjectIdFixtures.assertOidXtf;
import static guru.interlis.db.ObjectIdFixtures.assertTextOidTransfer;
import static guru.interlis.db.ObjectIdFixtures.oidBasket;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ObjectIdStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void uuidIdentityAndReferences(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("uuid.gpkg"), backend);
        var fixtures = new ObjectIdFixtures(work);
        String model = "DbOid" + version;
        assertSchemaWithIdentityColumns(db, model, copy(model + ".ili"));
        assertCliSuccess(db, "import", model, "oid-legacy", fixtures.oidFixture(model, version, "oid-legacy", REFERENCE_ID));
        Path output = work.resolve("oid-out.xtf");
        assertCliSuccess(db, "export", model, "oid-legacy", output);
        assertOidXtf(output, model, version, "oid-legacy", REFERENCE_ID);

        try (var sql = db.connect()) {
            assertOidRows(db, sql, "oid-legacy", REFERENCE_ID);
            Path invalid = fixtures.oidFixture(model, version, "oid-invalid", "malformed");
            assertCliFailure(db, "import", model, "oid-invalid", invalid, "Invalid UUID");
            assertMissingDataset(db, sql, "oid-invalid");

            Path external = fixtures.oidReferenceFixture(model, version, REFERENCE_ID, "oid-reference-own", "own-inspection");
            assertCliSuccess(db, "import", model, "oid-reference-own", external);
            assertOidReferenceRow(db, sql, "own-inspection", REFERENCE_ID, "oid-legacy", "remotesite");
            Path externalOutput = work.resolve("oid-external-out.xtf");
            assertCliSuccess(db, "export", model, "oid-reference-own", externalOutput);
            assertOidReferenceXtf(externalOutput, version, REFERENCE_ID, true);

            Path duplicate = fixtures.oidFixture(model, version, "oid-duplicate", REFERENCE_ID);
            assertCliSuccess(db, "import", model, "oid-duplicate", duplicate);
            // The reference export omits BID on this external reference in 2.3.
            Path ambiguous = Files.writeString(work.resolve("oid-ambiguous.xtf"), Files.readString(externalOutput)
                    .replace("own-inspection", "ambiguous-inspection")
                    .replace(oidBasket("oid-reference-own"), oidBasket("oid-reference-ambiguous"))
                    .replace(" BID=\"" + oidBasket("oid-legacy") + "\"", ""));
            assertCliFailure(db, "import", model, "oid-reference-ambiguous", ambiguous, "Ambiguous stored reference target");
            assertMissingDataset(db, sql, "oid-reference-ambiguous");
            assertOidReferenceRow(db, sql, "own-inspection", REFERENCE_ID, "oid-legacy", "remotesite");

            String metadata = db.table("t_ili2db_column_prop") + " where tablename='site' and tag='ch.ehi.ili2db.oidDomain'";
            assertEquals("INTERLIS.UUIDOID", scalar(sql, "select setting from " + metadata));
            execute(sql, "delete from " + metadata);
            assertExportRejected(db, model, "oid-legacy", output, "oidDomain", "site.t_ili_tid");
        }
    }

    @ParameterizedTest(name = "{0}, XTF 24")
    @EnumSource(Backend.class)
    void textOidCaseAndBlankValue(Backend backend) throws Exception {
        var db = POSTGIS.target(work.resolve("text-oid.gpkg"), backend);
        String model = "TextOid24";
        assertSchemaWithIdentityColumns(db, model, copy(model + ".ili"));
        assertCliSuccess(db, "import", model, "text-oid", copy(model + ".xtf"));
        Path output = work.resolve("text-oid-out.xtf");
        assertCliSuccess(db, "export", model, "text-oid", output);
        assertTextOidTransfer(output, 24);
        try (var sql = db.connect()) {
            execute(sql, "update " + db.table("recordroot") + " set codevalue=' ' where t_ili_tid='Entry.Mixed-Case'");
        }
        assertExportRejected(db, model, "text-oid", output, "Missing OID value at TextOid24.Data.RecordRoot.CodeValue");
    }

    private static void assertMissingDataset(Target db, Connection sql, String dataset) throws SQLException {
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='" + dataset + "'"));
    }

    private Path copy(String name) throws IOException {
        Path destination = work.resolve(name);
        try (var resource = ObjectIdStorageTest.class.getResourceAsStream("/" + name)) {
            Files.copy(java.util.Objects.requireNonNull(resource, name), destination);
        }
        return destination;
    }
}
