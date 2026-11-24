package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.ChbaseFixtures.basket;
import static guru.interlis.db.ChbaseFixtures.fixture;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseGeometryFixtures {
    private final int version;
    private final String points;
    private final String lines;
    private final String surfaces;
    private final Path pointModel;
    private final Path lineModel;
    private final Path surfaceModel;
    final Path input;

    ChbaseGeometryFixtures(Path work, int version) throws Exception {
        this.version = version;
        points = "ChbasePoints" + version;
        lines = "ChbaseLines" + version;
        surfaces = "ChbaseSurfaces" + version;
        pointModel = fixture(work, points + ".ili");
        lineModel = fixture(work, lines + ".ili");
        surfaceModel = fixture(work, surfaces + ".ili");
        input = fixture(work, "ChbaseGeometry" + version + ".xtf");
    }

    String model() {
        return points;
    }

    String referenceModels() {
        return String.join(";", points, lines, surfaces);
    }

    String[] extraModels() {
        return new String[]{"--model", lines, "--model", surfaces};
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, points, pointModel, "--model", lines, "--model", surfaces,
                "--model-file", lineModel.toString(), "--model-file", surfaceModel.toString(),
                "--coalesce-multi-point", "--coalesce-multi-line", "--coalesce-multi-surface");
    }

    void assertDelivery(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(3, baskets.size());
        assertPoints(basket(baskets, version, points, prefix + "-points-b"), points, prefix + "-points");
        assertLines(basket(baskets, version, lines, prefix + "-lines-b"), prefix + "-lines");
        assertSurfaces(basket(baskets, version, surfaces, prefix + "-surfaces-b"), prefix + "-surfaces");
    }

    private void assertSurfaces(Element basket, String prefix) {
        var objects = objects(basket, version);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        String types = version == 23 ? "GeometryCHLV03_V1" : "ChbaseSurfaceTypes24";
        var items = parts(objects.get(prefix + "-value"), types, "MultiSurface", "Surfaces", "SurfaceStructure", version);
        assertEquals(2, items.size());
        var first = children(children(child(items.getFirst(), "Surface")).getFirst());
        assertEquals(2, first.size());
        var exterior = children(children(first.getFirst()).getFirst());
        assertEquals(5, exterior.size());
        assertEquals(xy("600000", "200000"), coordinate(exterior.getFirst(), version));
        var arc = exterior.get(1);
        assertEquals(version == 23 ? "ARC" : "arc", arc.getLocalName());
        assertEquals(xy("600010", "199995"), position(arc, version == 23 ? "A" : "a"));
        assertEquals(xy("600020", "200000"), coordinate(arc, version));
        assertEquals(xy("600000", "200000"), coordinate(exterior.getLast(), version));
        var hole = children(children(first.getLast()).getFirst());
        assertEquals(List.of(xy("600005", "200005"), xy("600005", "200010"), xy("600010", "200010"),
                xy("600010", "200005"), xy("600005", "200005")), hole.stream().map(element -> coordinate(element, version)).toList());
        var second = children(children(child(items.getLast(), "Surface")).getFirst());
        assertEquals(1, second.size());
        var vertices = children(children(second.getFirst()).getFirst());
        assertEquals(List.of(xy("600100", "200100"), xy("600110", "200100"), xy("600110", "200110"), xy("600100", "200100")),
                vertices.stream().map(element -> coordinate(element, version)).toList());
    }

    private void assertLines(Element basket, String prefix) {
        var objects = objects(basket, version);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        String types = version == 23 ? "GeometryCHLV95_V1" : "ChbaseLineTypes24";
        var lines = parts(objects.get(prefix + "-value"), types, "MultiLine", "Lines", "LineStructure", version);
        assertEquals(2, lines.size());
        var mixed = children(children(child(lines.getFirst(), "Line")).getFirst());
        assertEquals(3, mixed.size());
        assertEquals(xy("2600000", "1200000"), coordinate(mixed.getFirst(), version));
        var arc = mixed.get(1);
        assertEquals(version == 23 ? "ARC" : "arc", arc.getLocalName());
        assertEquals(xy("2600005", "1200005"), position(arc, version == 23 ? "A" : "a"));
        assertEquals(xy("2600010", "1200000"), coordinate(arc, version));
        assertEquals(xy("2600020", "1200000"), coordinate(mixed.get(2), version));
        var straight = children(children(child(lines.getLast(), "Line")).getFirst());
        assertEquals(List.of(xy("2600100", "1200100"), xy("2600110", "1200120")),
                straight.stream().map(element -> coordinate(element, version)).toList());
        var directed = parts(objects.get(prefix + "-directed"), types, "MultiDirectedLine", "Lines", "DirectedLineStructure", version);
        assertEquals(1, directed.size());
        var steps = children(children(child(directed.getFirst(), "Line")).getFirst());
        assertEquals(List.of(xy("2600210", "1200200"), xy("2600200", "1200200")),
                steps.stream().map(element -> coordinate(element, version)).toList());
    }

    private void assertPoints(Element basket, String model, String prefix) {
        var objects = objects(basket, version);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        var grouped = parts(objects.get(prefix + "-value"), model, "PinGroup", "Members", "PinPart", version);
        assertEquals(List.of(
                List.of(new BigDecimal("2600000.125"), new BigDecimal("1200000.25")),
                List.of(new BigDecimal("2600000.125"), new BigDecimal("1200000.25")),
                List.of(new BigDecimal("2600400.5"), new BigDecimal("1200200.75"))),
                grouped.stream().map(part -> coordinate(children(child(part, "Position")).getFirst(), version)).toList());
    }

    static List<BigDecimal> coordinate(Element element, int version) {
        return position(element, version == 23 ? "C" : "c");
    }

    private static List<BigDecimal> position(Element element, String prefix) {
        return xy(child(element, prefix + "1").getTextContent(), child(element, prefix + "2").getTextContent());
    }

    static List<BigDecimal> xy(String x, String y) {
        return List.of(new BigDecimal(x).stripTrailingZeros(), new BigDecimal(y).stripTrailingZeros());
    }

    static List<Element> parts(Element object, String structureModel, String type, String bag, String itemType, int version) {
        var structure = children(child(object, "Shape")).getFirst();
        assertStructure(structure, structureModel, type, version);
        var items = new ArrayList<Element>();
        for (var field : children(structure)) {
            assertEquals(bag, field.getLocalName());
            for (var item : children(field)) {
                assertStructure(item, structureModel, itemType, version);
                items.add(item);
            }
        }
        return items;
    }

    private static void assertStructure(Element structure, String model, String type, int version) {
        assertEquals(version == 23 ? model + "." + type : type, structure.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, structure.getNamespaceURI());
    }

    static Map<String, Element> objects(Element basket, int version) {
        return children(basket).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }
}
