package guru.interlis.db;

import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.ArcRadius;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.MultiSurface;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferGeometry.RadiusConvention;
import guru.interlis.transfer.TransferGeometry.Ring;
import guru.interlis.transfer.TransferGeometry.Segment;
import guru.interlis.transfer.TransferGeometry.Straight;
import guru.interlis.transfer.TransferGeometry.Surface;
import guru.interlis.transfer.TransferValue.NumberValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static guru.interlis.db.DbMapping.Kind.MULTISURFACE;
import static guru.interlis.db.DbMapping.Kind.POINT;
import static guru.interlis.db.DbMapping.Kind.POLYLINE;
import static guru.interlis.db.DbMapping.Kind.SURFACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class GeometryBinaryTest {
    @Test
    void readsGeoPackagePointWithIndependentHeaderAndWkbByteOrders() {
        byte[] bytes = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(15000)
                .order(ByteOrder.BIG_ENDIAN).put((byte) 0).putInt(1)
                .putDouble(2600000.125).putDouble(1200000.25).array();

        var point = assertInstanceOf(Coordinate.class, GeometryBinary.decode(bytes, true, 15000, POINT));
        assertEquals("2600000.125", point.ordinates().get(0).lexeme());
        assertEquals("1200000.25", point.ordinates().get(1).lexeme());
        byte[] encoded = GeometryBinary.encode(point, true, 15000, POINT);
        assertEquals(point, GeometryBinary.decode(encoded, true, 15000, POINT));
        assertThrows(DbException.class, () -> GeometryBinary.decode(bytes, true, 2056, POINT));
    }

    @Test
    void rejectsExtendedGeometryAndNonPointWkb() {
        byte[] bytes = ByteBuffer.allocate(29)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 32).putInt(2056)
                .put((byte) 0).putInt(1).putDouble(2600000).putDouble(1200000).array();
        assertThrows(DbException.class, () -> GeometryBinary.decode(bytes, true, 2056, POINT));

        byte[] nonPoint = ByteBuffer.allocate(21)
                .put((byte) 0).putInt(2).putDouble(2600000).putDouble(1200000).array();
        assertThrows(DbException.class, () -> GeometryBinary.decode(nonPoint, false, 2056, POINT));
    }

    @Test
    void readsCompoundCurveWithIndependentComponentByteOrders() {
        byte[] bytes = ByteBuffer.allocate(74).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(15000)
                .order(ByteOrder.BIG_ENDIAN).put((byte) 0).putInt(9).putInt(1)
                .order(ByteOrder.LITTLE_ENDIAN).put((byte) 1).putInt(2).putInt(3)
                .putDouble(2600000.25).putDouble(1200000.125)
                .putDouble(2600002.5).putDouble(1200003.25)
                .putDouble(2599999.5).putDouble(1200001.75).array();

        assertEquals(polyline(), GeometryBinary.decode(bytes, true, 15000, POLYLINE));
        assertThrows(DbException.class, () -> GeometryBinary.decode(bytes, true, 2056, POLYLINE));
    }

    @Test
    void writesOneLineStringWithAnXyEnvelope() {
        var polyline = polyline();
        byte[] bytes = GeometryBinary.encode(polyline, true, 15000, POLYLINE);
        var data = ByteBuffer.wrap(bytes);
        assertEquals('G', data.get());
        assertEquals('P', data.get());
        assertEquals(0, data.get());
        assertEquals(2, data.get());
        assertEquals(15000, data.getInt());
        assertEquals(2599999.5, data.getDouble());
        assertEquals(2600002.5, data.getDouble());
        assertEquals(1200000.125, data.getDouble());
        assertEquals(1200003.25, data.getDouble());
        assertEquals(0, data.get());
        assertEquals(9, data.getInt());
        assertEquals(1, data.getInt());
        assertEquals(0, data.get());
        assertEquals(2, data.getInt());
        assertEquals(3, data.getInt());
        assertEquals(polyline, GeometryBinary.decode(bytes, true, 15000, POLYLINE));
    }

    @Test
    void groupsConsecutiveArcsAndWritesNoGeoPackageEnvelope() {
        var start = coordinate("2600000.25", "1200000.125");
        var straight = new Straight(coordinate("2600001.5", "1200001.25"));
        var arc = new Arc(coordinate("2600002.5", "1200003.25"), coordinate("2600004.5", "1200001.75"),
                Optional.empty());
        var nextArc = new Arc(coordinate("2600005.5", "1200004.75"), coordinate("2600007.5", "1200002.75"),
                Optional.empty());
        var end = new Straight(coordinate("2600008.5", "1200003.75"));
        var line = new Polyline(List.of(new CurvePart(start, List.of(straight, arc, nextArc, end))));

        byte[] bytes = GeometryBinary.encode(line, true, 15000, POLYLINE);
        var data = ByteBuffer.wrap(bytes);
        assertEquals(0, data.get(3));
        assertEquals(15000, data.getInt(4));
        assertEquals(9, data.getInt(9));
        assertEquals(3, data.getInt(13));
        data.position(17);
        assertComponent(data, 2, 2);
        assertComponent(data, 8, 5);
        assertComponent(data, 2, 2);
        assertEquals(0, data.remaining());
        assertEquals(line, GeometryBinary.decode(bytes, true, 15000, POLYLINE));
    }

    @Test
    void readsEachComponentWithItsOwnByteOrder() {
        byte[] bytes = mixedCurveWkb();
        var start = coordinate("2600000.25", "1200000.125");
        var join = coordinate("2600002.5", "1200003.25");
        var arc = new Arc(coordinate("2600001.5", "1200004.25"), coordinate("2599999.5", "1200001.75"),
                Optional.empty());
        var expected = new Polyline(List.of(new CurvePart(start, List.of(new Straight(join), arc))));
        assertEquals(expected, GeometryBinary.decode(bytes, false, 2056, POLYLINE));
    }

    @Test
    void rejectsUnrepresentableCompoundCurvePayloads() {
        byte[] valid = GeometryBinary.encode(polyline(), false, 2056, POLYLINE);
        assertEquals(polyline(), GeometryBinary.decode(valid, false, 2056, POLYLINE));
        assertThrows(DbException.class, () -> GeometryBinary.decode(Arrays.copyOf(valid, 17), false, 2056, POLYLINE));
        assertThrows(DbException.class, () -> GeometryBinary.decode(
                Arrays.copyOf(valid, valid.length - 1), false, 2056, POLYLINE));

        byte[] corrupted = valid.clone();
        ByteBuffer.wrap(corrupted).putInt(5, 2); // Component count.
        assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
        ByteBuffer.wrap(corrupted).putInt(5, 1).putInt(10, 3); // POLYGON.
        assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
        ByteBuffer.wrap(corrupted).putInt(10, 1002); // LINESTRING Z.
        assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
        ByteBuffer.wrap(corrupted).putInt(10, 2).putInt(14, 100_001); // Vertex count.
        var oversized = assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
        assertEquals("A polyline requires 2 to 100000 stored XY pairs", oversized.getMessage());
        ByteBuffer.wrap(corrupted).putInt(14, 3).putDouble(18, Double.NaN); // First X.
        assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
    }

    @Test
    void rejectsEvenCircularPointCountsAndDisconnectedComponents() {
        byte[] corrupted = mixedCurveWkb();
        var data = ByteBuffer.wrap(corrupted).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(55, 4);
        var even = assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
        assertEquals("A WKB CIRCULARSTRING requires an odd number of at least three vertices", even.getMessage());

        data.putInt(55, 3).putDouble(59, 2600002.75);
        var disconnected = assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
        assertEquals("WKB compound curve components do not share an endpoint", disconnected.getMessage());
    }

    @Test
    void countsSharedEndpointsInTheStoredCoordinateLimit() {
        byte[] corrupted = mixedCurveWkb();
        ByteBuffer.wrap(corrupted).order(ByteOrder.LITTLE_ENDIAN).putInt(55, 99_999);
        var read = assertThrows(DbException.class, () -> GeometryBinary.decode(corrupted, false, 2056, POLYLINE));
        assertEquals("A polyline requires 2 to 100000 stored XY pairs", read.getMessage());

        var start = coordinate("2600000.25", "1200000.125");
        var join = coordinate("2600002.5", "1200003.25");
        var segments = new ArrayList<Segment>(Collections.nCopies(99_997, new Straight(join)));
        segments.add(new Arc(coordinate("2600001.5", "1200004.25"), start, Optional.empty()));
        var line = new Polyline(List.of(new CurvePart(start, segments)));
        var write = assertThrows(DbException.class, () -> GeometryBinary.encode(line, false, 2056, POLYLINE));
        assertEquals(read.getMessage(), write.getMessage());
    }

    @Test
    void rejectsAPolylineWithAnExplicitArcRadiusOrNoSegments() {
        var start = coordinate("2600000.25", "1200000.125");
        var support = coordinate("2600002.5", "1200003.25");
        var end = coordinate("2599999.5", "1200001.75");
        for (var convention : RadiusConvention.values()) {
            var radius = new ArcRadius(new NumberValue("7", new BigDecimal("7")), convention);
            var arc = new Arc(support, end, Optional.of(radius));
            var line = new Polyline(List.of(new CurvePart(start, List.of(arc))));
            var failure = assertThrows(DbException.class, () -> GeometryBinary.encode(line, false, 2056, POLYLINE));
            assertEquals("Explicit arc radius is unsupported in WKB", failure.getMessage());
        }
        var noSegments = new Polyline(List.of(new CurvePart(start, List.of())));
        assertThrows(DbException.class, () -> GeometryBinary.encode(noSegments, false, 2056, POLYLINE));
    }

    @Test
    void writesCompoundRingsInOrderWithoutAGeoPackageEnvelope() {
        var start = coordinate("0.0", "0.0");
        var east = new Straight(coordinate("10.0", "0.0"));
        var north = new Straight(coordinate("10.0", "10.0"));
        var west = new Straight(coordinate("0.0", "10.0"));
        var south = new Straight(start);
        var shell = new Ring(List.of(line(start, east, north), line(coordinate("10.00", "10.000"), west, south)));
        var hole = circularHole();

        var data = ByteBuffer.wrap(GeometryBinary.encode(new Surface(shell, List.of(hole)), true, 15000, SURFACE));
        assertEquals(0, data.get(3));
        assertEquals(15000, data.getInt(4));
        assertEquals(0, data.get(8));
        assertEquals(10, data.getInt(9));
        assertEquals(2, data.getInt(13));
        data.position(17);
        assertCompoundRing(data, 2, 5);
        assertCompoundRing(data, 8, 5);
        assertEquals(0, data.remaining());
        var expected = new Surface(new Ring(List.of(line(start, east, north, west, south))), List.of(hole));
        assertEquals(expected, GeometryBinary.decode(data.array(), true, 15000, SURFACE));
    }

    @Test
    void readsDirectLinearAndCircularRingsWithTheirOwnByteOrder() {
        var bytes = ByteBuffer.allocate(187).put((byte) 0).putInt(10).putInt(2)
                .order(ByteOrder.LITTLE_ENDIAN).put((byte) 1).putInt(2).putInt(5)
                .putDouble(0.0).putDouble(0.0).putDouble(10.0).putDouble(0.0).putDouble(10.0).putDouble(10.0)
                .putDouble(0.0).putDouble(10.0).putDouble(0.0).putDouble(0.0)
                .order(ByteOrder.BIG_ENDIAN).put((byte) 0).putInt(8).putInt(5)
                .putDouble(4.0).putDouble(5.0).putDouble(5.0).putDouble(4.0).putDouble(6.0).putDouble(5.0)
                .putDouble(5.0).putDouble(6.0).putDouble(4.0).putDouble(5.0).array();
        assertEquals(new Surface(square(), List.of(circularHole())),
                GeometryBinary.decode(bytes, false, 2056, SURFACE));
        assertThrows(DbException.class, () -> GeometryBinary.decode(
                Arrays.copyOf(bytes, bytes.length - 1), false, 2056, SURFACE));
        assertThrows(DbException.class, () -> GeometryBinary.decode(
                Arrays.copyOf(bytes, bytes.length + 1), false, 2056, SURFACE));
        ByteBuffer.wrap(bytes).putDouble(bytes.length - Double.BYTES, 6.0);
        var open = assertThrows(DbException.class, () -> GeometryBinary.decode(bytes, false, 2056, SURFACE));
        assertEquals("A surface ring must be closed", open.getMessage());
    }

    @Test
    void rejectsOpenOrDisconnectedSurfaceRings() {
        var start = coordinate("0.0", "0.0");
        var first = line(start, new Straight(coordinate("10.0", "0.0")));
        var open = new Surface(new Ring(List.of(first)), List.of());
        var openFailure = assertThrows(DbException.class, () -> GeometryBinary.encode(open, false, 2056, SURFACE));
        assertEquals("A surface ring must be closed", openFailure.getMessage());

        var second = line(coordinate("10.1", "0.0"), new Straight(coordinate("5.0", "5.0")), new Straight(start));
        var disconnected = new Surface(new Ring(List.of(first, second)), List.of());
        var discontinuity = assertThrows(DbException.class, () -> GeometryBinary.encode(disconnected, false, 2056, SURFACE));
        assertEquals("Surface ring polylines do not share an endpoint", discontinuity.getMessage());
    }

    @Test
    void countsStoredCoordinatesAcrossAllSurfaceRings() {
        var shell = square();
        var bytes = ByteBuffer.allocate(107).put((byte) 0).putInt(10).putInt(2)
                .put((byte) 0).putInt(2).putInt(5)
                .putDouble(0.0).putDouble(0.0).putDouble(10.0).putDouble(0.0).putDouble(10.0).putDouble(10.0)
                .putDouble(0.0).putDouble(10.0).putDouble(0.0).putDouble(0.0)
                .put((byte) 0).putInt(2).putInt(99_996).array();
        var read = assertThrows(DbException.class, () -> GeometryBinary.decode(bytes, false, 2056, SURFACE));
        assertEquals("A surface permits at most 100000 stored XY pairs", read.getMessage());

        var start = coordinate("4.0", "5.0");
        var part = new CurvePart(start, Collections.nCopies(99_995, new Straight(start)));
        var hole = new Ring(List.of(new Polyline(List.of(part))));
        var surface = new Surface(shell, List.of(hole));
        var write = assertThrows(DbException.class, () -> GeometryBinary.encode(surface, false, 2056, SURFACE));
        assertEquals(read.getMessage(), write.getMessage());
    }

    @Test
    void writesMultiSurfaceChildrenAsCurvePolygonsInOrder() {
        var multi = new MultiSurface(List.of(new Surface(square(), List.of()),
                new Surface(circularHole(), List.of())));
        byte[] bytes = GeometryBinary.encode(multi, true, 2056, MULTISURFACE);
        var data = ByteBuffer.wrap(bytes);
        assertEquals(0, data.get(3)); // A curved member precludes an XY envelope.
        assertEquals(2056, data.getInt(4));
        data.position(8);
        assertEquals(0, data.get());
        assertEquals(12, data.getInt());
        assertEquals(2, data.getInt());
        for (int ringType : new int[] {2, 8}) {
            assertEquals(0, data.get());
            assertEquals(10, data.getInt());
            assertEquals(1, data.getInt());
            assertCompoundRing(data, ringType, 5);
        }
        assertEquals(0, data.remaining());
        assertEquals(multi, GeometryBinary.decode(bytes, true, 2056, MULTISURFACE));
    }

    @Test
    void readsCurvePolygonAndPolygonChildrenWithIndependentByteOrders() {
        // Each geometry token has an independent byte order. POLYGON rings use the order of their polygon.
        byte[] bytes = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 1).putInt(12).putInt(2)
                .order(ByteOrder.BIG_ENDIAN).put((byte) 0).putInt(10).putInt(1)
                .order(ByteOrder.LITTLE_ENDIAN).put((byte) 1).putInt(8).putInt(5)
                .putDouble(4.0).putDouble(5.0).putDouble(5.0).putDouble(4.0).putDouble(6.0).putDouble(5.0)
                .putDouble(5.0).putDouble(6.0).putDouble(4.0).putDouble(5.0)
                .put((byte) 1).putInt(3).putInt(1).putInt(5)
                .putDouble(0.0).putDouble(0.0).putDouble(10.0).putDouble(0.0).putDouble(10.0).putDouble(10.0)
                .putDouble(0.0).putDouble(10.0).putDouble(0.0).putDouble(0.0).array();
        var multi = new MultiSurface(List.of(new Surface(circularHole(), List.of()),
                new Surface(square(), List.of())));
        assertEquals(multi, GeometryBinary.decode(bytes, false, 2056, MULTISURFACE));
    }

    @Test
    void readsEmptyMultiSurfaceStorageAndRejectsAnEmptyTransferValue() {
        var empty = new MultiSurface(List.of());
        byte[] wkb = multiWkb();
        assertEquals(empty, GeometryBinary.decode(wkb, false, 2056, MULTISURFACE));
        for (int flags : new int[] {0, 2, 16}) {
            assertEquals(empty, GeometryBinary.decode(geoPackage(wkb, flags), true, 2056, MULTISURFACE));
        }
        assertThrows(DbException.class, () -> GeometryBinary.encode(empty, true, 2056, MULTISURFACE));
        assertThrows(DbException.class, () -> GeometryBinary.decode(geoPackage(wkb, 18), true, 2056, MULTISURFACE));
        byte[] child = GeometryBinary.encode(new Surface(square(), List.of()), false, 2056, SURFACE);
        assertThrows(DbException.class, () -> GeometryBinary.decode(
                geoPackage(multiWkb(child), 16), true, 2056, MULTISURFACE));
    }

    @Test
    void rejectsEmptyOrMalformedMultiSurfaceChildren() {
        byte[] child = GeometryBinary.encode(new Surface(square(), List.of()), false, 2056, SURFACE);
        byte[] valid = multiWkb(child, child);
        assertThrows(DbException.class, () -> GeometryBinary.decode(
                Arrays.copyOf(valid, valid.length - 1), false, 2056, MULTISURFACE));
        assertThrows(DbException.class, () -> GeometryBinary.decode(
                Arrays.copyOf(valid, valid.length + 1), false, 2056, MULTISURFACE));
        for (int type : new int[] {3, 10, 7}) {
            byte[] invalidChild = ByteBuffer.allocate(9).put((byte) 0).putInt(type).putInt(0).array();
            assertThrows(DbException.class, () -> GeometryBinary.decode(
                    multiWkb(child, invalidChild), false, 2056, MULTISURFACE));
        }
        var emptyChild = new Surface(new Ring(List.of(line(coordinate("0.0", "0.0")))), List.of());
        assertThrows(DbException.class, () -> GeometryBinary.encode(
                new MultiSurface(List.of(emptyChild)), false, 2056, MULTISURFACE));
    }

    @Test
    void countsStoredCoordinatesAcrossAllMultiSurfaceChildren() {
        var start = coordinate("4.0", "5.0");
        var manyVertices = new CurvePart(start, Collections.nCopies(50_000, new Straight(start)));
        var child = new Surface(new Ring(List.of(new Polyline(List.of(manyVertices)))), List.of());
        byte[] childWkb = GeometryBinary.encode(child, false, 2056, SURFACE);
        var read = assertThrows(DbException.class, () -> GeometryBinary.decode(
                multiWkb(childWkb, childWkb), false, 2056, MULTISURFACE));
        assertEquals("A multisurface permits at most 100000 stored XY pairs", read.getMessage());
        var write = assertThrows(DbException.class, () -> GeometryBinary.encode(
                new MultiSurface(List.of(child, child)), false, 2056, MULTISURFACE));
        assertEquals(read.getMessage(), write.getMessage());
    }

    private static byte[] multiWkb(byte[]... children) {
        int length = 9;
        for (byte[] child : children) length += child.length;
        var data = ByteBuffer.allocate(length).put((byte) 0).putInt(12).putInt(children.length);
        for (byte[] child : children) data.put(child);
        return data.array();
    }

    private static byte[] geoPackage(byte[] wkb, int flags) {
        int headerSize = (flags & 2) == 0 ? 8 : 40;
        var data = ByteBuffer.allocate(headerSize + wkb.length)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) flags).putInt(2056);
        return data.position(headerSize).put(wkb).array();
    }

    private static void assertCompoundRing(ByteBuffer data, int type, int vertices) {
        assertEquals(0, data.get());
        assertEquals(9, data.getInt());
        assertEquals(1, data.getInt());
        assertComponent(data, type, vertices);
    }

    private static Ring square() {
        var start = coordinate("0.0", "0.0");
        return new Ring(List.of(line(start, new Straight(coordinate("10.0", "0.0")),
                new Straight(coordinate("10.0", "10.0")), new Straight(coordinate("0.0", "10.0")), new Straight(start))));
    }

    private static Ring circularHole() {
        var start = coordinate("4.0", "5.0");
        var first = new Arc(coordinate("5.0", "4.0"), coordinate("6.0", "5.0"), Optional.empty());
        var second = new Arc(coordinate("5.0", "6.0"), start, Optional.empty());
        return new Ring(List.of(line(start, first, second)));
    }

    private static Polyline line(Coordinate start, Segment... segments) {
        return new Polyline(List.of(new CurvePart(start, List.of(segments))));
    }

    private static void assertComponent(ByteBuffer data, int type, int vertices) {
        assertEquals(0, data.get());
        assertEquals(type, data.getInt());
        assertEquals(vertices, data.getInt());
        data.position(data.position() + 2 * Double.BYTES * vertices);
    }

    private static byte[] mixedCurveWkb() {
        return ByteBuffer.allocate(107).order(ByteOrder.BIG_ENDIAN)
                .put((byte) 0).putInt(9).putInt(2)
                .put((byte) 0).putInt(2).putInt(2)
                .putDouble(2600000.25).putDouble(1200000.125)
                .putDouble(2600002.5).putDouble(1200003.25)
                .order(ByteOrder.LITTLE_ENDIAN).put((byte) 1).putInt(8).putInt(3)
                .putDouble(2600002.5).putDouble(1200003.25)
                .putDouble(2600001.5).putDouble(1200004.25)
                .putDouble(2599999.5).putDouble(1200001.75).array();
    }

    private static Polyline polyline() {
        return new Polyline(List.of(new CurvePart(coordinate("2600000.25", "1200000.125"), List.of(
                new Straight(coordinate("2600002.5", "1200003.25")),
                new Straight(coordinate("2599999.5", "1200001.75"))))));
    }

    private static Coordinate coordinate(String x, String y) {
        return new Coordinate(List.of(new NumberValue(x, new BigDecimal(x)), new NumberValue(y, new BigDecimal(y))));
    }
}
