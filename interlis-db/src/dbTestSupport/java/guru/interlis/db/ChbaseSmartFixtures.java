package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.ChbaseFixtures.basket;
import static guru.interlis.db.ChbaseFixtures.fixture;
import static guru.interlis.db.ChbaseGeometryFixtures.coordinate;
import static guru.interlis.db.ChbaseGeometryFixtures.objects;
import static guru.interlis.db.ChbaseGeometryFixtures.parts;
import static guru.interlis.db.ChbaseGeometryFixtures.xy;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChbaseSmartFixtures {
    private final int version;
    private final Path source;
    final String model;
    final Path input;
    final Path update;

    ChbaseSmartFixtures(Path work, int version) throws Exception {
        this.version = version;
        model = "ChbaseSmart" + version;
        source = fixture(work, model + ".ili");
        input = fixture(work, model + ".xtf");
        update = fixture(work, model + "-update.xtf");
    }

    void schema(Target db) {
        var options = new ArrayList<>(List.of("--inheritance", "smart2", "--coalesce-multi-point",
                "--expand-multilingual", "--create-geom-index"));
        if (db.postgis()) options.add("--one-geom-per-table");
        assertSchemaWithIdentityColumns(db, model, source, options.toArray(String[]::new));
    }

    void assertDelivery(Path output, String prefix, String german, String french, int x, int y) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = basket(baskets, version, model, prefix + "-b");
        var object = objects(basket, version).get(prefix + "-value");
        assertEquals("survey", child(object, "Marker").getTextContent());
        var wrapper = children(child(object, "Caption")).getFirst();
        var texts = children(wrapper).stream().flatMap(field -> children(field).stream())
                .collect(Collectors.toMap(entry -> child(entry, "Language").getTextContent(),
                        entry -> child(entry, "Text").getTextContent()));
        assertEquals(french == null ? Map.of("de", german) : Map.of("de", german, "fr", french), texts);
        assertEquals(xy(Integer.toString(x), Integer.toString(y)), coordinate(children(child(object, "Anchor")).getFirst(), version));
        var components = parts(object, model, "PinGroup", "Members", "PinPart", version);
        assertEquals(List.of(xy(Integer.toString(x), Integer.toString(y)), xy(Integer.toString(x + 10), Integer.toString(y + 10))),
                components.stream().map(part -> coordinate(children(child(part, "Position")).getFirst(), version)).toList());
    }
}
