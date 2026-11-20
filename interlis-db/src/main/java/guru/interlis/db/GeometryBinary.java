package guru.interlis.db;

import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferGeometry.Ring;
import guru.interlis.transfer.TransferGeometry.Segment;
import guru.interlis.transfer.TransferGeometry.Straight;
import guru.interlis.transfer.TransferGeometry.Surface;
import guru.interlis.transfer.TransferValue.NumberValue;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** WKB and GeoPackageBinary for two-dimensional points, polylines and surfaces. */
final class GeometryBinary {
    private static final int MAX_VERTICES = 100_000;

    private static final int POINT = 1;
    private static final int LINESTRING = 2;
    private static final int CIRCULARSTRING = 8;
    private static final int COMPOUNDCURVE = 9;
    private static final int CURVEPOLYGON = 10;
    private static final int TYPE_SIZE = 1 + Integer.BYTES;
    private static final int XY_SIZE = 2 * Double.BYTES;
    private static final int POINT_WKB_SIZE = TYPE_SIZE + XY_SIZE;
    private static final int CURVE_HEADER_SIZE = TYPE_SIZE + Integer.BYTES;
    private static final int MAX_COMPONENTS = MAX_VERTICES / 2;
    private static final int GPKG_HEADER_SIZE = 8;
    private static final int XY_ENVELOPE_SIZE = 4 * Double.BYTES;
    private static final int GPKG_PAYLOAD_OFFSET = GPKG_HEADER_SIZE + XY_ENVELOPE_SIZE;

    private GeometryBinary() { }

    private record CompoundCurve(CurvePart part, int components, int size, boolean hasArcs) { }

    private static final class CoordinateBudget {
        private final boolean surface;
        private int remaining = MAX_VERTICES;

        CoordinateBudget(boolean surface) {
            this.surface = surface;
        }

        void consume(int count) {
            if (count > remaining) {
                throw exceeded();
            }
            remaining -= count;
        }

        DbException exceeded() {
            return new DbException(surface ? "A surface permits at most " + MAX_VERTICES + " stored XY pairs"
                    : "A polyline requires 2 to " + MAX_VERTICES + " stored XY pairs");
        }
    }

    static int maxWkbSize(DbMapping.Kind kind) {
        return switch (kind) {
            case POINT -> POINT_WKB_SIZE;
            case POLYLINE -> CURVE_HEADER_SIZE * (1 + MAX_COMPONENTS) + MAX_VERTICES * XY_SIZE;
            // Each nonempty ring includes at least one component with two coordinates.
            case SURFACE -> CURVE_HEADER_SIZE * (1 + 2 * MAX_COMPONENTS) + MAX_VERTICES * XY_SIZE;
            default -> throw new IllegalArgumentException("Not a geometry kind: " + kind);
        };
    }

    static int maxGeoPackageSize(DbMapping.Kind kind) {
        return GPKG_PAYLOAD_OFFSET + maxWkbSize(kind);
    }

    static TransferGeometry decode(byte[] bytes, boolean gpkg, int srsId, DbMapping.Kind kind) {
        var data = ByteBuffer.wrap(bytes);
        if (gpkg) {
            readGeoPackageHeader(data, srsId);
        }
        return switch (kind) {
            case POINT -> readPoint(data);
            case POLYLINE -> readPolyline(data);
            case SURFACE -> readSurface(data);
            default -> throw new IllegalArgumentException("Not a geometry kind: " + kind);
        };
    }

    private static void readGeoPackageHeader(ByteBuffer data, int srsId) {
        if (data.remaining() < GPKG_HEADER_SIZE || data.get() != 'G' || data.get() != 'P' || data.get() != 0) {
            throw new DbException("Invalid GeoPackage geometry header");
        }
        int flags = Byte.toUnsignedInt(data.get());
        int envelope = flags >> 1;
        if (envelope > 1) {
            throw new DbException("Expected a nonempty standard GeoPackage geometry with at most an XY envelope");
        }
        data.order(byteOrder(flags & 1));
        if (data.getInt() != srsId) {
            throw new DbException("GeoPackage geometry SRS differs from its geometry column");
        }
        if (envelope == 1) {
            if (data.remaining() < XY_ENVELOPE_SIZE) {
                throw new DbException("Invalid GeoPackage geometry envelope size");
            }
            data.position(data.position() + XY_ENVELOPE_SIZE);
        }
    }

