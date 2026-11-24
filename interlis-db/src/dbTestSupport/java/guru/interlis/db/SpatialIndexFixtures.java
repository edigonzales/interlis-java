package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpatialIndexFixtures {
    private final Path work;
    private final int version;

    SpatialIndexFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path copy(String name) throws Exception {
        Path file = work.resolve(name);
        try (var source = SpatialIndexFixtures.class.getResourceAsStream("/" + name)) {
            Files.copy(Objects.requireNonNull(source, name), file);
        }
        return file;
    }

    String straight(String model) {
        String line = object(model, "IndexedLine", "line", "<Trace>" + polyline(
                coordinate("2601000.125", "1201000.375") + coordinate("2601020.250", "1201010.500")) + "</Trace>");
        String shell = box("2601050.125", "2601080.250", "1201050.250", "1201080.500");
        String hole = box("2601060.125", "2601065.250", "1201060.250", "1201065.500");
        String plot = object(model, "IndexedPlot", "plot", "<Footprint>" + surface(shell, hole) + "</Footprint>");
        String area = object(model, "IndexedArea", "area", "<Boundary>" + surface(
                box("2601100.125", "2601120.250", "1201100.250", "1201120.500"), "") + "</Boundary>");
        return line + plot + area;
    }

    String curves(String model) {
        // Radius 100 about (2602300, 1202300). The east extreme is beyond the three positions.
        String start = coordinate("2602360", "1202220");
        String arc = arc("2602380", "1202360", "2602240", "1202380");
        String line = object(model, "IndexedLine", "arc-line", "<Trace>" + polyline(start + arc) + "</Trace>");
        String plot = object(model, "IndexedPlot", "arc-plot", "<Footprint>"
                + surface(polyline(start + arc + start), "") + "</Footprint>");
        return line + plot;
    }

    static void assertCollectionOrder(Path path) throws Exception {
        var baskets = children(readDataSection(path, 24));
        var first = baskets.stream().filter(b -> "legacy-basket-1".equals(id(b, 24, "bid"))).findFirst().orElseThrow();
        assertParts(first, "Survey", "Locations", List.of("2601500.625", "2601100.125"));
        assertParts(first, "RouteGroup", "Traces", List.of("2600123.125", "2600600.500"));
        assertParts(first, "Park", "Coverage", List.of("2601000.125", "2601200.250"));
        assertParts(first, "District", "Coverage", List.of("2601000.125", "2601200.250"));
    }

    private static void assertParts(org.w3c.dom.Element basket, String tag, String field, List<String> expected) {
        var elements = children(children(child(child(basket, tag), field)).getFirst());
        assertEquals(expected.size(), elements.size());
        for (int i = 0; i < expected.size(); i++) {
            var text = elements.get(i).getElementsByTagNameNS("*", "c1").item(0).getTextContent();
            assertEquals(0, new BigDecimal(expected.get(i)).compareTo(new BigDecimal(text)));
        }
    }

    void assertCurvesInXtf(Path output) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        var arcs = basket.getElementsByTagNameNS("*", version == 23 ? "ARC" : "arc");
        assertEquals(2, arcs.getLength());
        for (int i = 0; i < arcs.getLength(); i++) {
            var arc = (org.w3c.dom.Element) arcs.item(i);
            assertEquals(0, new BigDecimal("2602380").compareTo(
                    new BigDecimal(child(arc, version == 23 ? "A1" : "a1").getTextContent())));
        }
    }

    private String arc(String supportX, String supportY, String endX, String endY) {
        return version == 23 ? """
                <ARC><C1>%3$s</C1><C2>%4$s</C2><A1>%1$s</A1><A2>%2$s</A2></ARC>
                """.formatted(supportX, supportY, endX, endY)
                : """
                <g:arc><g:c1>%3$s</g:c1><g:c2>%4$s</g:c2><g:a1>%1$s</g:a1><g:a2>%2$s</g:a2></g:arc>
                """.formatted(supportX, supportY, endX, endY);
    }

    private String box(String xmin, String xmax, String ymin, String ymax) {
        return polyline(coordinate(xmin, ymin) + coordinate(xmax, ymin) + coordinate(xmax, ymax)
                + coordinate(xmin, ymax) + coordinate(xmin, ymin));
    }

    private String surface(String shell, String hole) {
        if (version == 23) return "<SURFACE><BOUNDARY>" + shell + "</BOUNDARY>"
                + (hole.isEmpty() ? "" : "<BOUNDARY>" + hole + "</BOUNDARY>") + "</SURFACE>";
        return "<g:surface><g:exterior>" + shell + "</g:exterior>"
                + (hole.isEmpty() ? "" : "<g:interior>" + hole + "</g:interior>") + "</g:surface>";
    }

    private String coordinate(String x, String y) {
        return version == 23 ? "<COORD><C1>%s</C1><C2>%s</C2></COORD>".formatted(x, y)
                : "<g:coord><g:c1>%s</g:c1><g:c2>%s</g:c2></g:coord>".formatted(x, y);
    }

    private String polyline(String parts) {
        String tag = version == 23 ? "POLYLINE" : "g:polyline";
        return "<" + tag + ">" + parts + "</" + tag + ">";
    }

    static void assertGeoPackageHeader(Target db, Connection sql, String table, String field) throws SQLException {
        if (db.postgis()) return;
        try (var statement = sql.createStatement(); var rows = statement.executeQuery("SELECT " + field + " FROM " + table)) {
            assertTrue(rows.next());
            do {
                assertEquals(1, (rows.getBytes(1)[3] >>> 1) & 7, table + "." + field);
            } while (rows.next());
        }
    }

    static void assertSearch(Target db, Connection sql, String table, String field, String x, String y,
                                     boolean expected) throws SQLException {
        String query = db.postgis()
                ? "SELECT count(*) FROM " + db.table(table) + " WHERE " + field
                  + " && ST_MakeEnvelope(" + x + "," + y + "," + x + "," + y + ",2056)"
                : "SELECT count(*) FROM rtree_" + table + "_" + field
                  + " WHERE minx <= " + x + " AND maxx >= " + x + " AND miny <= " + y + " AND maxy >= " + y;
        assertEquals(expected ? "1" : "0", scalar(sql, query), table + "." + field);
    }

    Path delivery(String name, String model, String bid, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, bid, objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(name + ".xtf"), text);
    }

    String feature(String model, String type, String tid, String x) {
        String geometry = x == null ? "" : version == 23
                ? "<Center><COORD><C1>%s</C1><C2>1200000.375</C2></COORD></Center>".formatted(x)
                : "<Center><g:coord><g:c1>%s</g:c1><g:c2>1200000.375</g:c2></g:coord></Center>".formatted(x);
        return object(model, type, tid, geometry);
    }

    String object(String model, String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    static void assertIndex(Target db, Connection sql, String table, String field) throws SQLException {
        String query = db.postgis()
                ? "SELECT count(*) FROM pg_indexes WHERE schemaname='" + db.schema() + "' AND tablename='" + table
                   + "' AND indexdef LIKE '%USING gist%' AND indexdef LIKE '%(" + field + ")%'"
                : "SELECT count(*) FROM gpkg_extensions WHERE table_name='" + table + "' AND column_name='" + field
                   + "' AND extension_name='gpkg_rtree_index' AND scope='write-only'";
        assertEquals("1", scalar(sql, query));
    }

}
