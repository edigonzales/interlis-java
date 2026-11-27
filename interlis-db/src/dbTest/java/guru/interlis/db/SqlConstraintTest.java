package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SqlConstraintTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    private enum Backend { GEOPACKAGE, POSTGIS }

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void temporalChecksFollowBackendAndPhysicalStorage(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlTemporal";
            String declarations = """
                    DOMAIN DateWindow EXTENDS INTERLIS.XMLDate = "1900-01-01" .. "2100-12-31";
                    CLASS Asset =
                      CalendarDay : INTERLIS.XMLDate;
                      ReadingTime : INTERLIS.XMLTime;
                      ReadingStamp : INTERLIS.XMLDateTime;
                      RestrictedDay : DateWindow;
                    END Asset;
                    """;
            schema(db, model, version, declarations, "--create-date-time-checks");
            String low = "<CalendarDay>1582-01-01</CalendarDay><ReadingTime>00:00:00.000</ReadingTime>"
                    + "<ReadingStamp>1582-01-01T00:00:00.000</ReadingStamp>";
            String high = "<CalendarDay>2999-12-31</CalendarDay><ReadingTime>23:59:59.999</ReadingTime>"
                    + "<ReadingStamp>2999-12-31T23:59:59.999</ReadingStamp><RestrictedDay>1500-01-01</RestrictedDay>";
            assertCliSuccess(db, "import", model, "temporals", data(model, version, "temporals", low, high, ""));
            var output = work.resolve("temporals-export.xtf");
            assertCliSuccess(db, "export", model, "temporals", output);
            var rows = exportedRows(output, version, "temporals", 3);
            assertEquals("1582-01-01", child(rows.getFirst(), "CalendarDay").getTextContent());
            assertEquals("23:59:59.999", child(rows.get(1), "ReadingTime").getTextContent());
            assertEquals("2999-12-31T23:59:59.999", child(rows.get(1), "ReadingStamp").getTextContent());
            assertEquals("1500-01-01", child(rows.get(1), "RestrictedDay").getTextContent());
            assertNull(child(rows.getLast(), "CalendarDay"));
            try (var sql = db.connect()) {
                String table = db.table("asset");
                String row = " WHERE t_ili_tid='temporals-0'";
                if (db.postgis()) {
                    for (String invalid : List.of("calendarday='1581-12-31'", "calendarday='3000-01-01'",
                            "readingtime='23:59:59.999001'", "readingstamp='3000-01-01 00:00:00'")) {
                        var failure = assertThrows(SQLException.class, () -> execute(sql, "UPDATE " + table + " SET " + invalid + row));
                        assertEquals("23514", failure.getSQLState(), invalid);
                    }
                    assertCliFailure(db, "import", model, "broken-temporal",
                            data(model, version, "broken-temporal", low, "<CalendarDay>3000-01-01</CalendarDay>"), "23514");
                } else {
                    execute(sql, "UPDATE " + table + " SET calendarday='1581-12-31'" + row);
                    assertEquals("1581-12-31", scalar(sql, "SELECT calendarday FROM " + table + row));
                    assertCliFailure(db, "import", model, "broken-temporal",
                            data(model, version, "broken-temporal", low, "<CalendarDay>2024-02-30</CalendarDay>"), "Invalid SQL date");
                }
                assertRolledBack(db, sql, 3, 1);
                assertNull(scalar(sql, "SELECT readingstamp FROM " + table + " WHERE t_ili_tid='temporals-2'"));
                assertEquals("create", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_settings")
                        + " WHERE tag='ch.ehi.ili2db.datetimeCheckConstraints'"));
            }
            Path file = work.resolve("temporal-text.gpkg");
            var text = db.postgis() ? POSTGIS.target(file) : new Target(file, null, "temporal_text");
            schema(text, model, version, declarations, "--create-date-time-checks", "--sql-cols-as-text");
            assertCliSuccess(text, "import", model, "text-temporal",
                    data(model, version, "text-temporal", "<CalendarDay>1500-01-01</CalendarDay>"));
            try (var sql = text.connect()) {
                assertEquals("1500-01-01", scalar(sql, "SELECT calendarday FROM " + text.table("asset")));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void textChecksFollowBackendAndPhysicalStorage(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlText";
            String declarations = "CLASS Asset = Label : TEXT*24; Memo : MTEXT; END Asset;";
            schema(db, model, version, declarations, "--create-text-checks");
            assertCliSuccess(db, "import", model, "texts",
                    data(model, version, "texts", "<Label>valid</Label><Memo>one\ntwo</Memo>", ""));
            try (var sql = db.connect()) {
                String table = db.table("asset");
                String row = " WHERE t_ili_tid='texts-0'";
                assertEquals("one\ntwo", scalar(sql, "SELECT memo FROM " + table + row));
                assertNull(scalar(sql, "SELECT label FROM " + table + " WHERE t_ili_tid='texts-1'"));
                if (db.postgis()) {
                    for (String invalid : List.of("label=''", "label='   '", "memo='   '", "label='one\\ntwo'",
                            "label='one\\rtwo'", "label='one\\ttwo'")) {
                        var failure = assertThrows(SQLException.class, () -> execute(sql, "UPDATE " + table + " SET " + invalid + row));
                        assertEquals("23514", failure.getSQLState(), invalid);
                    }
                    // The pinned reference checks the literal backslash sequences.
                    execute(sql, "UPDATE " + table + " SET label='one\ntwo', memo='one\\ntwo'" + row);
                    assertEquals("one\ntwo", scalar(sql, "SELECT label FROM " + table + row));
                    assertCliFailure(db, "import", model, "broken-text",
                            data(model, version, "broken-text", "<Label>valid</Label>", "<Label>   </Label>"), "23514");
                    assertRolledBack(db, sql, 2, 1);
                } else {
                    execute(sql, "UPDATE " + table + " SET label='', memo='   '" + row);
                    assertEquals("", scalar(sql, "SELECT label FROM " + table + row));
                    assertEquals("   ", scalar(sql, "SELECT memo FROM " + table + row));
                }
                assertEquals("create", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_settings")
                        + " WHERE tag='ch.ehi.ili2db.textCheckConstraints'"));
            }
            Path file = work.resolve("scalar-text.gpkg");
            var text = db.postgis() ? POSTGIS.target(file) : new Target(file, null, "scalar_text");
            schema(text, model, version, declarations, "--create-text-checks", "--sql-cols-as-text");
            assertCliSuccess(text, "import", model, "raw-storage",
                    data(model, version, "raw-storage", "<Label>   </Label>"));
            try (var sql = text.connect()) {
                assertEquals("   ", scalar(sql, "SELECT label FROM " + text.table("asset")));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void uniqueKeysAndRollback(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlUnique";
            schema(db, model, version, """
                    CLASS Asset =
                      RegistryNo : TEXT*24;
                      LocalMark : TEXT*24;
                      VariantNo : 0..10;
                      UNIQUE RegistryNo;
                      UNIQUE LocalMark, VariantNo;
                    END Asset;
                    """, "--create-unique");
            for (String dataset : List.of("first", "second")) {
                assertCliSuccess(db, "import", model, dataset, data(model, version, dataset,
                        "<RegistryNo>" + dataset + "</RegistryNo><LocalMark>" + dataset
                                + "</LocalMark><VariantNo>5</VariantNo>", "", ""));
            }
            var output = work.resolve("unique-export.xtf");
            assertCliSuccess(db, "export", model, "second", output);
            var rows = exportedRows(output, version, "second", 3);
            assertEquals("second", child(rows.getFirst(), "RegistryNo").getTextContent());
            assertNull(child(rows.getLast(), "RegistryNo"));

            String duplicate = "<LocalMark>duplicate</LocalMark><VariantNo>7</VariantNo>";
            String valid = "<RegistryNo>tentative</RegistryNo>";
            String error = importSqlDiagnostic(db, "23505");
            assertCliFailure(db, "import", model, "broken-composite",
                    data(model, version, "broken-composite", valid, duplicate, duplicate), error);
            assertCliFailure(db, "import", model, "broken-global",
                    data(model, version, "broken-global", valid, "<RegistryNo>first</RegistryNo>"), error);
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 6, 2);
                assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("asset")
                        + " WHERE registryno IS NULL AND localmark IS NULL AND variantno IS NULL"));
                assertEquals("first", scalar(sql, "SELECT registryno FROM " + db.table("asset")
                        + " WHERE t_ili_tid='first-0'"));
                assertEquals("second", scalar(sql, "SELECT localmark FROM " + db.table("asset")
                        + " WHERE t_ili_tid='second-0' AND variantno=5"));
            }

            String extra = "SqlUniqueExtra";
            schema(db, extra, version, "CLASS Extra = Badge : TEXT*24; UNIQUE Badge; END Extra;", "--add-models");
            try (var sql = db.connect()) {
                String insert = "INSERT INTO " + db.table("extra") + " (t_id,t_basket,badge) VALUES ";
                String basket = "(SELECT min(t_id) FROM " + db.table("t_ili2db_basket") + ")";
                execute(sql, insert + "(999," + basket + ",'once')");
                var rejected = assertThrows(SQLException.class,
                        () -> execute(sql, insert + "(1000," + basket + ",'once')"));
                assertConstraint(db, rejected, "23505", "SQLITE_CONSTRAINT_UNIQUE");
                assertEquals("once", scalar(sql, "SELECT badge FROM " + db.table("extra")));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void numericChecksAndRollback(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlNumeric";
            schema(db, model, version, """
                    DOMAIN SmallReading = -20..30;
                    CLASS Asset =
                      Rating : SmallReading;
                      WideNo : -9223372036854775808..9223372036854775807;
                      Fraction : -2.500..3.125;
                      Precise : -9007199254740993.125..9007199254740993.125;
                    END Asset;
                    """, "--create-num-checks");
            String min = "<Rating>-20</Rating><WideNo>-9007199254740992</WideNo><Fraction>-2.500</Fraction>";
            String max = "<Rating>30</Rating><WideNo>9007199254740992</WideNo><Fraction>3.125</Fraction>";
            assertCliSuccess(db, "import", model, "numbers", data(model, version, "numbers", min, max, ""));
            var output = work.resolve("numeric-export.xtf");
            assertCliSuccess(db, "export", model, "numbers", output);
            var rows = exportedRows(output, version, "numbers", 3);
            assertEquals("-20", child(rows.getFirst(), "Rating").getTextContent());
            assertEquals("30", child(rows.get(1), "Rating").getTextContent());
            assertDecimal("-2.500", child(rows.getFirst(), "Fraction").getTextContent());
            assertDecimal("3.125", child(rows.get(1), "Fraction").getTextContent());
            assertNull(child(rows.getLast(), "Rating"));

            try (var sql = db.connect()) {
                String table = db.table("asset");
                String first = " FROM " + table + " WHERE t_ili_tid='numbers-0'";
                assertEquals("-9007199254740992", scalar(sql, "SELECT wideno" + first));
                assertEquals("9007199254740992", scalar(sql, "SELECT wideno FROM " + table + " WHERE t_ili_tid='numbers-1'"));
                assertNull(scalar(sql, "SELECT rating FROM " + table + " WHERE t_ili_tid='numbers-2'"));
                String row = " WHERE t_ili_tid='numbers-0'";
                execute(sql, "UPDATE " + table + " SET wideno=-9223372036854775808, precise=9007199254740994.0" + row);
                assertEquals("-9223372036854775808", scalar(sql, "SELECT wideno" + first));
                assertDecimal("9007199254740994", scalar(sql, "SELECT precise" + first));
                var rejected = assertThrows(SQLException.class,
                        () -> execute(sql, "UPDATE " + table + " SET precise=9007199254740996.0" + row));
                assertConstraint(db, rejected, "23514", "SQLITE_CONSTRAINT_CHECK");
            }
            String error = importSqlDiagnostic(db, "23514");
            assertCliFailure(db, "import", model, "broken-rating",
                    data(model, version, "broken-rating", min, "<Rating>31</Rating>"), error);
            assertCliFailure(db, "import", model, "broken-fraction",
                    data(model, version, "broken-fraction", min, "<Fraction>-2.501</Fraction>"), error);
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 3, 1);
                assertEquals("-20", scalar(sql, "SELECT rating FROM " + db.table("asset") + " WHERE t_ili_tid='numbers-0'"));
            }

            schema(db, "SqlNumericExtra", version, "CLASS Extra = Reading : 0..1; END Extra;", "--add-models");
            try (var sql = db.connect()) {
                String table = db.table("extra");
                execute(sql, "INSERT INTO " + table + " (t_id,t_basket,reading) VALUES (999,(SELECT t_id FROM "
                        + db.table("t_ili2db_basket") + "),1)");
                var rejected = assertThrows(SQLException.class, () -> execute(sql, "UPDATE " + table + " SET reading=2"));
                assertConstraint(db, rejected, "23514", "SQLITE_CONSTRAINT_CHECK");
                assertEquals("1", scalar(sql, "SELECT reading FROM " + table));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void mandatoryCheckOptionKeepsNullableProfile(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlMandatory";
            schema(db, model, version, """
                    CLASS Place = END Place;
                    STRUCTURE AssignmentDetail = Destination : REFERENCE TO Place; END AssignmentDetail;
                    CLASS Asset =
                      Label : MANDATORY TEXT*24;
                      DetailValue : AssignmentDetail;
                    END Asset;
                    """, "--create-mandatory-checks");
            String structure = version == 23 ? model + ".Data.AssignmentDetail" : "AssignmentDetail";
            assertCliSuccess(db, "import", model, "nullable", data(model, version, "nullable",
                    "<DetailValue><" + structure + "/></DetailValue>"));
            try (var sql = db.connect()) {
                String settings = db.table("t_ili2db_settings");
                assertEquals("enable", scalar(sql, "SELECT setting FROM " + settings + " WHERE tag='ch.ehi.ili2db.SqlNull'"));
                assertEquals("create", scalar(sql, "SELECT setting FROM " + settings
                        + " WHERE tag='ch.ehi.ili2db.mandatoryCheckConstraints'"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE label IS NULL"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("assignmentdetail")
                        + " WHERE destination IS NULL"));
            }
            var output = work.resolve("nullable-export.xtf");
            assertCliSuccess(db, "export", model, "nullable", output);
            var row = exportedRows(output, version, "nullable", 1).getFirst();
            assertNull(child(row, "Label"));
            assertNull(child(children(child(row, "DetailValue")).getFirst(), "Destination"));
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredScalarColumnsRollBackLateMissingValues(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequired";
            schema(db, model, version, """
                    DOMAIN Identifier = MANDATORY 0..100;
                    CLASS Asset =
                      SerialNo : Identifier;
                      Current : MANDATORY BOOLEAN;
                      Remark : TEXT*24;
                    END Asset;
                    """, "--sql-not-null");
            String valid = "<SerialNo>0</SerialNo><Current>false</Current>";
            assertCliSuccess(db, "import", model, "valid", data(model, version, "valid", valid));
            assertCliFailure(db, "import", model, "broken", data(model, version, "broken", valid, "<Current>false</Current>"),
                    importSqlDiagnostic(db, "23502"));
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 1, 1);
                assertEquals("0", scalar(sql, "SELECT serialno FROM " + db.table("asset")));
                assertNull(scalar(sql, "SELECT remark FROM " + db.table("asset")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_settings")
                        + " WHERE tag='ch.ehi.ili2db.SqlNull'"));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredRoleReservesTargetIdInBasket(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredRole";
            var fixture = new ProfileFixtures(work, version);
            var modelFile = fixture.requiredRoleModel(model);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, modelFile, "--sql-not-null", "--create-fk");
            String forward = "forward";
            String backward = "backward";
            assertCliSuccess(db, "import", model, forward, fixture.data(model, forward,
                    fixture.assignedAsset(model, forward, 1) + fixture.assignedSite(model, forward)));
            assertCliSuccess(db, "import", model, backward, fixture.data(model, backward,
                    fixture.assignedSite(model, backward) + fixture.assignedAsset(model, backward, 2)));
            try (var sql = db.connect()) {
                String query = "SELECT a.t_id || ':' || s.t_id FROM " + db.table("asset") + " a JOIN "
                        + db.table("site") + " s ON s.t_id=a.assignedsite WHERE a.t_ili_tid='forward-asset' AND s.t_ili_tid='forward-site'";
                String identity = scalar(sql, query);
                assertTrue(identity.matches("[0-9]+:[0-9]+"), identity);
                assertCliSuccess(db, "update", model, forward, fixture.data(model, forward,
                        fixture.assignedAsset(model, forward, 3) + fixture.assignedSite(model, forward)));
                assertEquals(identity, scalar(sql, query));
                assertCliFailure(db, "update", model, forward, fixture.data(model, forward,
                        fixture.assignedAsset(model, forward, 4)), "Cannot encode reference from forward-asset");
                assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                        fixture.assignedAsset(model, "broken", 5)
                                + fixture.object(model, "Other", "broken-site", "")), "Cannot encode reference from broken-asset");
                assertCliFailure(db, "import", model, "broken-role", fixture.data(model, "broken-role",
                        fixture.assignedSite(model, "broken-role")
                                + fixture.object(model, "Asset", "broken-role-asset", "<SerialNo>6</SerialNo>")),
                        importSqlDiagnostic(db, "23502"));
                assertEquals(identity, scalar(sql, query));
                assertEquals("3", scalar(sql, "SELECT serialno FROM " + db.table("asset") + " WHERE t_ili_tid='forward-asset'"));
                assertRolledBack(db, sql, 2, 2);
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("site")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("other")));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredOneToOneKeepsIdentityAndRollsBack(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredPair";
            var fixture = new ProfileFixtures(work, version);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, fixture.requiredRoleModel(model),
                    "--sql-not-null", "--create-fk");
            String prefix = "pair";
            assertCliSuccess(db, "import", model, prefix, fixture.data(model, prefix,
                    fixture.pairedLeft(model, prefix, "original") + fixture.pairedRight(model, prefix)));
            try (var sql = db.connect()) {
                String left = db.table("pairleft");
                String right = db.table("pairright");
                String query = "SELECT l.t_id || ':' || r.t_id FROM " + left + " l JOIN " + right
                        + " r ON l.requiredright=r.t_id AND r.requiredleft=l.t_id WHERE l.mayberight IS NULL";
                String identity = scalar(sql, query);
                assertTrue(identity.matches("[0-9]+:[0-9]+"), identity);
                assertCliSuccess(db, "update", model, prefix, fixture.data(model, prefix,
                        fixture.pairedLeft(model, prefix, "updated") + fixture.pairedRight(model, prefix)));
                assertEquals(identity, scalar(sql, query));
                assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                        fixture.pairedLeft(model, "broken", "tentative")
                                + fixture.object(model, "PairRight", "broken-right", "")),
                        importSqlDiagnostic(db, "23502"));
                assertRolledBack(db, sql, 0, 1);
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + left));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + right));
                assertEquals(identity, scalar(sql, query));
                assertEquals("updated", scalar(sql, "SELECT label FROM " + left));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void strictLinksReserveBothEndpointsAndRollBackUnresolvedTargets(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlStrictLinks";
            var fixture = new ProfileFixtures(work, version);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, fixture.strictLinkModel(model), "--sql-not-null", "--create-fk");
            assertCliSuccess(db, "import", model, "valid", fixture.data(model, "valid",
                    fixture.strictLinkObjects(model, "valid", "original", "Site")));
            try (var sql = db.connect()) {
                String link = db.table("membership");
                String endpoints = "SELECT assetend || ':' || siteend FROM " + link;
                String ids = scalar(sql, endpoints);
                assertTrue(ids.matches("[0-9]+:[0-9]+"), ids);
                assertCliSuccess(db, "update", model, "valid", fixture.data(model, "valid",
                        fixture.strictLinkObjects(model, "valid", "updated", "Site")));
                assertEquals(ids, scalar(sql, endpoints));

                assertCliFailure(db, "update", model, "valid", fixture.data(model, "valid",
                        fixture.strictLinkObjects(model, "valid", "cancelled", null)), "to valid-site");
                assertCliFailure(db, "import", model, "wrong-target", fixture.data(model, "wrong-target",
                        fixture.strictLinkObjects(model, "wrong-target", "wrong", "Other")), "to wrong-target-site");
                assertEquals(ids, scalar(sql, endpoints));
                assertEquals("updated", scalar(sql, "SELECT label FROM " + db.table("asset")));
                assertRolledBack(db, sql, 1, 1);
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("site")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("other")));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredReferenceAttributesRemainNullableAndRollBackUnresolvedTargets(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredReference";
            var fixture = new ProfileFixtures(work, version);
            var ili = fixture.requiredReferenceModel(model);
            var strictChecks = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(),
                    "--sql-not-null", "--create-mandatory-checks");
            assertEquals(2, strictChecks.code(), strictChecks::diagnostics);
            assertTrue(strictChecks.diagnostics().contains("cannot be combined"), strictChecks::diagnostics);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, ili, "--sql-not-null", "--create-fk");
            assertCliSuccess(db, "import", model, "nullable", fixture.data(model, "nullable",
                    fixture.referringAsset(model, "nullable-asset", null)));
            assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                    fixture.assignedSite(model, "broken") + fixture.referringAsset(model, "broken-asset", "broken-site")
                            + fixture.referringAsset(model, "broken-unknown", "absent")), "to absent");
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 1, 1);
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("site")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("sitedetail") + " WHERE assignedsite IS NULL"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("sitedetail")));
                if (version == 24) {
                    assertNull(scalar(sql, "SELECT directsite FROM " + db.table("asset") + " WHERE t_ili_tid='nullable-asset'"));
                }
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredStructureUsesNullableOwnersAndRollsBackMissingField(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredStructure";
            var fixture = new ProfileFixtures(work, version);
            var ili = fixture.source(model, """
                    STRUCTURE Note = Label : MANDATORY TEXT*24; END Note;
                    CLASS Asset = Brief : MANDATORY Note; Extra : Note; END Asset;
                    """);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, ili, "--sql-not-null", "--create-fk");
            String note = version == 23 ? model + ".Data.Note" : "Note";
            String valid = "<Extra><" + note + "><Label>optional</Label></" + note + "></Extra>";
            assertCliSuccess(db, "import", model, "valid", fixture.data(model, "valid",
                    fixture.object(model, "Asset", "valid-asset", valid)));
            Path output = work.resolve("structure-export.xtf");
            assertCliSuccess(db, "export", model, "valid", output);
            var row = fixture.rows(output, "valid", 1).getFirst();
            assertNull(child(row, "Brief"));
            assertEquals("optional", child(children(child(row, "Extra")).getFirst(), "Label").getTextContent());

            assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                    fixture.object(model, "Asset", "broken-first", valid)
                            + fixture.object(model, "Asset", "broken-last", "<Brief><" + note + "/></Brief>")),
                    db.postgis() ? "SQL code 23502)" : "SQL code 19)");
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 1, 1);
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("note")));
                assertEquals("optional", scalar(sql, "SELECT label FROM " + db.table("note") + " n JOIN "
                        + db.table("asset") + " a ON n.asset_extra=a.t_id WHERE n.asset_brief IS NULL"));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void nestedStructureConstraintRollsBackAllOwnerLevels(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlNestedStructure";
            var fixture = new ProfileFixtures(work, version);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, fixture.nestedStructureModel(model),
                    "--sql-not-null", "--create-fk");
            assertCliSuccess(db, "import", model, "valid",
                    fixture.data(model, "valid", fixture.nestedStructureObject(model, "kept", false)));
            assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                    fixture.nestedStructureObject(model, "broken-first", false)
                            + fixture.nestedStructureObject(model, "broken-last", true)),
                    db.postgis() ? "SQL code 23502)" : "SQL code 19)");
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 1, 1);
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("envelope")));
                assertEquals("5", scalar(sql, "SELECT count(*) FROM " + db.table("memopart")));
                assertEquals("set", scalar(sql, "SELECT e.heading FROM " + db.table("envelope")
                        + " e JOIN " + db.table("asset") + " a ON e.asset_payload=a.t_id WHERE a.t_ili_tid='kept'"));
                assertEquals("last", scalar(sql, "SELECT p.label FROM " + db.table("memopart")
                        + " p JOIN " + db.table("envelope") + " e ON p.envelope_parts=e.t_id WHERE p.t_seq=1"));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredHierarchyFieldsKeepFragmentIdsAndRollBack(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredHierarchy";
            var fixture = new ProfileFixtures(work, version);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, fixture.requiredHierarchyModel(model),
                    "--sql-not-null", "--create-fk");
            assertCliSuccess(db, "import", model, "sites", fixture.data(model, "sites",
                    fixture.surveySite(model, "sites", 4) + fixture.depotSite(model, "sites")));
            try (var sql = db.connect()) {
                String survey = " FROM " + db.table("basesite") + " b JOIN " + db.table("surveysite")
                        + " s ON s.t_id=b.t_id AND s.t_basket=b.t_basket WHERE b.t_ili_tid='sites-survey'";
                String ids = scalar(sql, "SELECT b.t_id || ':' || b.t_basket" + survey);
                assertNotNull(ids);
                assertCliSuccess(db, "update", model, "sites", fixture.data(model, "sites",
                        fixture.surveySite(model, "sites", 8) + fixture.depotSite(model, "sites")));
                assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                        fixture.depotSite(model, "broken") + fixture.surveySite(model, "broken", null)),
                        db.postgis() ? "SQL code 23502)" : "SQL code 19)");
                assertEquals(ids, scalar(sql, "SELECT b.t_id || ':' || b.t_basket" + survey));
                assertEquals("8", scalar(sql, "SELECT s.heightcode" + survey));
                assertNull(scalar(sql, "SELECT s.remark" + survey));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("basesite")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("surveysite")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("depotsite")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
                assertEquals("sites", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredHierarchyRolesShareTargetIdAndRejectInvalidTargets(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredHierarchyRoles";
            var fixture = new ProfileFixtures(work, version);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, fixture.requiredHierarchyModel(model, true),
                    "--sql-not-null", "--create-fk");
            assertCliSuccess(db, "import", model, "jobs", fixture.data(model, "jobs",
                    fixture.workOrder(model, "jobs", 2) + fixture.surveySite(model, "jobs", 4)));
            try (var sql = db.connect()) {
                String linked = " FROM " + db.table("jobbase") + " j JOIN " + db.table("workorder")
                        + " w ON w.t_id=j.t_id JOIN " + db.table("basesite") + " b ON b.t_id=j.assigned"
                        + " JOIN " + db.table("surveysite") + " s ON s.t_id=b.t_id AND s.t_id=w.inspected";
                String ids = scalar(sql, "SELECT j.t_id || ':' || b.t_id" + linked);
                assertNotNull(ids);
                assertCliSuccess(db, "update", model, "jobs", fixture.data(model, "jobs",
                        fixture.workOrder(model, "jobs", 8) + fixture.surveySite(model, "jobs", 7)));
                assertCliFailure(db, "import", model, "missing",
                        fixture.data(model, "missing", fixture.workOrder(model, "missing", 2)),
                        "Assigned to missing-survey");
                assertCliFailure(db, "import", model, "wrong", fixture.data(model, "wrong",
                        fixture.workOrder(model, "wrong", 2)
                                + fixture.object(model, "DepotSite", "wrong-survey", "<Caption>wrong branch</Caption>")),
                        "Inspected to wrong-survey");
                Path foreign = fixture.data(model, "foreign", fixture.workOrder(model, "foreign", 2));
                String tag = version == 23 ? model + ".Data" : "Data";
                String bid = version == 23 ? "BID" : "i:bid";
                String end = version == 23 ? "</DATASECTION>" : "</i:datasection>";
                Files.writeString(foreign, Files.readString(foreign).replace(end,
                        "<" + tag + " " + bid + "=\"other-basket\">" + fixture.surveySite(model, "foreign", 3)
                                + "</" + tag + ">" + end));
                assertCliFailure(db, "import", model, "foreign", foreign, "Assigned to foreign-survey");
                assertEquals(ids, scalar(sql, "SELECT j.t_id || ':' || b.t_id" + linked));
                assertEquals("8", scalar(sql, "SELECT w.priority" + linked));
                assertEquals("7", scalar(sql, "SELECT s.heightcode" + linked));
                for (String table : List.of("jobbase", "workorder", "basesite", "surveysite", "t_ili2db_basket")) {
                    assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
                }
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("depotsite")));
                assertEquals("jobs", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            }
        });
    }

    private static void assertRolledBack(Target db, Connection sql, int objects, int datasets) throws SQLException {
        assertEquals(Integer.toString(objects), scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
        assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE t_ili_tid LIKE 'broken-%'"));
        assertEquals(Integer.toString(datasets), scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
        assertEquals(Integer.toString(datasets), scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredGeometryRollsBackLateMissingValue(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredGeometry";
            var fixture = new ProfileFixtures(work, version);
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, fixture.requiredGeometryModel(model),
                    "--sql-not-null", "--create-geom-index");
            assertCliSuccess(db, "import", model, "valid",
                    fixture.data(model, "valid", fixture.requiredGeometryObjects(model, "valid")));
            assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                    fixture.requiredGeometryObjects(model, "broken") + fixture.object(model, "Marker", "absent", "")),
                    importSqlDiagnostic(db, "23502"));
            try (var sql = db.connect()) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
                assertEquals("valid", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
                assertIndexedGeometry(db, sql, "marker", "center");
                assertIndexedGeometry(db, sql, "trail", "trace");
                assertIndexedGeometry(db, sql, "plot", "outline");
                if (version == 24) assertIndexedGeometry(db, sql, "stops", "locations");
                assertEquals("valid-survey", scalar(sql, "SELECT t_ili_tid FROM " + db.table("survey")
                        + " WHERE optionalpoint IS NULL"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("survey")));
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void requiredGeometryFragmentsKeepIdsAndIndexesOnUpdateAndRollback(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlRequiredGeometryFragments";
            var fixture = new ProfileFixtures(work, version);
            var ili = fixture.requiredGeometryFragmentsModel(model);
            String[] options = db.postgis()
                    ? new String[]{"--sql-not-null", "--create-geom-index", "--create-fk", "--one-geom-per-table"}
                    : new String[]{"--sql-not-null", "--create-geom-index", "--create-fk"};
            DbTestSupport.assertSchemaWithIdentityColumns(db, model, ili, options);
            assertCliSuccess(db, "import", model, "markers", fixture.data(model, "markers",
                    fixture.markerWithGeometryFragments(model, "keep", "12.125", true)));
            try (var sql = db.connect()) {
                String fragments = " FROM " + db.table("marker") + " m JOIN " + db.table("marker_anchor")
                        + " a ON a.t_id=m.t_id AND a.t_basket=m.t_basket JOIN " + db.table("marker_peg")
                        + " p ON p.t_id=m.t_id AND p.t_basket=m.t_basket WHERE m.t_ili_tid='keep'";
                String ids = scalar(sql, "SELECT m.t_id || ':' || m.t_basket" + fragments);
                assertNotNull(ids);
                SpatialIndexFixtures.assertSearch(db, sql, "marker_anchor", "anchor", "12.125", "13.375", true);
                SpatialIndexFixtures.assertSearch(db, sql, "marker_peg", "peg", "16", "17", true);
                assertCliSuccess(db, "update", model, "markers", fixture.data(model, "markers",
                        fixture.markerWithGeometryFragments(model, "keep", "22.125", false)));
                assertCliFailure(db, "import", model, "broken", fixture.data(model, "broken",
                        fixture.markerWithGeometryFragments(model, "broken-first", "42.125", true)
                                + fixture.markerWithGeometryFragments(model, "broken-last", null, true)),
                        db.postgis() ? "SQL code 23502)" : "SQL code 19)");
                assertEquals(ids, scalar(sql, "SELECT m.t_id || ':' || m.t_basket" + fragments));
                assertEquals("1", scalar(sql, "SELECT count(*)" + fragments + " AND p.peg IS NULL"));
                assertIndexedGeometry(db, sql, "marker_anchor", "anchor");
                SpatialIndexFixtures.assertIndex(db, sql, "marker_peg", "peg");
                SpatialIndexFixtures.assertSearch(db, sql, "marker_anchor", "anchor", "12.125", "13.375", false);
                SpatialIndexFixtures.assertSearch(db, sql, "marker_anchor", "anchor", "22.125", "13.375", true);
                SpatialIndexFixtures.assertSearch(db, sql, "marker_anchor", "anchor", "42.125", "13.375", false);
                SpatialIndexFixtures.assertSearch(db, sql, "marker_peg", "peg", "16", "17", false);
                for (String table : List.of("marker", "marker_peg", "t_ili2db_basket")) {
                    assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
                }
                assertEquals("markers", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            }
        });
    }

    private static void assertIndexedGeometry(Target db, Connection sql, String table, String field) throws SQLException {
        SpatialIndexFixtures.assertIndex(db, sql, table, field);
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + (db.postgis() ? db.table(table)
                + " WHERE ST_SRID(" + field + ")=2056" : "rtree_" + table + "_" + field)), table);
    }

    private static String importSqlDiagnostic(Target db, String state) {
        return db.postgis() ? "SQL code " + state : "Database operation failed (SQL code ";
    }

    private static void assertConstraint(Target db, SQLException error, String state, String sqliteCode) {
        if (db.postgis()) assertEquals(state, error.getSQLState());
        else assertTrue(error.getMessage().contains(sqliteCode), error::getMessage);
    }

    private static void assertDecimal(String expected, String actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)), actual);
    }

    private void schema(Target db, String model, int version, String declarations, String... options) throws Exception {
        var ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, declarations, model));
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private Path data(String model, int version, String dataset, String... fields) throws Exception {
        String tag = version == 23 ? model + ".Data.Asset" : "Asset";
        String attribute = version == 23 ? "TID" : "i:tid";
        var objects = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            objects.append('<').append(tag).append(' ').append(attribute).append("=\"").append(dataset)
                    .append('-').append(i).append("\">").append(fields[i]).append("</").append(tag).append('>');
        }
        return Files.writeString(work.resolve(dataset + ".xtf"),
                referenceDatasetFixture(model, version, dataset + "-b", objects.toString()));
    }

    private static List<Element> exportedRows(Path file, int version, String dataset, int count) throws Exception {
        var baskets = children(readDataSection(file, version));
        assertEquals(1, baskets.size());
        assertEquals(dataset + "-b", id(baskets.getFirst(), version, "bid"));
        var rows = children(baskets.getFirst()).stream()
                .sorted(Comparator.comparing(row -> id(row, version, "tid"))).toList();
        assertEquals(count, rows.size());
        return rows;
    }

    @FunctionalInterface
    private interface DatabaseCase {
        void run(Target db) throws Exception;
    }

    private void withDatabase(Backend backend, DatabaseCase test) throws Exception {
        var file = work.resolve("constraints.gpkg");
        if (backend == Backend.GEOPACKAGE) {
            test.run(new Target(file, null, "sql_constraints"));
        } else {
            test.run(POSTGIS.target(file));
        }
    }
}
