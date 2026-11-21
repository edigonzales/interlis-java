package guru.interlis.db;

import guru.interlis.db.DbMapping.Kind;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.MultiCoordinate;
import guru.interlis.transfer.TransferGeometry.MultiPolyline;
import guru.interlis.transfer.TransferGeometry.MultiSurface;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferGeometry.Ring;
import guru.interlis.transfer.TransferGeometry.Segment;
import guru.interlis.transfer.TransferGeometry.Straight;
import guru.interlis.transfer.TransferGeometry.Surface;
import guru.interlis.transfer.TransferValue.NumberValue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** WKB and GeoPackageBinary for points, curves and surface collections. */
final class GeometryBinary {
    private static final int MAX_VERTICES = 100_000;

    private static final int POINT = 1;
    private static final int LINESTRING = 2;
    private static final int POLYGON = 3;
    private static final int MULTIPOINT = 4;
    private static final int CIRCULARSTRING = 8;
    private static final int COMPOUNDCURVE = 9;
    private static final int CURVEPOLYGON = 10;
    private static final int MULTICURVE = 11;
    private static final int MULTISURFACE = 12;
    private static final int EWKB_Z = 0x80000000;
    private static final int ISO_Z = 1000;
    private static final int TYPE_SIZE = 1 + Integer.BYTES;
    private static final int XY_SIZE = 2 * Double.BYTES;
    private static final int POINT_WKB_SIZE = TYPE_SIZE + XY_SIZE;
    private static final int CURVE_HEADER_SIZE = TYPE_SIZE + Integer.BYTES;
    private static final int MAX_COMPONENTS = MAX_VERTICES / 2;
    private static final int GPKG_HEADER_SIZE = 8;
    private static final int XY_ENVELOPE_SIZE = 4 * Double.BYTES;
    private static final int GPKG_PAYLOAD_OFFSET = GPKG_HEADER_SIZE + XY_ENVELOPE_SIZE;

    private GeometryBinary() { }

