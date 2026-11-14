package guru.interlis.xtf24;

import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.TransferEvent.Consistency;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.NumberValue;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;
import static javax.xml.stream.XMLStreamConstants.*;

final class Xtf24GeometryCodec {
    static final String NAMESPACE = "http://www.interlis.ch/geometry/1.0";

    private final TransferPlan plan;
    private final Xtf24Layout layout;

    Xtf24GeometryCodec(TransferPlan plan, Xtf24Layout layout) {
        this.plan = plan;
        this.layout = layout;
    }

    TransferGeometry read(Xtf24Input input, TypeDescriptor type, Map<SymbolRef, TypeRef> concreteTypes,
                          Consistency consistency) throws XMLStreamException {
        Set<String> allowed = allowed(type, concreteTypes, consistency);
        if (input.nextTag() != START_ELEMENT || !allowed.contains(input.cursor().getLocalName())
                || !NAMESPACE.equals(input.cursor().getNamespaceURI())) {
            throw input.error(INVALID_WIRE, "Geometry element does not match the XTF field encoding");
        }
        var value = new Reader(input).geometry();
        if (input.nextTag() != END_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected one geometry value in the XTF attribute");
        }
        return value;
    }

    void write(Xtf24Output output, TypeDescriptor type, TransferValue value,
               Map<SymbolRef, TypeRef> concreteTypes, Consistency consistency) throws XMLStreamException {
        var geometry = Xtf24Scalars.require(value, TransferGeometry.class);
        if (!allowed(type, concreteTypes, consistency).contains(tag(geometry))) {
            throw Xtf24Wire.failure(INVALID_WIRE, "Geometry value does not match the XTF field encoding");
        }
        new Writer(output).geometry(geometry);
    }

