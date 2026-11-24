package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseTextFixtures {
    static final List<String> SUFFIXES = List.of("", "_de", "_fr", "_rm", "_it", "_en");
    static final Map<String, String> CAPTIONS = Map.of("", "Default", "de", "Brücke", "fr", "Pont",
            "rm", "Punt", "it", "Ponte", "en", "Bridge");
    static final String NOTES = "Première ligne\nDeuxième ligne";

    private final int version;
    private final Path source;
    private final Path other;
    private final Path base;
    final String multilingual;
    final String localised;
    final Path input;

    ChbaseTextFixtures(Path work, int version) throws Exception {
        this.version = version;
        multilingual = "ChbaseMultilingual" + version;
        localised = "ChbaseLocalised" + version;
        source = fixture(work, multilingual + ".ili");
        other = fixture(work, localised + ".ili");
        base = fixture(work, "Localisation" + version + ".ili");
        input = fixture(work, "ChbaseText" + version + ".xtf");
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, multilingual, source,
                "--model", localised, "--model-file", other.toString(), "--model-file", base.toString(),
                "--expand-multilingual", "--expand-localised");
    }

    void assertDelivery(Path output, String prefix, String label) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(2, baskets.size());
        assertMultilingual(basket(baskets, version, multilingual, prefix + "-ml-b"), prefix + "-ml");
        assertLocalised(basket(baskets, version, localised, prefix + "-loc-b"), prefix + "-loc", label);
    }

    private void assertLocalised(Element basket, String prefix, String label) {
        var objects = objects(basket, prefix);
        var present = objects.get(prefix + "-value");
        var missing = objects.get(prefix + "-empty");
        var caption = children(child(present, "Label")).getFirst();
        assertStructure(caption, "LocalisedText");
        assertEquals(label, child(caption, "Text").getTextContent());
        assertEquals("it", child(caption, "Language").getTextContent());
        var description = children(child(present, "Description")).getFirst();
        assertStructure(description, "LocalisedMText");
        assertEquals(NOTES, child(description, "Text").getTextContent());
        assertNull(child(description, "Language"));
        assertNull(child(missing, "Label"));
        assertNull(child(missing, "Description"));
    }

    private void assertMultilingual(Element basket, String prefix) {
        var samples = objects(basket, prefix);
        assertNull(child(samples.get(prefix + "-empty"), "Caption"));
        assertNull(child(samples.get(prefix + "-empty"), "Notes"));
        var plaque = samples.get(prefix + "-value");
        assertEquals(CAPTIONS, translations(plaque, "Caption", "Text"));
        assertEquals(Map.of("fr", NOTES), translations(plaque, "Notes", "MText"));
    }

    private Map<String, String> translations(Element object, String field, String type) {
        var wrapper = children(child(object, field)).getFirst();
        assertStructure(wrapper, "Multilingual" + type);
        return children(wrapper).stream().flatMap(bag -> children(bag).stream()).collect(Collectors.toMap(entry -> {
            assertStructure(entry, "Localised" + type);
            var language = child(entry, "Language");
            return language == null ? "" : language.getTextContent();
        }, entry -> child(entry, "Text").getTextContent()));
    }

    private void assertStructure(Element element, String type) {
        assertEquals(version == 23 ? "LocalisationCH_V1." + type : type, element.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/LocalisationCH_V2", element.getNamespaceURI());
    }

    private Map<String, Element> objects(Element basket, String prefix) {
        var objects = children(basket).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
        assertEquals(2, objects.size());
        assertNotNull(objects.get(prefix + "-value"));
        assertNotNull(objects.get(prefix + "-empty"));
        return objects;
    }
}
