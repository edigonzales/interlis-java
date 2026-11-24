package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.SmartInteropSupport.exportDataset;
import static guru.interlis.db.SmartInteropSupport.importDataset;
import static guru.interlis.db.SmartInteropSupport.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmartClassInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageSmart123() throws Exception { exchange(23, null, "smart1"); }
    @Test void geoPackageSmart124() throws Exception { exchange(24, null, "smart1"); }
    @Test void postgisSmart123() throws Exception { exchange(23, POSTGIS.container(), "smart1"); }
    @Test void postgisSmart124() throws Exception { exchange(24, POSTGIS.container(), "smart1"); }

    @Test void geoPackageSmart223() throws Exception { exchange(23, null, "smart2"); }
    @Test void geoPackageSmart224() throws Exception { exchange(24, null, "smart2"); }
    @Test void postgisSmart223() throws Exception { exchange(23, POSTGIS.container(), "smart2"); }
    @Test void postgisSmart224() throws Exception { exchange(24, POSTGIS.container(), "smart2"); }

    private void exchange(int version, PostgreSQLContainer postgres, String layout) throws Exception {
        var fixtures = new SmartFixtures(work, version);
        var reference = new ReferenceTool(work, postgres != null);
        String scope = layout + "_v" + version + "_";
        var scalars = new TestDatabase(work, postgres, "scalars_reference", scope);
        scalarExchange(reference, scalars, fixtures, version, layout);
        var old = scalars.peer("targets_reference");
        referenceExchange(reference, old, old.peer("targets_own"), fixtures, version, layout);
    }

    private void scalarExchange(ReferenceTool reference, TestDatabase db, SmartFixtures fixtures, int version, String layout)
            throws Exception {
        String model = "SmartMeter" + version;
        var ili = fixtures.fixture(model + ".ili");
        schema(reference, db, work, model, layout.equals("smart1") ? null : layout);
        Files.delete(ili);
        Path input = fixtures.fixture(model + ".xtf");
        importDataset(reference, db, "legacy", input);
        try (var sql = db.connect()) {
            // The reference normalizes empty text. Exercise the stored SQL value on the return path.
            execute(sql, "UPDATE " + db.table("meter") + " SET memo='' WHERE t_ili_tid='fixture-m'");
        }
        Path output = work.resolve("scalars-native.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        fixtures.assertScalarObjects(output, model, "fixture", true);
        assertCliSuccess(db, "import", model, "native", fixtures.transferWithPrefix(input, "native"));
        output = work.resolve("scalars-reference.xtf");
        exportDataset(reference, db, "native", output);
        fixtures.assertScalarObjects(output, model, "native", false);
        try (var sql = db.connect()) {
            SmartFixtures.assertScalarStorage(db, sql, model, layout, "memo", 2);
        }
    }

    private void referenceExchange(ReferenceTool reference, TestDatabase old, TestDatabase own, SmartFixtures fixtures,
                                   int version, String layout) throws Exception {
        boolean smart2 = layout.equals("smart2");
        String model = (smart2 ? "SmartPoly" : "SmartRefs") + version;
        Path ili = fixtures.fixture(model + ".ili");
        Path input = fixtures.fixture(model + ".xtf");
        if (smart2) extendSmartPoly(ili, input, model, version);
        schema(reference, old, work, model, layout);
        assertSchemaWithIdentityColumns(own, model, ili, "--inheritance", layout);
        compareCatalog(old, own, smart2);
        Files.delete(ili);

        Path nativeInput = fixtures.transferWithPrefix(input, "native");
        for (var db : List.of(old, own)) {
            importDataset(reference, db, "legacy", input);
            Path output = work.resolve(db.schema() + "-native.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertReferenceTransfer(fixtures, output, model, "fixture", version, smart2);
            assertCliSuccess(db, "import", model, "native", nativeInput);
            output = work.resolve(db.schema() + "-reference.xtf");
            exportDataset(reference, db, "native", output);
            assertReferenceTransfer(fixtures, output, model, "native", version, smart2);

            if (smart2 && db == own) {
                importDataset(reference, db, "tail", fixtures.transferWithPrefix(input, "tail"));
                output = work.resolve("tail-native.xtf");
                assertCliSuccess(db, "export", model, "tail", output);
                assertReferenceTransfer(fixtures, output, model, "tail", version, true);
            }
            try (var sql = db.connect()) {
                int datasets = smart2 && db == own ? 3 : 2;
                SmartFixtures.assertReferenceStorage(db, sql, layout, datasets);
                if (smart2) {
                    assertEquals(Integer.toString(datasets), scalar(sql, "SELECT count(*) FROM " + db.table("note") + " n JOIN "
                            + db.table("smartunit") + " s ON n.metertarget_smartunit=s.t_id AND n.t_basket=s.t_basket"
                            + " WHERE n.metertarget_meter IS NULL"));
                    assertEquals(Integer.toString(datasets), scalar(sql, "SELECT count(*) FROM " + db.table("extrasurvey") + " e JOIN "
                            + db.table("meter") + " m ON e.observed_meter=m.t_id AND e.t_basket=m.t_basket"));
                }
            }
        }
        try (var sql = own.connect()) {
            if (smart2) {
                String ids = List.of("meter", "smartunit", "terminal", "survey", "extrasurvey", "note", "crosscheck",
                        "t_ili2db_basket", "t_ili2db_dataset").stream()
                        .map(table -> "SELECT t_id FROM " + own.table(table)).collect(Collectors.joining(" UNION ALL "));
                assertEquals("30", scalar(sql, "SELECT count(*) FROM (" + ids + ") ids"));
                assertEquals("30", scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + ids + ") ids"));
            }
        }
    }

    private static void compareCatalog(TestDatabase old, TestDatabase own, boolean smart2) throws Exception {
        try (var legacy = old.connect(); var nativeSql = own.connect()) {
            var expected = DbSchemaCatalog.read(legacy, old.postgis(), old.schema());
            var actual = DbSchemaCatalog.read(nativeSql, own.postgis(), own.schema());
            if (smart2) expected.canonicalReferenceAlternatives().assertSameAs(actual.canonicalReferenceAlternatives());
            else expected.assertSameAs(actual);
        }
    }

    private static void extendSmartPoly(Path ili, Path input, String model, int version) throws Exception {
        Files.writeString(ili, Files.readString(ili)
                .replace("SmartTarget : REFERENCE TO SmartUnit;", "SmartTarget : REFERENCE TO SmartUnit;\n      MeterTarget : REFERENCE TO Meter;")
                .replace("CLASS Survey = Details : Note; END Survey;",
                        "CLASS Survey = Details : Note; END Survey;\n    CLASS ExtraSurvey EXTENDS Survey = END ExtraSurvey;"));
        String ref = version == 23 ? "REF" : "i:ref";
        String surveyTag = version == 23 ? model + ".Data.Survey" : "Survey";
        String extraTag = version == 23 ? model + ".Data.ExtraSurvey" : "ExtraSurvey";
        String tid = version == 23 ? "TID" : "i:tid";
        String target = "<SmartTarget " + ref + "=\"fixture-s\"/>";
        String close = "</" + surveyTag + ">";
        Files.writeString(input, Files.readString(input)
                .replace(target, target + "<MeterTarget " + ref + "=\"fixture-s\"/>")
                .replace(close, close + "\n      <" + extraTag + " " + tid + "=\"fixture-extra\">"
                        + "<Observed " + ref + "=\"fixture-m\"/></" + extraTag + ">"));
    }

    private static void assertReferenceTransfer(SmartFixtures fixtures, Path output, String model, String prefix,
                                                int version, boolean smart2) throws Exception {
        if (!smart2) {
            fixtures.assertReferences(output, model, prefix);
            return;
        }
        fixtures.assertPolymorphicReferences(output, model, prefix);
        fixtures.assertExtraSurvey(output, model, prefix);
        var basket = children(readDataSection(output, version)).getFirst();
        var survey = fixtures.object(basket, model, "Survey", prefix + "-survey");
        var note = children(child(survey, "Details")).getFirst();
        assertEquals(prefix + "-s", id(child(note, "MeterTarget"), version, "ref"));
    }
}
