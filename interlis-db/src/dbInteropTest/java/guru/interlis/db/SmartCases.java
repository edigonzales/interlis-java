package guru.interlis.db;

import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SmartCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;
    private final SmartFixtures fixtures;

    SmartCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
        fixtures = new SmartFixtures(work, version);
    }

    void smart1Scalars(TestDatabase db, TestDatabase defaults) throws Exception {
        String model = "SmartMeter" + version;
        var source = fixtures.fixture(model + ".ili");
        referenceSchema(db, model, "smart1");
        referenceSchema(defaults, model, null);
        Files.delete(source);
        var input = fixtures.fixture(model + ".xtf");
        var nativeInput = fixtures.transferWithPrefix(input, "native");
        var output = work.resolve("output.xtf");
        referenceImport(defaults, "default", input);
        restoreEmptyMemo(defaults);
        assertCliSuccess(defaults, "export", model, "default", output);
        assertScalarObjects(output, model, "fixture", true);
        referenceImport(db, "legacy", input);
        restoreEmptyMemo(db);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertScalarObjects(output, model, "fixture", true);
        assertCliSuccess(db, "import", model, "native", nativeInput);
        referenceExport(db, "native", output);
        assertScalarObjects(output, model, "native", false);
        try (var sql = db.connect()) {
            String table = db.table("meter");
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + table + " WHERE t_type='meter' AND memo='' AND badge IS NULL"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + table + " WHERE t_type='smartunit' AND memo='base' AND badge='tag'"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("terminal") + " WHERE memo IS NULL"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_attrname")
                    + " WHERE iliname='" + model + ".Data.Instrument.Memo' AND colowner IN ('meter','terminal')"));
            execute(sql, "UPDATE " + table + " SET t_type='bogus' WHERE t_ili_tid='native-s'");
        }
        var previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, "Unknown or abstract T_Type bogus");
        assertEquals(previous, Files.readString(output));
    }

    private static void restoreEmptyMemo(TestDatabase db) throws Exception {
        // ili2db normalizes empty text. Store an empty SQL value for the native export.
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("meter") + " SET memo='' WHERE t_ili_tid='fixture-m'");
        }
    }

    void smart2Scalars(TestDatabase db) throws Exception {
        String model = "SmartMeter" + version;
        var source = fixtures.fixture(model + ".ili");
        referenceSchema(db, model, "smart2");
        Files.delete(source);
        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + db.table("smartunit") + " RENAME COLUMN memo TO stored_memo");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_memo'"
                    + " WHERE colowner='smartunit' AND iliname='" + model + ".Data.Instrument.Memo'");
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop") + " SET columnname='stored_memo'"
                    + " WHERE tablename='smartunit' AND columnname='memo'");
            try (var columns = sql.getMetaData().getColumns(null, db.schema(), "smartunit", "%")) {
                var names = new ArrayList<String>();
                while (columns.next()) names.add(columns.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                org.junit.jupiter.api.Assertions.assertTrue(names.contains("stored_memo"));
                org.junit.jupiter.api.Assertions.assertFalse(names.contains("t_type"));
            }
        }
        var input = fixtures.fixture(model + ".xtf");
        var output = work.resolve("smart2-scalar-output.xtf");
        referenceImport(db, "legacy", input);
        restoreEmptyMemo(db);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertScalarObjects(output, model, "fixture", true);
        assertCliSuccess(db, "import", model, "native", fixtures.transferWithPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertScalarObjects(output, model, "native", false);
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("smartunit")
                    + " WHERE stored_memo='base' AND counter=7 AND badge='tag'"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("meter")
                    + " WHERE memo='' AND counter=5"));
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("smartunit") + " s JOIN "
                    + db.table("meter") + " m ON m.t_id=s.t_id"));
        }
    }

    void smart1References(TestDatabase db) throws Exception {
        String model = "SmartRefs" + version;
        var source = fixtures.fixture(model + ".ili");
        referenceSchema(db, model, "smart1");
        Files.delete(source);
        var input = fixtures.fixture(model + ".xtf");
        var output = work.resolve("refs-output.xtf");
        referenceImport(db, "legacy", input);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertReferences(output, model, "fixture");
        var nativeInput = fixtures.transferWithPrefix(input, "native");
        assertCliSuccess(db, "import", model, "native", nativeInput);
        referenceExport(db, "native", output);
        assertReferences(output, model, "native");
        var bad = Files.writeString(work.resolve("bad.xtf"), Files.readString(input)
                .replace("fixture-", "bad-").replace("ref=\"bad-s\"", "ref=\"bad-t\"").replace("REF=\"bad-s\"", "REF=\"bad-t\""));
        assertCliFailure(db, "import", model, "bad", bad, "Cannot encode reference from bad-survey");
        try (var sql = db.connect()) {
            String instrument = db.table("instrument");
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("note") + " n JOIN " + instrument
                    + " t ON t.t_id=n.anyinstrument AND t.t_basket=n.t_basket JOIN " + instrument
                    + " s ON s.t_id=n.smarttarget AND s.t_basket=n.t_basket JOIN "
                    + db.table("survey") + " a ON a.t_id=n.survey_details JOIN " + instrument
                    + " m ON m.t_id=a.observed WHERE t.t_type='terminal' AND s.t_type='smartunit' AND m.t_type='meter'"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("note")));
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='bad'"));
            assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='bad-b'"));
        }
    }

    private void assertReferences(Path file, String model, String prefix) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var owner = fixtures.object(basket, model, "Survey", prefix + "-survey");
        var note = children(child(owner, "Details")).getFirst();
        assertEquals(prefix + "-t", id(child(note, "AnyInstrument"), version, "ref"));
        assertEquals(prefix + "-s", id(child(note, "SmartTarget"), version, "ref"));
        assertEquals(prefix + "-m", id(child(owner, "Observed"), version, "ref"));
        fixtures.object(basket, model, "SmartUnit", prefix + "-s");
        fixtures.object(basket, model, "Terminal", prefix + "-t");
    }

    void smart2References(TestDatabase db) throws Exception {
        String model = "SmartPoly" + version;
        var source = fixtures.fixture(model + ".ili");
        referenceSchema(db, model, "smart2");
        Files.delete(source);
        var input = fixtures.fixture(model + ".xtf");
        var output = work.resolve("smart2-refs-output.xtf");
        referenceImport(db, "legacy", input);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertPolymorphicReferences(output, model, "fixture");
        assertCliSuccess(db, "import", model, "native", fixtures.transferWithPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertPolymorphicReferences(output, model, "native");
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("note") + " n JOIN "
                    + db.table("terminal") + " t ON n.anyinstrument_terminal=t.t_id JOIN "
                    + db.table("smartunit") + " s ON n.smarttarget=s.t_id WHERE n.anyinstrument_meter IS NULL"
                    + " AND n.anyinstrument_smartunit IS NULL AND t.t_basket=s.t_basket AND n.t_basket=t.t_basket"));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("survey") + " s JOIN "
                    + db.table("meter") + " m ON s.observed_meter=m.t_id WHERE s.observed_terminal IS NULL"
                    + " AND s.observed_smartunit IS NULL AND s.t_basket=m.t_basket"));
            assertEquals("4", scalar(sql, "SELECT COUNT(*) FROM " + db.table("crosscheck")
                    + " WHERE checkedinstrument_meter IS NULL AND ((checkedinstrument_smartunit IS NULL)"
                    + " <> (checkedinstrument_terminal IS NULL))"));
            execute(sql, "UPDATE " + db.table("survey") + " SET observed_terminal=(SELECT t_id FROM "
                    + db.table("terminal") + " WHERE t_ili_tid='native-t') WHERE t_ili_tid='native-survey'");
        }
        var saved = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, "Multiple stored values for " + model + ".Data.Observation.Observed");
        assertEquals(saved, Files.readString(output));
    }

    private void assertPolymorphicReferences(Path file, String model, String prefix) throws Exception {
        assertReferences(file, model, prefix);
        var basket = children(readDataSection(file, version)).getFirst();
        var links = children(basket).stream().filter(element -> element.getLocalName().endsWith("CrossCheck")).toList();
        assertEquals(2, links.size());
        assertEquals(Set.of(prefix + "-t", prefix + "-s"), links.stream()
                .map(link -> id(child(link, "CheckedInstrument"), version, "ref")).collect(Collectors.toSet()));
        for (var link : links) assertEquals(prefix + "-survey", id(child(link, "CheckedSurvey"), version, "ref"));
    }

    void structures(TestDatabase smart1, TestDatabase smart2) throws Exception {
        String model = "SmartStructures" + version;
        var source = fixtures.fixture(model + ".ili");
        referenceSchema(smart1, model, "smart1");
        referenceSchema(smart2, model, "smart2");
        Files.delete(source);
        var input = fixtures.fixture(model + ".xtf");
        var nativeInput = fixtures.transferWithPrefix(input, "native");
        var output = work.resolve("structures-output.xtf");
        for (var db : List.of(smart1, smart2)) {
            referenceImport(db, "legacy", input);
            assertCliSuccess(db, "export", model, "legacy", output);
            assertStructures(output, model, "fixture");
            assertCliSuccess(db, "import", model, "native", nativeInput);
            referenceExport(db, "native", output);
            assertStructures(output, model, "native");
            try (var sql = db.connect()) {
                String note = db.table("note");
                String owner = db.table(db == smart1 ? "survey" : "detailedsurvey");
                String column = db == smart1 ? "survey_notes" : "detailedsurvey_notes";
                assertEquals("4", scalar(sql, "SELECT COUNT(*) FROM " + note + " n JOIN " + owner
                        + " s ON n." + column + "=s.t_id AND n.t_basket=s.t_basket WHERE "
                        + "(n.t_seq=0 AND n.memo='three') OR (n.t_seq=1 AND n.memo='four')"));
                assertEquals(db == smart1 ? "8" : "4",
                        scalar(sql, "SELECT COUNT(*) FROM " + note + " WHERE survey_notes IS NOT NULL"));
                String secondOwner = db == smart1 ? "survey_details" : "detailedsurvey_notes";
                execute(sql, "UPDATE " + note + " SET " + secondOwner + "=(SELECT t_id FROM " + owner
                        + " WHERE t_ili_tid='native-d') WHERE survey_notes=(SELECT t_id FROM " + db.table("survey")
                        + " WHERE t_ili_tid='native-s') AND t_seq=0");
            }
            var saved = Files.readString(output);
            assertCliFailure(db, "export", model, "native", output, "Expected exactly one structure owner");
            assertEquals(saved, Files.readString(output));
        }
    }

    private void assertStructures(Path file, String model, String prefix) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var base = fixtures.object(basket, model, "Survey", prefix + "-s");
        var derived = fixtures.object(basket, model, "DetailedSurvey", prefix + "-d");
        assertEquals("solo", child(children(child(base, "Details")).getFirst(), "Memo").getTextContent());
        assertNull(child(derived, "Details"));
        assertEquals("derived", child(derived, "Label").getTextContent());
        assertEquals(List.of("one", "two"), notes(base));
        assertEquals(List.of("three", "four"), notes(derived));
    }

    private static List<String> notes(Element object) {
        return children(object).stream().filter(e -> e.getLocalName().equals("Notes")).flatMap(e -> children(e).stream())
                .map(note -> child(note, "Memo").getTextContent()).toList();
    }

    void geometries(TestDatabase smart1, TestDatabase smart2) throws Exception {
        String model = "SmartGeometry" + version;
        var source = fixtures.fixture(model + ".ili");
        referenceSchema(smart1, model, "smart1");
        referenceSchema(smart2, model, "smart2");
        Files.delete(source);
        var input = fixtures.fixture(model + ".xtf");
        var nativeInput = fixtures.transferWithPrefix(input, "native");
        var output = work.resolve("geometry-output.xtf");
        for (var db : List.of(smart1, smart2)) {
            referenceImport(db, "legacy", input);
            assertCliSuccess(db, "export", model, "legacy", output);
            fixtures.assertGeometries(output, model, "fixture");
            assertCliSuccess(db, "import", model, "native", nativeInput);
            referenceExport(db, "native", output);
            fixtures.assertGeometries(output, model, "native");
            boolean shared = db == smart1;
            try (var sql = db.connect()) {
                String parent = shared ? "meter" : "smartunit";
                if (db.postgis()) {
                    assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table(parent)
                            + " WHERE st_srid(location)=2056 AND st_srid(extralocation)=2056"));
                    assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table(parent)
                            + " WHERE t_ili_tid LIKE '%-n' AND st_srid(location)=2056 AND extralocation IS NULL"));
                } else {
                    String helper = shared ? "meter_extralocation" : "smartunit_location";
                    String field = shared ? "extralocation" : "location";
                    assertEquals("2056", scalar(sql, "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name='"
                            + helper + "' AND column_name='" + field + "'"));
                    assertEquals("4", scalar(sql, "SELECT COUNT(*) FROM " + db.table(helper) + " g JOIN "
                            + db.table(parent) + " p ON g.t_id=p.t_id AND g.t_basket=p.t_basket"
                            + " WHERE p.t_ili_tid LIKE '%-s' OR p.t_ili_tid LIKE '%-n'"));
                    assertEquals("4", scalar(sql, "SELECT COUNT(*) FROM " + db.table(helper)));
                    execute(sql, "DELETE FROM " + db.table(helper) + " WHERE t_id=(SELECT t_id FROM "
                            + db.table(parent) + " WHERE t_ili_tid='native-s')");
                }
            }
            if (!db.postgis()) {
                var saved = Files.readString(output);
                assertCliFailure(db, "export", model, "native", output, "Missing secondary geometry row");
                assertEquals(saved, Files.readString(output));
            }
        }
    }

    void smart1Schema(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "SmartRefs" + version;
        createAndCompare(baseline, own, model, "smart1");
        var input = fixtures.fixture(model + ".xtf");
        var output = work.resolve("own-smart1.xtf");
        referenceImport(own, "legacy", input);
        assertCliSuccess(own, "export", model, "legacy", output);
        assertReferences(output, model, "fixture");
        assertCliSuccess(own, "import", model, "native", fixtures.transferWithPrefix(input, "native"));
        referenceExport(own, "native", output);
        assertReferences(output, model, "native");
    }

    void smart2Schema(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "SmartPoly" + version;
        var source = fixtures.fixture(model + ".ili");
        Files.writeString(source, Files.readString(source).replace("SmartTarget : REFERENCE TO SmartUnit",
                "SmartTarget : REFERENCE TO Meter").replace("CLASS Survey = Details : Note; END Survey;",
                "CLASS Survey = Details : Note; END Survey;\n    CLASS ExtraSurvey EXTENDS Survey = END ExtraSurvey;"));
        createAndCompare(baseline, own, model, "smart2", source);
        var input = fixtures.fixture(model + ".xtf");
        String closingSurvey = version == 23 ? "</" + model + ".Data.Survey>" : "</Survey>";
        String extra = version == 23 ? "<" + model
                + ".Data.ExtraSurvey TID=\"fixture-extra\"><Observed REF=\"fixture-m\"/></" + model + ".Data.ExtraSurvey>"
                : "<ExtraSurvey i:tid=\"fixture-extra\"><Observed i:ref=\"fixture-m\"/></ExtraSurvey>";
        Files.writeString(input, Files.readString(input).replace(closingSurvey, closingSurvey + "\n      " + extra));
        var output = work.resolve("own-smart2.xtf");
        referenceImport(own, "legacy", input);
        assertCliSuccess(own, "export", model, "legacy", output);
        assertPolymorphicReferences(output, model, "fixture");
        assertExtraSurvey(output, model, "fixture");
        assertCliSuccess(own, "import", model, "native", fixtures.transferWithPrefix(input, "native"));
        referenceExport(own, "native", output);
        assertPolymorphicReferences(output, model, "native");
        assertExtraSurvey(output, model, "native");
        referenceImport(own, "tail", fixtures.transferWithPrefix(input, "tail"));
        assertCliSuccess(own, "export", model, "tail", output);
        assertPolymorphicReferences(output, model, "tail");
        assertExtraSurvey(output, model, "tail");
        try (var sql = own.connect()) {
            String ids = List.of("meter", "smartunit", "terminal", "survey", "extrasurvey", "note", "crosscheck",
                    "t_ili2db_basket", "t_ili2db_dataset").stream()
                    .map(table -> "SELECT t_id FROM " + own.table(table)).collect(Collectors.joining(" UNION ALL "));
            assertEquals("30", scalar(sql, "SELECT COUNT(DISTINCT t_id) FROM (" + ids + ") ids"));
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM " + own.table("note") + " n JOIN "
                    + own.table("smartunit") + " s ON n.smarttarget_smartunit=s.t_id AND n.t_basket=s.t_basket"
                    + " WHERE n.smarttarget_meter IS NULL"));
        }
    }

    private void assertExtraSurvey(Path path, String model, String prefix) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        var survey = fixtures.object(basket, model, "ExtraSurvey", prefix + "-extra");
        assertEquals(prefix + "-m", id(child(survey, "Observed"), version, "ref"));
    }

    private void createAndCompare(TestDatabase baseline, TestDatabase own, String model, String profile) throws Exception {
        createAndCompare(baseline, own, model, profile, fixtures.fixture(model + ".ili"));
    }

    private void createAndCompare(TestDatabase baseline, TestDatabase own, String model, String profile, Path source)
            throws Exception {
        referenceSchema(baseline, model, profile);
        var result = schemaWithIdentityColumns(own, model, source, guru.interlis.execution.CancellationToken.none(),
                "--inheritance", profile);
        assertEquals(0, result.code(), result::diagnostics);
        Files.delete(source);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var ref = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            var nativeSchema = DbSchemaCatalog.read(actual, own.postgis(), own.schema());
            if (profile.equals("smart2")) {
                ref.canonicalReferenceAlternatives().assertSameAs(nativeSchema.canonicalReferenceAlternatives());
            } else {
                ref.assertSameAs(nativeSchema);
            }
        }
    }

    private void assertScalarObjects(Path path, String model, String prefix, boolean emptyMemo) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        assertEquals(3, children(basket).size());
        var meter = fixtures.object(basket, model, "Meter", prefix + "-m");
        var smart = fixtures.object(basket, model, "SmartUnit", prefix + "-s");
        var terminal = fixtures.object(basket, model, "Terminal", prefix + "-t");
        if (emptyMemo) {
            assertEquals("", child(meter, "Memo").getTextContent());
        } else {
            assertNull(child(meter, "Memo"));
        }
        assertEquals("idle", child(meter, "State").getTextContent());
        assertEquals("5", child(meter, "Counter").getTextContent());
        assertNull(child(meter, "Badge"));
        assertEquals("base", child(smart, "Memo").getTextContent());
        assertEquals("active", child(smart, "State").getTextContent());
        assertEquals("7", child(smart, "Counter").getTextContent());
        assertEquals("tag", child(smart, "Badge").getTextContent());
        assertNull(child(terminal, "Memo"));
        assertEquals("p1", child(terminal, "Socket").getTextContent());
    }

    private void referenceSchema(TestDatabase db, String model, String profile) throws Exception {
        SmartInteropSupport.schema(reference, db, work, model, profile);
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        SmartInteropSupport.importDataset(reference, db, dataset, input);
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        SmartInteropSupport.exportDataset(reference, db, dataset, output);
    }
}
