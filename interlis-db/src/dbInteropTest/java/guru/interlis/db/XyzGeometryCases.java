package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XyzGeometryCases {
    private final Path work;
    private final int version;
    private final String model;
    private final ReferenceTool reference;

    XyzGeometryCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        model = "DbXyz" + version;
        this.reference = reference;
    }

    void exchange(TestDatabase old, TestDatabase own) throws Exception {
        var ili = work.resolve(model + ".ili");
        try (var fixture = getClass().getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(fixture, ili);
        }
        reference.run(old, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--createGeomIdx", ili.toString());
        var create = cli(own, "schemaimport", model, null, ili, CancellationToken.none(), "--create-geom-index");
        assertEquals(0, create.code(), create::diagnostics);
        try (var oldSql = old.connect(); var ownSql = own.connect()) {
            var expected = DbSchemaCatalog.read(oldSql, old.postgis(), old.schema());
            if (!old.postgis()) {
                expected = expected.expectGeoPackageGeometryExtension("track", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("plot", "outline", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("parcel", "perimeter", "CURVEPOLYGON");
                if (version == 24) {
                    expected = expected.expectGeoPackageGeometryExtension("network", "routes", "MULTICURVE")
                            .expectGeoPackageGeometryExtension("park", "grounds", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("district", "territory", "MULTISURFACE");
                }
            }
            expected.assertSameAs(DbSchemaCatalog.read(ownSql, own.postgis(), own.schema()));
        }

        String markers = object("Marker", "above", "<Position>" + position("2600000.125", "1200000.375", "42.875") + "</Position>")
                + object("Marker", "below", "<Position>" + position("2600010.250", "1200020.500", "-31.125") + "</Position>")
                + object("Marker", "absent", "");
        String track = object("Track", "track-1", "<Trace>" + line(
                position("2600300.125", "1200300.375", "7.500")
                + position("2600310.250", "1200320.500", "-11.250")
                + position("2600320.375", "1200340.750", "12.875")) + "</Trace>");
        String shell = box(2600400, 1200400, 40, "1", "2", "3", "4");
        String hole = box(2600410, 1200410, 10, "11", "12", "13", "14");
        String areas = object("Plot", "plot-1", "<Outline>" + surface(shell, hole) + "</Outline>")
                + object("Parcel", "west", "<Perimeter>" + surface(
                        box(2600500, 1200500, 20, "21", "22", "23", "24"), "") + "</Perimeter>")
                + object("Parcel", "east", "<Perimeter>" + surface(
                        box(2600520, 1200500, 20, "22", "32", "33", "23"), "") + "</Perimeter>");
        String start = position("2602360", "1202220", "100");
        String arc = arc("2602240", "1202380", "340", "2602380", "1202360");
        String curved = object("Track", "arc-track", "<Trace>" + line(start + arc) + "</Trace>")
                + object("Plot", "arc-plot", "<Outline>" + surface(line(start + arc + start), "") + "</Outline>");
        String multi = version == 24 ? collections(start, arc, shell, hole) : "";
        Path input = delivery("xyz", markers + track + areas + curved + multi);
        reference.run(old, work.toString(), "--import", "--dataset", "xyz", "--importTid", "--importBid", input.toString());
        Path ownExport = work.resolve("xyz-native.xtf");
        assertCliSuccess(old, "export", model, "xyz", ownExport);
        assertTransfer(ownExport);

        assertCliSuccess(own, "import", model, "xyz", input);
        Path referenceExport = work.resolve("xyz-reference.xtf");
        reference.run(own, work.toString(), "--export", "--dataset", "xyz", "--exportTid", referenceExport.toString());
        assertTransfer(referenceExport);

        try (var sql = own.connect()) {
            assertStorage(own, sql);
            var broken = delivery("xyz-broken", markers + track + object("Track", "broken", "<Trace>" + line(
                    position("2600300.125", "1200300.375", "7.500") + position("2600320.375", "1200340.750"))
                    + "</Trace>"));
            var failure = cli(own, "replace", model, "xyz", broken, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertStorage(own, sql);
            if (version == 24) {
                String malformed = object("Survey", "wrong-dimension", "<Locations><g:multicoord>"
                        + position("2600600", "1200600", "13") + position("2600620", "1200620")
                        + "</g:multicoord></Locations>");
                var mixed = cli(own, "replace", model, "xyz", delivery("xyz-mixed", markers + malformed),
                        CancellationToken.none());
                assertNotEquals(0, mixed.code(), mixed::diagnostics);
                assertStorage(own, sql);
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + own.table("survey")));
            }
        }
    }

    private void assertStorage(TestDatabase db, Connection sql) throws SQLException {
        String table = db.table("marker");
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("plot") + " WHERE outline IS NOT NULL"));
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("parcel") + " WHERE perimeter IS NOT NULL"));
        assertEquals("3", scalar(sql, "SELECT count(*) FROM " + table));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + table + " WHERE aposition IS NULL"));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("track") + " WHERE t_ili_tid='track-1'"));
        String arcHit = db.postgis()
                ? "SELECT count(*) FROM " + db.table("track")
                        + " WHERE trace && ST_MakeEnvelope(2602399,1202300,2602399,1202300,2056)"
                : "SELECT count(*) FROM rtree_track_trace WHERE minx<=2602399 AND maxx>=2602399"
                        + " AND miny<=1202300 AND maxy>=1202300";
        assertEquals("1", scalar(sql, arcHit));
        if (db.postgis()) {
            assertTrue(scalar(sql, "SELECT ST_AsEWKT(trace) FROM " + db.table("track") + " WHERE t_ili_tid='arc-track'")
                    .contains("2602380 1202360 NaN"));
            assertEquals("42.875", scalar(sql, "SELECT ST_Z(aposition) FROM " + table + " WHERE t_ili_tid='above'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + table
                    + " WHERE aposition && ST_MakeEnvelope(2600000,1200000,2600001,1200001,2056)"));
        } else {
            assertEquals("1", scalar(sql, "SELECT count(*) FROM rtree_marker_aposition"
                    + " WHERE minx<=2600000.125 AND maxx>=2600000.125 AND miny<=1200000.375 AND maxy>=1200000.375"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM rtree_marker_aposition"));
        }
    }

    private void assertTransfer(Path file) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        assertEquals("xyz-basket", id(basket, version, "bid"));
        var objects = children(basket);
        assertEquals(version == 24 ? 14 : 9, objects.size());
        for (var object : objects) {
            switch (id(object, version, "tid")) {
                case "above" -> assertHeights(object, "Position", "42.875");
                case "below" -> assertHeights(object, "Position", "-31.125");
                case "absent" -> assertNull(child(object, "Position"));
                case "track-1" -> assertHeights(object, "Trace", "7.5", "-11.25", "12.875");
                case "plot-1" -> assertHeights(object, "Outline", "1", "2", "3", "4", "1", "11", "12", "13", "14", "11");
                case "west" -> assertHeights(object, "Perimeter", "21", "22", "23", "24", "21");
                case "east" -> assertHeights(object, "Perimeter", "22", "32", "33", "23", "22");
                case "arc-track" -> assertHeights(object, "Trace", "100", "340");
                case "arc-plot" -> assertHeights(object, "Outline", "100", "340", "100");
                case "survey" -> assertHeights(object, "Locations", "-113", "502.25");
                case "survey-empty" -> assertNull(child(object, "Locations"));
                case "network" -> assertHeights(object, "Routes", "100", "340", "13", "-14");
                case "park" -> assertHeights(object, "Grounds", "1", "2", "3", "4", "1", "11", "12", "13", "14", "11",
                        "100", "340", "100");
                case "district" -> assertHeights(object, "Territory", "5", "6", "7", "8", "5", "9", "10", "11", "12", "9");
                default -> throw new AssertionError("Unexpected object " + id(object, version, "tid"));
            }
        }
    }

    private void assertHeights(org.w3c.dom.Element object, String field, String... expected) {
        var elements = child(object, field).getElementsByTagNameNS("*", version == 23 ? "C3" : "c3");
        assertEquals(expected.length, elements.getLength(), field);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(0, new BigDecimal(expected[i]).compareTo(new BigDecimal(elements.item(i).getTextContent())), field);
        }
    }

    private String collections(String start, String arc, String shell, String hole) {
        return object("Survey", "survey", "<Locations><g:multicoord>"
                + position("2600600", "1200600", "-113") + position("2600620", "1200620", "502.25")
                + "</g:multicoord></Locations>")
                + object("Survey", "survey-empty", "")
                + object("Network", "network", "<Routes><g:multipolyline>" + line(start + arc)
                        + line(position("2600650", "1200600", "13") + position("2600660", "1200620", "-14"))
                        + "</g:multipolyline></Routes>")
                + object("Park", "park", "<Grounds><g:multisurface>" + surface(shell, hole)
                        + surface(line(start + arc + start), "") + "</g:multisurface></Grounds>")
                + object("District", "district", "<Territory><g:multisurface>"
                        + surface(box(2600700, 1200700, 10, "5", "6", "7", "8"), "")
                        + surface(box(2600800, 1200800, 10, "9", "10", "11", "12"), "")
                        + "</g:multisurface></Territory>");
    }

    private String arc(String x, String y, String z, String supportX, String supportY) {
        if (version == 23) {
            return "<ARC><C1>%s</C1><C2>%s</C2><C3>%s</C3><A1>%s</A1><A2>%s</A2></ARC>"
                    .formatted(x, y, z, supportX, supportY);
        }
        return "<g:arc><g:c1>%s</g:c1><g:c2>%s</g:c2><g:c3>%s</g:c3><g:a1>%s</g:a1><g:a2>%s</g:a2></g:arc>"
                .formatted(x, y, z, supportX, supportY);
    }

    private String box(int x, int y, int width, String first, String second, String third, String fourth) {
        return line(position(Integer.toString(x), Integer.toString(y), first)
                + position(Integer.toString(x + width), Integer.toString(y), second)
                + position(Integer.toString(x + width), Integer.toString(y + width), third)
                + position(Integer.toString(x), Integer.toString(y + width), fourth)
                + position(Integer.toString(x), Integer.toString(y), first));
    }

    private String surface(String shell, String hole) {
        if (version == 23) {
            return "<SURFACE><BOUNDARY>" + shell + "</BOUNDARY>"
                    + (hole.isEmpty() ? "" : "<BOUNDARY>" + hole + "</BOUNDARY>") + "</SURFACE>";
        }
        return "<g:surface><g:exterior>" + shell + "</g:exterior>"
                + (hole.isEmpty() ? "" : "<g:interior>" + hole + "</g:interior>") + "</g:surface>";
    }

    private String line(String positions) {
        String tag = version == 23 ? "POLYLINE" : "g:polyline";
        return "<" + tag + ">" + positions + "</" + tag + ">";
    }

    private Path delivery(String name, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, "xyz-basket", objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(name + ".xtf"), text);
    }

    private String object(String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private String position(String... ordinates) {
        String tag = version == 23 ? "COORD" : "g:coord";
        var xml = new StringBuilder("<").append(tag).append('>');
        for (int index = 0; index < ordinates.length; index++) {
            String axis = (version == 23 ? "C" : "g:c") + (index + 1);
            xml.append('<').append(axis).append('>').append(ordinates[index]).append("</").append(axis).append('>');
        }
        return xml.append("</").append(tag).append('>').toString();
    }
}
