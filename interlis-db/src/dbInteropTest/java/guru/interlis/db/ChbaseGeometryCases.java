package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseGeometryCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ChbaseGeometryCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void points(TestDatabase db) throws Exception {
        String model = "ChbasePoints" + version;
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        referenceSchema(db, model, "--coalesceMultiPoint");
        referenceImport(db, "legacy", input);
        var output = work.resolve("points-output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertPoints(output, model, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertPoints(output, model, "native");

        try (var sql = db.connect()) {
            assertGeometryColumn(db, "pinset", "shape", "MULTIPOINT", 2056);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("pingroup")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("pinpart")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("pinset") + " WHERE shape IS NULL"));
            if (db.postgis()) assertEquals("3", scalar(sql, "SELECT ST_NumGeometries(shape) FROM "
                    + db.table("pinset") + " WHERE t_ili_tid='native-value'"));
        }
    }

    void lines(TestDatabase db) throws Exception {
        String model = "ChbaseLines" + version;
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        referenceSchema(db, model, "--coalesceMultiLine");
        referenceImport(db, "legacy", input);
        var output = work.resolve("lines-output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertLines(output, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertLines(output, "native");
        assertGeometryColumn(db, "piperoute", "shape", "MULTICURVE", 2056);
        assertGeometryColumn(db, "flowroute", "shape", "MULTICURVE", 2056);
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("linestructure")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("piperoute") + " WHERE shape IS NULL"));
            if (db.postgis()) {
                assertEquals("2", scalar(sql, "SELECT ST_NumGeometries(shape) FROM " + db.table("piperoute")
                        + " WHERE t_ili_tid='native-value' AND ST_HasArc(shape)"));
            }
        }
    }

    private void assertLines(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        String types = version == 23 ? "GeometryCHLV95_V1" : "ChbaseLineTypes24";
        var lines = parts(objects.get(prefix + "-value"), types, "MultiLine", "Lines", "LineStructure");
        assertEquals(2, lines.size());
        var mixed = children(children(child(lines.getFirst(), "Line")).getFirst());
        assertEquals(3, mixed.size());
        assertEquals(xy("2600000", "1200000"), coordinate(mixed.getFirst()));
        var arc = mixed.get(1);
        assertEquals(version == 23 ? "ARC" : "arc", arc.getLocalName());
        assertEquals(xy("2600005", "1200005"), position(arc, version == 23 ? "A" : "a"));
        assertEquals(xy("2600010", "1200000"), coordinate(arc));
        assertEquals(xy("2600020", "1200000"), coordinate(mixed.get(2)));
        var straight = children(children(child(lines.getLast(), "Line")).getFirst());
        assertEquals(List.of(xy("2600100", "1200100"), xy("2600110", "1200120")),
                straight.stream().map(this::coordinate).toList());
        var directed = parts(objects.get(prefix + "-directed"), types, "MultiDirectedLine", "Lines", "DirectedLineStructure");
        assertEquals(1, directed.size());
        var steps = children(children(child(directed.getFirst(), "Line")).getFirst());
        assertEquals(List.of(xy("2600210", "1200200"), xy("2600200", "1200200")),
                steps.stream().map(this::coordinate).toList());
    }

    private void assertPoints(Path output, String model, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        var grouped = parts(objects.get(prefix + "-value"), model, "PinGroup", "Members", "PinPart");
        assertEquals(List.of(
                List.of(new BigDecimal("2600000.125"), new BigDecimal("1200000.25")),
                List.of(new BigDecimal("2600000.125"), new BigDecimal("1200000.25")),
                List.of(new BigDecimal("2600400.5"), new BigDecimal("1200200.75"))),
                grouped.stream().map(part -> coordinate(children(child(part, "Position")).getFirst())).toList());
    }

    private List<BigDecimal> coordinate(Element element) {
        return position(element, version == 23 ? "C" : "c");
    }

    private static List<BigDecimal> position(Element element, String prefix) {
        return xy(child(element, prefix + "1").getTextContent(), child(element, prefix + "2").getTextContent());
    }

    private static List<BigDecimal> xy(String x, String y) {
        return List.of(new BigDecimal(x).stripTrailingZeros(), new BigDecimal(y).stripTrailingZeros());
    }

    private List<Element> parts(Element object, String structureModel, String type, String bag, String itemType) {
        var structure = children(child(object, "Shape")).getFirst();
        assertStructure(structure, structureModel, type);
        var items = new ArrayList<Element>();
        for (var field : children(structure)) {
            assertEquals(bag, field.getLocalName());
            for (var item : children(field)) {
                assertStructure(item, structureModel, itemType);
                items.add(item);
            }
        }
        return items;
    }

    private void assertStructure(Element structure, String model, String type) {
        assertEquals(version == 23 ? model + "." + type : type, structure.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, structure.getNamespaceURI());
    }

    private Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        return children(basket).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }

    private static void assertGeometryColumn(TestDatabase db, String table, String column, String kind, int epsg) throws Exception {
        try (var sql = db.connect()) {
            String metadata = db.postgis() ? "SELECT type || ':' || srid FROM geometry_columns WHERE f_table_schema = '"
                    + db.schema() + "' AND f_table_name='" + table + "' AND f_geometry_column='" + column + "'"
                    : "SELECT geometry_type_name || ':' || srs_id FROM gpkg_geometry_columns WHERE table_name='" + table
                    + "' AND column_name='" + column + "'";
            assertEquals(kind + ":" + epsg, scalar(sql, metadata));
        }
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var source = getClass().getResourceAsStream("/chbase/" + name)) {
            Files.copy(source, path);
        }
        return path;
    }

    private Path withPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input).replace("fixture-", prefix + "-"));
    }

    private void referenceSchema(TestDatabase db, String model, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
        options.addAll(List.of(flags));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
