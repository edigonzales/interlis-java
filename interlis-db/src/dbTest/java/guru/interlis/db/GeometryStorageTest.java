package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static guru.interlis.db.BaseDbFixture.assertRows;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.emptyCollection;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeometryStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();
    private static final int MULTIPOINT = 4;
    private static final int MULTICURVE = 11;
    private static final int MULTISURFACE = 12;

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void invalidRadiusAndOpenSurfaceRollBack(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("geometry.gpkg"), backend);
        var base = new BaseDbFixture(work, version);
        base.seed(db);
        try (var sql = db.connect()) {
            assertExplicitRadiusRollback(db, sql, base.model(), version, base.transfer());
            assertOpenSurfaceRollback(db, sql, base.model(), version, base.transfer());
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void corruptStoredGeometriesKeepExport(Backend backend, int version) throws Exception {
        var db = POSTGIS.target(work.resolve("geometry.gpkg"), backend);
        var base = new BaseDbFixture(work, version);
        base.seed(db);
        Path output = work.resolve("geometry-out.xtf");
        assertCliSuccess(db, "export", base.model(), "legacy", output);
        try (var sql = db.connect()) {
            assertCorruptGeometriesRejected(db, sql, base.model(), output);
        }
    }

    @ParameterizedTest(name = "{0}, XTF 24")
    @EnumSource(Backend.class)
    void emptyAndCorruptGeometryCollections(Backend backend) throws Exception {
        var db = POSTGIS.target(work.resolve("geometry.gpkg"), backend);
        var base = new BaseDbFixture(work, 24);
        base.seed(db);
        Path output = work.resolve("geometry-out.xtf");
        assertCliSuccess(db, "export", base.model(), "legacy", output);
        try (var sql = db.connect()) {
            String model = base.model();
            String fixture = base.transfer();
            assertEmptyCollectionRollback(db, sql, model, fixture, "multisurface", "Park.Coverage", "multisurface", 13);
            assertEmptyCollectionRollback(db, sql, model, fixture, "multipolyline", "RouteGroup.Traces", "multicurve", 15);
            assertEmptyCollectionRollback(db, sql, model, fixture, "multicoord", "Survey.Locations", "multipoint", 16);
            assertCorruptNewCollectionRejected(db, sql, model, output);
            assertEmptyCollectionExport(db, sql, model);
            assertCorruptMultiSurfaceRejected(db, sql, model, output);
        }
    }

    private void assertExplicitRadiusRollback(Target db, Connection sql, String model, int version, String fixture)
            throws Exception {
        // The first arc follows the complete straight route in the same basket.
        String closing = version == 24 ? "</g:arc>" : "</ARC>";
        String radius = version == 24 ? "<g:r>5</g:r>" : "<R>5</R>";
        Path input = Files.writeString(work.resolve("radius.xtf"),
                fixture.replace("legacy-", "radius-").replaceFirst(closing, radius + closing));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "radius", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.Route.Trace"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("Explicit arc radius"), rejected::diagnostics);
        // On PostGIS the dataset, basket, straight route and rejected route each allocate an ID.
        // GeoPackage rolls back its counter together with the rows.
        assertEquals(Long.toString(db.postgis ? before + 4 : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='radius'"));
        assertRows(db, sql, version, 3);
    }

    private void assertOpenSurfaceRollback(Target db, Connection sql, String model, int version, String fixture)
            throws Exception {
        // Move only the closing position of the second plot, after the first plot and other objects.
        String x = "2601200.250";
        int plot = fixture.indexOf("legacy-plot-2");
        int closing = fixture.lastIndexOf(x, fixture.indexOf("</Footprint>", plot));
        assertTrue(closing > fixture.indexOf(x, plot));
        String openRing = fixture.substring(0, closing) + "2601200.375" + fixture.substring(closing + x.length());
        Path input = Files.writeString(work.resolve("open-surface.xtf"), openRing.replace("legacy-", "open-"));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "open", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.Plot.Footprint"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("closed"), rejected::diagnostics);
        // PostgreSQL allocates IDs for the dataset, basket, initial objects and the rejected plot.
        long allocated = version == 24 ? 10 : 9;
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='open'"));
        assertRows(db, sql, version, 3);
    }

    private void assertEmptyCollectionRollback(Target db, Connection sql, String model, String fixture,
                                               String tag, String attribute, String kind, long allocated) throws Exception {
        int start = fixture.indexOf("<g:" + tag + ">");
        String endTag = "</g:" + tag + ">";
        int end = fixture.indexOf(endTag, start) + endTag.length();
        String empty = fixture.substring(0, start) + "<g:" + tag + "/>" + fixture.substring(end);
        Path input = Files.writeString(work.resolve("empty-" + tag + ".xtf"), empty.replace("legacy-", "empty-"));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "empty", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data." + attribute), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("empty " + kind), rejected::diagnostics);
        // PostgreSQL allocates IDs for the dataset, basket, initial objects and the rejected object.
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='empty'"));
        assertRows(db, sql, 24, 3);
    }

    private static void assertCorruptGeometriesRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String corruptLine = db.postgis
                ? "st_geomfromtext('COMPOUNDCURVE EMPTY', 2056)"
                : "X'4750000200000808'";
        execute(sql, "update " + db.table("route") + " set trace=" + corruptLine + " where t_ili_tid='tail-route-1'");
        assertExportRejected(db, model, "tail", output, model + ".Data.Route.Trace");

        String tower = db.table("tower");
        String mast = db.table("mast");
        String nativeRelay = " where t_id=(select t_id from " + mast + " where t_ili_tid='native-relay')";
        execute(sql, "update " + tower + " set alignment=" + corruptLine + nativeRelay);
        try {
            assertExportRejected(db, model, "native", output, model + ".Data.Tower.Alignment");
        } finally {
            execute(sql, "update " + tower + " set alignment=(select t.alignment from " + tower
                    + " t join " + mast + " m on m.t_id=t.t_id where m.t_ili_tid='legacy-relay')" + nativeRelay);
        }

        String corruptSurface = db.postgis
                ? "st_geomfromtext('CURVEPOLYGON EMPTY', 2056)"
                : "X'4750000000000808000000000A00000001'";
        execute(sql, "update " + db.table("plot") + " set footprint=" + corruptSurface
                + " where t_ili_tid='native-plot-1'");
        assertExportRejected(db, model, "native", output, model + ".Data.Plot.Footprint");
    }

    private static void assertCorruptNewCollectionRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        if (db.postgis) {
            String survey = db.table("survey");
            String row = " where t_ili_tid='legacy-survey-1'";
            execute(sql, "update " + survey
                    + " set locations=st_geomfromtext('MULTIPOINT((2601500.625 1201500.500),EMPTY)', 2056)" + row);
            assertEquals("2", scalar(sql, "select st_numgeometries(locations) from " + survey + row));
            assertEquals("true", scalar(sql, "select cast(st_isempty(st_geometryn(locations, 2)) as text) from "
                    + survey + row));
            assertExportRejected(db, model, "legacy", output, model + ".Data.Survey.Locations");
        } else {
            execute(sql, "update routegroup set traces=X'4750000000000808000000000B00000001'"
                    + " where t_ili_tid='legacy-routegroup-1'");
            assertExportRejected(db, model, "legacy", output, model + ".Data.RouteGroup.Traces");
        }
    }

    private void assertEmptyCollectionExport(Target db, Connection sql, String model) throws Exception {
        String emptySurface = emptyCollection(db, "MULTISURFACE", MULTISURFACE);
        for (String table : List.of("park", "district")) {
            execute(sql, "update " + db.table(table) + " set coverage=" + emptySurface
                    + " where t_ili_tid='legacy-" + table + "-1'");
        }
        execute(sql, "update " + db.table("routegroup") + " set traces=" + emptyCollection(db, "MULTICURVE", MULTICURVE)
                + " where t_ili_tid='legacy-routegroup-1'");
        execute(sql, "update " + db.table("survey") + " set locations=" + emptyCollection(db, "MULTIPOINT", MULTIPOINT)
                + " where t_ili_tid='legacy-survey-1'");
        Path output = work.resolve("empty-collection-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        var basket = children(readDataSection(output, 24)).stream()
                .filter(element -> id(element, 24, "bid").equals("legacy-basket-1")).findFirst().orElseThrow();
        var attributes = Map.of("Park", "Coverage", "District", "Coverage", "RouteGroup", "Traces", "Survey", "Locations");
        for (var attribute : attributes.entrySet()) {
            var object = child(basket, attribute.getKey());
            assertNotNull(object);
            assertEquals("legacy-" + attribute.getKey().toLowerCase(Locale.ROOT) + "-1", id(object, 24, "tid"));
            assertNull(child(object, attribute.getValue()));
        }
    }

    private static void assertCorruptMultiSurfaceRejected(Target db, Connection sql, String model, Path output)
            throws Exception {
        String corrupt = db.postgis
                ? "st_geomfromtext('MULTISURFACE(CURVEPOLYGON((0 0,10 0,0 10,0 0)),CURVEPOLYGON EMPTY)', 2056)"
                : "X'4750000000000808000000000C00000001'";
        String park = db.table("park");
        String row = " where t_ili_tid='legacy-park-1'";
        execute(sql, "update " + park + " set coverage=" + corrupt + row);
        if (db.postgis) {
            assertEquals("2", scalar(sql, "select st_numgeometries(coverage) from " + park + row));
            assertEquals("true", scalar(sql, "select cast(st_isempty(st_geometryn(coverage, 2)) as text) from "
                    + park + row));
        }
        assertExportRejected(db, model, "legacy", output, model + ".Data.Park.Coverage");
    }
}
