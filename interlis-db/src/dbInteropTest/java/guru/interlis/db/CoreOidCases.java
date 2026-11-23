package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.ObjectIdFixtures.REFERENCE_ID;
import static guru.interlis.db.ObjectIdFixtures.assertOidReferenceRow;
import static guru.interlis.db.ObjectIdFixtures.assertOidReferenceXtf;
import static guru.interlis.db.ObjectIdFixtures.assertOidRows;
import static guru.interlis.db.ObjectIdFixtures.assertOidXtf;

final class CoreOidCases {
    private final Path work;
    private final ObjectIdFixtures fixtures;

    CoreOidCases(Path work) {
        this.work = work;
        fixtures = new ObjectIdFixtures(work);
    }

    void exchangeOid(ReferenceTool reference, TestDatabase referenceDb, int version) throws Exception {
        String model = "DbOid" + version;
        Path ili = work.resolve(model + ".ili");
        try (var resource = CoreOidCases.class.getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(resource, ili);
        }
        reference.run(referenceDb, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
        var nativeDb = referenceDb.peer("oid_native");
        assertSchemaWithIdentityColumns(nativeDb, model, ili);
        try (var legacy = referenceDb.connect(); var own = nativeDb.connect()) {
            DbSchemaCatalog.read(legacy, referenceDb.postgis, referenceDb.schema)
                    .assertSameAs(DbSchemaCatalog.read(own, nativeDb.postgis, nativeDb.schema));
        }
        String referenceId = REFERENCE_ID;
        String nativeId = "D09B1740-5C5A-48EA-B095-938D1C946974";
        String tailId = "E09B1740-5C5A-48EA-B095-938D1C946974";
        Path legacyInput = fixtures.oidFixture(model, version, "oid-legacy", referenceId);
        Path nativeInput = fixtures.oidFixture(model, version, "oid-own", nativeId);
        Path tailInput = fixtures.oidFixture(model, version, "oid-tail", tailId);
        for (var db : List.of(referenceDb, nativeDb)) {
            try (var sql = db.connect()) {
                reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "oid-legacy",
                        legacyInput.toString());
                assertOidRows(db, sql, "oid-legacy", referenceId);
                Path ownOutput = work.resolve("oid-own-output.xtf");
                assertCliSuccess(db, "export", model, "oid-legacy", ownOutput);
                assertOidXtf(ownOutput, model, version, "oid-legacy", referenceId);

                assertCliSuccess(db, "import", model, "oid-own", nativeInput);
                assertOidRows(db, sql, "oid-own", nativeId);
                Path legacyOutput = work.resolve("oid-legacy-output.xtf");
                reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "oid-own", legacyOutput.toString());
                assertOidXtf(legacyOutput, model, version, "oid-own", nativeId);

                exchangeOidReference(reference, db, sql, model, version, referenceId);
                if (db == nativeDb) {
                    reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "oid-tail",
                            tailInput.toString());
                    assertOidRows(db, sql, "oid-tail", tailId);
                }
            }
        }
    }

    private void exchangeOidReference(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                      int version, String targetId) throws Exception {
        Path legacy = fixtures.oidReferenceFixture(model, version, targetId, "oid-reference-legacy", "remote-inspection");
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "oid-reference-legacy", legacy.toString());
        assertOidReferenceRow(db, sql, "remote-inspection", targetId, "oid-legacy", "remotesite");
        Path output = work.resolve("oid-reference-output.xtf");
        assertCliSuccess(db, "export", model, "oid-reference-legacy", output);
        assertOidReferenceXtf(output, version, targetId, true);

        Path own = fixtures.oidReferenceFixture(model, version, targetId, "oid-reference-own", "own-inspection");
        assertCliSuccess(db, "import", model, "oid-reference-own", own);
        assertOidReferenceRow(db, sql, "own-inspection", targetId, "oid-legacy", "remotesite");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "oid-reference-own", output.toString());
        assertOidReferenceXtf(output, version, targetId, false);
    }
}
