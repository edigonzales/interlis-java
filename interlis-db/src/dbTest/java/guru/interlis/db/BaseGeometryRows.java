package guru.interlis.db;

import guru.interlis.db.BaseDbFixture.CurveComponent;
import guru.interlis.db.DbTestSupport.Target;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import static guru.interlis.db.BaseDbFixture.ARC_ROUTE;
import static guru.interlis.db.BaseDbFixture.COMPOUNDCURVE;
import static guru.interlis.db.BaseDbFixture.CURVED_SURFACE;
import static guru.interlis.db.BaseDbFixture.CURVEPOLYGON;
import static guru.interlis.db.BaseDbFixture.EAST_AREA;
import static guru.interlis.db.BaseDbFixture.MAST_CENTER;
import static guru.interlis.db.BaseDbFixture.MIXED_ROUTE;
import static guru.interlis.db.BaseDbFixture.MULTICURVE;
import static guru.interlis.db.BaseDbFixture.MULTIPOINT;
import static guru.interlis.db.BaseDbFixture.MULTISURFACE;
import static guru.interlis.db.BaseDbFixture.POINT;
import static guru.interlis.db.BaseDbFixture.SERVICE_POINT;
import static guru.interlis.db.BaseDbFixture.STRAIGHT_ROUTE;
import static guru.interlis.db.BaseDbFixture.SURFACE_WITH_HOLE;
import static guru.interlis.db.BaseDbFixture.SURVEY_POINTS;
import static guru.interlis.db.BaseDbFixture.WEST_AREA;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BaseGeometryRows {
    private BaseGeometryRows() { }

    static void assertStored(Target db, Connection sql, int version, String dataset) throws SQLException {
        assertStoredPlaces(db, sql, dataset);
        assertStoredClassHierarchy(db, sql, version, dataset);
        assertStoredRoutes(db, sql, dataset);
        assertStoredSurfaces(db, sql, dataset, "plot", "footprint", SURFACE_WITH_HOLE, CURVED_SURFACE);
        assertStoredSurfaces(db, sql, dataset, "parcel", "boundary", WEST_AREA, EAST_AREA);
        if (version == 24) {
            for (String table : List.of("park", "district")) {
                assertStoredCollection(db, sql, dataset, table, "coverage", MULTISURFACE, geometry -> {
                    assertCurvePolygon(geometry, SURFACE_WITH_HOLE);
                    assertCurvePolygon(geometry, CURVED_SURFACE);
                });
            }
            assertStoredCollection(db, sql, dataset, "routegroup", "traces", MULTICURVE, geometry -> {
                assertCompoundCurve(geometry, STRAIGHT_ROUTE);
                assertCompoundCurve(geometry, MIXED_ROUTE);
            });
            assertStoredCollection(db, sql, dataset, "survey", "locations", MULTIPOINT, geometry -> {
                for (var surveyPoint : SURVEY_POINTS) {
                    assertEquals(POINT, readWkbType(geometry));
                    assertStoredPosition(geometry, surveyPoint);
                }
            });
        }
    }

    private static void assertStoredPlaces(Target db, Connection sql, String dataset) throws SQLException {
        String point = db.postgis ? "st_srid(p.ageometry)" : "p.ageometry";
        String rows = "select p.t_ili_tid, b.t_ili_tid, p.aselect, " + point + " from " + db.table("place") + " p"
                + " join " + db.table("t_ili2db_basket") + " b on p.t_basket=b.t_id"
                + " join " + db.table("t_ili2db_dataset") + " d on b.dataset=d.t_id"
                + " where d.datasetname='" + dataset + "' order by p.t_ili_tid";
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals(dataset + "-place-1", result.getString(1));
            assertEquals(dataset + "-basket-1", result.getString(2));
            assertEquals("Brücke", result.getString(3));
            if (db.postgis) {
                srsId = result.getInt(4);
            } else {
                byte[] binary = result.getBytes(4);
                assertNotNull(binary);
                srsId = readGeoPackageHeader(ByteBuffer.wrap(binary));
            }
            assertTrue(result.next());
            assertEquals(dataset + "-place-2", result.getString(1));
            assertEquals(dataset + "-basket-1", result.getString(2));
            assertEquals("", result.getString(3));
            assertNull(result.getObject(4));
            assertTrue(result.next());
            assertEquals(dataset + "-place-3", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getString(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, "place", "ageometry", srsId);
    }

    private static void assertStoredClassHierarchy(Target db, Connection sql, int version, String dataset)
            throws SQLException {
        String mast = db.table("mast");
        String tower = db.table("tower");
        String relay = db.table("arelay");
        String beacon = db.table("beacon");
        String basket = db.table("t_ili2db_basket");
        String sites = version == 24 ? ", " + geometryField(db, "r.sites") : "";
        String objects = """
                select b.t_ili_tid, m.t_ili_tid, m.t_type, m.caption, t.height, r.aonline, f.asignal,
                       %s, %s%s
                from %s m join %s b on b.t_id=m.t_basket
                left join %s t on t.t_id=m.t_id and t.t_basket=m.t_basket
                left join %s r on r.t_id=m.t_id and r.t_basket=m.t_basket
                left join %s f on f.t_id=m.t_id and f.t_basket=m.t_basket
                where b.t_ili_tid='%s-basket-2' order by m.t_type
                """.formatted(geometryField(db, "m.center"), geometryField(db, "t.alignment"), sites,
                mast, basket, tower, relay, beacon, dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(objects)) {
            for (var item : List.of(List.of("relay", "leaf"), List.of("beacon", "sibling"),
                    List.of("mast", "base"), List.of("tower", "middle"))) {
                String type = item.getFirst();
                assertTrue(rows.next());
                assertEquals(dataset + "-basket-2", rows.getString(1));
                assertEquals(dataset + "-" + type, rows.getString(2));
                assertEquals(type.equals("relay") ? "arelay" : type, rows.getString(3));
                assertEquals(item.getLast(), rows.getString(4));
                assertEquals(switch (type) { case "relay" -> "24"; case "tower" -> "12"; default -> null; },
                        rows.getString(5));
                if (type.equals("relay")) {
                    assertFalse(rows.getBoolean(6));
                    assertFalse(rows.wasNull());
                    assertStoredPoint(hierarchyGeometry(db, rows, "center"), MAST_CENTER);
                    var alignment = hierarchyGeometry(db, rows, "alignment");
                    assertCompoundCurve(alignment, STRAIGHT_ROUTE);
                    assertFalse(alignment.hasRemaining());
                    if (version == 24) {
                        var locations = hierarchyGeometry(db, rows, "sites");
                        assertEquals(MULTIPOINT, readWkbType(locations));
                        assertEquals(SURVEY_POINTS.size(), locations.getInt());
                        for (var point : SURVEY_POINTS) {
                            assertEquals(POINT, readWkbType(locations));
                            assertStoredPosition(locations, point);
                        }
                        assertFalse(locations.hasRemaining());
                    }
                } else {
                    assertNull(rows.getObject(6));
                    assertNull(rows.getBytes("center"));
                    assertNull(rows.getBytes("alignment"));
                }
                assertEquals(type.equals("beacon") ? "green" : null, rows.getString(7));
            }
            assertFalse(rows.next());
        }
        String service = " from " + db.table("servicebase") + " b join " + db.table("serviceunit")
                + " u on b.t_id=u.t_id and b.t_basket=u.t_basket join " + basket + " k on k.t_id=b.t_basket"
                + " where b.t_ili_tid='" + dataset + "-serviceunit' and k.t_ili_tid='" + dataset + "-basket-2'"
                + " and b.t_type='serviceunit' and b.servicecode='unit-1'";
        try (var statement = sql.createStatement(); var rows = statement.executeQuery("select u.label, "
                + geometryField(db, "b.servicepoint") + service)) {
            assertTrue(rows.next());
            assertEquals("active", rows.getString(1));
            assertStoredPoint(hierarchyGeometry(db, rows, "servicepoint"), SERVICE_POINT);
            assertFalse(rows.next());
        }
    }

    private static String geometryField(Target db, String field) {
        if (!db.postgis) return field;
        String name = field.substring(field.indexOf('.') + 1);
        return "st_asbinary(" + field + ") as " + name + ", st_srid(" + field + ") as " + name + "_srid";
    }

    private static ByteBuffer hierarchyGeometry(Target db, ResultSet rows, String name) throws SQLException {
        byte[] bytes = rows.getBytes(name);
        assertNotNull(bytes, name);
        var geometry = ByteBuffer.wrap(bytes);
        assertEquals(2056, db.postgis ? rows.getInt(name + "_srid") : readGeoPackageHeader(geometry), name);
        return geometry;
    }

    private static void assertStoredPoint(ByteBuffer geometry, List<String> position) {
        assertEquals(POINT, readWkbType(geometry));
        assertStoredPosition(geometry, position);
        assertFalse(geometry.hasRemaining());
    }

    private static void assertStoredRoutes(Target db, Connection sql, String dataset)
            throws SQLException {
        String rows = geometryRows(db, dataset, "route", "trace");
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstBasket = dataset + "-basket-1";
            srsId = assertStoredRoute(db, result, dataset + "-route-1", firstBasket, STRAIGHT_ROUTE, false);
            assertTrue(result.next());
            assertEquals(dataset + "-route-2", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertEquals(srsId, assertStoredRoute(db, result, dataset + "-route-3", firstBasket, ARC_ROUTE, true));
            assertEquals(srsId, assertStoredRoute(db, result, dataset + "-route-4", firstBasket, MIXED_ROUTE, true));
            assertFalse(result.next());
        }
        assertSrs(db, sql, "route", "trace", srsId);
    }

    private static int assertStoredRoute(Target db, ResultSet result, String tid, String bid,
                                         List<CurveComponent> components, boolean withoutEnvelope) throws SQLException {
        var geometry = readStoredGeometry(result, tid, bid);
        int srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
        if (!db.postgis && withoutEnvelope) assertEquals(8, geometry.position(), "Curved GeoPackage header length");
        assertCompoundCurve(geometry, components);
        assertFalse(geometry.hasRemaining());
        return srsId;
    }

    private static void assertStoredSurfaces(Target db, Connection sql, String dataset, String table, String column,
                                             List<List<CurveComponent>> firstRings,
                                             List<List<CurveComponent>> secondRings)
            throws SQLException {
        String rows = geometryRows(db, dataset, table, column);
        String tidPrefix = dataset + "-" + table;
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstBasket = dataset + "-basket-1";
            srsId = assertStoredSurface(db, result, tidPrefix + "-1", firstBasket, firstRings);
            assertEquals(srsId,
                    assertStoredSurface(db, result, tidPrefix + "-2", firstBasket, secondRings));
            assertTrue(result.next());
            assertEquals(tidPrefix + "-3", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, table, column, srsId);
    }

    private static int assertStoredSurface(Target db, ResultSet result, String tid, String bid,
                                          List<List<CurveComponent>> rings) throws SQLException {
        var geometry = readStoredGeometry(result, tid, bid);
        int srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
        if (!db.postgis) assertEquals(8, geometry.position(), "Surface GeoPackage header length");
        assertCurvePolygon(geometry, rings);
        assertFalse(geometry.hasRemaining());
        return srsId;
    }

    private static void assertStoredCollection(Target db, Connection sql, String dataset, String table,
                                               String column, int type,
                                               Consumer<ByteBuffer> assertMembers) throws SQLException {
        String rows = geometryRows(db, dataset, table, column);
        String tidPrefix = dataset + "-" + table;
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            var geometry = readStoredGeometry(result, tidPrefix + "-1", dataset + "-basket-1");
            srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
            if (!db.postgis) assertEquals(8, geometry.position(), "Collection GeoPackage header length");
            assertEquals(type, readWkbType(geometry));
            assertEquals(2, geometry.getInt());
            assertMembers.accept(geometry);
            assertFalse(geometry.hasRemaining());
            assertTrue(result.next());
            assertEquals(tidPrefix + "-2", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, table, column, srsId);
    }

    private static String geometryRows(Target db, String dataset, String table, String column) {
        String field = "o." + column;
        String geometry = db.postgis ? "st_asbinary(" + field + "), st_srid(" + field + ")" : field;
        return "select o.t_ili_tid, b.t_ili_tid, " + geometry + " from " + db.table(table) + " o"
                + " join " + db.table("t_ili2db_basket") + " b on o.t_basket=b.t_id"
                + " join " + db.table("t_ili2db_dataset") + " d on b.dataset=d.t_id"
                + " where d.datasetname='" + dataset + "' order by o.t_ili_tid";
    }

    private static void assertCurvePolygon(ByteBuffer geometry, List<List<CurveComponent>> rings) {
        assertEquals(CURVEPOLYGON, readWkbType(geometry));
        assertEquals(rings.size(), geometry.getInt());
        for (var ring : rings) assertCompoundCurve(geometry, ring);
    }

    private static ByteBuffer readStoredGeometry(ResultSet result, String tid, String bid) throws SQLException {
        assertTrue(result.next());
        assertEquals(tid, result.getString(1));
        assertEquals(bid, result.getString(2));
        byte[] bytes = result.getBytes(3);
        assertNotNull(bytes);
        return ByteBuffer.wrap(bytes);
    }

    private static void assertCompoundCurve(ByteBuffer geometry, List<CurveComponent> components) {
        assertEquals(COMPOUNDCURVE, readWkbType(geometry));
        assertEquals(components.size(), geometry.getInt());
        for (var component : components) {
            assertEquals(component.type(), readWkbType(geometry));
            assertEquals(component.vertices().size(), geometry.getInt());
            for (var vertex : component.vertices()) {
                assertStoredPosition(geometry, vertex);
            }
        }
    }

    private static void assertStoredPosition(ByteBuffer geometry, List<String> position) {
        for (String ordinate : position) {
            assertEquals(Double.parseDouble(ordinate), geometry.getDouble());
        }
    }

    private static int readGeoPackageHeader(ByteBuffer bytes) {
        assertEquals('G', bytes.get());
        assertEquals('P', bytes.get());
        assertEquals(0, bytes.get());
        int flags = Byte.toUnsignedInt(bytes.get());
        assertTrue(flags <= 3, "Expected a nonempty standard 2D GeoPackage geometry");
        bytes.order((flags & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int srs = bytes.getInt();
        if ((flags & 2) != 0) bytes.position(bytes.position() + 4 * Double.BYTES);
        return srs;
    }

    private static int readWkbType(ByteBuffer bytes) {
        int order = bytes.get();
        assertTrue(order == 0 || order == 1, "Invalid WKB byte order");
        bytes.order(order == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return bytes.getInt();
    }

    private static void assertSrs(Target db, Connection sql, String table, String column, int srsId)
            throws SQLException {
        if (db.postgis) {
            assertEquals(2056, srsId);
            return;
        }
        assertEquals("2056", scalar(sql, "select organization_coordsys_id from gpkg_spatial_ref_sys"
                + " where organization='EPSG' and srs_id=" + srsId));
        assertEquals(Integer.toString(srsId), scalar(sql, "select srs_id from gpkg_geometry_columns"
                + " where table_name='" + table + "' and column_name='" + column + "'"));
    }
}