    record Envelope(double minX, double maxX, double minY, double maxY) {
        Envelope {
            if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)
                    || minX > maxX || minY > maxY) {
                throw new DbException("Invalid GeoPackage XY envelope");
            }
            if (minX < -Float.MAX_VALUE || maxX > Float.MAX_VALUE
                    || minY < -Float.MAX_VALUE || maxY > Float.MAX_VALUE) {
                throw new DbException("GeoPackage XY envelope exceeds the coordinate range of SQLite RTree");
            }
        }
    }

    private record GeoPackageHeader(int srsId, boolean empty, boolean envelope) { }

    private record CompoundCurve(CurvePart part, int components, int size, boolean hasArcs) { }
    private record CurvePolygon(List<CompoundCurve> rings, int size) { }

    private static final class CoordinateBudget {
        private final Kind kind;
        private int remaining = MAX_VERTICES;

        CoordinateBudget(Kind kind) {
            this.kind = kind;
        }

        void consume(int count) {
            if (count > remaining) {
                throw exceeded();
            }
            remaining -= count;
        }

        DbException exceeded() {
            return new DbException(switch (kind) {
                case POLYLINE -> "A polyline requires 2 to " + MAX_VERTICES + " stored XY pairs";
                case SURFACE -> "A surface permits at most " + MAX_VERTICES + " stored XY pairs";
                case MULTICURVE -> "A multicurve permits at most " + MAX_VERTICES + " stored XY pairs";
                case MULTISURFACE -> "A multisurface permits at most " + MAX_VERTICES + " stored XY pairs";
                default -> throw new IllegalStateException("No coordinate budget for " + kind);
            });
        }
    }

    static int maxWkbSize(Kind kind, int dimensions) {
        return switch (kind) {
            case POINT -> pointSize(dimensions);
            case MULTIPOINT -> CURVE_HEADER_SIZE + MAX_VERTICES * POINT_WKB_SIZE;
            case POLYLINE -> CURVE_HEADER_SIZE * (1 + MAX_COMPONENTS) + MAX_VERTICES * dimensions * Double.BYTES;
            // Each ring or line includes at least one component with two coordinates.
            case SURFACE, MULTICURVE -> CURVE_HEADER_SIZE * (1 + 2 * MAX_COMPONENTS) + MAX_VERTICES * dimensions * Double.BYTES;
            // Each child adds a header and includes at least one nonempty ring.
            case MULTISURFACE -> CURVE_HEADER_SIZE * (1 + 3 * MAX_COMPONENTS) + MAX_VERTICES * dimensions * Double.BYTES;
            default -> throw new IllegalArgumentException("Not a geometry kind: " + kind);
        };
    }

    static int maxGeoPackageSize(Kind kind, int dimensions) {
        return GPKG_PAYLOAD_OFFSET + maxWkbSize(kind, dimensions);
    }

    static TransferGeometry decode(byte[] bytes, boolean gpkg, int srsId, Kind kind, int dimensions) {
        var data = ByteBuffer.wrap(bytes);
        boolean empty = false;
        if (gpkg) {
            var header = readGeoPackageHeader(data);
            if (header.srsId() != srsId) {
                throw new DbException("GeoPackage geometry SRS differs from its geometry column");
            }
            empty = header.empty();
            if (empty && !kind.collection()) throw new DbException("Unsupported GeoPackage geometry flags");
        }
        return switch (kind) {
            case POINT -> {
                if (data.remaining() != pointSize(dimensions)) {
                    throw new DbException("Invalid WKB point size");
                }
                yield readPoint(data, gpkg, dimensions);
            }
            case MULTIPOINT -> readMultiPoint(data, empty);
            case POLYLINE -> readPolyline(data, gpkg, dimensions);
            case MULTICURVE -> readMultiCurve(data, empty);
            case SURFACE -> readSurface(data, gpkg, dimensions);
            case MULTISURFACE -> readMultiSurface(data, empty);
            default -> throw new IllegalArgumentException("Not a geometry kind: " + kind);
        };
    }

    private static GeoPackageHeader readGeoPackageHeader(ByteBuffer data) {
        if (data.remaining() < GPKG_HEADER_SIZE || data.get() != 'G' || data.get() != 'P' || data.get() != 0) {
            throw new DbException("Invalid GeoPackage geometry header");
        }
        int flags = Byte.toUnsignedInt(data.get());
        int envelope = (flags >>> 1) & 7;
        boolean empty = (flags & 0x10) != 0;
        if ((flags & 0xe0) != 0 || envelope > 1 || (empty && envelope != 0)) {
            throw new DbException("Unsupported GeoPackage geometry flags");
        }
        data.order(byteOrder(flags & 1));
        int srsId = data.getInt();
        if (envelope == 1) {
            if (data.remaining() < XY_ENVELOPE_SIZE) {
                throw new DbException("Invalid GeoPackage geometry envelope size");
            }
            data.position(data.position() + XY_ENVELOPE_SIZE);
        }
        return new GeoPackageHeader(srsId, empty, envelope == 1);
    }

    static Envelope indexEnvelope(byte[] bytes) {
        var data = ByteBuffer.wrap(bytes);
        var header = readGeoPackageHeader(data);
        int storageType = readType(data.duplicate());
        int dimensions = (storageType & EWKB_Z) == 0 ? 2 : 3;
        int type = storageType & ~EWKB_Z;
        if (type != POINT) {
            Kind kind = switch (type) {
                case COMPOUNDCURVE -> Kind.POLYLINE;
                case CURVEPOLYGON -> Kind.SURFACE;
                case MULTIPOINT -> Kind.MULTIPOINT;
                case MULTICURVE -> Kind.MULTICURVE;
                case MULTISURFACE -> Kind.MULTISURFACE;
                default -> throw new DbException("Unsupported 2D WKB type for GeoPackage RTree: " + type);
            };
            if (data.remaining() < CURVE_HEADER_SIZE || bytes.length > maxGeoPackageSize(kind, dimensions)) {
                throw new DbException("Invalid GeoPackage RTree geometry size");
            }
            if (header.empty()) {
                readType(data);
                if (data.remaining() != Integer.BYTES || data.getInt() != 0) {
                    throw new DbException("Invalid empty GeoPackage geometry");
                }
                return null;
            }
            return header.envelope() ? headerEnvelope(bytes) : envelope(decode(bytes, true, header.srsId(), kind, dimensions));
        }
        if (data.remaining() != pointSize(dimensions)) {
            throw new DbException("Invalid WKB point size for GeoPackage RTree");
        }
        readType(data);
        double x = data.getDouble();
        double y = data.getDouble();
        double z = dimensions == 3 ? data.getDouble() : Double.NaN;
        if (header.empty() && Double.isNaN(x) && Double.isNaN(y) && Double.isNaN(z)) return null;
        if (dimensions == 3) finite(z);
        if (header.empty()) throw new DbException("Invalid empty GeoPackage point");
        if (header.envelope()) {
            var declared = headerEnvelope(bytes);
            if (x < declared.minX() || x > declared.maxX() || y < declared.minY() || y > declared.maxY()) {
                throw new DbException("GeoPackage XY envelope does not contain its point");
            }
        }
        return new Envelope(x, x, y, y);
    }

    private static Envelope headerEnvelope(byte[] bytes) {
        var data = ByteBuffer.wrap(bytes, GPKG_HEADER_SIZE, XY_ENVELOPE_SIZE).order(byteOrder(bytes[3] & 1));
        return new Envelope(data.getDouble(), data.getDouble(), data.getDouble(), data.getDouble());
    }

    static byte[] encodeIndexedGeoPackage(TransferGeometry geometry, int srsId, Kind kind, int dimensions) {
        byte[] bytes = encode(geometry, true, srsId, kind, dimensions);
        var bounds = envelope(geometry);
        if ((bytes[3] & 2) != 0) return bytes;
        var result = allocate(bytes.length - GPKG_HEADER_SIZE, true, srsId, true);
        result.put(bytes, GPKG_HEADER_SIZE, bytes.length - GPKG_HEADER_SIZE);
        result.position(GPKG_HEADER_SIZE);
        result.putDouble(bounds.minX()).putDouble(bounds.maxX()).putDouble(bounds.minY()).putDouble(bounds.maxY());
        return result.array();
    }

    private static Envelope envelope(TransferGeometry geometry) {
        var bounds = new Bounds();
        bounds.add(geometry);
        return new Envelope(bounds.minX, bounds.maxX, bounds.minY, bounds.maxY);
    }

    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        void add(TransferGeometry geometry) {
            switch (geometry) {
                case Coordinate point -> addPoint(point);
                case Polyline line -> line.parts().forEach(this::addPart);
                case Surface surface -> {
                    addRing(surface.shell());
                    surface.holes().forEach(this::addRing);
                }
                case MultiCoordinate multi -> multi.coordinates().forEach(this::addPoint);
                case MultiPolyline multi -> multi.lines().forEach(this::add);
                case MultiSurface multi -> multi.surfaces().forEach(this::add);
            }
        }

        private void addRing(Ring ring) {
            ring.lines().forEach(this::add);
        }

        private void addPart(CurvePart part) {
            Coordinate start = part.start();
            addPoint(start);
            for (var segment : part.segments()) {
                if (segment instanceof Arc arc) addArc(start, arc.support(), arc.end());
                start = segment.end();
                addPoint(start);
            }
        }

        private void addArc(Coordinate start, Coordinate support, Coordinate end) {
            addPoint(support);
            // Use the exact binary ordinates and translate to the start point before solving the circle.
            // A small determinant is meaningful and must not be rounded to collinearity.
            var originX = binaryOrdinate(start, 0);
            var originY = binaryOrdinate(start, 1);
            var supportX = binaryOrdinate(support, 0).subtract(originX);
            var supportY = binaryOrdinate(support, 1).subtract(originY);
            var chordX = binaryOrdinate(end, 0).subtract(originX);
            var chordY = binaryOrdinate(end, 1).subtract(originY);
            var side = cross(chordX, chordY, supportX, supportY);
            BigDecimal centerX;
            BigDecimal centerY;
            if (chordX.signum() == 0 && chordY.signum() == 0) {
                if (supportX.signum() == 0 && supportY.signum() == 0) return;
                // A closed WKB circular string with three positions uses the diameter.
                centerX = supportX.divide(BigDecimal.TWO);
                centerY = supportY.divide(BigDecimal.TWO);
            } else {
                if (side.signum() == 0) return; // Start, support and end supply the straight bounds.
                var denominator = side.negate().multiply(BigDecimal.TWO);
                var supportSquared = squaredLength(supportX, supportY);
                var chordSquared = squaredLength(chordX, chordY);
                centerX = supportSquared.multiply(chordY).subtract(chordSquared.multiply(supportY))
                        .divide(denominator, MathContext.DECIMAL128);
                centerY = supportX.multiply(chordSquared).subtract(chordX.multiply(supportSquared))
                        .divide(denominator, MathContext.DECIMAL128);
            }
            var radius = squaredLength(centerX, centerY).sqrt(MathContext.DECIMAL128);
            // The chord divides the circle. Only extrema on the support point's side lie on this arc.
            addExtreme(originX, originY, chordX, chordY, side.signum(), centerX.subtract(radius), centerY);
            addExtreme(originX, originY, chordX, chordY, side.signum(), centerX.add(radius), centerY);
            addExtreme(originX, originY, chordX, chordY, side.signum(), centerX, centerY.subtract(radius));
            addExtreme(originX, originY, chordX, chordY, side.signum(), centerX, centerY.add(radius));
        }

        private void addExtreme(BigDecimal originX, BigDecimal originY, BigDecimal chordX, BigDecimal chordY,
                                int arcSide, BigDecimal x, BigDecimal y) {
            int side = cross(chordX, chordY, x, y).signum();
            if (arcSide != 0 && side != 0 && side != arcSide) return;
            double worldX = originX.add(x).doubleValue();
            double worldY = originY.add(y).doubleValue();
            // Round outwards before SQLite converts the envelope to its 32-bit RTree coordinates.
            minX = Math.min(minX, Math.nextDown(worldX));
            maxX = Math.max(maxX, Math.nextUp(worldX));
            minY = Math.min(minY, Math.nextDown(worldY));
            maxY = Math.max(maxY, Math.nextUp(worldY));
        }

        private static BigDecimal cross(BigDecimal ax, BigDecimal ay, BigDecimal bx, BigDecimal by) {
            return ax.multiply(by).subtract(ay.multiply(bx));
        }

        private static BigDecimal squaredLength(BigDecimal x, BigDecimal y) {
            return x.multiply(x).add(y.multiply(y));
        }

        private static BigDecimal binaryOrdinate(Coordinate point, int axis) {
            return new BigDecimal(point.ordinates().get(axis).exactValue().doubleValue());
        }

        private void addPoint(Coordinate point) {
            double x = point.ordinates().get(0).exactValue().doubleValue();
            double y = point.ordinates().get(1).exactValue().doubleValue();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
    }

    private static int pointSize(int dimensions) {
        return TYPE_SIZE + dimensions * Double.BYTES;
    }

    private static int storageType(int type, boolean gpkg, int dimensions) {
        if (dimensions == 2) return type;
        return gpkg ? type | EWKB_Z : type + ISO_Z;
    }

    private static Coordinate readPoint(ByteBuffer data, boolean gpkg, int dimensions) {
        if (readType(data) != storageType(POINT, gpkg, dimensions)) {
            throw new DbException("Expected a " + dimensions + "D WKB POINT");
        }
        return readCoordinate(data, dimensions);
    }

    private static MultiCoordinate readMultiPoint(ByteBuffer data, boolean empty) {
        int count = readCollectionCount(data, MULTIPOINT, Kind.MULTIPOINT, empty);
        if (data.remaining() != count * POINT_WKB_SIZE) {
            throw new DbException("Invalid WKB multipoint size");
        }
        var points = new ArrayList<Coordinate>(count);
        for (int index = 0; index < count; index++) {
            points.add(readPoint(data, false, 2));
        }
        return new MultiCoordinate(points);
    }

    private static Polyline readPolyline(ByteBuffer data, boolean gpkg, int dimensions) {
        if (readType(data) != storageType(COMPOUNDCURVE, gpkg, dimensions)) {
            throw new DbException("Expected a " + dimensions + "D WKB COMPOUNDCURVE");
        }
        var part = readCurve(data, COMPOUNDCURVE, new CoordinateBudget(Kind.POLYLINE), gpkg, dimensions);
        if (data.hasRemaining()) {
            throw new DbException("Invalid WKB polyline size");
        }
        return new Polyline(List.of(part));
    }

    private static MultiPolyline readMultiCurve(ByteBuffer data, boolean empty) {
        int count = readCollectionCount(data, MULTICURVE, Kind.MULTICURVE, empty);
        var budget = new CoordinateBudget(Kind.MULTICURVE);
        var lines = new ArrayList<Polyline>();
        for (int index = 0; index < count; index++) {
            int type = readType(data);
            if (type != COMPOUNDCURVE && type != LINESTRING) {
                throw new DbException("Expected a 2D WKB COMPOUNDCURVE or LINESTRING in a multicurve");
            }
            lines.add(new Polyline(List.of(readCurve(data, type, budget, false, 2))));
        }
        if (data.hasRemaining()) {
            throw new DbException("Invalid WKB multicurve size");
        }
        return new MultiPolyline(lines);
    }

    private static Surface readSurface(ByteBuffer data, boolean gpkg, int dimensions) {
        if (readType(data) != storageType(CURVEPOLYGON, gpkg, dimensions)) {
            throw new DbException("Expected a " + dimensions + "D WKB CURVEPOLYGON");
        }
        var surface = readSurface(data, CURVEPOLYGON, new CoordinateBudget(Kind.SURFACE), gpkg, dimensions);
        if (data.hasRemaining()) {
            throw new DbException("Invalid WKB curve polygon size");
        }
        return surface;
    }

    private static MultiSurface readMultiSurface(ByteBuffer data, boolean empty) {
        int count = readCollectionCount(data, MULTISURFACE, Kind.MULTISURFACE, empty);
        var budget = new CoordinateBudget(Kind.MULTISURFACE);
        var surfaces = new ArrayList<Surface>();
        for (int index = 0; index < count; index++) {
            int type = readType(data);
            if (type != CURVEPOLYGON && type != POLYGON) {
                throw new DbException("Expected a 2D WKB CURVEPOLYGON or POLYGON in a multisurface");
            }
            surfaces.add(readSurface(data, type, budget, false, 2));
        }
        if (data.hasRemaining()) {
            throw new DbException("Invalid WKB multisurface size");
        }
        return new MultiSurface(surfaces);
    }

    private static Surface readSurface(ByteBuffer data, int type, CoordinateBudget budget, boolean gpkg, int dimensions) {
        int rings = readCount(data);
        if (rings < 1 || rings > MAX_COMPONENTS) {
            throw new DbException("Invalid WKB polygon ring count. A surface requires an exterior ring");
        }
        var shell = readRing(data, type, budget, gpkg, dimensions);
        var holes = new ArrayList<Ring>();
        for (int index = 1; index < rings; index++) {
            holes.add(readRing(data, type, budget, gpkg, dimensions));
        }
        return new Surface(shell, holes);
    }

    private static Ring readRing(ByteBuffer data, int surfaceType, CoordinateBudget budget, boolean gpkg, int dimensions) {
        // A POLYGON ring uses the LINESTRING body without its byte order and type.
        int type = surfaceType == POLYGON ? LINESTRING : readType(data, gpkg, dimensions);
        var part = readCurve(data, type, budget, gpkg, dimensions);
        requireClosedRing(part);
        return new Ring(List.of(new Polyline(List.of(part))));
    }

    private static CurvePart readCurve(ByteBuffer data, int type, CoordinateBudget budget, boolean gpkg, int dimensions) {
        boolean compound = type == COMPOUNDCURVE;
        int components = compound ? readCount(data) : 1;
        if (components < 1 || components > MAX_COMPONENTS) {
            throw new DbException("Invalid WKB compound curve component count");
        }

        Coordinate start = null;
        var segments = new ArrayList<Segment>();
        for (int index = 0; index < components; index++) {
            int componentType = compound ? readType(data) : storageType(type, gpkg, dimensions);
            int lineType = storageType(LINESTRING, gpkg, dimensions);
            int arcType = storageType(CIRCULARSTRING, gpkg, dimensions);
            if (componentType != lineType && componentType != arcType) {
                throw new DbException("Expected a " + dimensions + "D WKB LINESTRING or CIRCULARSTRING component");
            }
            boolean circular = componentType == arcType;
            if (circular && dimensions == 3) throw new DbException("XYZ circular arcs are unsupported");
            int count = readCount(data);
            if (count < 2) {
                throw new DbException("A WKB curve component requires at least two vertices");
            }
            budget.consume(count);
            if (circular && count % 2 == 0) {
                throw new DbException("A WKB CIRCULARSTRING requires an odd number of at least three vertices");
            }
            if (data.remaining() < count * dimensions * Double.BYTES) {
                throw new DbException("Invalid WKB polyline size");
            }

            var componentStart = readCoordinate(data, dimensions);
            if (index == 0) {
                start = componentStart;
            } else if (!samePosition(segments.getLast().end(), componentStart)) {
                throw new DbException("WKB compound curve components do not share an endpoint");
            }

            for (int point = 1; point < count; point += circular ? 2 : 1) {
                if (circular) {
                    var support = readCoordinate(data, dimensions);
                    segments.add(new Arc(support, readCoordinate(data, dimensions), Optional.empty()));
                } else {
                    segments.add(new Straight(readCoordinate(data, dimensions)));
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

    private static int readType(ByteBuffer data, boolean gpkg, int dimensions) {
        int stored = readType(data);
        if (dimensions == 2) return stored;
        int type = gpkg ? stored & ~EWKB_Z : stored - ISO_Z;
        if (type < POINT || type > MULTISURFACE || stored != storageType(type, gpkg, dimensions)) {
            throw new DbException("Expected an XYZ WKB type");
        }
        return type;
    }

    private static int readCount(ByteBuffer data) {
        if (data.remaining() < Integer.BYTES) {
            throw new DbException("Truncated WKB geometry count");
        }
        return data.getInt();
    }

    private static int readCollectionCount(ByteBuffer data, int type, Kind kind, boolean empty) {
        if (readType(data) != type) {
            throw new DbException("Expected a 2D WKB " + kind.geometryType());
        }
        int count = readCount(data);
        int limit = kind == Kind.MULTIPOINT ? MAX_VERTICES : MAX_COMPONENTS;
        if (count < 0 || count > limit) {
            throw new DbException("Invalid WKB " + kind.geometryType() + " count");
        }
        if (empty && count != 0) {
            throw new DbException("GeoPackage empty flag disagrees with WKB " + kind.geometryType() + " count");
        }
        return count;
    }

    private static Coordinate readCoordinate(ByteBuffer data, int dimensions) {
        var ordinates = new ArrayList<NumberValue>(dimensions);
        for (int axis = 0; axis < dimensions; axis++) ordinates.add(number(data.getDouble()));
        return new Coordinate(ordinates);
    }

    static byte[] encode(TransferGeometry value, boolean gpkg, int srsId, Kind kind, int dimensions) {
        return switch (kind) {
            case POINT -> writePoint(value, gpkg, srsId, dimensions);
            case MULTIPOINT -> writeMultiPoint(value, gpkg, srsId);
            case POLYLINE -> writePolyline(value, gpkg, srsId, dimensions);
            case MULTICURVE -> writeMultiCurve(value, gpkg, srsId);
            case SURFACE -> writeSurface(value, gpkg, srsId, dimensions);
            case MULTISURFACE -> writeMultiSurface(value, gpkg, srsId);
            default -> throw new IllegalArgumentException("Not a geometry kind: " + kind);
        };
    }

    private static byte[] writePoint(TransferGeometry geometry, boolean gpkg, int srsId, int dimensions) {
        if (!(geometry instanceof Coordinate point)) {
            throw new DbException("Expected a point");
        }
        var data = allocate(pointSize(dimensions), gpkg, srsId, gpkg);
        int start = data.position() + TYPE_SIZE;
        writePoint(data, point, gpkg, dimensions);
        if (gpkg) {
            writeEnvelope(data, start, dimensions);
        }
        return data.array();
    }

    private static void writePoint(ByteBuffer data, Coordinate point, boolean gpkg, int dimensions) {
        data.put((byte) 0).putInt(storageType(POINT, gpkg, dimensions));
        writeCoordinate(data, point, dimensions);
    }

    private static byte[] writeMultiPoint(TransferGeometry geometry, boolean gpkg, int srsId) {
        if (!(geometry instanceof MultiCoordinate multi)) {
            throw new DbException("Expected a multipoint");
        }
        int count = multi.coordinates().size();
        if (count == 0) {
            throw new DbException("An empty multipoint cannot be imported");
        }
        if (count > MAX_VERTICES) {
            throw new DbException("A multipoint permits at most " + MAX_VERTICES + " stored XY pairs");
        }
        var data = allocate(CURVE_HEADER_SIZE + count * POINT_WKB_SIZE, gpkg, srsId, false);
        data.put((byte) 0).putInt(MULTIPOINT).putInt(count);
        for (var point : multi.coordinates()) {
            writePoint(data, point, gpkg, 2);
        }
        return data.array();
    }

    private static byte[] writePolyline(TransferGeometry geometry, boolean gpkg, int srsId, int dimensions) {
        if (!(geometry instanceof Polyline line)) {
            throw new DbException("Expected a polyline with one part and no line attributes");
        }
        var curve = planCurve(linePart(line), new CoordinateBudget(Kind.POLYLINE), dimensions);
        // Arc extrema can lie outside the bounds of the stored XY pairs.
        boolean envelope = gpkg && !curve.hasArcs();
        var data = allocate(curve.size(), gpkg, srsId, envelope);
        int firstCoordinate = data.position() + 2 * CURVE_HEADER_SIZE;
        writeCurve(data, curve, gpkg, dimensions);
        if (envelope) {
            writeEnvelope(data, firstCoordinate, dimensions);
        }
        return data.array();
    }

    private static byte[] writeSurface(TransferGeometry geometry, boolean gpkg, int srsId, int dimensions) {
        if (!(geometry instanceof Surface surface)) {
            throw new DbException("Expected a surface");
        }
        var polygon = planSurface(surface, new CoordinateBudget(Kind.SURFACE), dimensions);
        var data = allocate(polygon.size(), gpkg, srsId, false);
        writeSurface(data, polygon, gpkg, dimensions);
        return data.array();
    }

    private static byte[] writeMultiCurve(TransferGeometry geometry, boolean gpkg, int srsId) {
        if (!(geometry instanceof MultiPolyline multi)) {
            throw new DbException("Expected a multicurve");
        }
        if (multi.lines().isEmpty()) {
            throw new DbException("An empty multicurve cannot be imported");
        }
        var budget = new CoordinateBudget(Kind.MULTICURVE);
        var curves = new ArrayList<CompoundCurve>();
        int size = CURVE_HEADER_SIZE;
        for (var line : multi.lines()) {
            var curve = planCurve(linePart(line), budget, 2);
            curves.add(curve);
            size += curve.size();
        }
        var data = allocate(size, gpkg, srsId, false);
        data.put((byte) 0).putInt(MULTICURVE).putInt(curves.size());
        for (var curve : curves) {
            writeCurve(data, curve, gpkg, 2);
        }
        return data.array();
    }

    private static byte[] writeMultiSurface(TransferGeometry geometry, boolean gpkg, int srsId) {
        if (!(geometry instanceof MultiSurface multi)) {
            throw new DbException("Expected a multisurface");
        }
        if (multi.surfaces().isEmpty()) {
            throw new DbException("An empty multisurface cannot be imported");
        }
        var budget = new CoordinateBudget(Kind.MULTISURFACE);
        var polygons = new ArrayList<CurvePolygon>();
        int size = CURVE_HEADER_SIZE;
        for (var surface : multi.surfaces()) {
            var polygon = planSurface(surface, budget, 2);
            polygons.add(polygon);
            size += polygon.size();
        }
        var data = allocate(size, gpkg, srsId, false);
        data.put((byte) 0).putInt(MULTISURFACE).putInt(polygons.size());
        for (var polygon : polygons) {
            writeSurface(data, polygon, gpkg, 2);
        }
        return data.array();
    }

    private static CurvePolygon planSurface(Surface surface, CoordinateBudget budget, int dimensions) {
        var rings = new ArrayList<CompoundCurve>();
        int size = CURVE_HEADER_SIZE;
        var shell = planRing(surface.shell(), budget, dimensions);
        rings.add(shell);
        size += shell.size();
        for (var hole : surface.holes()) {
            var ring = planRing(hole, budget, dimensions);
            rings.add(ring);
            size += ring.size();
        }
        return new CurvePolygon(rings, size);
    }

    private static void writeSurface(ByteBuffer data, CurvePolygon polygon, boolean gpkg, int dimensions) {
        data.put((byte) 0).putInt(storageType(CURVEPOLYGON, gpkg, dimensions)).putInt(polygon.rings().size());
        for (var ring : polygon.rings()) {
            writeCurve(data, ring, gpkg, dimensions);
        }
    }

    private static CompoundCurve planRing(Ring ring, CoordinateBudget budget, int dimensions) {
        var first = linePart(ring.lines().getFirst());
        var part = ring.lines().size() == 1 ? first : joinRingLines(ring, first, budget);
        requireClosedRing(part);
        return planCurve(part, budget, dimensions);
    }

    private static CurvePart joinRingLines(Ring ring, CurvePart first, CoordinateBudget budget) {
        if (first.segments().isEmpty() || first.segments().size() >= budget.remaining) {
            throw budget.exceeded();
        }
        var segments = new ArrayList<>(first.segments());
        for (int index = 1; index < ring.lines().size(); index++) {
            var part = linePart(ring.lines().get(index));
            if (!samePosition(segments.getLast().end(), part.start())) {
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
        if (part.segments().isEmpty() || !samePosition(part.start(), part.segments().getLast().end())) {
            throw new DbException("A surface ring must be closed");
        }
    }

    private static boolean samePosition(Coordinate first, Coordinate second) {
        var a = first.ordinates();
        var b = second.ordinates();
        if (a.size() != b.size()) return false;
        for (int axis = 0; axis < a.size(); axis++) {
            if (a.get(axis).exactValue().compareTo(b.get(axis).exactValue()) != 0) return false;
        }
        return true;
    }

    private static CompoundCurve planCurve(CurvePart part, CoordinateBudget budget, int dimensions) {
        var segments = part.segments();
        if (segments.isEmpty()) {
            throw new DbException("A polyline requires at least one segment");
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
        int size = CURVE_HEADER_SIZE * (1 + components) + (before - budget.remaining) * dimensions * Double.BYTES;
        return new CompoundCurve(part, components, size, hasArcs);
    }

    private static void writeCurve(ByteBuffer data, CompoundCurve curve, boolean gpkg, int dimensions) {
        data.put((byte) 0).putInt(storageType(COMPOUNDCURVE, gpkg, dimensions)).putInt(curve.components());
        var segments = curve.part().segments();
        var componentStart = curve.part().start();
        for (int index = 0; index < segments.size();) {
            boolean circular = segments.get(index) instanceof Arc;
            int end = index + 1;
            while (end < segments.size() && (segments.get(end) instanceof Arc) == circular) {
                end++;
            }
            data.put((byte) 0).putInt(storageType(circular ? CIRCULARSTRING : LINESTRING, gpkg, dimensions));
            data.putInt(1 + (end - index) * (circular ? 2 : 1));
            writeCoordinate(data, componentStart, dimensions);
            for (; index < end; index++) {
                var segment = segments.get(index);
                if (segment instanceof Arc arc) {
                    if (dimensions == 3) throw new DbException("XYZ circular arcs are unsupported");
                    writeCoordinate(data, arc.support(), dimensions);
                }
                componentStart = segment.end();
                writeCoordinate(data, componentStart, dimensions);
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

    private static void writeCoordinate(ByteBuffer data, Coordinate coordinate, int dimensions) {
        var ordinates = coordinate.ordinates();
        if (ordinates.size() != dimensions) {
            throw new DbException("Expected a " + dimensions + "D coordinate");
        }
        for (var ordinate : ordinates) data.putDouble(finite(ordinate.exactValue().doubleValue()));
    }

    private static void writeEnvelope(ByteBuffer data, int start, int dimensions) {
        double minX = data.getDouble(start);
        double maxX = minX;
        double minY = data.getDouble(start + Double.BYTES);
        double maxY = minY;
        for (int offset = start + dimensions * Double.BYTES; offset < data.position(); offset += dimensions * Double.BYTES) {
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
