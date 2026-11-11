package guru.interlis.xtf24;

import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Structured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24GeometryTest {
    private static final String GEOM = Xtf24GeometryCodec.NAMESPACE;
    private static TransferPlan plan;

    @BeforeAll
    static void compileModel() {
        plan = plan("""
                INTERLIS 2.4;
                MODEL Shapes AT "https://example.org" VERSION "1" =
                  DOMAIN P = COORD 0.00..100.00,0.00..100.00;
                         Q = COORD 0.00..100.00,0.00..100.00,0.00..100.00;
                  TOPIC Data =
                    CLASS Item =
                      point2 : P;
                      point3 : Q;
                      points : MULTICOORD 0.00..100.00,0.00..100.00;
                      line : POLYLINE WITH (STRAIGHTS) VERTEX P;
                      lines : MULTIPOLYLINE WITH (STRAIGHTS,ARCS) VERTEX P;
                      shape : SURFACE WITH (STRAIGHTS,ARCS) VERTEX P WITHOUT OVERLAPS > 0.01;
                      shapes : MULTISURFACE WITH (STRAIGHTS,ARCS) VERTEX P WITHOUT OVERLAPS > 0.01;
                      area : AREA WITH (STRAIGHTS,ARCS) VERTEX P WITHOUT OVERLAPS > 0.01;
                      areas : MULTIAREA WITH (STRAIGHTS,ARCS) VERTEX P WITHOUT OVERLAPS > 0.01;
                    END Item;
                  END Data;
                END Shapes.
                """, "Shapes");
    }

    @Test
    void retainsTwoAndThreeDimensionalPositionsAndMixedMultiComponents() throws Exception {
        var events = read(plan, transfer("""
                <point2>%s</point2><point3>%s</point3><points><g:multicoord>%s%s</g:multicoord></points>
                """.formatted(point("1000.0001", "2.00", "3"), point("1", "2"), point("5", "6"),
                point("7", "8", "9")), false));
        assertEquals(List.of("1000.0001", "2.00", "3"), lexemes((Coordinate) field(events, "point2")));
        assertEquals(2, ((Coordinate) field(events, "point3")).ordinates().size());
        var multi = (MultiCoordinate) field(events, "points");
        assertEquals(List.of(2, 3), multi.coordinates().stream().map(value -> value.ordinates().size()).toList());
        var xml = parsed(write(plan, events));
        assertEquals("1000.0001", xml.getElementsByTagNameNS(GEOM, "c1").item(0).getTextContent());
        assertEquals(2, ((Element) xml.getElementsByTagNameNS(GEOM, "multicoord").item(0))
                .getElementsByTagNameNS(GEOM, "coord").getLength());
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void preservesStandardArcsIncludingZeroRadiusWithoutValidatingLineForms() throws Exception {
        var start = point("0", "0", "1");
        var arc = """
                <g:arc><g:c1>2.01</g:c1><g:c2>3.002</g:c2>
                  <g:a1>1.11000</g:a1><g:a2>2.000</g:a2><g:r>0.00</g:r>
                </g:arc>
                """;
        var events = read(plan, transfer("<line>" + line(start, point("1", "0"), arc) + "</line>", false));
        var line = (Polyline) field(events, "line");
        assertEquals(3, line.parts().getFirst().start().ordinates().size());
        assertEquals(2, line.parts().getFirst().segments().getFirst().end().ordinates().size());
        var value = assertInstanceOf(Arc.class, line.parts().getFirst().segments().getLast());
        assertEquals(List.of("1.11000", "2.000"), lexemes(value.support()));
        assertEquals(List.of("2.01", "3.002"), lexemes(value.end()));
        assertEquals(new NumberValue("0.00", new BigDecimal("0.00")), value.radius().orElseThrow());
        var xml = parsed(write(plan, events));
        var encoded = (Element) xml.getElementsByTagNameNS(GEOM, "arc").item(0);
        assertEquals(List.of("c1", "c2", "a1", "a2", "r"), children(encoded).stream().map(Element::getLocalName).toList());
        assertEquals("0.00", children(encoded).getLast().getTextContent());
        assertEquals(events, read(plan, write(plan, events)));

        for (String radius : List.of("<g:r>+2.001e-1</g:r>", "")) {
            var other = read(plan, transfer("<line>" + line(point("0", "0"),
                    arc.replace("<g:r>0.00</g:r>", radius)) + "</line>", false));
            var otherArc = (Arc) ((Polyline) field(other, "line")).parts().getFirst().segments().getFirst();
            assertEquals(radius.isEmpty(), otherArc.radius().isEmpty());
            assertEquals(other, read(plan, write(plan, other)));
        }

        var negative = read(plan, transfer("<line>" + line(point("0", "0"), arc.replace("0.00</g:r>", "-2.00</g:r>"))
                + "</line>", false));
        var negativeArc = (Arc) ((Polyline) field(negative, "line")).parts().getFirst().segments().getFirst();
        assertEquals("-2.00", negativeArc.radius().orElseThrow().lexeme());
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                assertThrows(Xtf24Exception.class, () -> write(plan, negative)).reason());
    }

    @Test
    void retainsExteriorAndInteriorRolesWithoutTopologyChecks() throws Exception {
        var exterior = line(point("0", "0"), point("4", "4"), point("0", "4"), point("4", "0"));
        var hole1 = line(point("1", "1"), point("2", "1"));
        var hole2 = line(point("8", "8", "9"), point("9", "8", "10"));
        String surface = "<g:surface><g:exterior>" + exterior + "</g:exterior><g:interior>" + hole1
                + "</g:interior><g:interior>" + hole2 + "</g:interior></g:surface>";
        var events = read(plan, transfer("<shape>" + surface + "</shape><area>" + surface + "</area>", false));
        var value = (Surface) field(events, "shape");
        assertEquals(2, value.holes().size());
        assertEquals(List.of("0", "0"), lexemes(value.shell().lines().getFirst().parts().getFirst().start()));
        assertEquals(List.of("8", "8", "9"), lexemes(value.holes().getLast().lines().getFirst().parts().getFirst().start()));
        var area = (guru.interlis.transfer.AttributeSlot) descriptor("area");
        assertEquals(GeometryKind.AREA, ((GeometryType) new Xtf24Layout(plan).type(area.declaredType(), java.util.Map.of())).geometryKind());
        var xml = parsed(write(plan, events));
        var encoded = (Element) xml.getElementsByTagNameNS(GEOM, "surface").item(0);
        assertEquals(List.of("exterior", "interior", "interior"), children(encoded).stream().map(Element::getLocalName).toList());
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void supportsNativeMultiGeometriesAndEmptyMultiValues() throws Exception {
        var first = line(point("0", "0"), point("1", "1"));
        var second = line(point("2", "2", "3"), point("3", "3", "4"));
        var surface = "<g:surface><g:exterior>" + first + "</g:exterior></g:surface>";
        var events = read(plan, transfer("<lines><g:multipolyline>" + first + second
                + "</g:multipolyline></lines><shapes><g:multisurface>" + surface + surface
                + "</g:multisurface></shapes><areas><g:multisurface>" + surface + "</g:multisurface></areas>", false));
        assertEquals(2, ((MultiPolyline) field(events, "lines")).lines().size());
        assertEquals(2, ((MultiSurface) field(events, "shapes")).surfaces().size());
        assertEquals(1, ((MultiSurface) field(events, "areas")).surfaces().size());
        var xml = parsed(write(plan, events));
        assertEquals(2, children((Element) xml.getElementsByTagNameNS(GEOM, "multipolyline").item(0)).size());
        assertEquals(2, children((Element) xml.getElementsByTagNameNS(GEOM, "multisurface").item(0)).size());
        assertEquals(events, read(plan, write(plan, events)));
        var empty = read(plan, transfer("<points><g:multicoord/></points><lines><g:multipolyline/></lines>"
                + "<shapes><g:multisurface/></shapes>", false));
        assertEquals(new MultiCoordinate(List.of()), field(empty, "points"));
        assertEquals(new MultiPolyline(List.of()), field(empty, "lines"));
        assertEquals(new MultiSurface(List.of()), field(empty, "shapes"));
        assertEquals(empty, read(plan, write(plan, empty)));
    }

    @Test
    void permitsExtractMultiFormsOnlyInIncompleteBaskets() {
        String value = "<line><g:multipolyline>" + line(point("0", "0"), point("1", "1")) + "</g:multipolyline></line>";
        var events = read(plan, transfer(value, true));
        assertInstanceOf(MultiPolyline.class, field(events, "line"));
        assertEquals(events, read(plan, write(plan, events)));
        assertEquals(Xtf24Exception.Reason.INVALID_WIRE,
                assertThrows(Xtf24Exception.class, () -> read(plan, transfer(value, false))).reason());
        var emptySurface = read(plan, transfer("<shape><g:multisurface/></shape>", true));
        assertEquals(new MultiSurface(List.of()), field(emptySurface, "shape"));
        assertEquals(emptySurface, read(plan, write(plan, emptySurface)));
    }

    @Test
    void rejectsUnsupportedSegmentsAndUnrepresentablePartitions() {
        var events = read(plan, transfer("<line>" + line(point("0", "0"), point("1", "1")) + "</line>", false));
        var nativeLine = (Polyline) field(events, "line");
        var fragmented = new Polyline(List.of(nativeLine.parts().getFirst(), nativeLine.parts().getFirst()));
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                assertThrows(Xtf24Exception.class, () -> write(plan, withValue(events, "line", fragmented))).reason());
        var ring = new Ring(List.of(nativeLine, nativeLine));
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                assertThrows(Xtf24Exception.class, () -> write(plan,
                        withValue(read(plan, transfer("", false)), "shape", new Surface(ring, List.of())))).reason());
        var lineAttributes = new Structured(object(events).type(), java.util.Map.of());
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                assertThrows(Xtf24Exception.class, () -> write(plan,
                        withValue(events, "line", new Polyline(nativeLine.parts(), Optional.of(lineAttributes))))).reason());
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                assertThrows(Xtf24Exception.class, () -> read(plan, transfer("<line>"
                        + line(point("0", "0"), "<g:customLineSegment/>") + "</line>", false))).reason());
    }

    private static TransferObject object(List<TransferEvent> events) {
        return ((ObjectEvent) events.get(2)).object();
    }

    private static guru.interlis.transfer.TransferSlot descriptor(String name) {
        return plan.objects().stream().filter(object -> object.name().declared().last().equals("Item"))
                .findFirst().orElseThrow().slots().stream()
                .filter(slot -> slot.name().declared().last().equals(name)).findFirst().orElseThrow();
    }

    private static TransferValue field(List<TransferEvent> events, String name) {
        return object(events).value(descriptor(name).symbol()).orElseThrow();
    }

    private static List<TransferEvent> withValue(List<TransferEvent> events, String name, TransferValue value) {
        var object = object(events);
        var values = new LinkedHashMap<>(object.data().values());
        values.put(descriptor(name).symbol(), value);
        var copy = new ArrayList<>(events);
        copy.set(2, new ObjectEvent(new TransferObject(object.identity(), new Structured(object.type(), values)),
                Optional.empty(), TransferEvent.Consistency.COMPLETE));
        return copy;
    }

    private static String transfer(String values, boolean incomplete) {
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                  xmlns="http://www.interlis.ch/xtf/2.4/Shapes" xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>Shapes</i:model></i:models></i:headersection>
                  <i:datasection><Data i:bid="b" %s><Item i:tid="one">%s</Item></Data></i:datasection>
                </i:transfer>
                """.formatted(incomplete ? "i:consistency=\"INCOMPLETE\"" : "", values);
    }

    private static String point(String... ordinates) {
        var xml = new StringBuilder("<g:coord>");
        for (int axis = 0; axis < ordinates.length; axis++) {
            xml.append("<g:c").append(axis + 1).append(">").append(ordinates[axis])
                    .append("</g:c").append(axis + 1).append(">");
        }
        return xml.append("</g:coord>").toString();
    }

    private static String line(String start, String... segments) {
        return "<g:polyline>" + start + String.join("", segments) + "</g:polyline>";
    }

    private static List<String> lexemes(Coordinate coordinate) {
        return coordinate.ordinates().stream().map(NumberValue::lexeme).toList();
    }

    private static org.w3c.dom.Document parsed(String xml) throws Exception {
        return DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Element> children(Element element) {
        var result = new ArrayList<Element>();
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element elementChild) {
                result.add(elementChild);
            }
        }
        return result;
    }
}
