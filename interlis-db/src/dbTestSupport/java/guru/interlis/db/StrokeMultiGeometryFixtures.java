package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static guru.interlis.db.ChbaseFixtures.basket;
import static guru.interlis.db.ChbaseFixtures.fixture;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.StrokeFixtures.objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrokeMultiGeometryFixtures {
    private static final String DIRECT = "StrokeCollections24";

    private final StrokeFixtures xml;
    private final Path lineSource;
    private final Path surfaceSource;
    private final Path directSource;
    final String lines;
    final String surfaces;
    final Path input;

    StrokeMultiGeometryFixtures(Path work, int version) throws Exception {
        xml = new StrokeFixtures(work, version);
        lines = "ChbaseLines" + version;
        surfaces = "ChbaseSurfaces" + version;
        lineSource = fixture(work, lines + ".ili");
        surfaceSource = fixture(work, surfaces + ".ili");
        directSource = version == 24 ? copyDirectModel(work) : null;
        input = combinedDelivery(work);
    }

    String referenceModels() {
        var models = String.join(";", lines, surfaces);
        return directSource == null ? models : models + ";" + DIRECT;
    }

    String[] extraModels() {
        var models = new ArrayList<>(List.of("--model", surfaces));
        if (directSource != null) models.addAll(List.of("--model", DIRECT));
        return models.toArray(String[]::new);
    }

    void schema(Target db) {
        var options = new ArrayList<>(List.of(extraModels()));
        options.addAll(List.of("--model-file", surfaceSource.toString(), "--stroke-arcs",
                "--coalesce-multi-line", "--coalesce-multi-surface"));
        if (directSource != null) options.addAll(List.of("--model-file", directSource.toString()));
        assertSchemaWithIdentityColumns(db, lines, lineSource, options.toArray(String[]::new));
    }

    private static Path copyDirectModel(Path work) throws Exception {
        var file = work.resolve(DIRECT + ".ili");
        try (var source = StrokeMultiGeometryFixtures.class.getResourceAsStream("/stroke/" + DIRECT + ".ili")) {
            Files.copy(Objects.requireNonNull(source, DIRECT), file);
        }
        return file;
    }

    private Path combinedDelivery(Path work) throws Exception {
        int version = xml.version;
        var data = readDataSection(fixture(work, "ChbaseGeometry" + version + ".xtf"), version);
        var document = data.getOwnerDocument();
        var modelEntries = models(data);
        data.removeChild(basket(children(data), version, "ChbasePoints" + version, "fixture-points-b"));
        for (var entry : children(modelEntries)) {
            var name = version == 23 ? entry.getAttribute("NAME") : entry.getTextContent();
            if (name.equals("ChbasePoints" + version)) modelEntries.removeChild(entry);
        }

        if (directSource != null) {
            var direct = readDataSection(xml.transfer(DIRECT, "fixture-direct", directObjects()), version);
            modelEntries.appendChild(document.importNode(children(models(direct)).getFirst(), true));
            data.appendChild(document.importNode(children(direct).getFirst(), true));
        }

        Path output = work.resolve("collections-input.xtf");
        TransformerFactory.newDefaultInstance().newTransformer()
                .transform(new DOMSource(document), new StreamResult(output.toFile()));
        return output;
    }

    private Element models(Element data) {
        var transfer = data.getOwnerDocument().getDocumentElement();
        var header = child(transfer, xml.version == 23 ? "HEADERSECTION" : "headersection");
        return child(header, xml.version == 23 ? "MODELS" : "models");
    }

    private String directObjects() {
        var multiLine = xml.geometry("multipolyline", xml.polyline(xml.coord(600000, 200000) + xml.arc(600005, 200005, 600010, 200000))
                + xml.polyline(xml.coord(600020, 200020) + xml.coord(600030, 200010)));
        var first = xml.surfaceValue(xml.polyline(xml.coord(600000, 200000) + xml.arc(600005, 199995, 600010, 200000)
                        + xml.coord(600010, 200010) + xml.coord(600000, 200000)),
                xml.polyline(xml.coord(600006, 200001) + xml.coord(600008, 200003)
                        + xml.coord(600008, 200001) + xml.coord(600006, 200001)));
        var second = xml.surfaceValue(xml.polyline(xml.coord(600020, 200020) + xml.coord(600030, 200020)
                + xml.coord(600030, 200030) + xml.coord(600020, 200020)));
        var multiSurface = xml.geometry("multisurface", first + second);
        return directObjects("RouteGroup", "line", multiLine)
                + directObjects("ParcelGroup", "surface", multiSurface)
                + directObjects("DistrictGroup", "area", multiSurface);
    }

    private String directObjects(String type, String kind, String shape) {
        return xml.object(DIRECT, type, "fixture-direct-" + kind + "-value", "<Shape>" + shape + "</Shape>")
                + xml.object(DIRECT, type, "fixture-direct-" + kind + "-null", "");
    }

    void assertDelivery(Path output, String prefix) throws Exception {
        int version = xml.version;
        var baskets = children(readDataSection(output, version));
        assertEquals(version == 23 ? 2 : 3, baskets.size());
        var linePrefix = prefix + "-lines";
        assertWrappedLines(objects(basket(baskets, version, lines, linePrefix + "-b"), version), linePrefix);
        var surfacePrefix = prefix + "-surfaces";
        assertWrappedSurfaces(objects(basket(baskets, version, surfaces, surfacePrefix + "-b"), version), surfacePrefix);
        if (version == 24) {
            var directPrefix = prefix + "-direct";
            var direct = objects(basket(baskets, version, DIRECT, directPrefix + "-b"), version);
            assertEquals(6, direct.size());
            assertMultiLine(direct, directPrefix + "-line");
            assertMultiSurface(direct, directPrefix + "-surface");
            assertMultiSurface(direct, directPrefix + "-area");
        }
    }

    private void assertMultiLine(Map<String, Element> objects, String prefix) {
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        var geometry = children(child(objects.get(prefix + "-value"), "Shape")).getFirst();
        assertEquals("multipolyline", geometry.getLocalName());
        var lines = children(geometry).stream().map(xml::positions).toList();
        assertEquals(2, lines.size());
        assertEquals(81, lines.getFirst().size());
        assertEquals(List.of(600000.0, 200000.0), lines.getFirst().getFirst());
        assertEquals(List.of(600005.0, 200005.0), lines.getFirst().get(40));
        assertEquals(List.of(600010.0, 200000.0), lines.getFirst().getLast());
        assertEquals(List.of(List.of(600020.0, 200020.0), List.of(600030.0, 200010.0)), lines.getLast());
    }

    private void assertWrappedLines(Map<String, Element> objects, String prefix) {
        assertEquals(3, objects.size());
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        var lines = wrapperMembers(objects.get(prefix + "-value"), "MultiLine", "Lines", "Line").stream()
                .map(xml::positions).toList();
        assertEquals(2, lines.size());
        assertEquals(82, lines.getFirst().size());
        assertEquals(List.of(2600000.0, 1200000.0), lines.getFirst().getFirst());
        assertEquals(List.of(2600005.0, 1200005.0), lines.getFirst().get(40));
        assertEquals(List.of(2600020.0, 1200000.0), lines.getFirst().getLast());
        assertEquals(List.of(List.of(2600100.0, 1200100.0), List.of(2600110.0, 1200120.0)), lines.getLast());
        var directed = wrapperMembers(objects.get(prefix + "-directed"), "MultiDirectedLine", "Lines", "Line");
        assertEquals(1, directed.size());
        assertEquals(List.of(List.of(2600210.0, 1200200.0), List.of(2600200.0, 1200200.0)), xml.positions(directed.getFirst()));
    }

    private void assertWrappedSurfaces(Map<String, Element> objects, String prefix) {
        assertEquals(2, objects.size());
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        var members = wrapperMembers(objects.get(prefix + "-value"), "MultiSurface", "Surfaces", "Surface");
        assertEquals(2, members.size());
        var first = xml.surfaceRings(members.getFirst());
        assertEquals(2, first.size());
        assertTrue(first.getFirst().contains(List.of(600010.0, 199995.0)));
        assertEquals(List.of(600000.0, 200000.0), first.getFirst().getFirst());
        assertEquals(first.getFirst().getFirst(), first.getFirst().getLast());
        assertEquals(List.of(List.of(600005.0, 200005.0), List.of(600005.0, 200010.0),
                List.of(600010.0, 200010.0), List.of(600010.0, 200005.0), List.of(600005.0, 200005.0)), first.getLast());
        assertEquals(List.of(List.of(List.of(600100.0, 200100.0), List.of(600110.0, 200100.0),
                List.of(600110.0, 200110.0), List.of(600100.0, 200100.0))), xml.surfaceRings(members.getLast()));
    }

    private void assertMultiSurface(Map<String, Element> objects, String prefix) {
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        var parts = children(children(child(objects.get(prefix + "-value"), "Shape")).getFirst());
        assertEquals(2, parts.size());
        var first = xml.surfaceRings(parts.getFirst());
        assertEquals(2, first.size());
        assertEquals(83, first.getFirst().size());
        assertEquals(List.of(600005.0, 199995.0), first.getFirst().get(40));
        assertEquals(List.of(List.of(600006.0, 200001.0), List.of(600008.0, 200003.0),
                List.of(600008.0, 200001.0), List.of(600006.0, 200001.0)), first.getLast());
        assertEquals(List.of(List.of(List.of(600020.0, 200020.0), List.of(600030.0, 200020.0),
                List.of(600030.0, 200030.0), List.of(600020.0, 200020.0))), xml.surfaceRings(parts.getLast()));
    }

    private List<Element> wrapperMembers(Element object, String wrapperName, String bag, String field) {
        var wrapper = children(child(object, "Shape")).getFirst();
        assertTrue(wrapper.getLocalName().equals(wrapperName) || wrapper.getLocalName().endsWith("." + wrapperName));
        return children(wrapper).stream().flatMap(element -> {
            assertEquals(bag, element.getLocalName());
            return children(element).stream();
        }).map(member -> children(child(member, field)).getFirst()).toList();
    }

}
