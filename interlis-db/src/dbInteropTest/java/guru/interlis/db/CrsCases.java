package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertMissingTarget;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.CrsFixtures.assertHits;
import static guru.interlis.db.CrsFixtures.assertStoredEpsg;
import static guru.interlis.db.CrsFixtures.assertStoredSrid;
import static guru.interlis.db.CrsFixtures.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CrsCases {
    private final Path work;
    private final int version;
    private final ReferenceTool reference;
    private final CrsFixtures fixtures;

    CrsCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        this.reference = reference;
        fixtures = new CrsFixtures(work, version);
    }

    void mutateDeferred(TestDatabase db) throws Exception {
        String model = "DeferredCrs24";
        createOwn(db, model, deferredPoints(model), "--multi-srs", "--create-geom-index");
        Path original = deferredDelivery(model, "main");
        reference.run(db, work.toString(), "--import", "--dataset", "main", "--importTid", "--importBid", original.toString());
        assertCliSuccess(db, "import", model, "other", deferredDelivery(model, "other"));

        String start = Files.readString(original);
        Path updated = Files.writeString(work.resolve("domains-update.xtf"), start.replace("2600001.625", "2600100.625"));
        String replacement = start.replace(".CurrentGrid", ".OldGrid")
                .replace("2600001.625", "620000.125").replace("1200002.125", "220000.375");
        Path switched = Files.writeString(work.resolve("domains-switch.xtf"), replacement);
        Path invalid = Files.writeString(work.resolve("domains-replace-invalid.xtf"), replacement.replace(
                "basket-old-main\" i:domains=\"" + model + ".Grid=" + model + ".OldGrid",
                "basket-old-main\" i:domains=\"" + model + ".Grid=" + model + ".MissingGrid"));
        String station = db.table("station");
        String oldTable = db.postgis() ? "station" : "station_location_21781";
        String objectId = "SELECT t_id FROM " + station + " WHERE t_ili_tid='current-main'";
        String basketId = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='basket-current-main'";
        try (var sql = db.connect()) {
            String objectBefore = scalar(sql, objectId);
            String basketBefore = scalar(sql, basketId);
            assertCliSuccess(db, "update", model, "main", updated);
            assertEquals(objectBefore, scalar(sql, objectId));
            assertEquals(basketBefore, scalar(sql, basketId));
            assertHits(db, sql, "station", "location_2056", "2600100.625", "1200002.125", 2056, 1);
            Path output = work.resolve("domains-updated.xtf");
            reference.run(db, work.toString(), "--export", "--dataset", "main", "--exportTid", output.toString());
            CrsFixtures.assertPoint(fixtures.object(fixtures.basket(output, "current-main"), "current-main"), "Location", "2600100.625", "1200002.125");

            var rejected = cli(db, "update", model, "main", switched, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("Cannot change domain assignments"), rejected::diagnostics);
            var rollback = cli(db, "replace", model, "main", invalid, CancellationToken.none());
            assertNotEquals(0, rollback.code(), rollback::diagnostics);
            assertTrue(rollback.diagnostics().contains("permitted assignments"), rollback::diagnostics);
            assertEquals(objectBefore, scalar(sql, objectId));
            assertEquals(basketBefore, scalar(sql, basketId));
            assertHits(db, sql, "station", "location_2056", "2600100.625", "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "location_21781", "620000.125", "220000.375", 21781, 0);

            assertCliSuccess(db, "replace", model, "main", switched);
            assertNotEquals(objectBefore, scalar(sql, objectId));
            assertHits(db, sql, "station", "location_2056", "2600100.625", "1200002.125", 2056, 0);
            assertHits(db, sql, oldTable, "location_21781", "620000.125", "220000.375", 21781, 1);
            output = work.resolve("domains-replaced.xtf");
            reference.run(db, work.toString(), "--export", "--dataset", "main", "--exportTid", output.toString());
            var switchedBasket = fixtures.basket(output, "current-main");
            assertEquals(model + ".Grid=" + model + ".OldGrid", id(switchedBasket, version, "domains"));
            CrsFixtures.assertPoint(fixtures.object(switchedBasket, "current-main"), "Location", "620000.125", "220000.375");

            assertCliSuccess(db, "delete", model, "main", null);
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertHits(db, sql, oldTable, "location_21781", "620000.125", "220000.375", 21781, 0);
            assertHits(db, sql, oldTable, "location_21781", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, "station", "location_2056", "2600001.625", "1200002.125", 2056, 1);
        }
    }

    void deferredMulti(TestDatabase legacy, TestDatabase nativeDb) throws Exception {
        String model = "DeferredMulti24";
        Path ili = fixtures.fixture(model);
        createReference(legacy, ili, 2056, "--multiSrs");
        createOwn(nativeDb, model, ili, "--multi-srs");
        for (var db : List.of(legacy, nativeDb)) {
            Path fromReference = multiDelivery(model, "ref-multi");
            reference.run(db, work.toString(), "--import", "--disableValidation", "--dataset", "reference",
                    "--importTid", "--importBid", fromReference.toString());
            Path output = work.resolve(db.schema() + "-native-multi.xtf");
            assertCliSuccess(db, "export", model, "reference", output);
            assertMulti(output, "ref-multi");
            Path fromNative = multiDelivery(model, "own-multi");
            assertCliSuccess(db, "import", model, "own", fromNative);
            output = work.resolve(db.schema() + "-ref-multi.xtf");
            reference.run(db, work.toString(), "--export", "--disableValidation", "--dataset", "own", "--exportTid", output.toString());
            assertMulti(output, "own-multi");
            try (var sql = db.connect()) {
                for (String pair : List.of("network.routes", "park.coverage", "district.boundary", "survey.sites")) {
                    String[] names = pair.split("\\.");
                    String tid = names[0] + (names[0].equals("survey") ? "-old" : "-current") + "-own-multi";
                    assertStoredEpsg(db, sql, names[0], names[1] + "_2056", tid, 2056);
                    String table = db.table(db.postgis() ? names[0] : names[0] + "_" + names[1] + "_21781");
                    tid = names[0] + (names[0].equals("survey") ? "-current" : "-old") + "-own-multi";
                    assertStoredSrid(db, sql, " FROM " + table + " WHERE t_id=(SELECT t_id FROM " + db.table(names[0])
                            + " WHERE t_ili_tid='" + tid + "')", names[1] + "_21781", 21781);
                    if (db.postgis()) {
                        assertEquals("3", scalar(sql, "SELECT ST_NDims(" + names[1] + "_21781) FROM " + table
                                + " WHERE t_ili_tid='" + tid + "'"));
                    } else {
                        assertEquals("1", scalar(sql, "SELECT z FROM gpkg_geometry_columns WHERE table_name='" + table
                                + "' AND column_name='" + names[1] + "_21781'"));
                    }
                }
            }
        }
    }

    private Path multiDelivery(String model, String suffix) throws Exception {
        Path source = deferredDelivery(model, suffix,
                multiObjects(model, "current-" + suffix, 2600000, 1200000, 600000, 200000),
                multiObjects(model, "old-" + suffix, 600000, 200000, 2600000, 1200000));
        String content = Files.readString(source)
                .replace(".CurrentGrid\"", ".CurrentGrid " + model + ".PointSet=" + model + ".OldPoints\"")
                .replace(".OldGrid\"", ".OldGrid " + model + ".PointSet=" + model + ".CurrentPoints\"");
        return Files.writeString(source, content);
    }

    private String multiObjects(String model, String tid, int x, int y, int pointX, int pointY) {
        String lines = fixtures.line(xyz(x, y, 1) + xyz(x + 20, y + 10, 2))
                + fixtures.line(xyz(x + 50, y + 50, 3) + xyz(x + 70, y + 80, 4));
        String surfaces = face3d(x + 100, y + 100) + face3d(x + 150, y + 150);
        String areas = face3d(x + 200, y + 200) + face3d(x + 250, y + 250);
        String points = xyz(pointX + 10, pointY + 10, 5) + xyz(pointX + 20, pointY + 20, 6);
        return fixtures.object(model, "Network", "network-" + tid, field("Routes", field("g:multipolyline", lines)))
                + fixtures.object(model, "Park", "park-" + tid, field("Coverage", field("g:multisurface", surfaces)))
                + fixtures.object(model, "District", "district-" + tid, field("Boundary", field("g:multisurface", areas)))
                + fixtures.object(model, "Survey", "survey-" + tid, field("Sites", field("g:multicoord", points)));
    }

    private String face3d(int x, int y) {
        return fixtures.surface(fixtures.line(xyz(x, y, 1) + xyz(x + 20, y, 2) + xyz(x + 20, y + 20, 3)
                + xyz(x, y + 20, 4) + xyz(x, y, 1)), "");
    }

    private String xyz(int x, int y, int z) {
        return fixtures.point(Integer.toString(x), Integer.toString(y), Integer.toString(z));
    }

    private void assertMulti(Path output, String suffix) throws Exception {
        var baskets = children(readDataSection(output, version));
        for (String type : List.of("current", "old")) {
            String tid = type + "-" + suffix;
            var basket = baskets.stream().filter(b -> ("basket-" + tid).equals(id(b, version, "bid"))).findFirst().orElseThrow();
            assertTrue(id(basket, version, "domains").contains(type.equals("current") ? ".OldPoints" : ".CurrentPoints"));
            fixtures.assertOrdinates(child(fixtures.object(basket, "network-" + tid), "Routes"), 3, "1", "2", "3", "4");
            for (String key : List.of("park-" + tid, "district-" + tid)) {
                var value = children(fixtures.object(basket, key)).getFirst();
                assertEquals(2, value.getElementsByTagNameNS("*", "surface").getLength());
                fixtures.assertOrdinates(value, 3, "1", "2", "3", "4", "1", "1", "2", "3", "4", "1");
            }
            var sites = child(fixtures.object(basket, "survey-" + tid), "Sites");
            fixtures.assertOrdinates(sites, 3, "5", "6");
            fixtures.assertOrdinates(sites, 1, type.equals("current") ? "600010" : "2600010",
                    type.equals("current") ? "600020" : "2600020");
        }
    }

    void deferredCurves(TestDatabase legacy, TestDatabase nativeDb) throws Exception {
        String model = "DeferredCurves24";
        Path ili = fixtures.fixture(model);
        createReference(legacy, ili, 2056, "--multiSrs");
        createOwn(nativeDb, model, ili, "--multi-srs");
        try (var ref = legacy.connect(); var own = nativeDb.connect()) {
            var expected = DbSchemaCatalog.read(ref, legacy.postgis(), legacy.schema());
            if (!legacy.postgis()) {
                for (String field : List.of("route.trace", "plot.outline", "parcel.boundary")) {
                    String[] names = field.split("\\.");
                    String type = names[0].equals("route") ? "COMPOUNDCURVE" : "CURVEPOLYGON";
                    expected = expected.expectGeoPackageGeometryExtension(names[0], names[1] + "_2056", type)
                            .expectGeoPackageGeometryExtension(names[0] + "_" + names[1] + "_21781", names[1] + "_21781", type);
                }
            }
            expected.assertSameAs(DbSchemaCatalog.read(own, nativeDb.postgis(), nativeDb.schema()));
        }
        for (var db : List.of(legacy, nativeDb)) {
            Path input = deferredDelivery(model, "curves-ref", curveObjects(model, "cr", 2600000, 1200000),
                    curveObjects(model, "or", 600000, 200000));
            // The 5.5.2 validator dereferences the absent bounds of the generic vertex domain.
            reference.run(db, work.toString(), "--import", "--disableValidation", "--dataset", "legacy", "--importTid", "--importBid", input.toString());
            Path output = work.resolve(db.schema() + "-curves-native.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertCurves(output, "cr", "or");
            input = deferredDelivery(model, "curves-native", curveObjects(model, "cn", 2600000, 1200000),
                    curveObjects(model, "on", 600000, 200000));
            assertCliSuccess(db, "import", model, "native", input);
            output = work.resolve(db.schema() + "-curves-ref.xtf");
            reference.run(db, work.toString(), "--export", "--disableValidation", "--dataset", "native", "--exportTid", output.toString());
            assertCurves(output, "cn", "on");
            try (var sql = db.connect()) {
                assertStoredEpsg(db, sql, "route", "trace_2056", "route-cn", 2056);
                String table = db.table(db.postgis() ? "route" : "route_trace_21781");
                assertStoredSrid(db, sql, " FROM " + table + " WHERE t_id=(SELECT t_id FROM " + db.table("route")
                        + " WHERE t_ili_tid='route-on')", "trace_21781", 21781);
            }
        }
    }

    private String curveObjects(String model, String tid, int x, int y) {
        String arc = "<g:arc>" + field("g:c1", Integer.toString(x + 20)) + field("g:c2", Integer.toString(y))
                + field("g:a1", Integer.toString(x + 10)) + field("g:a2", Integer.toString(y + 10)) + "</g:arc>";
        String trace = fixtures.line(xy(x, y) + arc + xy(x + 30, y));
        String shell = ring(x + 100, y + 100, 30, false);
        String hole = ring(x + 110, y + 110, 10, true);
        return fixtures.object(model, "Route", "route-" + tid, field("Trace", trace))
                + fixtures.object(model, "Plot", "plot-" + tid, field("Outline", fixtures.surface(shell, hole)))
                + fixtures.object(model, "Parcel", "parcel-" + tid, field("Boundary", fixtures.surface(ring(x + 200, y + 200, 20, false), "")));
    }

    private String ring(int x, int y, int span, boolean interior) {
        String a = xy(x, y);
        String b = xy(x + span, y);
        String c = xy(x + span, y + span);
        String d = xy(x, y + span);
        return fixtures.line(a + (interior ? d + c + b : b + c + d) + a);
    }

    private String xy(int x, int y) {
        return fixtures.point(Integer.toString(x), Integer.toString(y));
    }

    private void assertCurves(Path output, String current, String old) throws Exception {
        var baskets = children(readDataSection(output, version));
        for (String tid : List.of(current, old)) {
            var basket = baskets.stream().filter(b -> children(b).stream()
                    .anyMatch(o -> ("route-" + tid).equals(id(o, version, "tid")))).findFirst().orElseThrow();
            int offset = tid.equals(current) ? 2600000 : 600000;
            var trace = child(fixtures.object(basket, "route-" + tid), "Trace");
            fixtures.assertOrdinates(trace, 1, Integer.toString(offset), Integer.toString(offset + 20), Integer.toString(offset + 30));
            var controls = trace.getElementsByTagNameNS("*", "a1");
            assertEquals(1, controls.getLength());
            assertEquals(0, BigDecimal.valueOf(offset + 10).compareTo(new BigDecimal(controls.item(0).getTextContent())));
            var outline = child(fixtures.object(basket, "plot-" + tid), "Outline");
            assertEquals(1, outline.getElementsByTagNameNS("*", "interior").getLength());
            assertEquals(10, outline.getElementsByTagNameNS("*", "c1").getLength());
            assertEquals(5, child(fixtures.object(basket, "parcel-" + tid), "Boundary").getElementsByTagNameNS("*", "c1").getLength());
        }
    }

    void createDeferred(TestDatabase db, TestDatabase referenceDb, TestDatabase rejected) throws Exception {
        String model = "DeferredCrs24";
        Path ili = deferredPoints(model);
        createReference(referenceDb, ili, 2056, "--multiSrs", "--createGeomIdx");
        var withoutOption = schemaWithIdentityColumns(rejected, model, ili, CancellationToken.none());
        assertNotEquals(0, withoutOption.code(), withoutOption::diagnostics);
        assertTrue(withoutOption.diagnostics().contains("require --multi-srs"), withoutOption::diagnostics);
        assertMissingTarget(rejected);
        createOwn(db, model, ili, "--multi-srs", "--create-geom-index");
        assertCatalogs(referenceDb, db);
        deferredWriteExchange(db, model);
        Path exported = work.resolve("new-deferred-export.xtf");
        assertCliSuccess(db, "export", model, "tail", exported);
        assertDeferred(exported, model, "tail");

        Path ambiguous = Files.writeString(work.resolve("ambiguous.ili"),
                Files.readString(ili).replace("EPSG:21781", "EPSG:2056"));
        var result = schemaWithIdentityColumns(rejected, model, ambiguous, CancellationToken.none(), "--multi-srs");
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("multiple concrete domains with EPSG:2056"), result::diagnostics);
        assertMissingTarget(rejected);
    }

    void importDeferred(TestDatabase db) throws Exception {
        String model = "DeferredCrs24";
        createReference(db, deferredPoints(model), 2056, "--multiSrs", "--createGeomIdx");
        deferredWriteExchange(db, model);
    }

    private void deferredWriteExchange(TestDatabase db, String model) throws Exception {
        Path input = deferredDelivery(model, "native");
        assertCliSuccess(db, "import", model, "native", input);
        Path output = work.resolve(db.schema() + "-reference-export.xtf");
        reference.run(db, work.toString(), "--export", "--dataset", "native", "--exportTid", output.toString());
        assertDeferred(output, model, "native");

        String stations = db.table("station");
        String oldTable = db.postgis() ? "station" : "station_location_21781";
        try (var sql = db.connect()) {
            long maxId = Long.parseLong(scalar(sql, "SELECT max(t_id) FROM " + stations));
            Path tail = deferredDelivery(model, "tail");
            reference.run(db, work.toString(), "--import", "--dataset", "tail", "--importBid", "--importTid", tail.toString());
            assertTrue(Long.parseLong(scalar(sql, "SELECT min(t_id) FROM " + stations + " WHERE t_ili_tid LIKE '%-tail'")) > maxId);
            assertStoredEpsg(db, sql, "station", "location_2056", "current-native", 2056);
            assertStoredSrid(db, sql, " FROM " + db.table(oldTable) + " WHERE t_id=(SELECT t_id FROM " + stations
                    + " WHERE t_ili_tid='old-native')", "location_21781", 21781);
            assertNull(scalar(sql, "SELECT location_2056 FROM " + stations + " WHERE t_ili_tid='old-native'"));
            assertNull(scalar(sql, "SELECT location_21781 FROM " + db.table(oldTable) + " WHERE t_id=(SELECT t_id FROM "
                    + stations + " WHERE t_ili_tid='current-native')"));
            assertHits(db, sql, "station", "location_2056", "2600001.625", "1200002.125", 2056, 2);
            assertHits(db, sql, oldTable, "location_21781", "600100.125", "200200.375", 21781, 2);

            Path invalid = deferredDelivery(model, "invalid");
            String source = Files.readString(invalid);
            Files.writeString(invalid, source.replace("basket-old-invalid\" i:domains=\"" + model + ".Grid=" + model + ".OldGrid",
                    "basket-old-invalid\" i:domains=\"" + model + ".Grid=" + model + ".UnknownGrid"));
            var failure = cli(db, "import", model, "invalid", invalid, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertEquals("4", scalar(sql, "SELECT count(*) FROM " + stations));
            assertEquals("6", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertHits(db, sql, "station", "location_2056", "2600001.625", "1200002.125", 2056, 2);
        }
    }

    void exportDeferred(TestDatabase db) throws Exception {
        String model = "DeferredCrs24";
        createReference(db, deferredPoints(model), 2056, "--multiSrs");
        Path input = deferredDelivery(model, "reference");
        reference.run(db, work.toString(), "--import", "--dataset", "reference", "--importTid", "--importBid", input.toString());

        Path output = work.resolve("deferred-export.xtf");
        assertCliSuccess(db, "export", model, "reference", output);
        assertDeferred(output, model, "reference");
        String accepted = Files.readString(output);
        try (var sql = db.connect()) {
            String buckets = db.table("t_ili2db_basket");
            String where = " WHERE t_ili_tid='basket-current-reference'";
            String assignment = model + ".Grid=" + model + ".CurrentGrid";
            for (String corrupt : List.of("", assignment + " " + assignment, model + ".Grid=" + model + ".UnknownGrid")) {
                execute(sql, "UPDATE " + buckets + " SET domains='" + corrupt + "'" + where);
                rejectDeferredExport(db, model, output, accepted, "Invalid domain assignments");
            }
            execute(sql, "UPDATE " + buckets + " SET domains='" + assignment + "'" + where);

            String station = db.table("station");
            String old = db.table(db.postgis() ? "station" : "station_location_21781");
            String first = " WHERE t_id=(SELECT t_id FROM " + station + " WHERE t_ili_tid='current-reference')";
            String second = " WHERE t_id=(SELECT t_id FROM " + station + " WHERE t_ili_tid='old-reference')";
            execute(sql, "UPDATE " + old + " SET location_21781=(SELECT location_21781 FROM " + old + second + ")" + first);
            rejectDeferredExport(db, model, output, accepted, "inactive EPSG:21781");
        }
    }

    private static void rejectDeferredExport(TestDatabase db, String model, Path output, String accepted, String diagnosis)
            throws Exception {
        var rejected = cli(db, "export", model, "reference", output, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(diagnosis), rejected::diagnostics);
        assertEquals(accepted, Files.readString(output));
    }

    private Path deferredPoints(String model) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.4;
                MODEL %1$s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN
                    Grid (GENERIC) = COORD NUMERIC, NUMERIC;
                    CurrentGrid EXTENDS Grid = COORD 2460000.000 .. 2870000.000, 1045000.000 .. 1310000.000 REFSYS "EPSG:2056";
                    OldGrid EXTENDS Grid = COORD 460000.000 .. 870000.000, 45000.000 .. 310000.000 REFSYS "EPSG:21781";
                  CONTEXT grids = Grid = OldGrid OR CurrentGrid;
                  TOPIC Data =
                    DEFERRED GENERICS Grid;
                    CLASS Station =
                      Location : Grid;
                    END Station;
                  END Data;
                END %1$s.
                """.formatted(model));
    }

    private Path deferredDelivery(String model, String suffix) throws Exception {
        return deferredDelivery(model, suffix,
                fixtures.object(model, "Station", "current-" + suffix, field("Location", fixtures.point("2600001.625", "1200002.125"))),
                fixtures.object(model, "Station", "old-" + suffix, field("Location", fixtures.point("600100.125", "200200.375"))));
    }

    private Path deferredDelivery(String model, String suffix, String current, String old) throws Exception {
        return Files.writeString(work.resolve(model + "-" + suffix + ".xtf"), """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                    xmlns:g="http://www.interlis.ch/geometry/1.0" xmlns="http://www.interlis.ch/xtf/2.4/%1$s">
                  <i:headersection><i:models><i:model>%1$s</i:model></i:models><i:sender>interop</i:sender></i:headersection>
                  <i:datasection>
                    <Data i:bid="basket-current-%2$s" i:domains="%1$s.Grid=%1$s.CurrentGrid">%3$s</Data>
                    <Data i:bid="basket-old-%2$s" i:domains="%1$s.Grid=%1$s.OldGrid">%4$s</Data>
                    <Data i:bid="basket-empty-%2$s" i:domains="%1$s.Grid=%1$s.OldGrid"/>
                  </i:datasection>
                </i:transfer>
                """.formatted(model, suffix, current, old));
    }

    private void assertDeferred(Path output, String model, String suffix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(3, baskets.size());
        for (String type : List.of("current", "old", "empty")) {
            String bid = type + "-" + suffix;
            var basket = baskets.stream().filter(b -> ("basket-" + bid).equals(id(b, version, "bid"))).findFirst().orElseThrow();
            assertEquals(model + ".Grid=" + model + (type.equals("current") ? ".CurrentGrid" : ".OldGrid"),
                    id(basket, version, "domains"));
            switch (type) {
                case "current" -> CrsFixtures.assertPoint(fixtures.object(basket, bid), "Location", "2600001.625", "1200002.125");
                case "old" -> CrsFixtures.assertPoint(fixtures.object(basket, bid), "Location", "600100.125", "200200.375");
                case "empty" -> assertTrue(children(basket).isEmpty());
            }
        }
    }

    private void createReference(TestDatabase db, Path ili, int epsg, String... extra) throws Exception {
        CrsInteropSupport.createReference(reference, db, work, ili, epsg, extra);
    }

    private void createOwn(TestDatabase db, String model, Path ili, String... options) {
        var creation = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), options);
        assertEquals(0, creation.code(), creation::diagnostics);
    }

    private static void assertCatalogs(TestDatabase old, TestDatabase own) throws Exception {
        CrsInteropSupport.assertCatalogs(old, own);
    }

    @FunctionalInterface
    private interface ObjectFixture {
        String xml(String tid);
    }

    @FunctionalInterface
    private interface ExportAssertion {
        void verify(Path output, String tid) throws Exception;
    }

    private void mixedExchange(TestDatabase db, String model, String part, ObjectFixture object,
                               ExportAssertion assertion) throws Exception {
        String legacy = "legacy-" + part;
        String own = "own-" + part;
        Path input = fixtures.delivery(model, "basket-" + legacy, object.xml(legacy));
        reference.run(db, work.toString(), "--import", "--dataset", "legacy", "--importTid", "--importBid", input.toString());
        Path output = work.resolve(db.schema() + "-native.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertion.verify(output, legacy);

        input = fixtures.delivery(model, "basket-" + own, object.xml(own));
        assertCliSuccess(db, "import", model, "own", input);
        output = work.resolve(db.schema() + "-reference.xtf");
        reference.run(db, work.toString(), "--export", "--dataset", "own", "--exportTid", output.toString());
        assertion.verify(output, own);
    }

}