    private Set<String> allowed(TypeDescriptor type, Map<SymbolRef, TypeRef> concreteTypes, Consistency consistency) {
        if (type instanceof CoordinateType coordinate) {
            supportedDimension(coordinate);
            return Set.of(coordinate.multiCoordinate() ? "multicoord" : "coord");
        }
        var geometry = (GeometryType) type;
        var vertexRef = new SymbolRef(plan.snapshotId(), geometry.vertexDomain().orElseThrow());
        var vertex = (DomainSymbol) plan.symbol(vertexRef).known().orElseThrow();
        var coordinate = layout.type(concreteTypes.getOrDefault(vertexRef,
                new TypeRef(plan.snapshotId(), vertex.typeId())), concreteTypes);
        if (!(coordinate instanceof CoordinateType concrete)) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Geometry requires a concrete coordinate domain");
        }
        supportedDimension(concrete);
        return switch (geometry.geometryKind()) {
            case POLYLINE -> consistency == Consistency.INCOMPLETE ? Set.of("polyline", "multipolyline") : Set.of("polyline");
            case SURFACE, AREA -> consistency == Consistency.INCOMPLETE ? Set.of("surface", "multisurface") : Set.of("surface");
            case MULTIPOLYLINE -> Set.of("multipolyline");
            case MULTISURFACE, MULTIAREA -> Set.of("multisurface");
        };
    }

    private static void supportedDimension(CoordinateType coordinate) {
        if (coordinate.dimensions() < 2) {
            throw Xtf24Wire.failure(UNSUPPORTED, "One-dimensional geometry is outside the XTF 2.4 profile");
        }
    }

    private static String tag(TransferGeometry value) {
        return switch (value) {
            case Coordinate ignored -> "coord";
            case MultiCoordinate ignored -> "multicoord";
            case Polyline ignored -> "polyline";
            case MultiPolyline ignored -> "multipolyline";
            case Surface ignored -> "surface";
            case MultiSurface ignored -> "multisurface";
        };
    }

    private static QName geom(String name) {
        return new QName(NAMESPACE, name);
    }

    private static final class Reader {
        private final Xtf24Input input;

        Reader(Xtf24Input input) {
            this.input = input;
        }

        TransferGeometry geometry() throws XMLStreamException {
            String name = input.cursor().getLocalName();
            return switch (name) {
                case "coord" -> coordinate();
                case "polyline" -> line();
                case "surface" -> surface();
                case "multicoord" -> {
                    attributes();
                    var points = new ArrayList<Coordinate>();
                    while (input.nextTag() == START_ELEMENT) {
                        require(START_ELEMENT, "coord");
                        points.add(coordinate());
                    }
                    require(END_ELEMENT, name);
                    yield new MultiCoordinate(points);
                }
                case "multipolyline" -> {
                    attributes();
                    var lines = new ArrayList<Polyline>();
                    while (input.nextTag() == START_ELEMENT) {
                        require(START_ELEMENT, "polyline");
                        lines.add(line());
                    }
                    require(END_ELEMENT, name);
                    yield new MultiPolyline(lines);
                }
                case "multisurface" -> {
                    attributes();
                    var surfaces = new ArrayList<Surface>();
                    while (input.nextTag() == START_ELEMENT) {
                        require(START_ELEMENT, "surface");
                        surfaces.add(surface());
                    }
                    require(END_ELEMENT, name);
                    yield new MultiSurface(surfaces);
                }
                default -> throw input.error(UNSUPPORTED, "Unknown geometry or custom line form");
            };
        }

        private Coordinate coordinate() throws XMLStreamException {
            require(START_ELEMENT, "coord");
            attributes();
            startContents();
            var point = position();
            require(END_ELEMENT, "coord");
            return point;
        }

        private Coordinate position() throws XMLStreamException {
            var values = new ArrayList<NumberValue>();
            values.add(number("c1"));
            input.nextTag();
            values.add(number("c2"));
            input.nextTag();
            if (at(START_ELEMENT, "c3")) {
                values.add(number("c3"));
                input.nextTag();
            }
            return new Coordinate(values);
        }

        private Polyline line() throws XMLStreamException {
            attributes();
            startContents();
            var start = coordinate();
            var segments = new ArrayList<Segment>();
            while (input.nextTag() == START_ELEMENT) {
                if (at(START_ELEMENT, "coord")) {
                    segments.add(new Straight(coordinate()));
                } else if (at(START_ELEMENT, "arc")) {
                    segments.add(arc());
                } else {
                    throw input.error(UNSUPPORTED, "Custom XTF line segment");
                }
            }
            require(END_ELEMENT, "polyline");
            if (segments.isEmpty()) {
                throw input.error(INVALID_WIRE, "Polyline needs a start and at least one segment");
            }
            return new Polyline(List.of(new CurvePart(start, segments)));
        }

        private Arc arc() throws XMLStreamException {
            attributes();
            startContents();
            var end = position();
            var a1 = number("a1");
            input.nextTag();
            var a2 = number("a2");
            input.nextTag();
            Optional<ArcRadius> radius = Optional.empty();
            if (at(START_ELEMENT, "r")) {
                radius = Optional.of(new ArcRadius(number("r"), RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT));
                input.nextTag();
            }
            require(END_ELEMENT, "arc");
            return new Arc(new Coordinate(List.of(a1, a2)), end, radius);
        }

        private Surface surface() throws XMLStreamException {
            attributes();
            startContents();
            require(START_ELEMENT, "exterior");
            var shell = boundary("exterior");
            var holes = new ArrayList<Ring>();
            while (input.nextTag() == START_ELEMENT) {
                if (at(START_ELEMENT, "exterior")) {
                    throw input.error(UNSUPPORTED, "Multiple exterior boundary partitions");
                }
                require(START_ELEMENT, "interior");
                holes.add(boundary("interior"));
            }
            require(END_ELEMENT, "surface");
            return new Surface(shell, holes);
        }

        private Ring boundary(String name) throws XMLStreamException {
            attributes();
            input.nextTag();
            require(START_ELEMENT, "polyline");
            var line = line();
            if (input.nextTag() != END_ELEMENT) {
                throw input.error(UNSUPPORTED, "XTF 2.4 boundary cannot preserve multiple polyline partitions");
            }
            require(END_ELEMENT, name);
            return new Ring(List.of(line));
        }

        private void startContents() throws XMLStreamException {
            input.nextTag();
            if (input.cursor().getEventType() == START_ELEMENT
                    && input.cursor().getName().equals(geom("extensions"))) {
                attributes();
                if (input.nextTag() != END_ELEMENT) {
                    throw input.error(UNSUPPORTED, "Nonempty geometry extensions");
                }
                require(END_ELEMENT, "extensions");
                input.nextTag();
            }
        }

        private NumberValue number(String name) throws XMLStreamException {
            require(START_ELEMENT, name);
            attributes();
            return Xtf24Scalars.number(input.text());
        }

        private void attributes() {
            if (input.cursor().getAttributeCount() > 0) {
                throw input.error(UNSUPPORTED, "Geometry metadata has no neutral representation");
            }
        }

        private boolean at(int event, String name) {
            return input.cursor().getEventType() == event && input.cursor().getName().equals(geom(name));
        }

        private void require(int event, String name) {
            if (!at(event, name)) {
                throw input.error(INVALID_WIRE, "Expected geometry " + name);
            }
        }
    }

    private static final class Writer {
        private final Xtf24Output output;

        Writer(Xtf24Output output) {
            this.output = output;
        }

        void geometry(TransferGeometry value) throws XMLStreamException {
            output.start(geom(tag(value)));
            switch (value) {
                case Coordinate coordinate -> position(coordinate, "c");
                case MultiCoordinate multi -> {
                    for (var coordinate : multi.coordinates()) {
                        geometry(coordinate);
                    }
                }
                case Polyline line -> line(line);
                case MultiPolyline multi -> {
                    for (var line : multi.lines()) {
                        geometry(line);
                    }
                }
                case Surface surface -> {
                    boundary("exterior", surface.shell());
                    for (var hole : surface.holes()) {
                        boundary("interior", hole);
                    }
                }
                case MultiSurface multi -> {
                    for (var surface : multi.surfaces()) {
                        geometry(surface);
                    }
                }
            }
            output.end();
        }

        private void line(Polyline line) throws XMLStreamException {
            if (line.lineAttributes().isPresent() || line.parts().size() != 1) {
                throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 cannot preserve LINEATTR or curve partitions");
            }
            var part = line.parts().getFirst();
            if (part.segments().isEmpty()) {
                throw Xtf24Wire.failure(INVALID_WIRE, "Polyline needs a start and at least one segment");
            }
            geometry(part.start());
            for (var segment : part.segments()) {
                switch (segment) {
                    case Straight straight -> geometry(straight.end());
                    case Arc arc -> {
                        if (arc.radius().filter(radius -> radius.convention() != RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT
                                || radius.sourceValue().exactValue().signum() <= 0).isPresent()) {
                            throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 requires a positive radius selected by the intermediate position");
                        }
                        output.start(geom("arc"));
                        position(arc.end(), "c");
                        position(arc.support(), "a");
                        if (arc.radius().isPresent()) {
                            number("r", arc.radius().orElseThrow().sourceValue());
                        }
                        output.end();
                    }
                }
            }
        }

        private void boundary(String name, Ring ring) throws XMLStreamException {
            if (ring.lines().size() != 1) {
                throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 cannot preserve boundary partitions");
            }
            output.start(geom(name));
            geometry(ring.lines().getFirst());
            output.end();
        }

        private void position(Coordinate coordinate, String prefix) throws XMLStreamException {
            for (int axis = 0; axis < coordinate.ordinates().size(); axis++) {
                number(prefix + (axis + 1), coordinate.ordinates().get(axis));
            }
        }

        private void number(String name, NumberValue value) throws XMLStreamException {
            if (!Xtf24Scalars.number(value.lexeme()).equals(value)) {
                throw Xtf24Wire.failure(INVALID_WIRE, "Geometry number differs from its exact lexical value");
            }
            output.start(geom(name));
            output.text(value.lexeme());
            output.end();
        }
    }
}
