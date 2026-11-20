package guru.interlis.db;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PointBinaryTest {
    @Test
    void readsGeoPackageHeaderAndWkbWithIndependentByteOrders() {
        byte[] bytes = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(15000)
                .order(ByteOrder.BIG_ENDIAN).put((byte) 0).putInt(1)
                .putDouble(2600000.125).putDouble(1200000.25).array();

        var point = PointBinary.decode(bytes, true, 15000);
        assertEquals("2600000.125", point.ordinates().get(0).lexeme());
        assertEquals("1200000.25", point.ordinates().get(1).lexeme());
        assertEquals(point, PointBinary.decode(PointBinary.encode(point, 15000), true, 15000));
        assertThrows(DbException.class, () -> PointBinary.decode(bytes, true, 2056));
    }

    @Test
    void rejectsExtendedGeometryAndNonPointWkb() {
        byte[] bytes = ByteBuffer.allocate(29)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 32).putInt(2056)
                .put((byte) 0).putInt(1).putDouble(2600000).putDouble(1200000).array();
        assertThrows(DbException.class, () -> PointBinary.decode(bytes, true, 2056));

        byte[] nonPoint = ByteBuffer.allocate(21)
                .put((byte) 0).putInt(2).putDouble(2600000).putDouble(1200000).array();
        assertThrows(DbException.class, () -> PointBinary.decode(nonPoint, false, 2056));
    }
}
