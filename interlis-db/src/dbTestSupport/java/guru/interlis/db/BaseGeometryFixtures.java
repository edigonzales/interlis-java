package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals(version == 24 ? 15 : 7, children(basket).size());
        assertTracks(basket, cleared);
        assertSurfaces(basket);
        if (version == 24) assertCollections(basket);
    }

    private void assertTracks(Element basket, boolean cleared) {
        var straight = shape(basket, "Track", "straight", "Trace", !cleared);
        if (!cleared) {
            geometry.assertOrdinates(straight, 1, "2600100.125", "2600110.250");
            geometry.assertOrdinates(straight, 2, "1200100.375", "1200120.500");
            geometry.assertOrdinates(straight, 3, "20.125", "-11.250");
        }
        var curved = shape(basket, "Track", "curved", "Trace", true);
        geometry.assertOrdinates(curved, 1, "2600200.125", "2600240.125");
        geometry.assertOrdinates(curved, 2, "1200200.375", "1200200.375");
        geometry.assertOrdinates(curved, 3, "2.125", "30.125");
        assertArc(curved, "2600220.125", "1200210.375");
        shape(basket, "Track", "absent", "Trace", false);
    }

    private void assertSurfaces(Element basket) {
        var plot = shape(basket, "Plot", "plot", "Outline", true);
        var rings = children(children(plot).getFirst());
        assertEquals(2, rings.size());
        assertEquals(version == 23 ? "BOUNDARY" : "exterior", rings.getFirst().getLocalName());
        assertEquals(version == 23 ? "BOUNDARY" : "interior", rings.getLast().getLocalName());
        geometry.assertOrdinates(rings.getFirst(), 1, "2601000.125", "2601040.125", "2601040.125", "2601000.125", "2601000.125");
        geometry.assertOrdinates(rings.getFirst(), 3, "1", "2", "3", "4", "1");
        geometry.assertOrdinates(rings.getLast(), 2, "1201008.375", "1201020.375", "1201020.375", "1201008.375", "1201008.375");
        geometry.assertOrdinates(rings.getLast(), 3, "11", "12", "13", "14", "11");
        assertArc(rings.getFirst(), "2601020.125", "1200995.375");
        shape(basket, "Plot", "empty-plot", "Outline", false);

        var parcel = shape(basket, "Parcel", "parcel", "Boundary", true);
        assertEquals(1, children(children(parcel).getFirst()).size());
        geometry.assertOrdinates(parcel, 1, "2602000.125", "2602040.125", "2602040.125", "2602000.125", "2602000.125");
        geometry.assertOrdinates(parcel, 2, "1202000.375", "1202000.375", "1202040.375", "1202040.375", "1202000.375");
        geometry.assertOrdinates(parcel, 3, "21", "22", "23", "24", "21");
        shape(basket, "Parcel", "empty-parcel", "Boundary", false);
    }

    private void assertCollections(Element basket) {
        var survey = shape(basket, "Survey", "survey", "Locations", true);
        members(survey, "multicoord", "coord");
        geometry.assertOrdinates(survey, 1, "2603000.125", "2603010.125");
        geometry.assertOrdinates(survey, 2, "1203000.375", "1203020.375");
        geometry.assertOrdinates(survey, 3, "31", "32");
        shape(basket, "Survey", "empty-survey", "Locations", false);

        var network = shape(basket, "Network", "network", "Routes", true);
        var lines = members(network, "multipolyline", "polyline");
        geometry.assertOrdinates(lines.getFirst(), 1, "2604000.125", "2604020.125");
        geometry.assertOrdinates(lines.getFirst(), 3, "41", "42");
        geometry.assertOrdinates(lines.getLast(), 1, "2604100.125", "2604140.125");
        geometry.assertOrdinates(lines.getLast(), 3, "43", "44");
        assertArc(lines.getLast(), "2604120.125", "1204110.375");
        shape(basket, "Network", "empty-network", "Routes", false);

        var park = shape(basket, "Park", "park", "Grounds", true);
        var plots = members(park, "multisurface", "surface");
        geometry.assertOrdinates(plots.getFirst(), 3, "51", "52", "53", "54", "51");
        geometry.assertOrdinates(plots.getLast(), 3, "55", "56", "57", "58", "55");
        shape(basket, "Park", "empty-park", "Grounds", false);

        var district = shape(basket, "District", "district", "Territory", true);
        var parcels = members(district, "multisurface", "surface");
        geometry.assertOrdinates(parcels.getFirst(), 3, "61", "62", "63", "64", "61");
        geometry.assertOrdinates(parcels.getLast(), 3, "65", "66", "67", "68", "65");
        shape(basket, "District", "empty-district", "Territory", false);
    }

    private static List<Element> members(Element field, String collectionName, String memberName) {
        var collection = children(field).getFirst();
        assertEquals(collectionName, collection.getLocalName());
        var members = children(collection);
        assertEquals(List.of(memberName, memberName), members.stream().map(Element::getLocalName).toList());
        return members;
    }

    private void assertArc(Element geometry, String x, String y) {
        var arcs = geometry.getElementsByTagNameNS("*", version == 23 ? "ARC" : "arc");
        assertEquals(1, arcs.getLength());
        var arc = (Element) arcs.item(0);
        assertDecimal(x, child(arc, version == 23 ? "A1" : "a1"));
        assertDecimal(y, child(arc, version == 23 ? "A2" : "a2"));
    }

    void assertStored(Target db) throws Exception {
        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "track", "trace", "straight", 2056);
            assertStoredEpsg(db, sql, "track", "trace", "curved", 2056);
            assertNullGeometry(db, sql, "track", "trace", "absent");
            assertStoredEpsg(db, sql, "plot", "outline", "plot", 2056);
            assertNullGeometry(db, sql, "plot", "outline", "empty-plot");
            assertStoredEpsg(db, sql, "parcel", "boundary", "parcel", 2056);
            assertNullGeometry(db, sql, "parcel", "boundary", "empty-parcel");
            if (version == 24) {
                assertStoredEpsg(db, sql, "survey", "locations", "survey", 2056);
                assertNullGeometry(db, sql, "survey", "locations", "empty-survey");
                assertStoredEpsg(db, sql, "network", "routes", "network", 2056);
                assertNullGeometry(db, sql, "network", "routes", "empty-network");
                assertStoredEpsg(db, sql, "park", "grounds", "park", 2056);
                assertNullGeometry(db, sql, "park", "grounds", "empty-park");
                assertStoredEpsg(db, sql, "district", "territory", "district", 2056);
                assertNullGeometry(db, sql, "district", "territory", "empty-district");
            }
        }
    }

    private static void assertNullGeometry(Target db, Connection sql, String table, String field, String tid)
            throws Exception {
        assertEquals(Set.of(tid), catalogKeys(sql, "SELECT t_ili_tid FROM " + db.table(table) + " WHERE " + field + " IS NULL"));
    }

    private Element shape(Element basket, String type, String tid, String field, boolean present) {
        var object = geometry.object(basket, tid);
        assertName(object, version, base + ".Data." + type);
        assertEquals(present ? 1 : 0, children(object).size(), tid);
        var value = child(object, field);
        if (present) {
            assertNotNull(value, tid + "." + field);
            if (version == 24) assertName(value, version, base + "." + field);
        } else {
            assertNull(value);
        }
        return value;
    }

    private static void assertDecimal(String expected, Element ordinate) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(ordinate.getTextContent())));
    }
}
