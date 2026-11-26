package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModelFixtures {
    private ModelFixtures() { }

    static Path fixture(Path work, String name) throws Exception {
        Path target = work.resolve(Path.of(name).getFileName());
        try (var resource = ModelFixtures.class.getResourceAsStream("/" + name)) {
            Files.copy(Objects.requireNonNull(resource, name), target);
        }
        return target;
    }

    static Path mergeDeliveries(Path output, int version, Path first, Path... others) throws Exception {
        var data = readDataSection(first, version);
        var document = data.getOwnerDocument();
        var models = modelEntries(data, version);
        for (var input : others) {
            var additional = readDataSection(input, version);
            for (var model : children(modelEntries(additional, version))) {
                models.appendChild(document.importNode(model, true));
            }
            for (var basket : children(additional)) {
                data.appendChild(document.importNode(basket, true));
            }
        }
        TransformerFactory.newDefaultInstance().newTransformer()
                .transform(new DOMSource(document), new StreamResult(output.toFile()));
        return output;
    }

    static List<String> headerModels(Element data, int version) {
        return children(modelEntries(data, version)).stream()
                .map(model -> version == 23 ? model.getAttribute("NAME") : model.getTextContent().strip()).toList();
    }

    private static Element modelEntries(Element data, int version) {
        var header = child((Element) data.getParentNode(), version == 23 ? "HEADERSECTION" : "headersection");
        return child(header, version == 23 ? "MODELS" : "models");
    }

    static Element basket(Element data, int version, String topic, String bid) {
        var basket = children(data).stream().filter(element -> bid.equals(id(element, version, "bid"))).findFirst().orElseThrow();
        assertName(basket, version, topic);
        return basket;
    }

    static void assertName(Element element, int version, String qualifiedName) {
        assertEquals(version == 23 ? qualifiedName : qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
                element.getLocalName());
        if (version == 24) {
            assertEquals("http://www.interlis.ch/xtf/2.4/" + qualifiedName.substring(0, qualifiedName.indexOf('.')),
                    element.getNamespaceURI());
        }
    }

    static void assertStructures(Path file, int version, String model, String library, String prefix, String className) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(model), headerModels(data, version));
        assertEquals(1, children(data).size());
        assertStructures(basket(data, version, model + ".Data", prefix + "-basket"), version, model, library, prefix, className);
    }

    static void assertStructures(Element basket, int version, String model, String library, String prefix) {
        assertStructures(basket, version, model, library, prefix, "Report");
    }

    private static void assertStructures(Element basket, int version, String model, String library, String prefix, String className) {
        assertEquals(1, children(basket).size());
        var report = children(basket).getFirst();
        assertName(report, version, model + ".Data." + className);
        assertEquals(prefix + "-report", id(report, version, "tid"));
        assertEquals(List.of("summary"), structureLabels(report, version, "Summary", library));
        assertEquals(List.of("second", "first"), structureLabels(report, version, "Entries", library));
        assertEquals(List.of("same", "same"), structureLabels(report, version, "Observations", library));
    }

    private static List<String> structureLabels(Element parent, int version, String name, String library) {
        return children(parent).stream().filter(field -> name.equals(field.getLocalName()))
                .flatMap(field -> children(field).stream()).map(detail -> {
                    assertName(detail, version, library + ".Detail");
                    return child(detail, "Label").getTextContent();
                }).toList();
    }

    static String tableFor(Target db, String iliName) throws Exception {
        try (var sql = db.connect()) {
            return scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname") + " WHERE iliname='" + iliName + "'");
        }
    }
}
