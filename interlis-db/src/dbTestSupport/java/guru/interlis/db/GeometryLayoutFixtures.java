package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GeometryLayoutFixtures {
    final String model;
    final Path source;
    private final Path work;
    private final int version;

    GeometryLayoutFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "DbGeometryLayout" + version;
        source = ModelFixtures.fixture(work, model + ".ili");
    }

    void schema(Target db, boolean split) {
        if (db.postgis() && split) {
            assertSchemaWithIdentityColumns(db, model, source, "--create-geom-index", "--one-geom-per-table");
        } else if (!db.postgis() && !split) {
            assertSchemaWithIdentityColumns(db, model, source, "--create-geom-index", "--gpkg-multi-geom-per-table");
        } else {
            assertSchemaWithIdentityColumns(db, model, source, "--create-geom-index");
        }
    }

    Path mixedInput() throws Exception {
        var xml = transfer("layout-basket", assets());
        var body = hierarchy();
        if (version == 23) {
            xml = xml.replace("</DATASECTION>", "<%1$s.Data BID=\"hierarchy-basket\">%2$s</%1$s.Data></DATASECTION>"
                    .formatted(model, body));
        } else {
            xml = xml.replace("</i:datasection>", "<Data i:bid=\"hierarchy-basket\">" + body + "</Data></i:datasection>");
        }
        return Files.writeString(work.resolve("geometry-mixed.xtf"), xml);
    }

    Path assetInput() throws Exception {
        return delivery("geometry-assets", "layout-basket", assets());
    }

    Path assetUpdate() throws Exception {
        return delivery("geometry-assets-update", "layout-basket", asset("a1", "2600400.500", null, true));
    }

    Path hierarchyInput() throws Exception {
        return delivery("geometry-hierarchy", "hierarchy-basket", hierarchy());
    }

    Path hierarchyUpdate() throws Exception {
        return delivery("geometry-hierarchy-update", "hierarchy-basket", recorder("2601400.125", "11.750", false));
    }

    Path independentInput() throws Exception {
        return delivery("geometry-independent", "other-basket", beacon("other"));
    }

    void assertMixed(Path output, boolean assetsUpdated) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(model), headerModels(data, version));
        assertEquals(2, children(data).size());
        assertAssets(basket(data, version, model + ".Data", "layout-basket"), assetsUpdated);
        assertHierarchy(basket(data, version, model + ".Data", "hierarchy-basket"), false);
    }

    void assertAssetDelivery(Path output, boolean updated) throws Exception {
        assertAssets(singleBasket(output, "layout-basket"), updated);
    }

    void assertHierarchyDelivery(Path output, boolean updated) throws Exception {
        assertHierarchy(singleBasket(output, "hierarchy-basket"), updated);
    }

    void assertIndependentDelivery(Path output) throws Exception {
        var basket = singleBasket(output, "other-basket");
        assertEquals(1, children(basket).size());
        assertBeacon(object(basket, "Beacon", "other"));
    }

    private Element singleBasket(Path output, String bid) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(model), headerModels(data, version));
        assertEquals(1, children(data).size());
        return basket(data, version, model + ".Data", bid);
    }

    private void assertAssets(Element basket, boolean updated) {
        assertEquals(updated ? 1 : 2, children(basket).size());
        var first = object(basket, "Asset", "a1");
        assertPosition(first, "Anchor", updated ? "2600400.500" : "2600100.125", "1200200.375");
        assertLine(first, "Trace", List.of("2600600.125", "1200600.375"), List.of("2600620.250", "1200640.500"));
        if (updated) {
            assertNull(child(first, "Surveyed"));
        } else {
            assertPosition(first, "Surveyed", "2600200.125", "1200300.375", "-40.125");
            var second = object(basket, "Asset", "a2");
            assertNull(child(second, "Anchor"));
            assertNull(child(second, "Trace"));
            assertPosition(second, "Surveyed", "2600200.125", "1200300.375", "90.25");
        }
    }

    private void assertHierarchy(Element basket, boolean updated) {
        assertEquals(updated ? 1 : 2, children(basket).size());
        var recorder = object(basket, "Recorder", "rec");
        assertPosition(recorder, "Origin", "2600700.125", "1200700.375", updated ? "11.750" : "-81.125");
        assertLine(recorder, "Track", List.of(updated ? "2601400.125" : "2601000.125", "1201000.375", "100"),
                List.of("2601100.375", "1201020.500", "-32"));
        if (updated) {
            assertNull(child(recorder, "Peg"));
        } else {
            assertPosition(recorder, "Peg", "2600800.125", "1200800.375");
            assertBeacon(object(basket, "Beacon", "beacon"));
        }
    }

    private static void assertBeacon(Element beacon) {
        assertNull(child(beacon, "Track"));
        assertPosition(beacon, "Origin", "2600900.125", "1200900.375", "14.250");
        assertPosition(beacon, "Signal", "2600910.125", "1200910.375");
    }

    private Element object(Element basket, String type, String tid) {
        var object = children(basket).stream().filter(element -> tid.equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertName(object, version, model + ".Data." + type);
        return object;
    }

    private static void assertPosition(Element object, String field, String... expected) {
        assertOrdinates(children(child(object, field)).getFirst(), List.of(expected));
    }

    private static void assertLine(Element object, String field, List<String> start, List<String> end) {
        var positions = children(children(child(object, field)).getFirst());
        assertEquals(2, positions.size(), field);
        assertOrdinates(positions.getFirst(), start);
        assertOrdinates(positions.getLast(), end);
    }

    private static void assertOrdinates(Element point, List<String> expected) {
        var axes = children(point);
        assertEquals(expected.size(), axes.size(), point.getLocalName());
        for (int axis = 0; axis < expected.size(); axis++) {
            assertEquals(0, new BigDecimal(expected.get(axis)).compareTo(new BigDecimal(axes.get(axis).getTextContent())));
        }
    }

    private Path delivery(String name, String bid, String objects) throws Exception {
        return Files.writeString(work.resolve(name + ".xtf"), transfer(bid, objects));
    }

    private String transfer(String bid, String objects) {
        String xml = referenceDatasetFixture(model, version, bid, objects);
        return version == 23 ? xml : xml.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
    }

    private String assets() {
        return asset("a1", "2600100.125", "-40.125", true) + asset("a2", null, "90.25", false);
    }

    private String hierarchy() {
        return recorder("2601000.125", "-81.125", true) + beacon("beacon");
    }

    private String asset(String tid, String x, String z, boolean trace) {
        String body = x == null ? "" : "<Anchor>" + position(x, "1200200.375", null) + "</Anchor>";
        if (z != null) body += "<Surveyed>" + position("2600200.125", "1200300.375", z) + "</Surveyed>";
        if (trace) {
            body += "<Trace>" + line(position("2600600.125", "1200600.375", null)
                    + position("2600620.250", "1200640.500", null)) + "</Trace>";
        }
        return object("Asset", tid, body);
    }

    private String recorder(String trackStart, String originZ, boolean peg) {
        String origin = "<Origin>" + position("2600700.125", "1200700.375", originZ) + "</Origin>";
        String pegField = peg ? "<Peg>" + position("2600800.125", "1200800.375", null) + "</Peg>" : "";
        String track = "<Track>" + line(position(trackStart, "1201000.375", "100")
                + position("2601100.375", "1201020.500", "-32")) + "</Track>";
        return object("Recorder", "rec", origin + pegField + track);
    }

    private String beacon(String tid) {
        return object("Beacon", tid, "<Origin>" + position("2600900.125", "1200900.375", "14.250")
                + "</Origin><Signal>" + position("2600910.125", "1200910.375", null) + "</Signal>");
    }

    private String line(String positions) {
        String tag = version == 23 ? "POLYLINE" : "g:polyline";
        return "<" + tag + ">" + positions + "</" + tag + ">";
    }

    private String object(String type, String tid, String body) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, body)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, body);
    }

    private String position(String x, String y, String z) {
        return version == 23 ? "<COORD><C1>" + x + "</C1><C2>" + y + "</C2>"
                + (z == null ? "" : "<C3>" + z + "</C3>") + "</COORD>"
                : "<g:coord><g:c1>" + x + "</g:c1><g:c2>" + y + "</g:c2>"
                + (z == null ? "" : "<g:c3>" + z + "</g:c3>") + "</g:coord>";
    }
}
