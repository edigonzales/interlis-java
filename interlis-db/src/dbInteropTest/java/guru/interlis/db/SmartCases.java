package guru.interlis.db;

import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

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
