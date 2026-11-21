package guru.interlis.db;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeoPackageRtreeTest {
    @Test
    void sqliteFunctionsDistinguishNullEmptyAndNonEmptyGeometry() throws SQLException {
        byte[] point = header(29, 1).putInt(1).putDouble(2600000.125).putDouble(1200000.375).array();
        byte[] empty = header(29, 17).putInt(1).putDouble(Double.NaN).putDouble(Double.NaN).array();
        byte[] collection = header(17, 17).putInt(4).putInt(0).array();
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            GeoPackageRtree.register(connection);
            try (var stmt = connection.prepareStatement(
                    "SELECT ST_IsEmpty(?1), ST_MinX(?1), ST_MaxX(?1), ST_MinY(?1), ST_MaxY(?1)")) {
                stmt.setBytes(1, point);
                try (var rows = stmt.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                    assertEquals(2600000.125, rows.getDouble(2));
                    assertEquals(2600000.125, rows.getDouble(3));
                    assertEquals(1200000.375, rows.getDouble(4));
                    assertEquals(1200000.375, rows.getDouble(5));
                }
                for (var value : new byte[][] {empty, collection, null}) {
                    stmt.setBytes(1, value);
                    try (var rows = stmt.executeQuery()) {
                        assertTrue(rows.next());
                        if (value == null) assertNull(rows.getObject(1));
                        else assertEquals(1, rows.getInt(1));
                        for (int col = 2; col <= 5; col++) assertNull(rows.getObject(col));
                    }
                }
                stmt.setBytes(1, new byte[] {'G', 'P'});
                assertThrows(SQLException.class, stmt::executeQuery);
            }
        }
    }

    private static ByteBuffer header(int size, int flags) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) flags).putInt(2056).put((byte) 1);
    }
}
