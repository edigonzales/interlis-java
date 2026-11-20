package guru.interlis.db;

import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferValue.NumberValue;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** The two-dimensional POINT subset of WKB and GeoPackageBinary. */
final class PointBinary {
    private static final int WKB_SIZE = 1 + Integer.BYTES + 2 * Double.BYTES;
    private static final int HEADER_SIZE = 8;
    private static final int XY_ENVELOPE_SIZE = 4 * Double.BYTES;
    static final int MAX_GPKG_SIZE = HEADER_SIZE + XY_ENVELOPE_SIZE + WKB_SIZE;

    private PointBinary() { }

    static Coordinate decode(byte[] bytes, boolean gpkg, int srsId) {
        var data = ByteBuffer.wrap(bytes);
        if (gpkg) {
            if (data.remaining() < HEADER_SIZE || data.get() != 'G' || data.get() != 'P' || data.get() != 0) {
                throw new DbException("Invalid GeoPackage geometry header");
            }
            int flags = Byte.toUnsignedInt(data.get());
            int envelope = flags >> 1;
            if (envelope > 1) {
                throw new DbException("Expected a nonempty standard GeoPackage point with at most an XY envelope");
            }
            data.order(byteOrder(flags & 1));
            if (data.getInt() != srsId) {
                throw new DbException("GeoPackage point SRS differs from its geometry column");
            }
            if (envelope == 1) {
                if (data.remaining() != XY_ENVELOPE_SIZE + WKB_SIZE) {
                    throw new DbException("Invalid GeoPackage point envelope size");
                }
                data.position(data.position() + XY_ENVELOPE_SIZE);
            }
        }
        if (data.remaining() != WKB_SIZE) {
            throw new DbException("Invalid WKB point size");
        }
        data.order(byteOrder(data.get()));
        if (data.getInt() != 1) {
            throw new DbException("Expected a 2D WKB POINT");
        }
        return new Coordinate(List.of(number(data.getDouble()), number(data.getDouble())));
    }

    static byte[] encode(Coordinate coordinate, int srsId) {
        var values = coordinate.ordinates();
        if (values.size() != 2) {
            throw new DbException("Expected a 2D point");
        }
        double x = finite(values.get(0).exactValue().doubleValue());
        double y = finite(values.get(1).exactValue().doubleValue());
        return ByteBuffer.allocate(MAX_GPKG_SIZE)
                .put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 2).putInt(srsId)
                .putDouble(x).putDouble(x).putDouble(y).putDouble(y)
                .put((byte) 0).putInt(1).putDouble(x).putDouble(y).array();
    }

    private static ByteOrder byteOrder(int flag) {
        return switch (flag) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new DbException("Invalid geometry byte order");
        };
    }

    private static NumberValue number(double value) {
        // XTF 2.3 accepts decimal lexemes without an exponent.
        String decimal = BigDecimal.valueOf(finite(value)).toPlainString();
        return new NumberValue(decimal, new BigDecimal(decimal));
    }

    static double finite(double value) {
        if (!Double.isFinite(value)) {
            throw new DbException("Point ordinates must be finite");
        }
        return value;
    }
}
