package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CrsCases {
    private final Path work;
    private final int version;
    private final ReferenceTool reference;

    CrsCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        this.reference = reference;
    }

    void defaults(TestDatabase db, TestDatabase own, int epsg) throws Exception {
        String model = "DefaultCrs" + version + "_" + epsg;
        Path ili = defaultModel(model, epsg);
        createReference(db, ili, epsg);
        createOwn(own, model, ili, "--default-epsg", Integer.toString(epsg));
        assertCatalogs(db, own);
        defaultExchange(db, model, epsg);
        defaultExchange(own, model, epsg);

        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_settings")
                    + " SET setting = '2056' WHERE tag = 'ch.ehi.ili2db.defaultSrsCode'");
        }
        Path output = Files.writeString(work.resolve(model + "-bad-catalog.xtf"), "previous output");
        var failed = cli(db, "export", model, "legacy", output, CancellationToken.none());
        assertNotEquals(0, failed.code(), failed::diagnostics);
        assertTrue(failed.diagnostics().contains("Expected an EPSG:2056"), failed::diagnostics);
        assertTrue(failed.diagnostics().contains("station.location"), failed::diagnostics);
        assertEquals("previous output", Files.readString(output));
    }

    void declaredPointCrs(TestDatabase old, TestDatabase own) throws Exception {
        String model = "DeclaredCrs" + version;
        String declaration = version == 24 ? "21781" : "4326";
        String refsys = version == 24 ? " REFSYS \"EPSG:4326\"" : "";
        Path ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%s;
                !!@ CRS="EPSG:21781"
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN
                    !!@ CRS="EPSG:%s"
                    Angles = COORD -180.000 .. 180.000, -90.000 .. 90.000%s;
                  TOPIC Data =
                    CLASS Globe =
                      Location : Angles;
                    END Globe;
                    CLASS Marker =
                      !!@ CRS="EPSG:2056"
                      Location : COORD 2460000.000 .. 2870000.000, 1045000.000 .. 1310000.000;
                    END Marker;
                    CLASS Station =
                      Location : COORD 460000.000 .. 870000.000, 45000.000 .. 310000.000;
                    END Station;
                  END Data;
                END %s.
                """.formatted(version % 10, model, declaration, refsys, model));
        createReference(old, ili, 2056);
        createOwn(own, model, ili);
        assertCatalogs(old, own);
        declaredExchange(old, model);
        declaredExchange(own, model);
        try (var sql = old.connect()) {
            execute(sql, "DELETE FROM " + old.table("t_ili2db_settings")
                    + " WHERE tag = 'ch.ehi.ili2db.defaultSrsCode'");
        }
        Path output = work.resolve(model + "-no-default.xtf");
        assertCliSuccess(old, "export", model, "legacy", output);
        assertDeclared(output, "legacy-declared");
    }

    private void declaredExchange(TestDatabase db, String model) throws Exception {
        mixedExchange(db, model, "declared", tid ->
                object(model, "Globe", tid + "-globe", field("Location", point("7.125", "46.375")))
                + object(model, "Marker", tid + "-marker", field("Location", point("2600001.625", "1200002.125")))
                + object(model, "Station", tid + "-station", field("Location", point("600100.125", "200200.375"))),
                this::assertDeclared);
        try (var sql = db.connect()) {
            for (String tid : List.of("legacy-declared", "own-declared")) {
                assertStoredEpsg(db, sql, "globe", "location", tid + "-globe", 4326);
                assertStoredEpsg(db, sql, "marker", "location", tid + "-marker", 2056);
                assertStoredEpsg(db, sql, "station", "location", tid + "-station", 21781);
            }
        }
    }

    private void assertDeclared(Path file, String tid) throws Exception {
        var basket = basket(file, tid);
        assertEquals(3, children(basket).size());
        assertPoint(object(basket, tid + "-globe"), "Location", "7.125", "46.375");
        assertPoint(object(basket, tid + "-marker"), "Location", "2600001.625", "1200002.125");
        assertPoint(object(basket, tid + "-station"), "Location", "600100.125", "200200.375");
    }

    void vertexCrs(TestDatabase old, TestDatabase own) throws Exception {
        String model = "VertexCrs" + version;
        Path ili = fixture(model);
        createReference(old, ili, 2056);
        createOwn(own, model, ili);
        try (var ref = old.connect(); var sql = own.connect()) {
            var catalog = DbSchemaCatalog.read(ref, old.postgis(), old.schema());
            if (!old.postgis()) {
                catalog = catalog.expectGeoPackageGeometryExtension("route", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("plot", "outline", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("parcel", "boundary", "CURVEPOLYGON");
                if (version == 24) catalog = catalog.expectGeoPackageGeometryExtension("network", "routes", "MULTICURVE");
            }
            catalog.assertSameAs(DbSchemaCatalog.read(sql, own.postgis(), own.schema()));
        }
        vertexExchange(old, model);
        vertexExchange(own, model);
    }

    private void vertexExchange(TestDatabase db, String model) throws Exception {
        String trace = line(point("600100.125", "200100.375", "4.125") + point("600120.250", "200130.625", "-9.375"));
        String shell = line(point("7", "46") + point("8", "46") + point("8", "47") + point("7", "47") + point("7", "46"));
        String hole = line(point("7.125", "46.125") + point("7.125", "46.375") + point("7.375", "46.375")
                + point("7.375", "46.125") + point("7.125", "46.125"));
        String area = line(point("600300", "200300", "1") + point("600340", "200300", "2")
                + point("600340", "200340", "3") + point("600300", "200340", "4") + point("600300", "200300", "1"));
        String second = line(point("600150", "200150", "11") + point("600160", "200180", "12"));
        mixedExchange(db, model, "vertex", tid -> object(model, "Route", tid + "-route", field("Trace", trace))
                + object(model, "Plot", tid + "-plot", field("Outline", surface(shell, hole)))
                + object(model, "Parcel", tid + "-parcel", field("Boundary", surface(area, "")))
                + (version == 24 ? object(model, "Network", tid + "-network",
                        field("Routes", "<g:multipolyline>" + trace + second + "</g:multipolyline>")) : ""), this::assertVertex);
        try (var sql = db.connect()) {
            for (String tid : List.of("legacy-vertex", "own-vertex")) {
                assertStoredEpsg(db, sql, "route", "trace", tid + "-route", 21781);
                assertStoredEpsg(db, sql, "plot", "outline", tid + "-plot", 4326);
                assertStoredEpsg(db, sql, "parcel", "boundary", tid + "-parcel", 21781);
                if (version == 24) assertStoredEpsg(db, sql, "network", "routes", tid + "-network", 21781);
            }
        }
    }

    private void assertVertex(Path file, String tid) throws Exception {
        var basket = basket(file, tid);
        assertEquals(version == 24 ? 4 : 3, children(basket).size());
        assertOrdinates(child(object(basket, tid + "-route"), "Trace"), 3, "4.125", "-9.375");
        assertOrdinates(child(object(basket, tid + "-plot"), "Outline"), 1,
                "7", "8", "8", "7", "7", "7.125", "7.125", "7.375", "7.375", "7.125");
        assertOrdinates(child(object(basket, tid + "-parcel"), "Boundary"), 3, "1", "2", "3", "4", "1");
        if (version == 24) {
            var routes = child(object(basket, tid + "-network"), "Routes");
            assertOrdinates(routes, 3, "4.125", "-9.375", "11", "12");
            assertEquals(2, children(children(routes).getFirst()).size());
        }
    }

    private void assertOrdinates(Element field, int axis, String... values) {
        var found = field.getElementsByTagNameNS("*", (version == 23 ? "C" : "c") + axis);
        assertEquals(values.length, found.getLength(), field.getTagName());
        for (int i = 0; i < values.length; i++) {
            assertEquals(0, new BigDecimal(values[i]).compareTo(new BigDecimal(found.item(i).getTextContent())));
        }
    }

    void modelOverride(TestDatabase db) throws Exception {
        String model = "OverrideCrs" + version;
        Path ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%s;
                !!@ CRS="EPSG:2056"
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    CLASS Station =
                      Location : COORD 460000.000 .. 870000.000, 45000.000 .. 310000.000;
                    END Station;
                    CLASS Globe =
                      !!@ CRS="EPSG:4326"
                      Location : COORD -180.000 .. 180.000, -90.000 .. 90.000;
                    END Globe;
                  END Data;
                END %s.
                """.formatted(version % 10, model, model));
        createReference(db, ili, 2056, "--modelSrsCode", model + "=21781");
        mixedExchange(db, model, "override", tid ->
                object(model, "Station", tid + "-station", field("Location", point("600100.125", "200200.375")))
                + object(model, "Globe", tid + "-globe", field("Location", point("7.125", "46.375"))),
                (output, tid) -> {
                    var basket = basket(output, tid);
                    assertPoint(object(basket, tid + "-station"), "Location", "600100.125", "200200.375");
                    assertPoint(object(basket, tid + "-globe"), "Location", "7.125", "46.375");
                });
        try (var sql = db.connect()) {
            for (String tid : List.of("legacy-override", "own-override")) {
                assertStoredEpsg(db, sql, "station", "location", tid + "-station", 21781);
                assertStoredEpsg(db, sql, "globe", "location", tid + "-globe", 4326);
            }
        }
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
            assertPoint(object(basket(output, "current-main"), "current-main"), "Location", "2600100.625", "1200002.125");

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
            var switchedBasket = basket(output, "current-main");
            assertEquals(model + ".Grid=" + model + ".OldGrid", id(switchedBasket, version, "domains"));
            assertPoint(object(switchedBasket, "current-main"), "Location", "620000.125", "220000.375");

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
        Path ili = fixture(model);
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
        String lines = line(xyz(x, y, 1) + xyz(x + 20, y + 10, 2))
                + line(xyz(x + 50, y + 50, 3) + xyz(x + 70, y + 80, 4));
        String surfaces = face3d(x + 100, y + 100) + face3d(x + 150, y + 150);
        String areas = face3d(x + 200, y + 200) + face3d(x + 250, y + 250);
        String points = xyz(pointX + 10, pointY + 10, 5) + xyz(pointX + 20, pointY + 20, 6);
        return object(model, "Network", "network-" + tid, field("Routes", field("g:multipolyline", lines)))
                + object(model, "Park", "park-" + tid, field("Coverage", field("g:multisurface", surfaces)))
                + object(model, "District", "district-" + tid, field("Boundary", field("g:multisurface", areas)))
                + object(model, "Survey", "survey-" + tid, field("Sites", field("g:multicoord", points)));
    }

    private String face3d(int x, int y) {
        return surface(line(xyz(x, y, 1) + xyz(x + 20, y, 2) + xyz(x + 20, y + 20, 3)
                + xyz(x, y + 20, 4) + xyz(x, y, 1)), "");
    }

    private String xyz(int x, int y, int z) {
        return point(Integer.toString(x), Integer.toString(y), Integer.toString(z));
    }

    private void assertMulti(Path output, String suffix) throws Exception {
        var baskets = children(readDataSection(output, version));
        for (String type : List.of("current", "old")) {
            String tid = type + "-" + suffix;
            var basket = baskets.stream().filter(b -> ("basket-" + tid).equals(id(b, version, "bid"))).findFirst().orElseThrow();
            assertTrue(id(basket, version, "domains").contains(type.equals("current") ? ".OldPoints" : ".CurrentPoints"));
            assertOrdinates(child(object(basket, "network-" + tid), "Routes"), 3, "1", "2", "3", "4");
            for (String key : List.of("park-" + tid, "district-" + tid)) {
                var value = children(object(basket, key)).getFirst();
                assertEquals(2, value.getElementsByTagNameNS("*", "surface").getLength());
                assertOrdinates(value, 3, "1", "2", "3", "4", "1", "1", "2", "3", "4", "1");
            }
            var sites = child(object(basket, "survey-" + tid), "Sites");
            assertOrdinates(sites, 3, "5", "6");
            assertOrdinates(sites, 1, type.equals("current") ? "600010" : "2600010",
                    type.equals("current") ? "600020" : "2600020");
        }
    }

    void deferredCurves(TestDatabase legacy, TestDatabase nativeDb) throws Exception {
        String model = "DeferredCurves24";
        Path ili = fixture(model);
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
        String trace = line(xy(x, y) + arc + xy(x + 30, y));
        String shell = ring(x + 100, y + 100, 30, false);
        String hole = ring(x + 110, y + 110, 10, true);
        return object(model, "Route", "route-" + tid, field("Trace", trace))
                + object(model, "Plot", "plot-" + tid, field("Outline", surface(shell, hole)))
                + object(model, "Parcel", "parcel-" + tid, field("Boundary", surface(ring(x + 200, y + 200, 20, false), "")));
    }

    private String ring(int x, int y, int span, boolean interior) {
        String a = xy(x, y);
        String b = xy(x + span, y);
        String c = xy(x + span, y + span);
        String d = xy(x, y + span);
        return line(a + (interior ? d + c + b : b + c + d) + a);
    }

    private String xy(int x, int y) {
        return point(Integer.toString(x), Integer.toString(y));
    }

    private void assertCurves(Path output, String current, String old) throws Exception {
        var baskets = children(readDataSection(output, version));
        for (String tid : List.of(current, old)) {
            var basket = baskets.stream().filter(b -> children(b).stream()
                    .anyMatch(o -> ("route-" + tid).equals(id(o, version, "tid")))).findFirst().orElseThrow();
            int offset = tid.equals(current) ? 2600000 : 600000;
            var trace = child(object(basket, "route-" + tid), "Trace");
            assertOrdinates(trace, 1, Integer.toString(offset), Integer.toString(offset + 20), Integer.toString(offset + 30));
            var controls = trace.getElementsByTagNameNS("*", "a1");
            assertEquals(1, controls.getLength());
            assertEquals(0, BigDecimal.valueOf(offset + 10).compareTo(new BigDecimal(controls.item(0).getTextContent())));
            var outline = child(object(basket, "plot-" + tid), "Outline");
            assertEquals(1, outline.getElementsByTagNameNS("*", "interior").getLength());
            assertEquals(10, outline.getElementsByTagNameNS("*", "c1").getLength());
            assertEquals(5, child(object(basket, "parcel-" + tid), "Boundary").getElementsByTagNameNS("*", "c1").getLength());
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
                object(model, "Station", "current-" + suffix, field("Location", point("2600001.625", "1200002.125"))),
                object(model, "Station", "old-" + suffix, field("Location", point("600100.125", "200200.375"))));
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
                case "current" -> assertPoint(object(basket, bid), "Location", "2600001.625", "1200002.125");
                case "old" -> assertPoint(object(basket, bid), "Location", "600100.125", "200200.375");
                case "empty" -> assertTrue(children(basket).isEmpty());
            }
        }
    }

    void staticGeneric(TestDatabase old, TestDatabase own) throws Exception {
        String model = "StaticCrs24";
        String x = "600100.125";
        String y = "200200.375";
        String selection = model + ".Grid=" + model + ".LocalGrid";
        Path ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.4;
                MODEL StaticCrs24 (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN
                    Grid (GENERIC) = COORD NUMERIC, NUMERIC;
                    LocalGrid EXTENDS Grid = COORD 460000.000 .. 870000.000, 45000.000 .. 310000.000 REFSYS "EPSG:21781";
                  CONTEXT fixed = Grid = LocalGrid;
                  TOPIC Data =
                    CLASS Station =
                      Location : Grid;
                    END Station;
                  END Data;
                END StaticCrs24.
                """);
        createReference(old, ili, 2056);
        createOwn(own, model, ili);
        assertCatalogs(old, own);
        for (var db : List.of(old, own)) {
            mixedExchange(db, model, "static", tid -> object(model, "Station", tid, field("Location", point(x, y))),
                    (output, tid) -> assertPoint(output, tid, "Location", x, y));
            Path output = work.resolve(db.schema() + "-explicit.xtf");
            assertCliSuccess(db, "export", model, "own", output);
            assertEquals(selection, id(basket(output, "own-static"), version, "domains"));
            try (var sql = db.connect()) {
                assertStoredEpsg(db, sql, "station", "location", "own-static", 21781);
            }
            assertCliSuccess(db, "import", model, "explicit", output);

            String content = Files.readString(output);
            Files.writeString(output, content.replace(selection, model + ".Grid=" + model + ".Grid"));
            var invalid = cli(db, "import", model, "invalid", output, CancellationToken.none());
            assertNotEquals(0, invalid.code(), invalid::diagnostics);
            assertTrue(invalid.diagnostics().contains("permitted assignments"), invalid::diagnostics);
        }
    }

    void fixedMultiSrs(TestDatabase old, TestDatabase own, boolean split) throws Exception {
        String model = "MixedSrid" + version;
        Path ili = fixture(model);
        var referenceFlags = new ArrayList<>(List.of("--multiSrs"));
        var nativeFlags = new ArrayList<>(List.of("--multi-srs"));
        if (split && old.postgis()) {
            referenceFlags.add("--oneGeomPerTable");
            nativeFlags.add("--one-geom-per-table");
        } else if (!split && !old.postgis()) {
            referenceFlags.add("--gpkgMultiGeomPerTable");
            nativeFlags.add("--gpkg-multi-geom-per-table");
        }
        createReference(old, ili, 2056, referenceFlags.toArray(String[]::new));
        createOwn(own, model, ili, nativeFlags.toArray(String[]::new));
        assertCatalogs(old, own);
        mixedObjectExchange(old, model, split, true);
        mixedObjectExchange(own, model, split, true);

        String table = old.table("asset");
        try (var sql = old.connect()) {
            execute(sql, "ALTER TABLE " + table + " RENAME COLUMN center_2056 TO renamed_center");
            execute(sql, "UPDATE " + old.table("t_ili2db_attrname")
                    + " SET sqlname='renamed_center' WHERE sqlname='center_2056'");
            execute(sql, "UPDATE " + old.table("t_ili2db_column_prop")
                    + " SET columnname='renamed_center' WHERE columnname='center_2056'");
            if (!old.postgis()) {
                execute(sql, "UPDATE gpkg_geometry_columns SET column_name='renamed_center' WHERE table_name='asset' AND column_name='center_2056'");
            }
        }
        Path renamed = work.resolve(old.schema() + "-renamed.xtf");
        assertCliSuccess(old, "export", model, "legacy", renamed);
        assertMixed(object(basket(renamed, "legacy-mixed"), "legacy-mixed"), "2600001.625", true);
    }

    void mixedSrid(TestDatabase old, TestDatabase own, boolean split) throws Exception {
        String model = "MixedSrid" + version;
        Path ili = fixture(model);
        if (split && old.postgis()) {
            createReference(old, ili, 2056, "--oneGeomPerTable", "--createGeomIdx");
            createOwn(own, model, ili, "--one-geom-per-table", "--create-geom-index");
        } else if (!split && !old.postgis()) {
            createReference(old, ili, 2056, "--gpkgMultiGeomPerTable", "--createGeomIdx");
            createOwn(own, model, ili, "--gpkg-multi-geom-per-table", "--create-geom-index");
        } else {
            createReference(old, ili, 2056, "--createGeomIdx");
            createOwn(own, model, ili, "--create-geom-index");
        }
        assertCatalogs(old, own);
        mixedObjectExchange(old, model, split, false);
        mixedObjectExchange(own, model, split, false);

        if (split) {
            String table = own.table("t_ili2db_trafo");
            String path = model + ".Data.Asset.OldPosition:21781(" + model + ".Data.Asset)";
            String wrong = path.replace(":21781(", ":2056(");
            try (var sql = own.connect()) {
                assertEquals(path, scalar(sql, "SELECT iliname FROM " + table + " WHERE setting='asset_oldposition'"));
                execute(sql, "UPDATE " + table + " SET iliname='" + wrong + "' WHERE iliname='" + path + "'");
            }
            Path output = Files.writeString(work.resolve("wrong-trafo.xtf"), "previous output");
            var failure = cli(own, "export", model, "own", output, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("secondary geometry CLASSNAME/TRAFO/ATTRNAME mapping"), failure::diagnostics);
            assertEquals("previous output", Files.readString(output));
            try (var sql = own.connect()) {
                execute(sql, "UPDATE " + table + " SET iliname='" + path + "' WHERE iliname='" + wrong + "'");
            }
        } else if (!own.postgis()) {
            assertGeoPackageMultiSrs(own, model);
        }
        assertMixedMutations(own, model, split);
    }

    private void assertMixedMutations(TestDatabase db, String model, boolean split) throws Exception {
        String assets = db.table("asset");
        String baskets = db.table("t_ili2db_basket");
        String datasets = db.table("t_ili2db_dataset");
        String oldTable = split ? "asset_oldposition" : "asset";
        String geoTable = split ? "asset_geoposition" : "asset";
        String centerX = "2600001.625";
        String updatedX = "2600800.125";
        String replacementX = "2600900.125";
        Path updated = delivery(model, "basket-own-mixed", mixedObject(model, "own-mixed", updatedX, false));
        Path replacement = delivery(model, "replacement-srid", mixedObject(model, "replaced", replacementX, true));
        Path invalid = delivery(model, "rollback-srid", mixedObject(model, "rolled-back", replacementX, true)
                + mixedObject(model, "wrong-srid", updatedX, true));
        Path output = work.resolve(db.schema() + "-updated-srid.xtf");
        try (var sql = db.connect()) {
            String originalId = scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'");
            String basketId = scalar(sql, "SELECT t_id FROM " + baskets + " WHERE t_ili_tid='basket-own-mixed'");
            String datasetId = scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='own'");
            rejectWrongSridProperty(db, sql, model, oldTable, updated, output);
            assertHits(db, sql, "asset", "center", centerX, "1200002.125", 2056, 2);
            assertHits(db, sql, "asset", "center", updatedX, "1200002.125", 2056, 0);

            assertCliSuccess(db, "update", model, "own", updated);
            assertEquals(originalId, scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertEquals(basketId, scalar(sql, "SELECT t_basket FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertStoredEpsg(db, sql, "asset", "center", "own-mixed", 2056);
            assertStoredSrid(db, sql, " FROM " + db.table(geoTable) + " WHERE t_id=" + originalId, "geoposition", 4326);
            assertHits(db, sql, "asset", "center", centerX, "1200002.125", 2056, 1);
            assertHits(db, sql, "asset", "center", updatedX, "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, geoTable, "geoposition", "7.125", "46.375", 4326, 2);
            Files.delete(output);
            reference.run(db, work.toString(), "--export", "--dataset", "own", "--exportTid", output.toString());
            assertMixed(object(basket(output, "own-mixed"), "own-mixed"), updatedX, false);

            // Only the second new object fails the database check, after the first was written.
            if (db.postgis()) {
                execute(sql, "ALTER TABLE " + assets + " ADD CONSTRAINT test_srid_check"
                        + " CHECK (t_ili_tid <> 'wrong-srid' OR ST_SRID(center)=21781)");
            } else {
                execute(sql, """
                        CREATE TRIGGER test_srid_check BEFORE INSERT ON asset
                        WHEN NEW.t_ili_tid='wrong-srid' AND substr(hex(NEW.center),9,8) <> '00005515'
                        BEGIN SELECT RAISE(ABORT, 'test_srid_check'); END
                        """);
            }
            var failure = cli(db, "replace", model, "own", invalid, CancellationToken.none());
            assertEquals(1, failure.code(), failure::diagnostics);
            String sqlCode = db.postgis() ? "23514" : "19";
            assertEquals("Database operation failed (SQL code " + sqlCode + ")", failure.diagnostics().strip());
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + assets));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + baskets));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + datasets));
            assertEquals(originalId, scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertEquals(basketId, scalar(sql, "SELECT t_id FROM " + baskets + " WHERE t_ili_tid='basket-own-mixed'"));
            assertHits(db, sql, "asset", "center", updatedX, "1200002.125", 2056, 1);
            assertHits(db, sql, "asset", "center", replacementX, "1200002.125", 2056, 0);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, geoTable, "geoposition", "7.125", "46.375", 4326, 2);
            execute(sql, db.postgis() ? "ALTER TABLE " + assets + " DROP CONSTRAINT test_srid_check"
                    : "DROP TRIGGER test_srid_check");

            assertCliSuccess(db, "replace", model, "own", replacement);
            assertEquals(datasetId, scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='own'"));
            assertNull(scalar(sql, "SELECT t_id FROM " + assets + " WHERE t_ili_tid='own-mixed'"));
            assertHits(db, sql, "asset", "center", replacementX, "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 2);
            assertCliSuccess(db, "delete", model, "own", null);
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + assets));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + datasets));
            assertHits(db, sql, "asset", "center", replacementX, "1200002.125", 2056, 0);
            assertHits(db, sql, "asset", "center", centerX, "1200002.125", 2056, 1);
            assertHits(db, sql, oldTable, "oldposition", "600100.125", "200200.375", 21781, 1);
            assertHits(db, sql, geoTable, "geoposition", "7.125", "46.375", 4326, 1);
        }
    }

    private static void rejectWrongSridProperty(TestDatabase db, Connection sql, String model, String table,
                                                 Path updated, Path output) throws Exception {
        String properties = db.table("t_ili2db_column_prop");
        String where = " WHERE tablename='" + table + "' AND columnname='oldposition' AND tag='ch.ehi.ili2db.srid'";
        assertEquals("21781", scalar(sql, "SELECT setting FROM " + properties + where));
        execute(sql, "UPDATE " + properties + " SET setting='2056'" + where);
        Files.writeString(output, "previous output");
        for (String operation : List.of("export", "update")) {
            var failure = cli(db, operation, model, "own", operation.equals("export") ? output : updated,
                    CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("COLUMN_PROP.srid for " + table + ".oldposition"), failure::diagnostics);
        }
        assertEquals("previous output", Files.readString(output));
        execute(sql, "UPDATE " + properties + " SET setting='21781'" + where);
    }

    static void assertHits(TestDatabase db, Connection sql, String table, String column, String x, String y,
                                   int epsg, int count) throws Exception {
        String actual = db.postgis() ? scalar(sql, "SELECT count(*) FROM " + db.table(table) + " WHERE " + column
                + " && ST_MakeEnvelope(" + x + "," + y + "," + x + "," + y + "," + epsg + ")")
                : geoPackageHits(sql, table, column, x, y);
        assertEquals(Integer.toString(count), actual, table + "." + column);
    }

    private void assertGeoPackageMultiSrs(TestDatabase db, String model) throws Exception {
        try (var sql = db.connect()) {
            assertEquals("2056", scalar(sql, "SELECT srs_id FROM gpkg_contents WHERE table_name='asset'"));
            assertEquals("2460000.0", scalar(sql, "SELECT min_x FROM gpkg_contents WHERE table_name='asset'"));
            assertEquals("2", geoPackageHits(sql, "asset", "center", "2600001.625", "1200002.125"));
            assertEquals("2", geoPackageHits(sql, "asset", "oldposition", "600100.125", "200200.375"));
            assertEquals("2", geoPackageHits(sql, "asset", "geoposition", "7.125", "46.375"));
            execute(sql, "UPDATE gpkg_contents SET srs_id=4326 WHERE table_name='asset'");
            Path output = Files.writeString(work.resolve("wrong-contents-srs.xtf"), "previous output");
            var result = cli(db, "export", model, "own", output, CancellationToken.none());
            assertNotEquals(0, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("contents SRS differs from the first geometry column"), result::diagnostics);
            assertEquals("previous output", Files.readString(output));
            execute(sql, "UPDATE gpkg_contents SET srs_id=2056 WHERE table_name='asset'");
        }
    }

    private static String geoPackageHits(Connection sql, String table, String column, String x, String y)
            throws Exception {
        return scalar(sql, "SELECT count(*) FROM rtree_" + table + "_" + column + " WHERE minx <= " + x
                + " AND maxx >= " + x + " AND miny <= " + y + " AND maxy >= " + y);
    }

    private void mixedObjectExchange(TestDatabase db, String model, boolean split, boolean multiSrs) throws Exception {
        mixedExchange(db, model, "mixed", tid -> mixedObject(model, tid, "2600001.625", true),
                (output, tid) -> assertMixed(object(basket(output, tid), tid), "2600001.625", true));
        try (var sql = db.connect()) {
            for (String tid : List.of("legacy-mixed", "own-mixed")) {
                assertStoredEpsg(db, sql, "asset", multiSrs ? "center_2056" : "center", tid, 2056);
                for (String field : List.of("oldposition", "geoposition")) {
                    int epsg = field.equals("oldposition") ? 21781 : 4326;
                    String column = multiSrs ? field + "_" + epsg : field;
                    String table = split ? "asset_" + column : "asset";
                    String where = " WHERE t_id = (SELECT t_id FROM " + db.table("asset")
                            + " WHERE t_ili_tid='" + tid + "')";
                    assertStoredSrid(db, sql, " FROM " + db.table(table) + where, column, epsg);
                }
            }
        }
    }

    private String mixedObject(String model, String tid, String centerX, boolean withOld) {
        return object(model, "Asset", tid, field("Center", point(centerX, "1200002.125"))
                + (withOld ? field("OldPosition", point("600100.125", "200200.375")) : "")
                + field("GeoPosition", point("7.125", "46.375")));
    }

    private static void assertMixed(Element object, String centerX, boolean withOld) {
        assertPoint(object, "Center", centerX, "1200002.125");
        if (withOld) assertPoint(object, "OldPosition", "600100.125", "200200.375");
        else assertNull(child(object, "OldPosition"));
        assertPoint(object, "GeoPosition", "7.125", "46.375");
    }

    void rejectMissingDefault(TestDatabase db) throws Exception {
        String model = "MissingCrs" + version;
        Path ili = defaultModel(model, 4326);
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), "--default-epsg", "999999");
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("missing the EPSG:999999 SRS"), result::diagnostics);
        assertMissingTarget(db);
    }

    void localSrid(TestDatabase db) throws Exception {
        String model = "LocalSrid" + version;
        String x = "600100.125";
        String y = "200200.375";
        createReference(db, defaultModel(model, 21781), 21781);
        if (!db.postgis()) {
            try (var sql = db.connect()) {
                execute(sql, "UPDATE gpkg_spatial_ref_sys SET srs_id=921781 WHERE srs_id=21781");
                execute(sql, "UPDATE gpkg_geometry_columns SET srs_id=921781 WHERE srs_id=21781");
                execute(sql, "UPDATE gpkg_contents SET srs_id=921781 WHERE srs_id=21781");
            }
        }
        mixedExchange(db, model, "local", tid -> object(model, "Station", tid, field("Location", point(x, y))),
                (output, tid) -> assertPoint(output, tid, "Location", x, y));

        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "station", "location", "legacy-local", 21781);
            assertStoredEpsg(db, sql, "station", "location", "own-local", db.postgis() ? 21781 : 921781);
            if (db.postgis()) {
                execute(sql, "UPDATE spatial_ref_sys SET srid=921781 WHERE srid=21781");
            } else {
                execute(sql, """
                        INSERT INTO gpkg_spatial_ref_sys (srs_name, srs_id, organization, organization_coordsys_id, definition)
                        SELECT srs_name, 21781, organization, organization_coordsys_id, definition
                        FROM gpkg_spatial_ref_sys WHERE srs_id=4326
                        """);
            }
        }
        Path output = Files.writeString(work.resolve("ambiguous-local-srid.xtf"), "previous output");
        var failure = cli(db, "export", model, "legacy", output, CancellationToken.none());
        assertNotEquals(0, failure.code(), failure::diagnostics);
        String message = db.postgis() ? "Expected an EPSG:21781" : "SRS differs from its geometry column";
        assertTrue(failure.diagnostics().contains(message), failure::diagnostics);
        assertEquals("previous output", Files.readString(output));
        if (!db.postgis()) {
            assertCliSuccess(db, "export", model, "own", output);
            assertPoint(output, "own-local", "Location", x, y);
        }
    }

    private Path fixture(String model) throws Exception {
        Path ili = work.resolve(model + ".ili");
        if (Files.notExists(ili)) {
            try (var resource = getClass().getResourceAsStream("/" + ili.getFileName())) {
                Files.copy(resource, ili);
            }
        }
        return ili;
    }

    private Path defaultModel(String model, int epsg) throws Exception {
        String ranges = epsg == 21781 ? "460000.000 .. 870000.000, 45000.000 .. 310000.000"
                : "-180.000 .. 180.000, -90.000 .. 90.000";
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%s;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    CLASS Station =
                      Location : COORD %s;
                    END Station;
                  END Data;
                END %s.
                """.formatted(version % 10, model, ranges, model));
    }

    private void createReference(TestDatabase db, Path ili, Integer epsg, String... extra) throws Exception {
        var args = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG"));
        if (epsg != null) args.addAll(List.of("--defaultSrsCode", epsg.toString()));
        args.addAll(List.of(extra));
        args.add(ili.toString());
        reference.run(db, work.toString(), args.toArray(String[]::new));
    }

    private void createOwn(TestDatabase db, String model, Path ili, String... options) {
        var creation = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), options);
        assertEquals(0, creation.code(), creation::diagnostics);
    }

    private static void assertCatalogs(TestDatabase old, TestDatabase own) throws Exception {
        try (var ref = old.connect(); var sql = own.connect()) {
            DbSchemaCatalog.read(ref, old.postgis(), old.schema())
                    .assertSameAs(DbSchemaCatalog.read(sql, own.postgis(), own.schema()));
        }
    }

    private void defaultExchange(TestDatabase db, String model, int epsg) throws Exception {
        String x = epsg == 21781 ? "600100.125" : "7.125";
        String y = epsg == 21781 ? "200200.375" : "46.375";
        mixedExchange(db, model, "default", (tid) ->
                object(model, "Station", tid, field("Location", point(x, y))), (output, tid) ->
                assertPoint(output, tid, "Location", x, y));
        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "station", "location", "legacy-default", epsg);
            assertStoredEpsg(db, sql, "station", "location", "own-default", epsg);
        }
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
        Path input = delivery(model, "basket-" + legacy, object.xml(legacy));
        reference.run(db, work.toString(), "--import", "--dataset", "legacy", "--importTid", "--importBid", input.toString());
        Path output = work.resolve(db.schema() + "-native.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertion.verify(output, legacy);

        input = delivery(model, "basket-" + own, object.xml(own));
        assertCliSuccess(db, "import", model, "own", input);
        output = work.resolve(db.schema() + "-reference.xtf");
        reference.run(db, work.toString(), "--export", "--dataset", "own", "--exportTid", output.toString());
        assertion.verify(output, own);
    }

    private void assertStoredEpsg(TestDatabase db, Connection sql, String table, String field, String tid, int epsg)
            throws Exception {
        String query = " FROM " + db.table(table) + " WHERE t_ili_tid = '" + tid + "'";
        assertStoredSrid(db, sql, query, field, epsg);
    }

    private static void assertStoredSrid(TestDatabase db, Connection sql, String query, String field, int srid)
            throws Exception {
        if (db.postgis()) {
            assertEquals(Integer.toString(srid), scalar(sql, "SELECT ST_SRID(" + field + ")" + query));
        } else {
            try (var stmt = sql.createStatement(); var rows = stmt.executeQuery("SELECT " + field + query)) {
                assertTrue(rows.next());
                byte[] blob = rows.getBytes(1);
                var order = (blob[3] & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                assertEquals(srid, ByteBuffer.wrap(blob).order(order).getInt(4));
            }
        }
    }

    private Path delivery(String model, String bid, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, bid, objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(model + "-" + bid + ".xtf"), text);
    }

    private String object(String model, String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private String point(String... coordinates) {
        var body = new StringBuilder();
        for (int axis = 0; axis < coordinates.length; axis++) {
            body.append(field((version == 23 ? "C" : "g:c") + (axis + 1), coordinates[axis]));
        }
        return field(version == 23 ? "COORD" : "g:coord", body.toString());
    }

    private String line(String positions) {
        return field(version == 23 ? "POLYLINE" : "g:polyline", positions);
    }

    private String surface(String shell, String hole) {
        if (version == 23) {
            return field("SURFACE", field("BOUNDARY", shell) + (hole.isEmpty() ? "" : field("BOUNDARY", hole)));
        }
        return field("g:surface", field("g:exterior", shell) + (hole.isEmpty() ? "" : field("g:interior", hole)));
    }

    private static String field(String name, String value) {
        return "<" + name + ">" + value + "</" + name + ">";
    }

    private Element basket(Path output, String tid) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals("basket-" + tid, id(basket, version, "bid"));
        return basket;
    }

    private Element object(Element basket, String tid) {
        return children(basket).stream().filter(object -> id(object, version, "tid").equals(tid))
                .findFirst().orElseThrow();
    }

    private void assertPoint(Path output, String tid, String field, String x, String y) throws Exception {
        assertPoint(object(basket(output, tid), tid), field, x, y);
    }

    private static void assertPoint(Element object, String field, String x, String y) {
        var position = children(children(child(object, field)).getFirst());
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(position.get(0).getTextContent())));
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(position.get(1).getTextContent())));
    }
}
