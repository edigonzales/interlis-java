package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
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

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.assertMissingTarget;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    void rejectMissingDefault(TestDatabase db) throws Exception {
        String model = "MissingCrs" + version;
        Path ili = defaultModel(model, 4326);
        var result = cli(db, "schemaimport", model, null, ili, CancellationToken.none(), "--default-epsg", "999999");
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("missing the EPSG:999999 SRS"), result::diagnostics);
        assertMissingTarget(db);
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
        var creation = cli(db, "schemaimport", model, null, ili, CancellationToken.none(), options);
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
        if (db.postgis()) {
            assertEquals(Integer.toString(epsg), scalar(sql, "SELECT ST_SRID(" + field + ")" + query));
        } else {
            try (var stmt = sql.createStatement(); var rows = stmt.executeQuery("SELECT " + field + query)) {
                assertTrue(rows.next());
                byte[] blob = rows.getBytes(1);
                var order = (blob[3] & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                assertEquals(epsg, ByteBuffer.wrap(blob).order(order).getInt(4));
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
