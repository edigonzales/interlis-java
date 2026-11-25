package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StrokeFixtures {
    private final Path work;
    final int version;

    StrokeFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    List<List<List<Double>>> rings(Element object) {
        var surface = children(child(object, "Shape")).getFirst();
        return surfaceRings(surface);
    }

    List<List<List<Double>>> surfaceRings(Element surface) {
        return children(surface).stream().map(ring -> positions(children(ring).getFirst())).toList();
    }

    String surfaceValue(String exterior, String... interiors) {
        String contents = geometry(version == 23 ? "boundary" : "exterior", exterior);
        for (String interior : interiors) contents += geometry(version == 23 ? "boundary" : "interior", interior);
        return geometry("surface", contents);
    }

    List<List<Double>> linePositions(Element object) {
        return positions(children(child(object, "Shape")).getFirst());
    }

    String route(String model, String tid, String shape) {
        return object(model, "Route", tid, "<Shape>" + shape + "</Shape>");
    }

    Path source(String model, String kind) throws Exception {
        return source(model, kind, "000");
    }

    Path source(String model, String kind, String precision) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%1$d;
                MODEL %2$s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN Vertex = COORD 400000.%3$s .. 900000.%3$s, 0.%3$s .. 400000.%3$s;
                  TOPIC Data =
                    CLASS Route =
                      Shape : %4$s WITH (STRAIGHTS, ARCS) VERTEX Vertex%5$s;
                    END Route;
                  END Data;
                END %2$s.
                """.formatted(version - 20, model, precision, kind,
                        kind.endsWith("LINE") ? "" : " WITHOUT OVERLAPS > 0.001"));
    }

    Path dataset(String model, String prefix, String shape) throws Exception {
        return transfer(model, prefix, route(model, prefix + "-value", shape)
                + object(model, "Route", prefix + "-null", ""));
    }

    Path transfer(String model, String prefix, String objects) throws Exception {
        return transfer(model, prefix, prefix + "-b", objects);
    }

    Path transfer(String model, String filename, String bid, String objects) throws Exception {
        var xml = referenceDatasetFixture(model, version, bid, objects);
        if (version == 24) xml = xml.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(filename + ".xtf"), xml);
    }

    String object(String model, String name, String tid, String fields) {
        String tag = version == 23 ? model + ".Data." + name : name;
        return "<" + tag + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + fields + "</" + tag + ">";
    }

    String polyline(String contents) { return geometry("polyline", contents); }

    String coord(int x, int y) { return geometry("coord", geometry("c1", Integer.toString(x)) + geometry("c2", Integer.toString(y))); }

    String arc(int ax, int ay, int x, int y) {
        String support = geometry("a1", Integer.toString(ax)) + geometry("a2", Integer.toString(ay));
        String end = geometry("c1", Integer.toString(x)) + geometry("c2", Integer.toString(y));
        return geometry("arc", version == 23 ? end + support : support + end);
    }

    String geometry(String name, String contents) {
        String tag = version == 23 ? name.toUpperCase(java.util.Locale.ROOT) : "g:" + name;
        return "<" + tag + ">" + contents + "</" + tag + ">";
    }

    List<List<Double>> positions(Element polyline) {
        return children(polyline).stream().map(this::position).toList();
    }

    List<Double> position(Element point) {
        String coord = version == 23 ? "COORD" : "coord";
        String axis = version == 23 ? "C" : "c";
        assertEquals(coord, point.getLocalName());
        return List.of(Double.valueOf(child(point, axis + "1").getTextContent()),
                Double.valueOf(child(point, axis + "2").getTextContent()));
    }

    Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        return objects(basket, version);
    }

    static Map<String, Element> objects(Element basket, int version) {
        return children(basket).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }

    static void assertGeometryColumn(Target db, String table, String column, String type, int epsg) throws Exception {
        try (var sql = db.connect()) {
            String query = db.postgis()
                    ? "SELECT type || ':' || srid FROM geometry_columns WHERE f_table_schema='" + db.schema()
                        + "' AND f_table_name='" + table + "' AND f_geometry_column='" + column + "'"
                    : "SELECT geometry_type_name || ':' || srs_id FROM gpkg_geometry_columns WHERE table_name='" + table
                        + "' AND column_name='" + column + "'";
            assertEquals(type + ":" + epsg, scalar(sql, query));
        }
    }
}
