package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnumerationCases {
    private static final String BASE = """
            DOMAIN Grade = (Zulu, Family (Zed, Alpha), Last_Code) ORDERED;
            CLASS Asset =
              GradeValue : Grade;
              DirectValue : (No_Sign, Present);
              Active : BOOLEAN;
            END Asset;
            """;

    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    EnumerationCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void itfCodes(TestDatabase db) throws Exception {
        String model = "EnumItf" + version;
        source(model, BASE);
        schema(db, model, "--createEnumColAsItfCode");
        mixScalars(db, model);
        try (var sql = db.connect()) {
            assertItfRows(db, sql, "legacy");
            assertItfRows(db, sql, "native");
            String assets = db.table("asset");
            Path output = work.resolve("output.xtf");
            byte[] previous = Files.readAllBytes(output);
            execute(sql, "UPDATE " + assets + " SET gradevalue = 99 WHERE t_ili_tid = 'legacy-value'");
            assertCliFailure(db, "export", model, "legacy", output, "Unknown numeric enumeration value 99");
            assertArrayEquals(previous, Files.readAllBytes(output));

            var broken = dataset(model, "broken", object(model, "Asset", "broken-first", "<GradeValue>Zulu</GradeValue>")
                    + object(model, "Asset", "broken-last", "<GradeValue>Family.Missing</GradeValue>"));
            assertCliFailure(db, "import", model, "broken", broken, "Cannot encode enumeration value Family.Missing");
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + assets));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
        }
    }

    void itfSchema(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumItfSchema" + version;
        var ili = source(model, BASE);
        schema(expected, model, "--createEnumColAsItfCode");
        create(own, model, ili, "--create-enum-col-as-itf-code");
        assertSameCatalog(expected, own);
        for (var db : List.of(expected, own)) {
            mixScalars(db, model);
            try (var sql = db.connect()) {
                assertItfRows(db, sql, "legacy");
                assertItfRows(db, sql, "native");
                assertEquals("1", scalar(sql, "SELECT CASE WHEN aactive THEN 1 ELSE 0 END FROM "
                        + db.table("asset") + " WHERE t_ili_tid = 'native-value'"));
            }
        }
    }

    private static void assertSameCatalog(TestDatabase expected, TestDatabase actual) throws Exception {
        try (var left = expected.connect(); var right = actual.connect()) {
            DbSchemaCatalog.read(left, expected.postgis(), expected.schema())
                    .assertSameAs(DbSchemaCatalog.read(right, actual.postgis(), actual.schema()));
        }
    }

    void enumText(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumLabels" + version;
        var ili = source(model, BASE.replace("Zed, Alpha", """
                Zed,
                  !!@ili2db.dispName="Alpine label"
                  Alpha"""));
        schema(expected, model, "--createEnumTxtCol", "--beautifyEnumDispName");
        create(own, model, ili, "--create-enum-txt-col", "--beautify-enum-disp-name");
        assertSameCatalog(expected, own);
        textRoundtrip(expected, model, "Family.Alpha");
        textRoundtrip(own, model, "Family.Alpha");

        var integerReference = own.peer("enum_txt_itf_ref");
        var integerOwn = own.peer("enum_txt_itf_own");
        schema(integerReference, model, "--createEnumTxtCol", "--beautifyEnumDispName", "--createEnumColAsItfCode");
        create(integerOwn, model, ili, "--create-enum-txt-col", "--beautify-enum-disp-name", "--create-enum-col-as-itf-code");
        assertSameCatalog(integerReference, integerOwn);
        textRoundtrip(integerReference, model, "2");
        textRoundtrip(integerOwn, model, "2");
    }

    void singleLookup(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumSingle" + version;
        var ili = source(model, BASE.replace("Zed, Alpha", """
                Zed,
                  /** Alpine documentation */
                  !!@ili2db.dispName="Alpine label"
                  Alpha""").replace("CLASS Asset", """
                ExtraGrade = (Zulu, Spare);
                ExtendedGrade EXTENDS ExtraGrade = (Other_Value);
                CLASS Asset"""));
        schema(expected, model, "--createSingleEnumTab", "--beautifyEnumDispName");
        create(own, model, ili, "--create-single-enum-tab", "--beautify-enum-disp-name");
        assertSameCatalog(expected, own);
        try (var left = expected.connect(); var right = own.connect()) {
            String columns = "thisclass, baseclass, ilicode, itfcode, seq, dispname, description, inactive";
            String order = " ORDER BY thisclass, itfcode";
            String table = own.table("t_ili2db_enum");
            String query = "SELECT " + columns + " FROM " + table + order;
            var initial = rows(right, query);
            assertEquals(rows(left, "SELECT " + columns + " FROM " + expected.table("t_ili2db_enum") + order), initial);
            assertEquals(11, initial.size());
            assertEquals("Alpine label", scalar(right, "SELECT dispname FROM " + table
                    + " WHERE ilicode = 'Family.Alpha'"));
            assertEquals("2", scalar(right, "SELECT seq FROM " + table + " WHERE ilicode = 'Family.Alpha'"));
            assertEquals(model + ".Data.ExtraGrade", scalar(right, "SELECT baseclass FROM " + table
                    + " WHERE thisclass = '" + model + ".Data.ExtendedGrade' AND ilicode = 'Other_Value'"));
            assertEquals("3", scalar(right, "SELECT count(*) FROM " + table + " WHERE ilicode = 'Zulu'"));
        }
        for (var db : List.of(expected, own)) {
            try (var sql = db.connect()) {
                String table = db.table("t_ili2db_enum");
                execute(sql, "UPDATE " + table + " SET dispname = 'editor label', inactive = TRUE WHERE ilicode = 'Family.Alpha'");
                String query = "SELECT thisclass, ilicode, itfcode, dispname, inactive FROM " + table + " ORDER BY thisclass, itfcode";
                var before = rows(sql, query);
                mixScalars(db, model);
                assertEquals(before, rows(sql, query));
            }
        }
    }

    void perLookup(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumTables" + version;
        var ili = source(model, BASE + "CLASS Asset_DirectValue = Memo : TEXT*40; END Asset_DirectValue;");
        schema(expected, model, "--createEnumTabs");
        create(own, model, ili, "--create-enum-tabs");
        assertSameCatalog(expected, own);
        for (var db : List.of(expected, own)) {
            try (var sql = db.connect()) {
                String direct = mappedTable(db, sql, model + ".Data.Asset.DirectValue");
                String collision = mappedTable(db, sql, model + ".Data.Asset_DirectValue");
                assertEquals("asset_directvalue", collision);
                assertNotEquals(collision, direct);
                assertEquals("No_Sign", scalar(sql, "SELECT ilicode FROM " + db.table(direct) + " WHERE itfcode = 0"));
                String bool = mappedTable(db, sql, "INTERLIS.BOOLEAN");
                assertEquals(List.of(List.of("false", "0"), List.of("true", "1")),
                        rows(sql, "SELECT ilicode, itfcode FROM " + db.table(bool) + " ORDER BY itfcode"));
                String grade = mappedTable(db, sql, model + ".Data.Grade");
                assertEquals("Family.Alpha", scalar(sql, "SELECT ilicode FROM " + db.table(grade) + " WHERE itfcode = 2"));
                execute(sql, "ALTER TABLE " + db.table(grade) + " RENAME TO stored_grade");
                execute(sql, "UPDATE " + db.table("t_ili2db_classname") + " SET sqlname = 'stored_grade' WHERE sqlname = '" + grade + "'");
                execute(sql, "UPDATE " + db.table("t_ili2db_table_prop") + " SET tablename = 'stored_grade' WHERE tablename = '" + grade + "'");
                if (!db.postgis()) {
                    execute(sql, "UPDATE gpkg_contents SET table_name = 'stored_grade', identifier = 'stored_grade' WHERE table_name = '" + grade + "'");
                }
            }
            mixScalars(db, model);
            try (var sql = db.connect()) {
                assertEquals("Family.Alpha", scalar(sql, "SELECT gradevalue FROM " + db.table("asset")
                        + " WHERE t_ili_tid = 'native-value'"));
                assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("stored_grade")));
            }
        }
    }

    private static String mappedTable(TestDatabase db, Connection sql, String path) throws Exception {
        return scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname") + " WHERE iliname = '" + path + "'");
    }

    void storedIds(TestDatabase db) throws Exception {
        String model = "EnumStoredIds" + version;
        source(model, BASE);
        schema(db, model, "--createEnumTabsWithId");
        try (var sql = db.connect()) {
            String grade = db.table(mappedTable(db, sql, model + ".Data.Grade"));
            String direct = db.table(mappedTable(db, sql, model + ".Data.Asset.DirectValue"));
            execute(sql, "UPDATE " + grade + " SET t_id = 7001 WHERE ilicode = 'Family.Alpha'");
            execute(sql, "UPDATE " + direct + " SET t_id = 8111 WHERE ilicode = 'No_Sign'");
            mixScalars(db, model);
            String assets = db.table("asset");
            for (var prefix : List.of("legacy", "native")) {
                String from = " FROM " + assets + " WHERE t_ili_tid = '" + prefix;
                assertEquals("7001", scalar(sql, "SELECT gradevalue" + from + "-value'"));
                assertEquals("8111", scalar(sql, "SELECT directvalue" + from + "-value'"));
                assertNull(scalar(sql, "SELECT gradevalue" + from + "-missing'"));
            }

            var output = work.resolve("output.xtf");
            byte[] existing = Files.readAllBytes(output);
            execute(sql, "UPDATE " + assets + " SET gradevalue = 9000 WHERE t_ili_tid = 'legacy-value'");
            assertCliFailure(db, "export", model, "legacy", output, "Unknown numeric enumeration value 9000");
            assertArrayEquals(existing, Files.readAllBytes(output));
            execute(sql, "UPDATE " + assets + " SET gradevalue = 7001 WHERE t_ili_tid = 'legacy-value'");

            execute(sql, "INSERT INTO " + grade + """
                     (t_id, thisclass, baseclass, itfcode, ilicode, seq, dispname, description, inactive)
                     SELECT 9000, thisclass, baseclass, itfcode, ilicode, seq, dispname, description, inactive
                     FROM
                    """ + grade + " WHERE t_id = 7001");
            assertCliFailure(db, "export", model, "legacy", output, "Ambiguous enumeration lookup");
            assertArrayEquals(existing, Files.readAllBytes(output));
        }
    }

    void idSchema(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumIdSchema" + version;
        var ili = source(model, BASE);
        schema(expected, model, "--createEnumTabsWithId");
        create(own, model, ili, "--create-enum-tabs-with-id");
        assertSameCatalog(expected, own);
        assertLookupContents(expected, own, "grade", "asset_directvalue");
        for (var db : List.of(expected, own)) {
            mixScalars(db, model);
            referenceImport(db, "tail", scalars(model, "tail"));
            var output = work.resolve("output.xtf");
            assertCliSuccess(db, "export", model, "tail", output);
            assertScalars(output, "tail");
            try (var sql = db.connect()) {
                String gradeId = scalar(sql, "SELECT t_id FROM " + db.table("grade") + " WHERE ilicode = 'Family.Alpha'");
                String directId = scalar(sql, "SELECT t_id FROM " + db.table("asset_directvalue") + " WHERE ilicode = 'No_Sign'");
                for (var prefix : List.of("legacy", "native", "tail")) {
                    String row = " FROM " + db.table("asset") + " WHERE t_ili_tid = '" + prefix + "-value'";
                    assertEquals(gradeId, scalar(sql, "SELECT gradevalue" + row));
                    assertEquals(directId, scalar(sql, "SELECT directvalue" + row));
                }
            }
        }
    }

    private static void assertLookupContents(TestDatabase expected, TestDatabase own, String... tables) throws Exception {
        try (var left = expected.connect(); var right = own.connect()) {
            for (var table : tables) {
                String columns = "SELECT thisclass, baseclass, ilicode, itfcode, seq, dispname, description, inactive FROM ";
                String order = " ORDER BY thisclass, itfcode";
                assertEquals(rows(left, columns + expected.table(table) + order),
                        rows(right, columns + own.table(table) + order), table);
            }
        }
    }

    void idExtension(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumExtended" + version;
        var ili = source(model, BASE.replace("CLASS Asset", """
                MoreGrade EXTENDS Grade = (Family (Extra), Final_Code);
                OrderedGrade EXTENDS Grade = (Other) ORDERED;
                CLASS Asset""").replace("GradeValue : Grade;", """
                GradeValue : Grade;
                  DerivedValue : MoreGrade;"""));
        schema(expected, model, "--createEnumTabsWithId");
        create(own, model, ili, "--create-enum-tabs-with-id");
        assertSameCatalog(expected, own);
        assertLookupContents(expected, own, "grade", "asset_directvalue");
        var output = work.resolve("output.xtf");
        for (var db : List.of(expected, own)) {
            referenceImport(db, "legacy", extensionDataset(model, "legacy"));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertExtension(output, "legacy");
            assertCliSuccess(db, "import", model, "native", extensionDataset(model, "native"));
            referenceExport(db, "native", output);
            assertExtension(output, "native");

            try (var sql = db.connect()) {
                String table = db.table("grade");
                String parent = model + ".Data.Grade";
                String derived = model + ".Data.MoreGrade";
                String inherited = " AND ilicode = 'Family.Alpha'";
                String lookup = "SELECT t_id FROM " + table + " WHERE thisclass = '";
                String baseId = scalar(sql, lookup + parent + "'" + inherited);
                String derivedId = scalar(sql, lookup + derived + "'" + inherited);
                assertNotEquals(baseId, derivedId);
                assertNull(scalar(sql, "SELECT seq FROM " + table + " WHERE thisclass = '" + derived + "'" + inherited));
                assertEquals("2", scalar(sql, "SELECT seq FROM " + table + " WHERE thisclass = '" + model
                        + ".Data.OrderedGrade'" + inherited));
                String assets = db.table("asset");
                assertEquals(baseId, scalar(sql, "SELECT gradevalue FROM " + assets + " WHERE t_ili_tid = 'native-pair'"));
                assertEquals(derivedId, scalar(sql, "SELECT derivedvalue FROM " + assets + " WHERE t_ili_tid = 'native-pair'"));
                execute(sql, "UPDATE " + assets + " SET derivedvalue = " + baseId + " WHERE t_ili_tid = 'native-pair'");
                byte[] previous = Files.readAllBytes(output);
                assertCliFailure(db, "export", model, "native", output, "Unknown numeric enumeration value " + baseId);
                assertArrayEquals(previous, Files.readAllBytes(output));
            }
        }
    }

    private Path extensionDataset(String model, String prefix) throws Exception {
        return dataset(model, prefix, object(model, "Asset", prefix + "-pair",
                "<GradeValue>Family.Alpha</GradeValue><DerivedValue>Family.Alpha</DerivedValue>")
                + object(model, "Asset", prefix + "-extra", "<DerivedValue>Family.Extra</DerivedValue>"));
    }

    private void assertExtension(Path output, String prefix) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var objects = children(basket).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        assertEquals("Family.Alpha", child(objects.get(prefix + "-pair"), "GradeValue").getTextContent());
        assertEquals("Family.Alpha", child(objects.get(prefix + "-pair"), "DerivedValue").getTextContent());
        assertEquals("Family.Extra", child(objects.get(prefix + "-extra"), "DerivedValue").getTextContent());
    }

    void appendEnums(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumBase" + version;
        int domainEnd = BASE.indexOf("CLASS Asset");
        Path base = source(model, BASE.substring(0, domainEnd), BASE.substring(domainEnd));
        schema(expected, model, "--createEnumTabsWithId");
        create(own, model, base, "--create-enum-tabs-with-id");
        String added = "EnumAppend" + version;
        Path addition = source(added, """
                IMPORTS %s;
                DOMAIN MoreGrade EXTENDS %s.Grade = (Family (Extra), Final_Code);
                       Status = (Open_Code, Closed_Code) ORDERED;
                """.formatted(model, model), """
                CLASS Detail =
                  Comparison : %s.Grade;
                  Choice : MoreGrade;
                  StatusValue : Status;
                END Detail;
                """.formatted(model));
        String independent = "EnumIndependent" + version;
        Path standalone = source(independent, """
                DOMAIN Stage = (Start_Code, End_Code);
                CLASS IndependentAsset = StageValue : Stage; END IndependentAsset;
                """);
        String broken = "EnumBrokenAppend" + version;
        Path conflict = source(broken, """
                IMPORTS %s;
                DOMAIN BrokenGrade EXTENDS %s.Grade = (Unstorable);
                """.formatted(model, model), "CLASS AbortAsset = Value : BrokenGrade; END AbortAsset;");
        var output = work.resolve("output.xtf");
        for (var db : List.of(expected, own)) {
            referenceImport(db, "base", scalars(model, "base"));
            try (var sql = db.connect()) {
                String grade = db.table("grade");
                String original = " WHERE thisclass = '" + model + ".Grade'";
                execute(sql, "UPDATE " + grade + " SET dispname = 'kept editor label'" + original + " AND ilicode = 'Family.Alpha'");
                String existing = "SELECT t_id, ilicode, itfcode, dispname FROM " + grade + original + " ORDER BY itfcode";
                var before = rows(sql, existing);
                create(db, independent, standalone, "--add-models");
                create(db, added, addition, "--add-models", "--model-file", base.toString());
                assertEquals(before, rows(sql, existing));
                String status = mappedTable(db, sql, added + ".Status");
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(status)
                        + " WHERE thisclass = '" + added + ".Status'"));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("stage")
                        + " WHERE thisclass = '" + independent + ".Data.Stage'"));

                String fields = "<Comparison>Family.Alpha</Comparison><Choice>Family.Extra</Choice><StatusValue>Closed_Code</StatusValue>";
                referenceImport(db, "legacy", dataset(added, "legacy", object(added, "Detail", "legacy-value", fields)));
                assertCliSuccess(db, "export", added, "legacy", output);
                assertAppended(output, "legacy");
                assertCliSuccess(db, "import", added, "native", dataset(added, "native", object(added, "Detail", "native-value", fields)));
                referenceExport(db, "native", output);
                assertAppended(output, "native");

                String column = db.postgis()
                        ? "ADD CONSTRAINT blocked_enum CHECK (thisclass <> '" + broken + ".BrokenGrade')"
                        : "ADD COLUMN blocked_enum INTEGER CONSTRAINT blocked_enum CHECK (thisclass <> '" + broken + ".BrokenGrade')";
                execute(sql, "ALTER TABLE " + grade + " " + column);
                var result = schemaWithIdentityColumns(db, broken, conflict, CancellationToken.none(),
                        "--add-models", "--model-file", base.toString());
                assertEquals(1, result.code(), result::diagnostics);
                assertTrue(result.diagnostics().contains("Cannot write enumeration lookup rows for grade"), result::diagnostics);
                String abortTable = db.postgis()
                        ? "information_schema.tables WHERE table_schema = '" + db.schema() + "' AND table_name = 'abortasset'"
                        : "sqlite_schema WHERE type = 'table' AND name = 'abortasset'";
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + abortTable));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_classname")
                        + " WHERE iliname LIKE '" + broken + ".%'"));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_model")
                        + " WHERE modelname LIKE '" + broken + "%'"));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + grade + " WHERE thisclass = '" + broken + ".BrokenGrade'"));
                assertEquals(before, rows(sql, existing));
            }
            assertCliSuccess(db, "export", model, "base", output);
            assertScalars(output, "base");
            referenceExport(db, "base", output);
            assertScalars(output, "base");
        }
    }

    private void assertAppended(Path output, String prefix) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var row = children(basket).getFirst();
        assertEquals(prefix + "-value", id(row, version, "tid"));
        assertEquals("Family.Alpha", child(row, "Comparison").getTextContent());
        assertEquals("Family.Extra", child(row, "Choice").getTextContent());
        assertEquals("Closed_Code", child(row, "StatusValue").getTextContent());
    }

    void numericArrays(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumArrayNumbers" + version;
        var ili = source(model, """
                DOMAIN Grade = (Zulu, Family (Zed, Alpha), Last_Code) ORDERED;
                %s
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Grades : LIST OF %s;
                END Asset;
                """.formatted(version == 23 ? "STRUCTURE Member = Value : Grade; END Member;" : "",
                version == 23 ? "Member" : "Grade"));
        schema(expected, model, "--coalesceArray", "--createEnumColAsItfCode");
        create(own, model, ili, "--coalesce-array", "--create-enum-col-as-itf-code");
        assertSameCatalog(expected, own);
        for (var db : List.of(expected, own)) numericArrayRoundtrip(db, model, false);

        var refs = own.peer("enum_array_ids_ref");
        var nativeIds = own.peer("enum_array_ids_own");
        schema(refs, model, "--coalesceArray", "--createEnumTabsWithId");
        create(nativeIds, model, ili, "--coalesce-array", "--create-enum-tabs-with-id");
        assertSameCatalog(refs, nativeIds);
        assertLookupContents(refs, nativeIds, "grade");
        for (var db : List.of(refs, nativeIds)) numericArrayRoundtrip(db, model, true);

        String expanded = "EnumExpandedGuard" + version;
        var source = source(expanded, """
                DOMAIN Grade = (Zulu, Alpha);
                STRUCTURE Member = Value : Grade; END Member;
                CLASS Asset =
                  !!@ili2db.mapping=EXPAND
                  Detail : Member;
                END Asset;
                """);
        var rejected = schemaWithIdentityColumns(own.peer("enum_array_expand_guard"), expanded, source,
                CancellationToken.none(), "--expand-struct", "--create-enum-col-as-itf-code");
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("enumeration column options for expanded field"), rejected::diagnostics);
    }

    private void numericArrayRoundtrip(TestDatabase db, String model, boolean ids) throws Exception {
        var values = List.of("Family.Alpha", "Zulu", "Family.Alpha");
        var output = work.resolve("output.xtf");
        String first = ids ? "7100" : "2";
        String second = ids ? "7200" : "0";
        String table = db.table("asset");
        try (var sql = db.connect()) {
            if (ids) {
                execute(sql, "UPDATE " + db.table("grade") + " SET t_id = 7100 WHERE ilicode = 'Family.Alpha'");
                execute(sql, "UPDATE " + db.table("grade") + " SET t_id = 7200 WHERE ilicode = 'Zulu'");
            }
            referenceImport(db, "legacy", numericDataset(model, "legacy", values));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertNumericArray(output, "legacy", values);
            assertCliSuccess(db, "import", model, "native", numericDataset(model, "native", values));
            referenceExport(db, "native", output);
            assertNumericArray(output, "native", values);
            for (var prefix : List.of("legacy", "native")) {
                String from = " FROM " + table + " WHERE t_ili_tid = '" + prefix;
                String series = db.postgis() ? "array_to_string(grades, ',')"
                        : "json_extract(grades, '$[0]') || ',' || json_extract(grades, '$[1]') || ',' || json_extract(grades, '$[2]')";
                String type = db.postgis() ? "pg_typeof(grades)::text" : "json_type(grades, '$[0]')";
                assertEquals(first + "," + second + "," + first, scalar(sql, "SELECT " + series + from + "-value'"));
                assertEquals(db.postgis() ? "bigint[]" : "integer", scalar(sql, "SELECT " + type + from + "-value'"));
                assertNull(scalar(sql, "SELECT grades" + from + "-missing'"));
            }
            var invalid = dataset(model, "broken", object(model, "Asset", "broken-first", numericFields(model, values))
                    + object(model, "Asset", "broken-last", numericFields(model, List.of("Family.Unknown"))));
            assertCliFailure(db, "import", model, "broken", invalid, "Cannot encode enumeration value Family.Unknown");
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + table));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            String invalidSql = db.postgis() ? "ARRAY[9999]::bigint[]" : "'[9999]'";
            execute(sql, "UPDATE " + table + " SET grades = " + invalidSql + " WHERE t_ili_tid = 'native-value'");
            byte[] previous = Files.readAllBytes(output);
            assertCliFailure(db, "export", model, "native", output, "Unknown numeric enumeration value 9999");
            assertArrayEquals(previous, Files.readAllBytes(output));
        }
    }

    private Path numericDataset(String model, String prefix, List<String> values) throws Exception {
        return dataset(model, prefix, object(model, "Asset", prefix + "-value", numericFields(model, values))
                + object(model, "Asset", prefix + "-missing", ""));
    }

    private String numericFields(String model, List<String> values) {
        if (version == 24) {
            return values.stream().map(value -> "<Grades>" + value + "</Grades>").collect(Collectors.joining());
        }
        String wrapper = model + ".Data.Member";
        return "<Grades>" + values.stream().map(value -> "<" + wrapper + "><Value>" + value + "</Value></" + wrapper + ">")
                .collect(Collectors.joining()) + "</Grades>";
    }

    private void assertNumericArray(Path output, String prefix, List<String> values) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var rows = children(basket).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        assertNull(child(rows.get(prefix + "-missing"), "Grades"));
        var row = rows.get(prefix + "-value");
        var paths = version == 23
                ? children(child(row, "Grades")).stream().map(item -> child(item, "Value").getTextContent()).toList()
                : children(row).stream().filter(item -> item.getLocalName().equals("Grades")).map(item -> item.getTextContent()).toList();
        assertEquals(values, paths);
    }

    private static List<List<String>> rows(Connection sql, String query) throws Exception {
        var result = new ArrayList<List<String>>();
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            int width = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                var row = new ArrayList<String>(width);
                for (int i = 1; i <= width; i++) row.add(rows.getString(i));
                result.add(row);
            }
        }
        return result;
    }

    private void textRoundtrip(TestDatabase db, String model, String storedGrade) throws Exception {
        mixScalars(db, model);
        var output = work.resolve("output.xtf");
        try (var sql = db.connect()) {
            String table = db.table("asset");
            for (var prefix : List.of("legacy", "native")) {
                String row = " FROM " + table + " WHERE t_ili_tid = '" + prefix + "-value'";
                assertEquals(storedGrade, scalar(sql, "SELECT gradevalue" + row));
                assertEquals("Alpine label", scalar(sql, "SELECT gradevalue_txt" + row));
                assertEquals("No Sign", scalar(sql, "SELECT directvalue_txt" + row));
                assertEquals("true", scalar(sql, "SELECT aactive_txt" + row));
            }
            execute(sql, "UPDATE " + table + " SET gradevalue_txt = 'editor label' WHERE t_ili_tid = 'native-value'");
            assertCliSuccess(db, "export", model, "native", output);
            assertScalars(output, "native");
            var empty = dataset(model, "native", object(model, "Asset", "native-value", "")
                    + object(model, "Asset", "native-missing", ""));
            assertCliSuccess(db, "update", model, "native", empty);
            for (var column : List.of("gradevalue", "gradevalue_txt", "directvalue_txt", "aactive_txt")) {
                assertNull(scalar(sql, "SELECT " + column + " FROM " + table + " WHERE t_ili_tid = 'native-value'"), column);
            }
        }
    }

    private static void create(TestDatabase db, String model, Path ili, String... options) {
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private static void assertItfRows(TestDatabase db, Connection sql, String prefix) throws Exception {
        String value = " FROM " + db.table("asset") + " WHERE t_ili_tid = '" + prefix;
        assertEquals("2", scalar(sql, "SELECT gradevalue" + value + "-value'"));
        assertEquals("0", scalar(sql, "SELECT directvalue" + value + "-value'"));
        assertNull(scalar(sql, "SELECT gradevalue" + value + "-missing'"));
    }

    private void mixScalars(TestDatabase db, String model) throws Exception {
        Path output = work.resolve("output.xtf");
        referenceImport(db, "legacy", scalars(model, "legacy"));
        assertCliSuccess(db, "export", model, "legacy", output);
        assertScalars(output, "legacy");
        assertCliSuccess(db, "import", model, "native", scalars(model, "native"));
        referenceExport(db, "native", output);
        assertScalars(output, "native");
    }

    private Path scalars(String model, String prefix) throws Exception {
        return dataset(model, prefix, object(model, "Asset", prefix + "-value", """
                <GradeValue>Family.Alpha</GradeValue><DirectValue>No_Sign</DirectValue><Active>true</Active>
                """) + object(model, "Asset", prefix + "-missing", ""));
    }

    private void assertScalars(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-basket", id(baskets.getFirst(), version, "bid"));
        var objects = children(baskets.getFirst()).stream()
                .collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
        assertEquals(2, objects.size());
        var value = objects.get(prefix + "-value");
        assertEquals("Family.Alpha", child(value, "GradeValue").getTextContent());
        assertEquals("No_Sign", child(value, "DirectValue").getTextContent());
        assertEquals("true", child(value, "Active").getTextContent());
        assertNull(child(objects.get(prefix + "-missing"), "GradeValue"));
    }

    private Path source(String model, String declarations) throws Exception {
        return source(model, "", declarations);
    }

    private Path source(String model, String header, String declarations) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                %s
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, header, declarations, model));
    }

    private Path dataset(String model, String prefix, String objects) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"),
                referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    private String object(String model, String type, String tid, String fields) {
        String name = version == 23 ? model + ".Data." + type : type;
        return "<" + name + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + fields + "</" + name + ">";
    }

    private void schema(TestDatabase db, String model, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
        options.addAll(List.of(flags));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
