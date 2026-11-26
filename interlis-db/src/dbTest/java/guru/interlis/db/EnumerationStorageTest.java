package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.EnumerationFixtures.BASE;
import static guru.interlis.db.EnumerationFixtures.assertItfRows;
import static guru.interlis.db.EnumerationFixtures.mappedTable;
import static guru.interlis.db.EnumerationFixtures.rows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnumerationStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void itfCodesAndRollback(Backend backend, int version) throws Exception {
        var db = target(backend, "itf");
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumItf" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.source(model, BASE), "--create-enum-col-as-itf-code");
        Path output = roundTrip(db, model, "native", fixtures.scalars(model, "native"));
        fixtures.assertScalars(output, "native");
        try (var sql = db.connect()) {
            assertItfRows(db, sql, "native");
            execute(sql, "UPDATE " + db.table("asset") + " SET gradevalue=99 WHERE t_ili_tid='native-value'");
            assertExportRejected(db, model, "native", output, "Unknown numeric enumeration value 99");

            var broken = fixtures.dataset(model, "broken", fixtures.object(model, "Asset", "broken-first", "<GradeValue>Zulu</GradeValue>")
                    + fixtures.object(model, "Asset", "broken-last", "<GradeValue>Family.Missing</GradeValue>"));
            assertCliFailure(db, "import", model, "broken", broken, "Cannot encode enumeration value Family.Missing");
            assertOnlyDataset(db, sql);
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void editedTextAndStoredLookupName(Backend backend, int version) throws Exception {
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumLabels" + version;
        Path ili = fixtures.textModel(model);
        var labelOptions = List.of("--create-enum-txt-col", "--beautify-enum-disp-name");
        for (boolean numeric : List.of(false, true)) {
            var db = target(backend, "label-" + numeric);
            var options = new java.util.ArrayList<>(labelOptions);
            if (numeric) options.add("--create-enum-col-as-itf-code");
            assertSchemaWithIdentityColumns(db, model, ili, options.toArray(String[]::new));
            Path output = roundTrip(db, model, "native", fixtures.scalars(model, "native"));
            try (var sql = db.connect()) {
                String row = " FROM " + db.table("asset") + " WHERE t_ili_tid='native-value'";
                assertEquals(numeric ? "2" : "Family.Alpha", scalar(sql, "SELECT gradevalue" + row));
                assertEquals("Alpine label", scalar(sql, "SELECT gradevalue_txt" + row));
                assertEquals("No Sign", scalar(sql, "SELECT directvalue_txt" + row));
                assertEquals("true", scalar(sql, "SELECT aactive_txt" + row));
                execute(sql, "UPDATE " + db.table("asset") + " SET gradevalue_txt='editor label' WHERE t_ili_tid='native-value'");
                assertCliSuccess(db, "export", model, "native", output);
                fixtures.assertScalars(output, "native");
                var empty = fixtures.dataset(model, "native", fixtures.object(model, "Asset", "native-value", "")
                        + fixtures.object(model, "Asset", "native-missing", ""));
                assertCliSuccess(db, "update", model, "native", empty);
                for (String column : List.of("gradevalue", "gradevalue_txt", "directvalue_txt", "aactive_txt")) {
                    assertNull(scalar(sql, "SELECT " + column + row), column);
                }
            }
        }

        var db = target(backend, "lookup-name");
        String lookupModel = "EnumTables" + version;
        assertSchemaWithIdentityColumns(db, lookupModel, fixtures.source(lookupModel,
                BASE + "CLASS Asset_DirectValue = Memo : TEXT*40; END Asset_DirectValue;"), "--create-enum-tabs");
        try (var sql = db.connect()) {
            String collision = mappedTable(db, sql, lookupModel + ".Data.Asset_DirectValue");
            assertEquals("asset_directvalue", collision);
            assertNotEquals(collision, mappedTable(db, sql, lookupModel + ".Data.Asset.DirectValue"));
            String grade = mappedTable(db, sql, lookupModel + ".Data.Grade");
            execute(sql, "ALTER TABLE " + db.table(grade) + " RENAME TO stored_grade");
            execute(sql, "UPDATE " + db.table("t_ili2db_classname") + " SET sqlname='stored_grade' WHERE sqlname='" + grade + "'");
            execute(sql, "UPDATE " + db.table("t_ili2db_table_prop") + " SET tablename='stored_grade' WHERE tablename='" + grade + "'");
            if (!db.postgis()) {
                execute(sql, "UPDATE gpkg_contents SET table_name='stored_grade', identifier='stored_grade' WHERE table_name='" + grade + "'");
            }
            var output = roundTrip(db, lookupModel, "native", fixtures.scalars(lookupModel, "native"));
            fixtures.assertScalars(output, "native");
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("stored_grade")));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void extendedInlineLabelsFollowTheConcreteType(Backend backend, int version) throws Exception {
        var db = target(backend, "inline-overrides");
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumInlineLabels" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.source(model, EnumerationFixtures.INLINE_OVERRIDES),
                "--create-enum-txt-col", "--beautify-enum-disp-name");
        Path output = roundTrip(db, model, "native", fixtures.inlineDataset(model, "native"));
        try (var sql = db.connect()) {
            String row = " FROM " + db.table("meter") + " WHERE t_ili_tid='native-";
            assertEquals("standby", scalar(sql, "SELECT astate_txt" + row + "base'"));
            assertEquals("Ready label", scalar(sql, "SELECT astate_txt" + row + "ready'"));
            assertEquals("out of service", scalar(sql, "SELECT astate_txt" + row + "fallback'"));
            assertNull(scalar(sql, "SELECT astate_txt" + row + "missing'"));
            execute(sql, "UPDATE " + db.table("meter") + " SET astate_txt='edited' WHERE t_ili_tid='native-ready'");
        }
        assertCliSuccess(db, "export", model, "native", output);
        fixtures.assertInlineOverrides(output, "native");
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void singleLookupUsesTheCopiedInlineDefinition(Backend backend, int version) throws Exception {
        var db = target(backend, "inline-single");
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumInlineCopied" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.source(model, EnumerationFixtures.ABSTRACT_INLINE_OVERRIDES),
                "--inheritance", "smart1", "--create-single-enum-tab");
        Path output = roundTrip(db, model, "native", fixtures.inlineDataset(model, "native", false));
        fixtures.assertInlineOverrides(output, "native", false);
        try (var sql = db.connect()) {
            String lookup = db.table("t_ili2db_enum");
            String base = model + ".Data.Meter.State";
            String derived = model + ".Data.SmartUnit.State";
            assertEquals(List.of(List.of(derived, base, "idle", "0"),
                            List.of(derived, base, "active.ready", "1"),
                            List.of(derived, base, "active.busy", "2"),
                            List.of(derived, base, "standby", "3"),
                            List.of(derived, base, "out_of_service", "4")),
                    rows(sql, "SELECT thisclass,baseclass,ilicode,itfcode FROM " + lookup + " ORDER BY itfcode"));
            assertEquals("0", scalar(sql, "SELECT COUNT(seq) FROM " + lookup));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void perLookupStoresBothInlineDefinitions(Backend backend, int version) throws Exception {
        var db = target(backend, "inline-per-definition");
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumInlineTables" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.source(model, EnumerationFixtures.INLINE_OVERRIDES),
                "--create-enum-tabs");
        Path output = roundTrip(db, model, "native", fixtures.inlineDataset(model, "native"));
        fixtures.assertInlineOverrides(output, "native");
        try (var sql = db.connect()) {
            String base = mappedTable(db, sql, model + ".Data.Meter.State");
            String derived = mappedTable(db, sql, model + ".Data.SmartUnit.State");
            assertNotEquals(base, derived);
            assertEquals(List.of(List.of("idle", "0"), List.of("active", "1"), List.of("standby", "2")),
                    rows(sql, "SELECT ilicode,itfcode FROM " + db.table(base) + " ORDER BY itfcode"));
            assertEquals(List.of(List.of("idle", "0"), List.of("active.ready", "1"), List.of("active.busy", "2"),
                            List.of("standby", "3"), List.of("out_of_service", "4")),
                    rows(sql, "SELECT ilicode,itfcode FROM " + db.table(derived) + " ORDER BY itfcode"));
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void storedIdsAndInvalidLookupRows(Backend backend, int version) throws Exception {
        var db = target(backend, "stored-ids");
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumExtended" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.extensionModel(model), "--create-enum-tabs-with-id");
        String grade = db.table("grade");
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + grade + " SET t_id=7001 WHERE thisclass='" + model + ".Data.Grade' AND ilicode='Family.Alpha'");
            execute(sql, "UPDATE " + db.table("asset_directvalue") + " SET t_id=8111 WHERE ilicode='No_Sign'");
            Path output = roundTrip(db, model, "native", fixtures.extensionDataset(model, "native"));
            fixtures.assertExtension(output, "native");
            String table = db.table("asset");
            String pair = " FROM " + table + " WHERE t_ili_tid='native-pair'";
            String derived = scalar(sql, "SELECT derivedvalue" + pair);
            assertEquals("7001", scalar(sql, "SELECT gradevalue" + pair));
            assertEquals("8111", scalar(sql, "SELECT directvalue" + pair));
            assertNotEquals("7001", derived);
            assertNull(scalar(sql, "SELECT gradevalue FROM " + table + " WHERE t_ili_tid='native-missing'"));

            execute(sql, "UPDATE " + table + " SET derivedvalue=7001 WHERE t_ili_tid='native-pair'");
            assertExportRejected(db, model, "native", output, "Unknown numeric enumeration value 7001");
            execute(sql, "UPDATE " + table + " SET derivedvalue=" + derived + ", gradevalue=9000 WHERE t_ili_tid='native-pair'");
            assertExportRejected(db, model, "native", output, "Unknown numeric enumeration value 9000");
            execute(sql, "UPDATE " + table + " SET gradevalue=7001 WHERE t_ili_tid='native-pair'");

            execute(sql, "INSERT INTO " + grade
                    + " (t_id,thisclass,baseclass,itfcode,ilicode,seq,dispname,description,inactive)"
                    + " SELECT 9000,thisclass,baseclass,itfcode,ilicode,seq,dispname,description,inactive"
                    + " FROM " + grade + " WHERE t_id=7001");
            assertExportRejected(db, model, "native", output, "Ambiguous enumeration lookup");
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void appendModelsAndRollbackOnLookupConflict(Backend backend, int version) throws Exception {
        var db = target(backend, "append");
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumBase" + version;
        var sources = fixtures.appendModels(model);
        assertSchemaWithIdentityColumns(db, model, sources.base(), "--create-enum-tabs-with-id");
        assertCliSuccess(db, "import", model, "base", fixtures.scalars(model, "base"));
        try (var sql = db.connect()) {
            String grade = db.table("grade");
            String original = " WHERE thisclass='" + model + ".Grade'";
            execute(sql, "UPDATE " + grade + " SET dispname='kept editor label'" + original + " AND ilicode='Family.Alpha'");
            String existing = "SELECT t_id,ilicode,itfcode,dispname FROM " + grade + original + " ORDER BY itfcode";
            var before = rows(sql, existing);
            assertSchemaWithIdentityColumns(db, sources.independent(), sources.standalone(), "--add-models");
            assertSchemaWithIdentityColumns(db, sources.added(), sources.addition(), "--add-models", "--model-file", sources.base().toString());
            assertEquals(before, rows(sql, existing));

            String broken = sources.broken();
            String constraint = "CONSTRAINT blocked_enum CHECK (thisclass <> '" + broken + ".BrokenGrade')";
            execute(sql, "ALTER TABLE " + grade + (db.postgis() ? " ADD " : " ADD COLUMN blocked_enum INTEGER ") + constraint);
            var failure = schemaWithIdentityColumns(db, broken, sources.conflict(), CancellationToken.none(),
                    "--add-models", "--model-file", sources.base().toString());
            assertEquals(1, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("Cannot write enumeration lookup rows for grade"), failure::diagnostics);
            String abortTable = db.postgis()
                    ? "information_schema.tables WHERE table_schema='" + db.schema() + "' AND table_name='abortasset'"
                    : "sqlite_schema WHERE type='table' AND name='abortasset'";
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + abortTable));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_classname") + " WHERE iliname LIKE '" + broken + ".%'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_model") + " WHERE modelname LIKE '" + broken + "%'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + grade + " WHERE thisclass='" + broken + ".BrokenGrade'"));
            assertEquals(before, rows(sql, existing));
        }
        Path output = work.resolve("base-after-rollback.xtf");
        assertCliSuccess(db, "export", model, "base", output);
        fixtures.assertScalars(output, "base");
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void numericArrayFailures(Backend backend, int version) throws Exception {
        var fixtures = new EnumerationFixtures(work, version);
        String model = "EnumArrayNumbers" + version;
        Path ili = fixtures.arrayModel(model);
        var values = List.of("Family.Alpha", "Zulu", "Family.Alpha");
        for (String flag : List.of("--create-enum-col-as-itf-code", "--create-enum-tabs-with-id")) {
            var db = target(backend, flag.substring(2));
            assertSchemaWithIdentityColumns(db, model, ili, "--coalesce-array", flag);
            Path output = roundTrip(db, model, "native", fixtures.numericDataset(model, "native", values));
            fixtures.assertNumericArray(output, "native", values);
            try (var sql = db.connect()) {
                var invalid = fixtures.dataset(model, "broken", fixtures.object(model, "Asset", "broken-first", fixtures.numericFields(model, values))
                        + fixtures.object(model, "Asset", "broken-last", fixtures.numericFields(model, List.of("Family.Unknown"))));
                assertCliFailure(db, "import", model, "broken", invalid, "Cannot encode enumeration value Family.Unknown");
                assertOnlyDataset(db, sql);
                String invalidSql = db.postgis() ? "ARRAY[9999]::bigint[]" : "'[9999]'";
                execute(sql, "UPDATE " + db.table("asset") + " SET grades=" + invalidSql + " WHERE t_ili_tid='native-value'");
                assertExportRejected(db, model, "native", output, "Unknown numeric enumeration value 9999");
            }
        }
        String expanded = "EnumExpandedGuard" + version;
        var source = fixtures.source(expanded, """
                DOMAIN Grade = (Zulu, Alpha);
                STRUCTURE Member = Value : Grade; END Member;
                CLASS Asset =
                  !!@ili2db.mapping=EXPAND
                  Detail : Member;
                END Asset;
                """);
        var rejected = schemaWithIdentityColumns(target(backend, "guard"), expanded, source, CancellationToken.none(),
                "--expand-struct", "--create-enum-col-as-itf-code");
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("enumeration column options for expanded field"), rejected::diagnostics);
    }

    private Target target(Backend backend, String name) {
        return POSTGIS.target(work.resolve(name + ".gpkg"), backend);
    }

    private Path roundTrip(Target db, String model, String dataset, Path input) {
        Path output = work.resolve(dataset + "-out.xtf");
        assertCliSuccess(db, "import", model, dataset, input);
        assertCliSuccess(db, "export", model, dataset, output);
        return output;
    }

    private static void assertOnlyDataset(Target db, Connection sql) throws Exception {
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
    }
}
