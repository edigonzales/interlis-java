package guru.interlis.db;

import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SmartFixtures {
    private final Path work;
    private final int version;

    SmartFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path changedRegion(Path input) throws Exception {
        return Files.writeString(work.resolve("region-changed.xtf"), Files.readString(input)
                .replace(">child<", ">updated<").replace(">inherited<", ">marker updated<"));
    }

    void assertGeometries(Path file, String model, String prefix) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        assertGeometries(basket, model, prefix);
    }

    void assertGeometries(Element basket, String model, String prefix) {
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var meter = object(basket, model, "Meter", prefix + "-m");
        var smart = object(basket, model, "SmartUnit", prefix + "-s");
        var absent = object(basket, model, "SmartUnit", prefix + "-n");
        var terminal = object(basket, model, "Terminal", prefix + "-t");
        assertPoint(child(meter, "Location"), "2600123.125", "1200456.375");
        assertPoint(child(smart, "Location"), "2600124.125", "1200457.375");
        assertPoint(child(smart, "ExtraLocation"), "2600125.125", "1200458.375");
        assertPoint(child(absent, "Location"), "2600126.125", "1200459.375");
        assertNull(child(meter, "ExtraLocation"));
        assertNull(child(absent, "ExtraLocation"));
        assertNull(child(terminal, "Location"));
    }

    private static void assertPoint(Element point, String x, String y) {
        var coordinates = children(children(point).getFirst());
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(coordinates.get(0).getTextContent())));
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(coordinates.get(1).getTextContent())));
    }

    void assertRegion(Path path, String memo) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        assertEquals("region-b", id(basket, version, "bid"));
        var station = children(basket).stream().filter(element -> "region-s".equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertEquals(memo, child(station, "LocalMemo").getTextContent());
        assertEquals("base", child(station, "Memo").getTextContent());
    }

    Element object(Element basket, String model, String name, String tid) {
        var object = children(basket).stream().filter(element -> tid.equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertEquals(version == 23 ? model + ".Data." + name : name, object.getLocalName());
        return object;
    }

    Path transferWithPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input).replace("fixture-", prefix + "-"));
    }

    Path fixture(String name) throws Exception {
        return fixture("/smart/", name);
    }

    Path fixture(String directory, String name) throws Exception {
        var path = work.resolve(name);
        try (var source = SmartFixtures.class.getResourceAsStream(directory + name)) {
            Files.copy(java.util.Objects.requireNonNull(source, directory + name), path);
        }
        return path;
    }

}
