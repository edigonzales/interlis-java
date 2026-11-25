package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.db.BaseDbFixture.assertRows;
import static guru.interlis.db.BaseDbFixture.assertTransferredNestedTree;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertLocalReference;
import static guru.interlis.db.DbTestSupport.assertMissingTarget;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BaseMappingStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void storesBaseMappingsAndRejectsDamagedAttribute(Backend backend, int version) throws Exception {
        var fixture = new BaseDbFixture(work, version);
        var db = POSTGIS.target(work.resolve("base-mapping.gpkg"), backend);
        String model = fixture.model();
        assertSchemaWithIdentityColumns(db, model, fixture.source());
        var input = Files.writeString(work.resolve("base-input.xtf"), fixture.transfer());
        assertCliSuccess(db, "import", model, "legacy", input);

        try (var sql = db.connect()) {
            BaseGeometryRows.assertStored(db, sql, version, "legacy");
            BaseStructureRows.assertStored(db, sql, version, "legacy");
            BaseAssociationRows.assertStored(db, sql, version, "legacy");
            assertRows(db, sql, version, 1);
        }
        if (db.postgis()) assertPostgresIdDefault(db);

        var output = work.resolve("base-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        try (var sql = db.connect()) {
            execute(sql, "update " + db.table("t_ili2db_attrname") + " set sqlname='missing_select'"
                    + " where iliname='" + model + ".Data.Place.Select'");
        }
        assertExportRejected(db, model, "legacy", output, "Select", "missing_select");
    }

    private static void assertPostgresIdDefault(Target db) throws SQLException {
        try (var sql = db.connect()) {
            sql.setAutoCommit(false);
            long lastId = Long.parseLong(scalar(sql, "select last_value from " + db.table("t_ili2db_seq")));
            String newId = scalar(sql, "insert into " + db.table("place") + " (t_basket) values ((select min(t_id) from "
                    + db.table("t_ili2db_basket") + ")) returning t_id");
            assertEquals(Long.toString(lastId + 1), newId);
            sql.rollback();
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void failedSchemaCreationPreservesTarget(Backend backend, int version) throws Exception {
        var fixture = new BaseDbFixture(work, version);
        var db = POSTGIS.target(work.resolve("native.gpkg"), backend);
        String model = fixture.model();
        Path ili = fixture.source();
        boolean postgis = db.postgis();
        assertSchemaWithIdentityColumns(db, model, ili);
        try (var sql = db.connect()) {
            var catalog = DbSchemaCatalog.read(sql, postgis, db.schema());
            var duplicate = schemaWithIdentityColumns(db, model, ili, CancellationToken.none());
            assertNotEquals(0, duplicate.code(), duplicate::diagnostics);
            catalog.assertSameAs(DbSchemaCatalog.read(sql, postgis, db.schema()));
            assertEmptySchema(sql, db, version);
        }

        var invalidDb = POSTGIS.target(work.resolve("invalid.gpkg"), backend);
        Path invalid = Files.writeString(work.resolve("invalid.ili"), "INTERLIS 2.4; MODEL");
        var failure = schemaWithIdentityColumns(invalidDb, model, invalid, CancellationToken.none());
        assertNotEquals(0, failure.code(), failure::diagnostics);
        assertMissingTarget(invalidDb);

        assertExistingTargetPreserved(POSTGIS.target(work.resolve("occupied.gpkg"), backend), model, ili);
        var cancelledDb = POSTGIS.target(work.resolve("cancelled.gpkg"), backend);
        var plan = SchemaPlan.read(List.of(model), ili, List.of(), !postgis, !postgis, 2056, false,
                java.util.Optional.empty(), new SqlNames(), DbInheritance.Profile.NEW_CLASS, Set.of(), false,
                DbEnumOptions.DEFAULT, Set.of(Ili2dbLayout.SchemaOption.BASKET_COLUMN, Ili2dbLayout.SchemaOption.TID_COLUMN),
                CancellationToken.none());
        if (postgis) {
            try (var reserved = Database.openPostgres(cancelledDb.url, "select", cancelledDb.user,
                    cancelledDb.password, true)) {
                var rejected = assertThrows(DbException.class,
                        () -> SchemaSql.createPostgres(reserved, plan, false, CancellationToken.none()));
                assertTrue(rejected.getMessage().contains("reserved word"), rejected::getMessage);
            }
            try (var target = Database.openPostgres(cancelledDb.url, cancelledDb.schema, cancelledDb.user,
                    cancelledDb.password, true)) {
                var checks = new AtomicInteger();
                int srsChecks = plan.geometryEpsgCodes().size();
                // Let the SRS catalog checks finish and cancel at the first checkpoint after CREATE SCHEMA.
                assertThrows(OperationCancelledException.class,
                        () -> SchemaSql.createPostgres(target, plan, false, () -> checks.incrementAndGet() > srsChecks));
                assertEquals(cancelledDb.schema, scalar(target.connection(),
                        "select nspname from pg_namespace where nspname='" + cancelledDb.schema + "'"));
            }
        } else {
            int before = temporaryFiles();
            var checks = new AtomicInteger();
            // The fourth check precedes CREATE TABLE gpkg_spatial_ref_sys.
            assertThrows(OperationCancelledException.class,
                    () -> GeoPackageSchema.create(cancelledDb.file, plan, false, () -> checks.incrementAndGet() == 5));
            assertEquals(before, temporaryFiles());
        }
        assertMissingTarget(cancelledDb);
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void lateImportFailuresRollBack(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("rollback.gpkg"), backend);
        var base = new BaseDbFixture(work, version);
        String model = base.model();
        String fixture = base.transfer();
        assertSchemaWithIdentityColumns(db, model, base.source());
        base.importDataset(db, "legacy");
        base.importDataset(db, "native");
        try (var sql = db.connect()) {
            assertMissingReferenceRollback(db, sql, model, version, fixture,
                    "LinkedHub", "MarkerLink", "missing-link", "marker", "hub", "markerlink");
            assertMissingReferenceRollback(db, sql, model, version, fixture,
                    "Witness", "Review", "missing-review", "marker", "hub", "review");
            assertMissingReferenceRollback(db, sql, model, version, fixture,
                    "ConsultedMarker", "AnnotatedLink", "missing-annotated-link", "marker", "report", "annotatedlink");
            assertWrongTargetBranchRollback(db, sql, model, version, fixture);
            assertWrongHierarchyRoleRollback(db, sql, model, version, fixture);
            assertWrongStandaloneBranchRollback(db, sql, model, version, fixture,
                    "HierarchyLink", "WorkBranch", "wrong-hierarchy-link", "hierarchylink");
            assertWrongStandaloneBranchRollback(db, sql, model, version, fixture,
                    "DispatchRecord", "DispatchMast", "wrong-ternary-link", "dispatchrecord");
            base.importDataset(db, "tail");
            assertRollback(db, sql, model, fixture);
            assertStructureRollback(db, sql, model, fixture);
            assertMissingStructureTargetRollback(db, sql, model, version, fixture);
            assertMissingReferenceRollback(db, sql, model, version, fixture,
                    "Select", "missing-role-marker-1", "missing-role", "marker", "hub");
            assertClassHierarchyRollback(db, sql, model, fixture);
            assertUnresolvedHierarchyReferenceRollback(db, sql, model, version, fixture);
            assertRows(db, sql, version, 3);
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void damagedStructuresAndRolesKeepPreviousExport(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("stored.gpkg"), backend);
        var base = new BaseDbFixture(work, version);
        String model = base.model();
        base.seed(db);
        Path output = work.resolve("mapping-out.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        try (var sql = db.connect()) {
            assertCorruptStructureRejected(db, sql, model, output);
            assertNestedSequenceOrder(db, sql, model, version);
            assertCorruptNestedStructureRejected(db, sql, model, output);
            assertCorruptRoleMappingRejected(db, sql, model, output);
            assertWrongStructureBranchRejected(db, sql, model, output);
            assertCorruptStructureTargetRejected(db, sql, model, output);
            assertCorruptPolymorphicTargetsRejected(db, sql, model, output);
            assertCorruptHierarchyRoleRejected(db, sql, model, output);
            assertCorruptHierarchyLinkRejected(db, sql, model, output);
            assertCorruptTernaryLinkRejected(db, sql, model, output);
            assertCorruptClassHierarchyRejected(db, sql, model, output);
            assertMissingAssociationFragmentRejected(db, sql, model, output);
            assertCorruptInheritedOwnerRejected(db, sql, model, output);
            assertWrongRelayReviewOwnerRejected(db, sql, model, output);
            assertAmbiguousRatingRejected(db, sql, model, output);
            assertAmbiguousReviewRejected(db, sql, model, output);
        }
    }

    @Test
    void widePolymorphicProjectionInGeoPackage24() throws Exception {
        var attributes = new StringBuilder();
        var values = new StringBuilder();
        // A source table plus two joins per field would exceed SQLite's 64-table limit.
        for (int index = 0; index < 32; index++) {
            attributes.append("    To").append(index).append(" : REFERENCE TO Target;\n");
            values.append("<To").append(index).append(" i:ref=\"target\"/>");
        }
        Path model = Files.writeString(work.resolve("WideRefs.ili"), """
                INTERLIS 2.4;
                MODEL WideRefs (en) AT "https://example.org/interlis/db" VERSION "2026-11-20" =
                  TOPIC Data =
                    CLASS Base (ABSTRACT) = END Base;
                    CLASS Target EXTENDS Base = END Target;
                    CLASS Source =
                %s    END Source;
                  END Data;
                END WideRefs.
                """.formatted(attributes));
        Path input = Files.writeString(work.resolve("wide-input.xtf"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="http://www.interlis.ch/xtf/2.4/WideRefs">
                  <i:headersection><i:models><i:model>WideRefs</i:model></i:models><i:sender>interop fixture</i:sender></i:headersection>
                  <i:datasection><Data i:bid="basket"><Source i:tid="source">%s</Source>
                    <Target i:tid="target"/></Data></i:datasection>
                </i:transfer>
                """.formatted(values));
        var db = POSTGIS.target(work.resolve("wide.gpkg"), Backend.GEOPACKAGE);
        assertSchemaWithIdentityColumns(db, "WideRefs", model);
        assertCliSuccess(db, "import", "WideRefs", "wide", input);
        Path output = work.resolve("wide-output.xtf");
        assertCliSuccess(db, "export", "WideRefs", "wide", output);
        var source = child(children(readDataSection(output, 24)).getFirst(), "Source");
        assertEquals(32, children(source).size());
        for (int index = 0; index < 32; index++) {
            assertLocalReference(source, "To" + index, 24, "target");
        }
    }

    private static void assertEmptySchema(Connection sql, Target db, int version) throws SQLException {
        assertRows(db, sql, version, 0);
        if (db.postgis) {
            assertEquals("false", scalar(sql, "select cast(is_called as text) from " + db.table("t_ili2db_seq")));
        } else {
            assertEquals("0", scalar(sql, "select count(*) from t_key_object"));
        }
    }

    private void assertExistingTargetPreserved(Target db, String model, Path ili) throws Exception {
        if (db.postgis) {
            try (var sql = db.connect()) {
                execute(sql, "create schema " + db.schema);
                var failure = schemaWithIdentityColumns(db, model, ili, CancellationToken.none());
                assertNotEquals(0, failure.code(), failure::diagnostics);
                assertEquals(db.schema, scalar(sql, "select nspname from pg_namespace where nspname='"
                        + db.schema + "'"));
                assertEquals("0", scalar(sql, "select count(*) from information_schema.tables where table_schema='"
                        + db.schema + "'"));
            }
        } else {
            String original = "Existing unrelated file";
            Files.writeString(db.file, original);
            var failure = schemaWithIdentityColumns(db, model, ili, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertEquals(original, Files.readString(db.file));
        }
    }

    private int temporaryFiles() throws IOException {
        try (var files = Files.list(work)) {
            return Math.toIntExact(files.filter(file -> file.getFileName().toString().startsWith(".interlis-db-"))
                    .count());
        }
    }

    private void assertRollback(Target db, Connection sql, String model, String fixture)
            throws Exception {
        Path broken = Files.writeString(work.resolve("rollback.xtf"), fixture.replace("legacy-", "fail-"));
        var rows = storedIds(db, sql, "route", "place");
        if (db.postgis) {
            String sequenceValue = "select last_value from " + db.table("t_ili2db_seq");
            long before = Long.parseLong(scalar(sql, sequenceValue));
            // Sequence increments are visible outside the transaction. The third allocation is for the
            // first Route object, after the dataset and basket. The reader checks cancellation between inserts.
            CancellationToken afterFirstObject = () -> {
                try {
                    return Long.parseLong(scalar(sql, sequenceValue)) >= before + 3;
                } catch (SQLException failure) {
                    throw new AssertionError(failure);
                }
            };
            var cancelled = cli(db, "import", model, "fail", broken, afterFirstObject);
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(Long.toString(before + 3), scalar(sql, sequenceValue));
        } else {
            // The trigger fires after the line and first place have both reached the transaction.
            execute(sql, """
                    create trigger fail_place before insert on place
                    when new.t_ili_tid='fail-place-2' and exists(select 1 from place where t_ili_tid='fail-place-1')
                    and exists(select 1 from route where t_ili_tid='fail-route-1' and trace is not null)
                    begin select raise(abort, 'rollback probe after line'); end
                    """);
            var rejected = cli(db, "import", model, "fail", broken, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("SQL code"), rejected::diagnostics);
        }
        assertRolledBack(db, sql, "fail", rows);
    }

    private void assertStructureRollback(Target db, Connection sql, String model, String fixture)
            throws Exception {
        // Reject the second basket's child after the flat collections and nested leaves reached the database.
        String condition = """
                new.label='later' and exists (
                    select 1 from %s d join %s r on r.t_id=d.report_entries
                    where r.t_ili_tid='structure-fail-report-1' and d.t_seq=2 and d.label='b'
                )
                and exists (
                    select 1 from %s d join %s r on r.t_id=d.report_observations
                    where r.t_ili_tid='structure-fail-report-1' and d.t_seq=2 and d.label='x'
                )
                and exists (
                    select 1 from %s l join %s i on i.t_id=l.nestedinner_values
                    join %s o on o.t_id=i.nestedouter_items join %s r on r.t_id=o.nestedrecord_tree
                    where r.t_ili_tid='structure-fail-nested-1' and l.t_seq=2 and l.amount=2
                )
                """.formatted(db.table("detail"), db.table("report"), db.table("detail"), db.table("report"),
                db.table("nestedleaf"), db.table("nestedinner"), db.table("nestedouter"), db.table("nestedrecord"));
        if (db.postgis) {
            String function = db.table("reject_detail_import");
            execute(sql, """
                    create function %s() returns trigger language plpgsql as $$
                    begin
                        if %s then raise exception using errcode = 'P0052', message = 'rollback after structure'; end if;
                        return new;
                    end;
                    $$
                    """.formatted(function, condition));
            execute(sql, "create trigger reject_detail_import before insert on " + db.table("detail")
                    + " for each row execute function " + function + "()");
        } else {
            execute(sql, """
                    create trigger reject_detail_import before insert on detail when %s
                    begin select raise(abort, 'rollback after structure'); end
                    """.formatted(condition));
        }
        Path input = Files.writeString(work.resolve("structure-rollback.xtf"), fixture.replace("legacy-", "structure-fail-"));
        var rows = storedIds(db, sql, "report", "detail", "nestedrecord", "nestedouter", "nestedinner", "nestedleaf");
        var rejected = cli(db, "import", model, "structure-fail", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(db.postgis ? "SQL code P0052" : "SQL code"), rejected::diagnostics);
        assertRolledBack(db, sql, "structure-fail", rows);
    }

    private void assertMissingStructureTargetRollback(Target db, Connection sql, String model, int version,
                                                      String fixture) throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String broken = fixture.replace("<Destination " + ref + "=\"legacy-place-3\"",
                "<Destination " + ref + "=\"missing-destination\"");
        Path input = Files.writeString(work.resolve("missing-structure-target.xtf"),
                broken.replace("legacy-", "missing-structure-target-"));
        var rows = storedIds(db, sql, "report", "linkdetail");
        var rejected = cli(db, "import", model, "missing-structure-target", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("missing-structure-target-report-2 at " + model + ".Data.LinkDetail.Destination")
                && rejected.diagnostics().contains("missing-destination"), rejected::diagnostics);
        assertRolledBack(db, sql, "missing-structure-target", rows);
    }

    private void assertMissingReferenceRollback(Target db, Connection sql, String model, int version,
                                                String fixture, String role, String affectedObject, String dataset,
                                                String... affectedTables) throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String roleStart = "<" + role + " " + ref + "=\"";
        String broken = fixture.replaceFirst(roleStart + "legacy-[^\"]+", roleStart + "unresolved-target");
        Path input = Files.writeString(work.resolve(dataset + "-target.xtf"), broken.replace("legacy-", dataset + "-"));
        var rows = storedIds(db, sql, affectedTables);
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, dataset, input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(affectedObject)
                && rejected.diagnostics().contains("unresolved-target"), rejected::diagnostics);
        // PostgreSQL has allocated IDs for the dataset, first basket and all its rows, including both late Hubs.
        long allocated = version == 24 ? 35 : 30;
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertRolledBack(db, sql, dataset, rows);
    }

    private void assertWrongTargetBranchRollback(Target db, Connection sql, String model, int version,
                                                 String fixture) throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String field = "<RootTarget " + ref + "=\"legacy-serviceunit\"/><BranchTarget " + ref + "=";
        String broken = fixture.replace(field + "\"legacy-relay\"", field + "\"legacy-beacon\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve("wrong-branch.xtf"), broken.replace("legacy-", "wrong-branch-"));
        var rows = storedIds(db, sql, "refcarrier", "targetpair");
        var rejected = cli(db, "import", model, "wrong-branch", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("TargetPair.BranchTarget")
                && rejected.diagnostics().contains("wrong-branch-beacon"), rejected::diagnostics);
        assertRolledBack(db, sql, "wrong-branch", rows);
    }

    private void assertWrongHierarchyRoleRollback(Target db, Connection sql, String model, int version,
                                                   String fixture) throws Exception {
        String role = "<Supervisor " + (version == 24 ? "i:ref" : "REF") + "=";
        String broken = fixture.replace(role + "\"legacy-relay\"", role + "\"legacy-beacon\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve("wrong-role-branch.xtf"), broken.replace("legacy-", "wrong-role-branch-"));
        var rows = storedIds(db, sql, "servicebase", "serviceunit", "mast");
        var rejected = cli(db, "import", model, "wrong-role-branch", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("HierarchyCrew.Supervisor")
                && rejected.diagnostics().contains("wrong-role-branch-beacon"), rejected::diagnostics);
        assertRolledBack(db, sql, "wrong-role-branch", rows);
    }

    private void assertWrongStandaloneBranchRollback(Target db, Connection sql, String model, int version,
                                                     String fixture, String association, String role, String dataset,
                                                     String linkTable)
            throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String field = "<" + role + " " + ref + "=";
        String broken = fixture.replace(field + "\"legacy-relay\"", field + "\"legacy-beacon\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve(dataset + ".xtf"), broken.replace("legacy-", dataset + "-"));
        var rows = storedIds(db, sql, "mast", linkTable);
        var rejected = cli(db, "import", model, dataset, input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(association + "." + role)
                && rejected.diagnostics().contains(dataset + "-beacon"), rejected::diagnostics);
        assertRolledBack(db, sql, dataset, rows);
    }

    private static Map<String, Set<String>> storedIds(Target db, Connection sql, String... tables) throws SQLException {
        var rows = new LinkedHashMap<String, Set<String>>();
        for (String table : tables) {
            rows.put(table, catalogKeys(sql, "select t_id from " + db.table(table)));
        }
        return rows;
    }

    private static void assertRolledBack(Target db, Connection sql, String dataset, Map<String, Set<String>> rows)
            throws SQLException {
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='" + dataset + "'"),
                dataset);
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_basket")
                + " where t_ili_tid in ('" + dataset + "-basket-1', '" + dataset + "-basket-2')"), dataset);
        assertEquals(rows, storedIds(db, sql, rows.keySet().toArray(String[]::new)), dataset);
    }

    private static void assertCorruptStructureRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String detail = db.table("detail");
        String parent = scalar(sql, "select t_id from " + db.table("report")
                + " where t_ili_tid='legacy-report-1'");
        String child = scalar(sql, "select t_id from " + detail + " where t_seq=0 and report_entries=" + parent);
        String row = " where t_id=" + child;
        execute(sql, "update " + detail + " set report_summary=report_entries" + row);
        assertExportRejected(db, model, "legacy", output, "owner");
        execute(sql, "update " + detail + " set report_summary=null" + row);

        execute(sql, "update " + detail + " set t_seq=1" + row);
        assertExportRejected(db, model, "legacy", output, "Duplicate or out-of-order T_Seq");
        execute(sql, "update " + detail + " set t_seq=0, report_entries=-1" + row);
        assertExportRejected(db, model, "legacy", output, "Orphan structure row");
        execute(sql, "update " + detail + " set report_entries=" + parent + row);

        String mapping = db.table("t_ili2db_attrname");
        String attribute = " where iliname='" + model + ".Data.Report.Entries'";
        execute(sql, "update " + mapping + " set sqlname='missing_report_entries'" + attribute);
        assertExportRejected(db, model, "legacy", output, "Report.Entries", "missing_report_entries");
        execute(sql, "update " + mapping + " set sqlname='report_entries'" + attribute);
    }

    private void assertNestedSequenceOrder(Target db, Connection sql, String model, int version)
            throws Exception {
        String leaf = db.table("nestedleaf");
        String parent = nestedInnerId(db, sql);
        String positions = " where nestedinner_values=" + parent + " and t_seq in (0, 1)";
        Path output = work.resolve("nested-reordered.xtf");
        execute(sql, "update " + leaf + " set t_seq=1-t_seq" + positions);
        try {
            assertCliSuccess(db, "export", model, "legacy", output);
            var record = children(readDataSection(output, version)).stream().flatMap(basket -> children(basket).stream())
                    .filter(object -> id(object, version, "tid").equals("legacy-nested-1")).findFirst().orElseThrow();
            assertTransferredNestedTree(record, version, List.of("3", "2", "2"));
        } finally {
            execute(sql, "update " + leaf + " set t_seq=1-t_seq" + positions);
        }
    }

    private static void assertCorruptNestedStructureRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String leaf = db.table("nestedleaf");
        String parent = nestedInnerId(db, sql);
        String child = scalar(sql, "select t_id from " + leaf + " where nestedinner_values=" + parent + " and t_seq=0");
        String row = " where t_id=" + child;
        execute(sql, "update " + leaf + " set nestedinner_values=-1" + row);
        try {
            assertExportRejected(db, model, "legacy", output, "Orphan structure row", "NestedInner.Values");
        } finally {
            execute(sql, "update " + leaf + " set nestedinner_values=" + parent + row);
        }
    }

    private static String nestedInnerId(Target db, Connection sql) throws SQLException {
        return scalar(sql, """
                select i.t_id from %s i join %s o on o.t_id=i.nestedouter_items
                join %s r on r.t_id=o.nestedrecord_tree where r.t_ili_tid='legacy-nested-1' and i.t_seq=0
                """.formatted(db.table("nestedinner"), db.table("nestedouter"), db.table("nestedrecord")));
    }

    private static void assertCorruptRoleMappingRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String mapping = db.table("t_ili2db_attrname");
        var roles = Map.of("MarkerHub.Select", "hub", "MarkerLink.LinkedHub", "hub", "Review.Witness", "hub",
                "AnnotatedLink.ConsultedReport", "report");
        for (var entry : roles.entrySet()) {
            String role = model + ".Data." + entry.getKey();
            execute(sql, "update " + mapping + " set target='place' where iliname='" + role + "'");
            assertExportRejected(db, model, "legacy", output, role);
            execute(sql, "update " + mapping + " set target='" + entry.getValue() + "' where iliname='" + role + "'");
        }
    }

    private static void assertWrongStructureBranchRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String notes = db.table("notebase");
        String owner = scalar(sql, "select t_id from " + db.table("typedrecord") + " where t_ili_tid='legacy-typed-1'");
        String main = " where t_id=" + scalar(sql, "select t_id from " + notes + " where typedrecord_main=" + owner);
        String sibling = " where t_id=" + scalar(sql, "select t_id from " + notes
                + " where typedrecord_values=" + owner + " and t_type='flagnote'");
        // Both rows retain valid SQL fragments, but Main is declared as CountNote.
        execute(sql, "update " + notes + " set typedrecord_main=null, typedrecord_values=" + owner + ", t_seq=2" + main);
        execute(sql, "update " + notes + " set typedrecord_values=null, typedrecord_main=" + owner + ", t_seq=0" + sibling);
        try {
            assertExportRejected(db, model, "legacy", output, "TypedRecord.Main", "flagnote");
        } finally {
            execute(sql, "update " + notes + " set typedrecord_main=null, typedrecord_values=" + owner + ", t_seq=2" + sibling);
            execute(sql, "update " + notes + " set typedrecord_values=null, typedrecord_main=" + owner + ", t_seq=0" + main);
        }
    }

    private static void assertCorruptStructureTargetRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String structure = db.table("linkdetail");
        String place = db.table("place");
        String row = " where destination=(select t_id from " + place + " where t_ili_tid='legacy-place-3')";
        execute(sql, "update " + structure + " set destination=(select t_id from " + place
                + " where t_ili_tid='legacy-place-1')" + row);
        assertExportRejected(db, model, "legacy", output,
                model + ".Data.LinkDetail.Destination of object legacy-report-2");
        execute(sql, "update " + structure + " set destination=(select t_id from " + place
                + " where t_ili_tid='legacy-place-3') where destination=(select t_id from " + place
                + " where t_ili_tid='legacy-place-1')");
    }

    private static void assertCorruptPolymorphicTargetsRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String table = db.table("targetpair");
        String pair = " where label='forward' and refcarrier_pairs=(select t_id from " + db.table("refcarrier")
                + " where t_ili_tid='legacy-refcarrier')";
        String change = "update " + table + " set ";
        String branch = scalar(sql, "select branchtarget from " + table + pair);
        String root = scalar(sql, "select roottarget from " + table + pair);
        String sibling = scalar(sql, "select t_id from " + db.table("mast") + " where t_ili_tid='legacy-beacon'");
        String otherBasket = scalar(sql, "select t_id from " + db.table("servicebase") + " where t_ili_tid='native-serviceunit'");

        execute(sql, change + "branchtarget=" + sibling + pair);
        assertExportRejected(db, model, "legacy", output, "TargetPair.BranchTarget");
        execute(sql, change + "branchtarget=" + branch + pair);
        execute(sql, change + "roottarget=" + otherBasket + pair);
        assertExportRejected(db, model, "legacy", output, "TargetPair.RootTarget");
        execute(sql, change + "roottarget=" + root + pair);
    }

    private static void assertCorruptHierarchyRoleRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String mast = db.table("mast");
        String service = db.table("servicebase");
        String row = " where t_ili_tid='legacy-serviceunit'";
        String original = scalar(sql, "select supervisor from " + service + row);
        try {
            for (String tid : List.of("legacy-beacon", "native-relay")) {
                execute(sql, "update " + service + " set supervisor=(select t_id from " + mast
                        + " where t_ili_tid='" + tid + "')" + row);
                assertExportRejected(db, model, "legacy", output, "HierarchyCrew.Supervisor");
            }
        } finally {
            execute(sql, "update " + service + " set supervisor=" + original + row);
        }
    }

    private static void assertCorruptHierarchyLinkRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String links = db.table("hierarchylink");
        String mast = db.table("mast");
        String service = db.table("servicebase");
        String row = " where t_basket=(select t_basket from " + mast + " where t_ili_tid='legacy-relay')";
        String branch = scalar(sql, "select workbranch from " + links + row);
        String staff = scalar(sql, "select staffroot from " + links + row);
        String sibling = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-beacon'");
        String otherBasket = scalar(sql, "select t_id from " + service + " where t_ili_tid='native-serviceunit'");
        try {
            execute(sql, "update " + links + " set workbranch=" + sibling + row);
            assertExportRejected(db, model, "legacy", output, "HierarchyLink.WorkBranch");
            execute(sql, "update " + links + " set workbranch=" + branch + ", staffroot=" + otherBasket + row);
            assertExportRejected(db, model, "legacy", output, "HierarchyLink.StaffRoot");
        } finally {
            execute(sql, "update " + links + " set workbranch=" + branch + ", staffroot=" + staff + row);
        }
    }

    private static void assertCorruptTernaryLinkRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String links = db.table("dispatchrecord");
        String mast = db.table("mast");
        String row = " where t_basket=(select t_basket from " + mast + " where t_ili_tid='legacy-relay')";
        String branch = scalar(sql, "select dispatchmast from " + links + row);
        String sibling = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-beacon'");
        try {
            execute(sql, "update " + links + " set dispatchmast=" + sibling + row);
            assertExportRejected(db, model, "legacy", output, "DispatchRecord.DispatchMast");
        } finally {
            execute(sql, "update " + links + " set dispatchmast=" + branch + row);
        }
    }

    private static void assertCorruptClassHierarchyRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String root = db.table("mast");
        String relay = db.table("arelay");
        String beacon = db.table("beacon");
        String rootRow = " where t_ili_tid='legacy-relay'";
        String id = scalar(sql, "select t_id from " + root + rootRow);
        String basket = scalar(sql, "select t_basket from " + root + rootRow);
        execute(sql, "update " + root + " set t_type='unknown'" + rootRow);
        assertExportRejected(db, model, "legacy", output, "T_Type", "mast");
        execute(sql, "update " + root + " set t_type='arelay'" + rootRow);

        execute(sql, "delete from " + relay + " where t_id=" + id);
        assertExportRejected(db, model, "legacy", output, "relay", "Relay");
        execute(sql, "insert into " + relay + " (t_id,t_basket,aonline) values (" + id + "," + basket + ",false)");

        execute(sql, "insert into " + beacon + " (t_id,t_basket) values (" + id + "," + basket + ")");
        assertExportRejected(db, model, "legacy", output, "beacon", "Beacon");
        execute(sql, "delete from " + beacon + " where t_id=" + id);

        String inheritance = db.table("t_ili2db_inheritance");
        String entry = " where thisclass='" + model + ".Data.Relay'";
        execute(sql, "update " + inheritance + " set baseclass='" + model + ".Data.Mast'" + entry);
        assertExportRejected(db, model, "legacy", output, "INHERITANCE", "Relay");
        execute(sql, "update " + inheritance + " set baseclass='" + model + ".Data.Tower'" + entry);
    }

    private static void assertMissingAssociationFragmentRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String basket = scalar(sql, "select t_id from " + db.table("t_ili2db_basket") + " where t_ili_tid='legacy-basket-2'");
        String id = scalar(sql, "select t_id from " + db.table("allocationbase") + " where t_basket=" + basket);
        assertNotNull(id);
        String leaf = db.table("taggedallocation");
        execute(sql, "delete from " + leaf + " where t_id=" + id);
        try {
            assertExportRejected(db, model, "legacy", output, "Missing inheritance fragment in taggedallocation");
        } finally {
            execute(sql, "insert into " + leaf + " (t_id,t_basket,detailrank) values (" + id + "," + basket + ",17)");
        }
    }

    private static void assertCorruptInheritedOwnerRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String mast = db.table("mast");
        String outer = db.table("nestedouter");
        String relayId = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-relay'");
        String mastId = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-mast'");
        assertNotNull(relayId);
        assertNotNull(mastId);
        execute(sql, "update " + outer + " set tower_supplement=" + mastId + " where tower_supplement=" + relayId);
        try {
            assertExportRejected(db, model, "legacy", output, "Orphan structure row", "Tower.Supplement");
        } finally {
            execute(sql, "update " + outer + " set tower_supplement=" + relayId + " where tower_supplement=" + mastId);
        }
    }

    private void assertClassHierarchyRollback(Target db, Connection sql, String model, String fixture)
            throws Exception {
        String condition = "exists (select 1 from " + db.table("mast") + " m join " + db.table("tower")
                + " t on t.t_id=m.t_id and t.t_basket=m.t_basket"
                + " where m.t_id=new.t_id and m.t_ili_tid='class-fail-relay' and t.height=24)";
        if (db.postgis) {
            String function = db.table("reject_relay_import");
            execute(sql, """
                    create function %s() returns trigger language plpgsql as $$
                    begin
                        if %s then raise exception using errcode = 'P0054', message = 'rollback after parent fragments'; end if;
                        return new;
                    end;
                    $$
                    """.formatted(function, condition));
            execute(sql, "create trigger reject_relay_import before insert on " + db.table("arelay")
                    + " for each row execute function " + function + "()");
        } else {
            execute(sql, """
                    create trigger reject_relay_import before insert on arelay when %s
                    begin select raise(abort, 'rollback after parent fragments'); end
                    """.formatted(condition));
        }
        Path input = Files.writeString(work.resolve("class-rollback.xtf"), fixture.replace("legacy-", "class-fail-"));
        var rows = storedIds(db, sql, "mast", "tower", "arelay");
        var rejected = cli(db, "import", model, "class-fail", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(db.postgis ? "SQL code P0054" : "SQL code"), rejected::diagnostics);
        assertRolledBack(db, sql, "class-fail", rows);
    }

    private void assertUnresolvedHierarchyReferenceRollback(Target db, Connection sql, String model,
                                                             int version, String fixture) throws Exception {
        String dataset = "unresolved-hierarchy-source";
        String ref = version == 24 ? "i:ref" : "REF";
        String noteReference = "            <BranchTarget " + ref + "=";
        String broken = fixture.replace(noteReference + "\"legacy-relay\"", noteReference + "\"missing-branch-target\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve(dataset + ".xtf"), broken.replace("legacy-", dataset + "-"));
        var rows = storedIds(db, sql, "refnotebook", "refnotebase", "refnotebranch", "refnoteleaf");
        var rejected = cli(db, "import", model, dataset, input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.RefNoteBranch.BranchTarget")
                && rejected.diagnostics().contains("missing-branch-target"), rejected::diagnostics);
        assertRolledBack(db, sql, dataset, rows);
    }

    private static void assertWrongRelayReviewOwnerRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String mast = db.table("mast");
        String valid = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-relay'");
        String invalid = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-beacon'");
        String update = "update " + db.table("relayreview") + " set reviewingrelays=";
        execute(sql, update + invalid + " where reviewingrelays=" + valid);
        try {
            assertExportRejected(db, model, "legacy", output,
                    "Missing or inconsistent link owner at " + model + ".Data.RelayReview.Recommended");
        } finally {
            execute(sql, update + valid + " where reviewingrelays=" + invalid);
        }
    }

    private static void assertAmbiguousRatingRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String table = db.table("rating");
        String id = scalar(sql, "select max(t_id)+1000 from " + table);
        execute(sql, """
                insert into %s (t_id, t_basket, rated, ratingforms)
                select %s, r.t_basket, r.rated, r.ratingforms from %s r
                join %s f on f.t_id=r.ratingforms where f.t_ili_tid='legacy-ratingform-1'
                """.formatted(table, id, table, db.table("ratingform")));
        try {
            assertExportRejected(db, model, "legacy", output, "Multiple link rows at " + model + ".Data.Rating.Rated");
        } finally {
            execute(sql, "delete from " + table + " where t_id=" + id);
        }
    }

    private static void assertAmbiguousReviewRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String review = db.table("review");
        String hub = db.table("hub");
        String marker = db.table("marker");
        String duplicateId = scalar(sql, "select max(t_id)+1000 from " + review);
        execute(sql, """
                insert into %s (t_id, t_basket, witness, reviewmarkers)
                select %s, r.t_basket, (select t_id from %s where t_ili_tid='legacy-hub-2'), r.reviewmarkers
                from %s r join %s m on m.t_id=r.reviewmarkers where m.t_ili_tid='legacy-marker-1'
                """.formatted(review, duplicateId, hub, review, marker));
        assertExportRejected(db, model, "legacy", output, "Review.Witness");
        execute(sql, "delete from " + review + " where t_id=" + duplicateId);
    }
}
