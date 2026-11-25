package guru.interlis.db;

import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StrokeGeometryFixtures {
    final String model;
    final Path source;
    final StrokeFixtures xml;

    StrokeGeometryFixtures(Path work, int version) throws Exception {
        xml = new StrokeFixtures(work, version);
        model = "StrokeGeometry" + version;
        source = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%1$d;
                MODEL %2$s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN Vertex = COORD 400000.000 .. 900000.000, 0.000 .. 400000.000;
                  TOPIC Data =
                    CLASS Straight =
                      Shape : POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Vertex;
                    END Straight;
                    CLASS Route =
                      Shape : POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Vertex;
                    END Route;
                    CLASS Parcel =
                      Shape : SURFACE WITH (STRAIGHTS, ARCS) VERTEX Vertex WITHOUT OVERLAPS > 0.001;
                    END Parcel;
                    CLASS District =
                      Shape : AREA WITH (STRAIGHTS, ARCS) VERTEX Vertex WITHOUT OVERLAPS > 0.001;
                    END District;
                    CLASS Marker =
                      Anchor : Vertex;
                    END Marker;
                  END Data;
                END %2$s.
                """.formatted(version - 20, model, model));
    }

    Path delivery(String prefix) throws Exception {
        var outer = xml.polyline(xml.coord(600000, 200000) + xml.arc(600005, 199995, 600010, 200000)
                + xml.coord(600010, 200010) + xml.coord(600000, 200010) + xml.coord(600000, 200000));
        var hole = xml.polyline(xml.coord(600002, 200002) + xml.coord(600002, 200004)
                + xml.coord(600004, 200002) + xml.coord(600002, 200002));
        var start = xml.coord(600000, 200000);
        var end = xml.coord(600010, 200000);
        var top = start + xml.arc(600005, 200005, 600010, 200000);
        var topClosure = xml.coord(600010, 200010) + xml.coord(600000, 200010) + start;
        var bottom = end + xml.arc(600005, 200005, 600000, 200000)
                + xml.coord(600000, 199990) + xml.coord(600010, 199990) + end;
        var upper = xml.version == 23 ? xml.polyline(top) + xml.polyline(end + topClosure) : xml.polyline(top + topClosure);
        return xml.transfer(model, prefix, shaped("Straight", prefix + "-straight",
                xml.polyline(start + end + xml.coord(600020, 200010)))
                + xml.object(model, "Straight", prefix + "-straight-null", "")
                + shaped("Route", prefix + "-arc", ordinaryArc(xml))
                + xml.object(model, "Route", prefix + "-arc-null", "")
                + shaped("Parcel", prefix + "-parcel", xml.surfaceValue(outer, hole))
                + xml.object(model, "Parcel", prefix + "-parcel-null", "")
                + shaped("District", prefix + "-top", xml.surfaceValue(upper))
                + shaped("District", prefix + "-bottom", xml.surfaceValue(xml.polyline(bottom)))
                + xml.object(model, "Marker", prefix + "-point", "<Anchor>" + xml.coord(600005, 200005) + "</Anchor>"));
    }

    static String ordinaryArc(StrokeFixtures xml) {
        return xml.polyline(xml.coord(600000, 200000) + xml.arc(600005, 200005, 600010, 200000) + xml.coord(600020, 200000));
    }

    private String shaped(String type, String tid, String shape) {
        return xml.object(model, type, tid, "<Shape>" + shape + "</Shape>");
    }

    List<List<Double>> assertDelivery(Path output, String prefix) throws Exception {
        var objects = xml.objects(output, prefix);
        assertEquals(9, objects.size());
        assertStraight(objects, prefix);
        assertNull(child(objects.get(prefix + "-arc-null"), "Shape"));
        var arc = xml.linePositions(objects.get(prefix + "-arc"));
        assertEquals(82, arc.size());
        assertEquals(List.of(600000.0, 200000.0), arc.getFirst());
        assertEquals(List.of(600005.0, 200005.0), arc.get(40));
        assertEquals(List.of(600010.0, 200000.0), arc.get(80));
        assertEquals(List.of(600020.0, 200000.0), arc.getLast());
        assertPolygon(objects, prefix);
        assertSharedAreaEdge(objects, prefix);
        var point = children(child(objects.get(prefix + "-point"), "Anchor")).getFirst();
        assertEquals(List.of(600005.0, 200005.0), xml.position(point));
        return arc;
    }

    private void assertStraight(Map<String, Element> objects, String prefix) {
        assertNull(child(objects.get(prefix + "-straight-null"), "Shape"));
        assertEquals(List.of(List.of(600000.0, 200000.0), List.of(600010.0, 200000.0), List.of(600020.0, 200010.0)),
                xml.linePositions(objects.get(prefix + "-straight")));
    }

    private void assertPolygon(Map<String, Element> objects, String prefix) {
        assertNull(child(objects.get(prefix + "-parcel-null"), "Shape"));
        var rings = xml.rings(objects.get(prefix + "-parcel"));
        assertEquals(2, rings.size());
        assertEquals(84, rings.getFirst().size());
        assertEquals(List.of(600000.0, 200000.0), rings.getFirst().getFirst());
        assertEquals(rings.getFirst().getFirst(), rings.getFirst().getLast());
        assertEquals(List.of(600005.0, 199995.0), rings.getFirst().get(40));
        assertEquals(List.of(List.of(600002.0, 200002.0), List.of(600002.0, 200004.0), List.of(600004.0, 200002.0),
                List.of(600002.0, 200002.0)), rings.getLast());
    }

    private void assertSharedAreaEdge(Map<String, Element> objects, String prefix) {
        var upper = xml.rings(objects.get(prefix + "-top")).getFirst();
        var lower = xml.rings(objects.get(prefix + "-bottom")).getFirst();
        assertEquals(84, upper.size());
        assertEquals(84, lower.size());
        assertEquals(List.of(600000.0, 200000.0), upper.getFirst());
        assertEquals(List.of(600010.0, 200000.0), lower.getFirst());
        assertEquals(List.of(600005.0, 200005.0), upper.get(40));
        assertEquals(upper.getFirst(), upper.getLast());
        assertEquals(lower.getFirst(), lower.getLast());
        assertEquals(upper.subList(0, 81), lower.subList(0, 81).reversed());
    }
}
