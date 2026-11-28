package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.CrsFixtures.assertStoredEpsg;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BaseGeometryFixtures {
    final String base;
    final String regional;
    final Path delivery;
    private final int version;
    private final CrsFixtures geometry;
    private final Path baseSource;
    private final Path regionalSource;

    BaseGeometryFixtures(Path work, int version) throws Exception {
        this.version = version;
        base = "Terrain" + version;
        regional = "RegionalTerrain" + version;
        baseSource = fixture(work, "base-geometry/" + base + ".ili");
        regionalSource = fixture(work, "base-geometry/" + regional + ".ili");
        delivery = fixture(work, "base-geometry/" + regional + ".xtf");
        geometry = new CrsFixtures(work, version);
    }

    void schema(Target db, boolean baseOnly) {
        if (baseOnly) {
            assertSchemaWithIdentityColumns(db, base, baseSource);
        } else {
            assertSchemaWithIdentityColumns(db, regional, regionalSource, "--model-file", baseSource.toString());
        }
    }

    void assertDelivery(Path output, boolean cleared) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(base), headerModels(data, version));
        assertEquals(1, children(data).size());
        var basket = basket(data, version, base + ".Data", "terrain-basket");
        assertEquals(3, children(basket).size());
        var straight = track(basket, "straight", !cleared);
        if (!cleared) {
            geometry.assertOrdinates(straight, 1, "2600100.125", "2600110.250");
            geometry.assertOrdinates(straight, 2, "1200100.375", "1200120.500");
            geometry.assertOrdinates(straight, 3, "20.125", "-11.250");
        }
        var curved = track(basket, "curved", true);
        geometry.assertOrdinates(curved, 1, "2600200.125", "2600240.125");
        geometry.assertOrdinates(curved, 2, "1200200.375", "1200200.375");
        geometry.assertOrdinates(curved, 3, "2.125", "30.125");
        var arcs = curved.getElementsByTagNameNS("*", version == 23 ? "ARC" : "arc");
        assertEquals(1, arcs.getLength());
        var arc = (Element) arcs.item(0);
        assertDecimal("2600220.125", child(arc, version == 23 ? "A1" : "a1"));
        assertDecimal("1200210.375", child(arc, version == 23 ? "A2" : "a2"));
        track(basket, "absent", false);
    }

    void assertStored(Target db) throws Exception {
        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "track", "trace", "straight", 2056);
            assertStoredEpsg(db, sql, "track", "trace", "curved", 2056);
            assertEquals(Set.of("absent"), catalogKeys(sql, "SELECT t_ili_tid FROM " + db.table("track") + " WHERE trace IS NULL"));
        }
    }

    private Element track(Element basket, String tid, boolean present) {
        var object = geometry.object(basket, tid);
        assertName(object, version, base + ".Data.Track");
        assertEquals(present ? 1 : 0, children(object).size(), tid);
        var trace = child(object, "Trace");
        if (present && version == 24) assertName(trace, version, base + ".Trace");
        return trace;
    }

    private static void assertDecimal(String expected, Element ordinate) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(ordinate.getTextContent())));
    }
}
