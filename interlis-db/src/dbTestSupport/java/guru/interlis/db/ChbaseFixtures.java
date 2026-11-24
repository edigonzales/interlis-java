package guru.interlis.db;

import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static guru.interlis.db.DbTestSupport.id;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChbaseFixtures {
    private ChbaseFixtures() { }

    static Path fixture(Path work, String name) throws Exception {
        Path path = work.resolve(name);
        try (var source = ChbaseFixtures.class.getResourceAsStream("/chbase/" + name)) {
            Files.copy(Objects.requireNonNull(source, name), path);
        }
        return path;
    }

    static Path withPrefix(Path work, Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input).replace("fixture-", prefix + "-"));
    }

    static Element basket(List<Element> baskets, int version, String model, String bid) {
        var basket = baskets.stream().filter(element -> bid.equals(id(element, version, "bid"))).findFirst().orElseThrow();
        assertEquals(version == 23 ? model + ".Data" : "Data", basket.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, basket.getNamespaceURI());
        return basket;
    }
}
