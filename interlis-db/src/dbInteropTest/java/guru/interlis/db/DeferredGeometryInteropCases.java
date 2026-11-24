package guru.interlis.db;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.CrsFixtures.assertStoredEpsg;
import static guru.interlis.db.CrsFixtures.assertStoredSrid;
import static guru.interlis.db.CrsFixtures.field;
import static guru.interlis.db.CrsInteropSupport.createReference;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeferredGeometryInteropCases {
    private final Path work;
    private final ReferenceTool reference;
    private final CrsFixtures fixtures;

    DeferredGeometryInteropCases(Path work, ReferenceTool reference) {
        this.work = work;
        this.reference = reference;
        fixtures = new CrsFixtures(work, 24);
    }

    void deferredMulti(TestDatabase legacy, TestDatabase nativeDb) throws Exception {
        String model = "DeferredMulti24";
        Path ili = fixtures.fixture(model);
        createReference(reference, legacy, work, ili, 2056, "--multiSrs");
        assertSchemaWithIdentityColumns(nativeDb, model, ili, "--multi-srs");
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
        Path source = fixtures.deferredDelivery(model, suffix,
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
        var baskets = children(readDataSection(output, 24));
        for (String type : List.of("current", "old")) {
            String tid = type + "-" + suffix;
            var basket = baskets.stream().filter(b -> ("basket-" + tid).equals(id(b, 24, "bid"))).findFirst().orElseThrow();
            assertTrue(id(basket, 24, "domains").contains(type.equals("current") ? ".OldPoints" : ".CurrentPoints"));
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
        createReference(reference, legacy, work, ili, 2056, "--multiSrs");
        assertSchemaWithIdentityColumns(nativeDb, model, ili, "--multi-srs");
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
            Path input = fixtures.deferredDelivery(model, "curves-ref", curveObjects(model, "cr", 2600000, 1200000),
                    curveObjects(model, "or", 600000, 200000));
            // The 5.5.2 validator dereferences the absent bounds of the generic vertex domain.
            reference.run(db, work.toString(), "--import", "--disableValidation", "--dataset", "legacy", "--importTid", "--importBid", input.toString());
            Path output = work.resolve(db.schema() + "-curves-native.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertCurves(output, "cr", "or");
            input = fixtures.deferredDelivery(model, "curves-native", curveObjects(model, "cn", 2600000, 1200000),
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
        var baskets = children(readDataSection(output, 24));
        for (String tid : List.of(current, old)) {
            var basket = baskets.stream().filter(b -> children(b).stream()
                    .anyMatch(o -> ("route-" + tid).equals(id(o, 24, "tid")))).findFirst().orElseThrow();
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

}