    private static Coordinate readPoint(ByteBuffer data) {
        if (data.remaining() != POINT_WKB_SIZE) {
            throw new DbException("Invalid WKB point size");
        }
        if (readType(data) != POINT) {
            throw new DbException("Expected a 2D WKB POINT");
        }
        return readCoordinate(data);
    }

    private static Polyline readPolyline(ByteBuffer data) {
        if (readType(data) != COMPOUNDCURVE) {
            throw new DbException("Expected a 2D WKB COMPOUNDCURVE");
        }
        var part = readCurve(data, COMPOUNDCURVE, new CoordinateBudget(false));
        if (data.hasRemaining()) {
            throw new DbException("Invalid WKB polyline size");
        }
        return new Polyline(List.of(part));
    }

    private static Surface readSurface(ByteBuffer data) {
        if (readType(data) != CURVEPOLYGON) {
            throw new DbException("Expected a 2D WKB CURVEPOLYGON");
        }
        int rings = readCount(data);
        if (rings < 1 || rings > MAX_COMPONENTS) {
            throw new DbException("Invalid WKB curve polygon ring count");
        }
        var budget = new CoordinateBudget(true);
        var shell = readRing(data, budget);
        var holes = new ArrayList<Ring>();
        for (int index = 1; index < rings; index++) {
            holes.add(readRing(data, budget));
        }
        if (data.hasRemaining()) {
            throw new DbException("Invalid WKB curve polygon size");
        }
        return new Surface(shell, holes);
    }

    private static Ring readRing(ByteBuffer data, CoordinateBudget budget) {
        var part = readCurve(data, readType(data), budget);
        requireClosedRing(part);
        return new Ring(List.of(new Polyline(List.of(part))));
    }

    private static CurvePart readCurve(ByteBuffer data, int type, CoordinateBudget budget) {
        boolean compound = type == COMPOUNDCURVE;
        int components = compound ? readCount(data) : 1;
        if (components < 1 || components > MAX_COMPONENTS) {
            throw new DbException("Invalid WKB compound curve component count");
        }

        Coordinate start = null;
        var segments = new ArrayList<Segment>();
        for (int index = 0; index < components; index++) {
            int componentType = compound ? readType(data) : type;
            if (componentType != LINESTRING && componentType != CIRCULARSTRING) {
                throw new DbException("Expected a 2D WKB LINESTRING or CIRCULARSTRING component");
            }
            boolean circular = componentType == CIRCULARSTRING;
            int count = readCount(data);
            if (count < 2) {
                throw new DbException("A WKB curve component requires at least two vertices");
            }
            budget.consume(count);
            if (circular && count % 2 == 0) {
                throw new DbException("A WKB CIRCULARSTRING requires an odd number of at least three vertices");
            }
            if (data.remaining() < count * XY_SIZE) {
                throw new DbException("Invalid WKB polyline size");
            }

            var componentStart = readCoordinate(data);
            if (index == 0) {
                start = componentStart;
            } else if (!sameXY(segments.getLast().end(), componentStart)) {
                throw new DbException("WKB compound curve components do not share an endpoint");
            }

            for (int point = 1; point < count; point += circular ? 2 : 1) {
                if (circular) {
                    var support = readCoordinate(data);
                    segments.add(new Arc(support, readCoordinate(data), Optional.empty()));
                } else {
                    segments.add(new Straight(readCoordinate(data)));
                }
            }
        }
        return new CurvePart(start, segments);
    }

    private static int readType(ByteBuffer data) {
        if (data.remaining() < TYPE_SIZE) {
            throw new DbException("Truncated WKB geometry type");
        }
        data.order(byteOrder(data.get()));
        return data.getInt();
    }

    private static int readCount(ByteBuffer data) {
        if (data.remaining() < Integer.BYTES) {
            throw new DbException("Truncated WKB geometry count");
        }
        return data.getInt();
    }

