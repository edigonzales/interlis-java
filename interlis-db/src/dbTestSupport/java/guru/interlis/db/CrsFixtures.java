package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CrsFixtures {
    private final Path work;
    private final int version;

    CrsFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    String station(String model, String tid, int epsg) {
        return object(model, "Station", tid, field("Location",
                epsg == 21781 ? point("600100.125", "200200.375") : point("7.125", "46.375")));
    }

    void assertStation(Path output, String tid, int epsg) throws Exception {
        assertPoint(output, tid, "Location", epsg == 21781 ? "600100.125" : "7.125", epsg == 21781 ? "200200.375" : "46.375");
    }

    String declaredObjects(String model, String tid) {
        String trace = line(point("600100.125", "200100.375", "4.125") + point("600120.250", "200130.625", "-9.375"));
        String shell = line(point("7", "46") + point("8", "46") + point("8", "47") + point("7", "47") + point("7", "46"));
        String hole = line(point("7.125", "46.125") + point("7.125", "46.375") + point("7.375", "46.375")
                + point("7.375", "46.125") + point("7.125", "46.125"));
        String area = line(point("600300", "200300", "1") + point("600340", "200300", "2")
                + point("600340", "200340", "3") + point("600300", "200340", "4") + point("600300", "200300", "1"));
        String second = line(point("600150", "200150", "11") + point("600160", "200180", "12"));
        return object(model, "Globe", tid + "-globe", field("Location", point("7.125", "46.375")))
                + object(model, "Marker", tid + "-marker", field("Location", point("2600001.625", "1200002.125")))
                + station(model, tid + "-station", 21781)
                + object(model, "Route", tid + "-route", field("Trace", trace))
                + object(model, "Plot", tid + "-plot", field("Outline", surface(shell, hole)))
                + object(model, "Parcel", tid + "-parcel", field("Boundary", surface(area, "")))
                + (version == 24 ? object(model, "Network", tid + "-network",
                        field("Routes", "<g:multipolyline>" + trace + second + "</g:multipolyline>")) : "");
    }

    void assertDeclaredSrs(Target db, Connection sql, String tid) throws Exception {
        assertStoredEpsg(db, sql, "globe", "location", tid + "-globe", 4326);
        assertStoredEpsg(db, sql, "marker", "location", tid + "-marker", 2056);
        assertStoredEpsg(db, sql, "station", "location", tid + "-station", 21781);
        assertStoredEpsg(db, sql, "route", "trace", tid + "-route", 21781);
        assertStoredEpsg(db, sql, "plot", "outline", tid + "-plot", 4326);
        assertStoredEpsg(db, sql, "parcel", "boundary", tid + "-parcel", 21781);
        if (version == 24) assertStoredEpsg(db, sql, "network", "routes", tid + "-network", 21781);
    }

    Path overrideModel(String model) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
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
    }

    Path staticModel(String model) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
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
    }

    static void useLocalGeoPackageSrs(Connection sql) throws Exception {
        execute(sql, "UPDATE gpkg_spatial_ref_sys SET srs_id=921781 WHERE srs_id=21781");
        execute(sql, "UPDATE gpkg_geometry_columns SET srs_id=921781 WHERE srs_id=21781");
        execute(sql, "UPDATE gpkg_contents SET srs_id=921781 WHERE srs_id=21781");
    }

    void assertDeclared(Path file, String tid) throws Exception {
        var basket = basket(file, tid);
        assertEquals(version == 24 ? 7 : 6, children(basket).size());
        assertPoint(object(basket, tid + "-globe"), "Location", "7.125", "46.375");
        assertPoint(object(basket, tid + "-marker"), "Location", "2600001.625", "1200002.125");
        assertPoint(object(basket, tid + "-station"), "Location", "600100.125", "200200.375");
        var route = child(object(basket, tid + "-route"), "Trace");
        assertOrdinates(route, 1, "600100.125", "600120.250");
        assertOrdinates(route, 2, "200100.375", "200130.625");
        assertOrdinates(route, 3, "4.125", "-9.375");
        var plot = child(object(basket, tid + "-plot"), "Outline");
        assertOrdinates(plot, 1, "7", "8", "8", "7", "7", "7.125", "7.125", "7.375", "7.375", "7.125");
        assertOrdinates(plot, 2, "46", "46", "47", "47", "46", "46.125", "46.375", "46.375", "46.125", "46.125");
        assertOrdinates(child(object(basket, tid + "-parcel"), "Boundary"), 3, "1", "2", "3", "4", "1");
        if (version == 24) {
            var routes = child(object(basket, tid + "-network"), "Routes");
            assertOrdinates(routes, 3, "4.125", "-9.375", "11", "12");
            assertEquals(2, children(children(routes).getFirst()).size());
        }
    }

    void assertOrdinates(Element field, int axis, String... values) {
        var found = field.getElementsByTagNameNS("*", (version == 23 ? "C" : "c") + axis);
        assertEquals(values.length, found.getLength(), field.getTagName());
        for (int i = 0; i < values.length; i++) {
            assertEquals(0, new BigDecimal(values[i]).compareTo(new BigDecimal(found.item(i).getTextContent())));
        }
    }

    Path fixture(String model) throws Exception {
        Path ili = work.resolve(model + ".ili");
        if (Files.notExists(ili)) {
            try (var resource = CrsFixtures.class.getResourceAsStream("/" + ili.getFileName())) {
                Files.copy(resource, ili);
            }
        }
        return ili;
    }

    Path defaultModel(String model, int epsg) throws Exception {
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

    static void assertStoredEpsg(Target db, Connection sql, String table, String field, String tid, int epsg)
            throws Exception {
        String query = " FROM " + db.table(table) + " WHERE t_ili_tid = '" + tid + "'";
        assertStoredSrid(db, sql, query, field, epsg);
    }

    static void assertStoredSrid(Target db, Connection sql, String query, String field, int srid)
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

    Path delivery(String model, String bid, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, bid, objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(model + "-" + bid + ".xtf"), text);
    }

    String object(String model, String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    String point(String... coordinates) {
        var body = new StringBuilder();
        for (int axis = 0; axis < coordinates.length; axis++) {
            body.append(field((version == 23 ? "C" : "g:c") + (axis + 1), coordinates[axis]));
        }
        return field(version == 23 ? "COORD" : "g:coord", body.toString());
    }

    String line(String positions) {
        return field(version == 23 ? "POLYLINE" : "g:polyline", positions);
    }

    String surface(String shell, String hole) {
        if (version == 23) {
            return field("SURFACE", field("BOUNDARY", shell) + (hole.isEmpty() ? "" : field("BOUNDARY", hole)));
        }
        return field("g:surface", field("g:exterior", shell) + (hole.isEmpty() ? "" : field("g:interior", hole)));
    }

    static String field(String name, String value) {
        return "<" + name + ">" + value + "</" + name + ">";
    }

    Element basket(Path output, String tid) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals("basket-" + tid, id(basket, version, "bid"));
        return basket;
    }

    Element object(Element basket, String tid) {
        return children(basket).stream().filter(object -> id(object, version, "tid").equals(tid))
                .findFirst().orElseThrow();
    }

    void assertPoint(Path output, String tid, String field, String x, String y) throws Exception {
        assertPoint(object(basket(output, tid), tid), field, x, y);
    }

    static void assertPoint(Element object, String field, String x, String y) {
        var position = children(children(child(object, field)).getFirst());
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(position.get(0).getTextContent())));
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(position.get(1).getTextContent())));
    }

    static void assertHits(Target db, Connection sql, String table, String column, String x, String y,
                                   int epsg, int count) throws Exception {
        String actual = db.postgis() ? scalar(sql, "SELECT count(*) FROM " + db.table(table) + " WHERE " + column
                + " && ST_MakeEnvelope(" + x + "," + y + "," + x + "," + y + "," + epsg + ")")
                : geoPackageHits(sql, table, column, x, y);
        assertEquals(Integer.toString(count), actual, table + "." + column);
    }

    private static String geoPackageHits(Connection sql, String table, String column, String x, String y)
            throws Exception {
        return scalar(sql, "SELECT count(*) FROM rtree_" + table + "_" + column + " WHERE minx <= " + x
                + " AND maxx >= " + x + " AND miny <= " + y + " AND maxy >= " + y);
    }

    String mixedObject(String model, String tid, String centerX, boolean withOld) {
        return object(model, "Asset", tid, field("Center", point(centerX, "1200002.125"))
                + (withOld ? field("OldPosition", point("600100.125", "200200.375")) : "")
                + field("GeoPosition", point("7.125", "46.375")));
    }

    static void assertMixed(Element object, String centerX, boolean withOld) {
        assertPoint(object, "Center", centerX, "1200002.125");
        if (withOld) assertPoint(object, "OldPosition", "600100.125", "200200.375");
        else assertNull(child(object, "OldPosition"));
        assertPoint(object, "GeoPosition", "7.125", "46.375");
    }

    static List<String> nativeLayout(Target db, boolean split) {
        if (split && db.postgis()) return List.of("--one-geom-per-table");
        if (!split && !db.postgis()) return List.of("--gpkg-multi-geom-per-table");
        return List.of();
    }

    static void assertMixedSrs(Target db, Connection sql, String tid, boolean split, boolean suffix, boolean withOld)
            throws Exception {
        assertStoredEpsg(db, sql, "asset", suffix ? "center_2056" : "center", tid, 2056);
        for (String field : List.of("oldposition", "geoposition")) {
            boolean old = field.equals("oldposition");
            int epsg = old ? 21781 : 4326;
            String column = suffix ? field + "_" + epsg : field;
            String table = split ? "asset_" + column : "asset";
            String where = " WHERE t_id=(SELECT t_id FROM " + db.table("asset") + " WHERE t_ili_tid='" + tid + "')";
            if (old && !withOld) {
                assertNull(scalar(sql, "SELECT " + column + " FROM " + db.table(table) + where));
            } else {
                assertStoredSrid(db, sql, " FROM " + db.table(table) + where, column, epsg);
            }
        }
    }
}
