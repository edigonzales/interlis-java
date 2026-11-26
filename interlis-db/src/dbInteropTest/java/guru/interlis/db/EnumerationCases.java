package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.EnumerationFixtures.BASE;
import static guru.interlis.db.EnumerationFixtures.ABSTRACT_INLINE_OVERRIDES;
import static guru.interlis.db.EnumerationFixtures.INLINE_OVERRIDES;
import static guru.interlis.db.EnumerationFixtures.assertItfRows;
import static guru.interlis.db.EnumerationFixtures.mappedTable;
import static guru.interlis.db.EnumerationFixtures.rows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnumerationCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;
    private final EnumerationFixtures fixtures;

    EnumerationCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
        fixtures = new EnumerationFixtures(work, version);
    }

    void itfSchema(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumItfSchema" + version;
        var ili = fixtures.source(model, BASE);
        schema(expected, model, "--createEnumColAsItfCode");
        create(own, model, ili, "--create-enum-col-as-itf-code");
        assertItfExchange(expected, own, model);

        var lookupReference = expected.peer("itf_and_lookup_reference");
        var lookupOwn = own.peer("itf_and_lookup_own");
        schema(lookupReference, model, "--createEnumColAsItfCode", "--createEnumTabsWithId", "--createFk");
        create(lookupOwn, model, ili, "--create-enum-col-as-itf-code", "--create-enum-tabs-with-id", "--create-fk");
        assertItfExchange(lookupReference, lookupOwn, model);
    }

    private void assertItfExchange(TestDatabase expected, TestDatabase own, String model) throws Exception {
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
        var ili = fixtures.textModel(model);
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

        String extended = "EnumInlineLabels" + version;
        var extendedReference = own.peer("inline_txt_ref");
        var extendedOwn = own.peer("inline_txt_own");
        var source = fixtures.source(extended, INLINE_OVERRIDES);
        schema(extendedReference, extended, "--createEnumTxtCol", "--beautifyEnumDispName");
        create(extendedOwn, extended, source, "--create-enum-txt-col", "--beautify-enum-disp-name");
        assertSameCatalog(extendedReference, extendedOwn);
        for (var db : List.of(extendedReference, extendedOwn)) {
            mixInline(db, extended);
            try (var sql = db.connect()) {
                for (var prefix : List.of("legacy-inline", "native-inline")) {
                    String row = " FROM " + db.table("meter") + " WHERE t_ili_tid='" + prefix;
                    assertEquals("Ready label", scalar(sql, "SELECT astate_txt" + row + "-ready'"));
                    assertEquals("out of service", scalar(sql, "SELECT astate_txt" + row + "-fallback'"));
                    assertNull(scalar(sql, "SELECT astate_txt" + row + "-missing'"));
                }
            }
        }
    }

    void singleLookup(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumSingle" + version;
        var ili = fixtures.source(model, BASE.replace("Zed, Alpha", """
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
        inlineSingleLookup(expected.peer("inline_single_reference"), own.peer("inline_single_own"), false);
        inlineSingleLookup(expected.peer("inline_single_smart_reference"), own.peer("inline_single_smart_own"), true);
    }

    private void inlineSingleLookup(TestDatabase expected, TestDatabase own, boolean smart) throws Exception {
        String model = (smart ? "EnumInlineSmart" : "EnumInlineShared") + version;
        var source = fixtures.source(model, INLINE_OVERRIDES);
        InteropCommands.schema(reference, expected, work, model, smart ? "smart2" : "newClass",
                "--createSingleEnumTab", "--beautifyEnumDispName");
        if (smart) {
            create(own, model, source, "--inheritance", "smart2", "--create-single-enum-tab", "--beautify-enum-disp-name");
        } else {
            create(own, model, source, "--create-single-enum-tab", "--beautify-enum-disp-name");
        }
        assertSameCatalog(expected, own);
        assertLookupContents(expected, own, "t_ili2db_enum");
        for (var db : List.of(expected, own)) {
            mixInline(db, model);
            try (var sql = db.connect()) {
                String lookup = db.table("t_ili2db_enum");
                String base = model + ".Data.Meter.State";
                String derived = model + ".Data.SmartUnit.State";
                assertEquals("2", scalar(sql, "SELECT seq FROM " + lookup
                        + " WHERE thisclass='" + base + "' AND ilicode='standby'"));
                assertEquals(smart ? base : null, scalar(sql, "SELECT baseclass FROM " + lookup
                        + " WHERE thisclass='" + derived + "' AND ilicode='active.ready'"));
                assertEquals(smart ? "3" : null, scalar(sql, "SELECT itfcode FROM " + lookup
                        + " WHERE thisclass='" + derived + "' AND ilicode='standby'"));
            }
        }
    }

    void perLookup(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumTables" + version;
        var ili = fixtures.source(model, BASE + "CLASS Asset_DirectValue = Memo : TEXT*40; END Asset_DirectValue;"
                + INLINE_OVERRIDES);
        schema(expected, model, "--createEnumTabs");
        create(own, model, ili, "--create-enum-tabs");
        assertSameCatalog(expected, own);
        try (var left = expected.connect(); var right = own.connect()) {
            for (String table : List.of("meter_state", "smartunit_state")) {
                String query = "SELECT ilicode,itfcode,seq,dispname,description,inactive FROM ";
                assertEquals(rows(left, query + expected.table(table) + " ORDER BY itfcode"),
                        rows(right, query + own.table(table) + " ORDER BY itfcode"), table);
            }
        }
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
            mixInline(db, model);
            try (var sql = db.connect()) {
                assertEquals("Family.Alpha", scalar(sql, "SELECT gradevalue FROM " + db.table("asset")
                        + " WHERE t_ili_tid = 'native-value'"));
                assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("stored_grade")));
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
        var ili = fixtures.extensionModel(model);
        schema(expected, model, "--createEnumTabsWithId");
        create(own, model, ili, "--create-enum-tabs-with-id");
        assertSameCatalog(expected, own);
        assertLookupContents(expected, own, "grade", "asset_directvalue");
        var output = work.resolve("output.xtf");
        for (var db : List.of(expected, own)) {
            try (var sql = db.connect()) {
                execute(sql, "UPDATE " + db.table("grade")
                        + " SET t_id=7001 WHERE thisclass='" + model + ".Data.Grade' AND ilicode='Family.Alpha'");
                execute(sql, "UPDATE " + db.table("asset_directvalue") + " SET t_id=8111 WHERE ilicode='No_Sign'");
            }
            referenceImport(db, "legacy", fixtures.extensionDataset(model, "legacy"));
            assertCliSuccess(db, "export", model, "legacy", output);
            fixtures.assertExtension(output, "legacy");
            assertCliSuccess(db, "import", model, "native", fixtures.extensionDataset(model, "native"));
            referenceExport(db, "native", output);
            fixtures.assertExtension(output, "native");
            if (db == own) {
                referenceImport(db, "tail", fixtures.extensionDataset(model, "tail"));
                assertCliSuccess(db, "export", model, "tail", output);
                fixtures.assertExtension(output, "tail");
            }

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
                assertEquals("7001", baseId);
                for (String prefix : db == own ? List.of("legacy", "native", "tail") : List.of("legacy", "native")) {
                    String row = " FROM " + assets + " WHERE t_ili_tid = '" + prefix;
                    assertEquals(baseId, scalar(sql, "SELECT gradevalue" + row + "-pair'"));
                    assertEquals(derivedId, scalar(sql, "SELECT derivedvalue" + row + "-pair'"));
                    assertEquals("8111", scalar(sql, "SELECT directvalue" + row + "-pair'"));
                    assertNull(scalar(sql, "SELECT gradevalue" + row + "-missing'"));
                }
            }
        }
        inlineIds(expected.peer("inline_ids_concrete_reference"), own.peer("inline_ids_concrete_own"), true);
        inlineIds(expected.peer("inline_ids_copied_reference"), own.peer("inline_ids_copied_own"), false);

        String shared = "EnumInlineSharedIds" + version;
        fixtures.source(shared, INLINE_OVERRIDES);
        var sharedDb = expected.peer("inline_ids_shared");
        schema(sharedDb, shared, "--createEnumTabsWithId");
        reference.expectFailure(sharedDb, "%ILI_FROM_DB", "longValue()", "--import", "--dataset", "inline-shared",
                "--importTid", "--importBid", fixtures.inlineDataset(shared, "inline-shared").toString());
    }

    private void inlineIds(TestDatabase expected, TestDatabase own, boolean concreteBase) throws Exception {
        String layout = concreteBase ? "smart2" : "smart1";
        String model = (concreteBase ? "EnumInlineConcreteIds" : "EnumInlineCopiedIds") + version;
        var source = fixtures.source(model, concreteBase ? INLINE_OVERRIDES : ABSTRACT_INLINE_OVERRIDES);
        InteropCommands.schema(reference, expected, work, model, layout, "--createEnumTabsWithId", "--createFk",
                "--createEnumTxtCol", "--beautifyEnumDispName");
        create(own, model, source, "--inheritance", layout, "--create-enum-tabs-with-id", "--create-fk",
                "--create-enum-txt-col", "--beautify-enum-disp-name");
        assertSameCatalog(expected, own);
        assertLookupContents(expected, own, "meter_state");
        for (var db : List.of(expected, own)) {
            mixInline(db, model, concreteBase);
            try (var sql = db.connect()) {
                var foreignKeys = foreignKeyColumns(db, sql);
                assertTrue(foreignKeys.contains("smartunit:astate:meter_state"), foreignKeys::toString);
                if (concreteBase) {
                    assertTrue(foreignKeys.contains("meter:astate:meter_state"), foreignKeys::toString);
                    assertTrue(foreignKeys.contains("leaf:astate:meter_state"), foreignKeys::toString);
                }
                String root = model + ".Data.Meter.State";
                String derived = model + ".Data.SmartUnit.State";
                for (var prefix : List.of("legacy-inline", "native-inline")) {
                    if (concreteBase) {
                        assertInlineId(db, sql, "meter", prefix + "-base", root, "standby", "standby");
                    }
                    String leafTable = concreteBase ? "leaf" : "smartunit";
                    assertInlineId(db, sql, "smartunit", prefix + "-ready", derived, "active.ready", "Ready label");
                    assertInlineId(db, sql, leafTable, prefix + "-inherited", derived, "standby", "standby");
                    assertInlineId(db, sql, leafTable, prefix + "-fallback", derived, "out_of_service", "out of service");
                    assertNull(scalar(sql, "SELECT astate FROM " + db.table(leafTable)
                            + " WHERE t_ili_tid='" + prefix + "-missing'"));
                }
            }
        }
    }

    private static void assertInlineId(TestDatabase db, Connection sql, String table, String tid,
                                       String definition, String code, String label) throws Exception {
        assertEquals(List.of(List.of(definition, code, label)), rows(sql,
                "SELECT e.thisclass,e.ilicode,t.astate_txt FROM " + db.table(table) + " t JOIN "
                        + db.table("meter_state") + " e ON e.t_id=t.astate WHERE t.t_ili_tid='" + tid + "'"), tid);
    }

    void appendEnums(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumBase" + version;
        var sources = fixtures.appendModels(model);
        schema(expected, model, "--createEnumTabsWithId");
        create(own, model, sources.base(), "--create-enum-tabs-with-id");
        String added = sources.added();
        String independent = sources.independent();
        var output = work.resolve("output.xtf");
        for (var db : List.of(expected, own)) {
            if (db == expected) referenceImport(db, "base", fixtures.scalars(model, "base"));
            else assertCliSuccess(db, "import", model, "base", fixtures.scalars(model, "base"));
            try (var sql = db.connect()) {
                String grade = db.table("grade");
                String original = " WHERE thisclass = '" + model + ".Grade'";
                execute(sql, "UPDATE " + grade + " SET dispname = 'kept editor label'" + original + " AND ilicode = 'Family.Alpha'");
                String existing = "SELECT t_id, ilicode, itfcode, dispname FROM " + grade + original + " ORDER BY itfcode";
                var before = rows(sql, existing);
                create(db, independent, sources.standalone(), "--add-models");
                create(db, added, sources.addition(), "--add-models", "--model-file", sources.base().toString());
                assertEquals(before, rows(sql, existing));
                String status = mappedTable(db, sql, added + ".Status");
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(status)
                        + " WHERE thisclass = '" + added + ".Status'"));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("stage")
                        + " WHERE thisclass = '" + independent + ".Data.Stage'"));

                String fields = "<Comparison>Family.Alpha</Comparison><Choice>Family.Extra</Choice><StatusValue>Closed_Code</StatusValue>";
                referenceImport(db, "legacy", fixtures.dataset(added, "legacy", fixtures.object(added, "Detail", "legacy-value", fields)));
                assertCliSuccess(db, "export", added, "legacy", output);
                fixtures.assertAppended(output, "legacy");
                assertCliSuccess(db, "import", added, "native", fixtures.dataset(added, "native", fixtures.object(added, "Detail", "native-value", fields)));
                referenceExport(db, "native", output);
                fixtures.assertAppended(output, "native");

            }
            assertCliSuccess(db, "export", model, "base", output);
            fixtures.assertScalars(output, "base");
            if (db == own) {
                referenceExport(db, "base", output);
                fixtures.assertScalars(output, "base");
            }
        }
    }

    void numericArrays(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "EnumArrayNumbers" + version;
        var ili = fixtures.arrayModel(model);
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
            referenceImport(db, "legacy", fixtures.numericDataset(model, "legacy", values));
            assertCliSuccess(db, "export", model, "legacy", output);
            fixtures.assertNumericArray(output, "legacy", values);
            assertCliSuccess(db, "import", model, "native", fixtures.numericDataset(model, "native", values));
            referenceExport(db, "native", output);
            fixtures.assertNumericArray(output, "native", values);
            for (var prefix : List.of("legacy", "native")) {
                String from = " FROM " + table + " WHERE t_ili_tid = '" + prefix;
                String series = db.postgis() ? "array_to_string(grades, ',')"
                        : "json_extract(grades, '$[0]') || ',' || json_extract(grades, '$[1]') || ',' || json_extract(grades, '$[2]')";
                String type = db.postgis() ? "pg_typeof(grades)::text" : "json_type(grades, '$[0]')";
                assertEquals(first + "," + second + "," + first, scalar(sql, "SELECT " + series + from + "-value'"));
                assertEquals(db.postgis() ? "bigint[]" : "integer", scalar(sql, "SELECT " + type + from + "-value'"));
                assertNull(scalar(sql, "SELECT grades" + from + "-missing'"));
            }
        }
    }

    private void textRoundtrip(TestDatabase db, String model, String storedGrade) throws Exception {
        mixScalars(db, model);
        try (var sql = db.connect()) {
            String table = db.table("asset");
            for (var prefix : List.of("legacy", "native")) {
                String row = " FROM " + table + " WHERE t_ili_tid = '" + prefix + "-value'";
                assertEquals(storedGrade, scalar(sql, "SELECT gradevalue" + row));
                assertEquals("Alpine label", scalar(sql, "SELECT gradevalue_txt" + row));
                assertEquals("No Sign", scalar(sql, "SELECT directvalue_txt" + row));
                assertEquals("true", scalar(sql, "SELECT aactive_txt" + row));
            }
        }
    }

    private static void create(TestDatabase db, String model, Path ili, String... options) {
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private void mixScalars(TestDatabase db, String model) throws Exception {
        Path output = work.resolve("output.xtf");
        referenceImport(db, "legacy", fixtures.scalars(model, "legacy"));
        assertCliSuccess(db, "export", model, "legacy", output);
        fixtures.assertScalars(output, "legacy");
        assertCliSuccess(db, "import", model, "native", fixtures.scalars(model, "native"));
        referenceExport(db, "native", output);
        fixtures.assertScalars(output, "native");
    }

    private void mixInline(TestDatabase db, String model) throws Exception {
        mixInline(db, model, true);
    }

    private void mixInline(TestDatabase db, String model, boolean concreteBase) throws Exception {
        Path output = work.resolve("inline-overrides.xtf");
        referenceImport(db, "legacy-inline", fixtures.inlineDataset(model, "legacy-inline", concreteBase));
        assertCliSuccess(db, "export", model, "legacy-inline", output);
        fixtures.assertInlineOverrides(output, "legacy-inline", concreteBase);
        assertCliSuccess(db, "import", model, "native-inline", fixtures.inlineDataset(model, "native-inline", concreteBase));
        referenceExport(db, "native-inline", output);
        fixtures.assertInlineOverrides(output, "native-inline", concreteBase);
    }

    private void schema(TestDatabase db, String model, String... flags) throws Exception {
        InteropCommands.schema(reference, db, work, model, "newClass", flags);
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