    private static Coordinate readCoordinate(ByteBuffer data) {
        return new Coordinate(List.of(number(data.getDouble()), number(data.getDouble())));
    }

    static byte[] encode(TransferGeometry value, boolean gpkg, int srsId, DbMapping.Kind kind) {
        return switch (kind) {
            case POINT -> writePoint(value, gpkg, srsId);
            case POLYLINE -> writePolyline(value, gpkg, srsId);
            case SURFACE -> writeSurface(value, gpkg, srsId);
            default -> throw new IllegalArgumentException("Not a geometry kind: " + kind);
        };
    }

    private static byte[] writePoint(TransferGeometry geometry, boolean gpkg, int srsId) {
        if (!(geometry instanceof Coordinate point)) {
            throw new DbException("Expected a 2D point");
        }
        var data = allocate(POINT_WKB_SIZE, gpkg, srsId, gpkg);
        data.put((byte) 0).putInt(POINT);
        int start = data.position();
        writeCoordinate(data, point);
        if (gpkg) {
            writeEnvelope(data, start);
        }
        return data.array();
    }

    private static byte[] writePolyline(TransferGeometry geometry, boolean gpkg, int srsId) {
        if (!(geometry instanceof Polyline line)) {
            throw new DbException("Expected a polyline with one part and no line attributes");
        }
        var curve = planCurve(linePart(line), new CoordinateBudget(false));
        // Arc extrema can lie outside the bounds of the stored XY pairs.
        boolean envelope = gpkg && !curve.hasArcs();
        var data = allocate(curve.size(), gpkg, srsId, envelope);
        int firstCoordinate = data.position() + 2 * CURVE_HEADER_SIZE;
        writeCurve(data, curve);
        if (envelope) {
            writeEnvelope(data, firstCoordinate);
        }
        return data.array();
    }

    private static byte[] writeSurface(TransferGeometry geometry, boolean gpkg, int srsId) {
        if (!(geometry instanceof Surface surface)) {
            throw new DbException("Expected a surface");
        }
        var budget = new CoordinateBudget(true);
        var rings = new ArrayList<CompoundCurve>();
        int size = CURVE_HEADER_SIZE;
        var shell = planRing(surface.shell(), budget);
        rings.add(shell);
        size += shell.size();
        for (var hole : surface.holes()) {
            var ring = planRing(hole, budget);
            rings.add(ring);
            size += ring.size();
        }
        var data = allocate(size, gpkg, srsId, false);
        data.put((byte) 0).putInt(CURVEPOLYGON).putInt(rings.size());
        for (var ring : rings) {
            writeCurve(data, ring);
        }
        return data.array();
    }

    private static CompoundCurve planRing(Ring ring, CoordinateBudget budget) {
        var first = linePart(ring.lines().getFirst());
        var part = ring.lines().size() == 1 ? first : joinRingLines(ring, first, budget);
        requireClosedRing(part);
        return planCurve(part, budget);
    }

    private static CurvePart joinRingLines(Ring ring, CurvePart first, CoordinateBudget budget) {
        if (first.segments().isEmpty() || first.segments().size() >= budget.remaining) {
            throw budget.exceeded();
        }
        var segments = new ArrayList<>(first.segments());
        for (int index = 1; index < ring.lines().size(); index++) {
            var part = linePart(ring.lines().get(index));
            if (!sameXY(segments.getLast().end(), part.start())) {
                throw new DbException("Surface ring polylines do not share an endpoint");
            }
            if (part.segments().isEmpty() || part.segments().size() >= budget.remaining - segments.size()) {
                throw budget.exceeded();
            }
            segments.addAll(part.segments());
        }
        return new CurvePart(first.start(), segments);
    }

    private static CurvePart linePart(Polyline line) {
        if (line.parts().size() != 1 || line.lineAttributes().isPresent()) {
            throw new DbException("Expected a polyline with one part and no line attributes");
        }
        return line.parts().getFirst();
    }

    private static void requireClosedRing(CurvePart part) {
        if (part.segments().isEmpty() || !sameXY(part.start(), part.segments().getLast().end())) {
            throw new DbException("A surface ring must be closed");
        }
    }

    private static boolean sameXY(Coordinate first, Coordinate second) {
        var a = first.ordinates();
        var b = second.ordinates();
        return a.size() == 2 && b.size() == 2
                && a.get(0).exactValue().compareTo(b.get(0).exactValue()) == 0
                && a.get(1).exactValue().compareTo(b.get(1).exactValue()) == 0;
    }

    private static CompoundCurve planCurve(CurvePart part, CoordinateBudget budget) {
        var segments = part.segments();
        if (segments.isEmpty()) {
            throw budget.exceeded();
        }
        int before = budget.remaining;
        int components = 0;
        int previousType = 0;
        boolean hasArcs = false;
        for (var segment : segments) {
            boolean circular = segment instanceof Arc;
            if (segment instanceof Arc arc && arc.radius().isPresent()) {
                throw new DbException("Explicit arc radius is unsupported in WKB");
            }
            int type = circular ? CIRCULARSTRING : LINESTRING;
            if (type != previousType) {
                components++;
                budget.consume(1); // Every WKB component stores its start, including a shared endpoint.
                previousType = type;
            }
            budget.consume(circular ? 2 : 1);
            hasArcs |= circular;
        }
        int size = CURVE_HEADER_SIZE * (1 + components) + (before - budget.remaining) * XY_SIZE;
        return new CompoundCurve(part, components, size, hasArcs);
    }

    private static void writeCurve(ByteBuffer data, CompoundCurve curve) {
        data.put((byte) 0).putInt(COMPOUNDCURVE).putInt(curve.components());
        var segments = curve.part().segments();
        var componentStart = curve.part().start();
        for (int index = 0; index < segments.size();) {
            boolean circular = segments.get(index) instanceof Arc;
            int end = index + 1;
            while (end < segments.size() && (segments.get(end) instanceof Arc) == circular) {
                end++;
            }
            data.put((byte) 0).putInt(circular ? CIRCULARSTRING : LINESTRING);
            data.putInt(1 + (end - index) * (circular ? 2 : 1));
            writeCoordinate(data, componentStart);
            for (; index < end; index++) {
                var segment = segments.get(index);
                if (segment instanceof Arc arc) {
                    writeCoordinate(data, arc.support());
                }
                componentStart = segment.end();
                writeCoordinate(data, componentStart);
            }
        }
    }

    private static ByteBuffer allocate(int wkbSize, boolean gpkg, int srsId, boolean envelope) {
        int headerSize = gpkg ? GPKG_HEADER_SIZE + (envelope ? XY_ENVELOPE_SIZE : 0) : 0;
        var data = ByteBuffer.allocate(wkbSize + headerSize);
        if (gpkg) {
            data.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) (envelope ? 2 : 0)).putInt(srsId);
            data.position(headerSize);
        }
        return data;
    }

    private static void writeCoordinate(ByteBuffer data, Coordinate coordinate) {
        var ordinates = coordinate.ordinates();
        if (ordinates.size() != 2) {
            throw new DbException("Expected a 2D coordinate");
        }
        data.putDouble(finite(ordinates.get(0).exactValue().doubleValue()));
        data.putDouble(finite(ordinates.get(1).exactValue().doubleValue()));
    }

    private static void writeEnvelope(ByteBuffer data, int start) {
        double minX = data.getDouble(start);
        double maxX = minX;
        double minY = data.getDouble(start + Double.BYTES);
        double maxY = minY;
        for (int offset = start + XY_SIZE; offset < data.position(); offset += XY_SIZE) {
            double x = data.getDouble(offset);
            double y = data.getDouble(offset + Double.BYTES);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        data.putDouble(GPKG_HEADER_SIZE, minX);
        data.putDouble(GPKG_HEADER_SIZE + Double.BYTES, maxX);
        data.putDouble(GPKG_HEADER_SIZE + 2 * Double.BYTES, minY);
        data.putDouble(GPKG_HEADER_SIZE + 3 * Double.BYTES, maxY);
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

    private static double finite(double value) {
        if (!Double.isFinite(value)) {
            throw new DbException("Geometry ordinates must be finite");
        }
        return value;
    }
}
