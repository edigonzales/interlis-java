package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpatialIndexCases {
    private final Path work;
    private final int version;
    private final ReferenceTool reference;

    SpatialIndexCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        this.reference = reference;
    }

    void points(TestDatabase db) throws Exception {
        String model = "DbMutGeom" + version;
        createReferenceSchema(db, model);
        Path start = delivery("indexed-points-original", model, "points",
                feature(model, "GeoLeaf", "moved", "2600100.125")
                + feature(model, "GeoLeaf", "cleared", "2600200.250")
                + feature(model, "GeoLeaf", "dropped", "2600300.375"));
        Path legacyUpdate = delivery("indexed-points-legacy", model, "points",
                feature(model, "GeoLeaf", "moved", "2600400.500")
                + feature(model, "GeoLeaf", "cleared", "2600200.250")
                + feature(model, "GeoLeaf", "dropped", "2600300.375"));
        Path update = delivery("indexed-points-update", model, "points",
                feature(model, "GeoLeaf", "moved", "2600500.625") + feature(model, "GeoLeaf", "cleared", null));
        Path replacement = delivery("indexed-points-replace", model, "points",
                feature(model, "GeoLeaf", "moved", "2600600.750"));

        assertCliSuccess(db, "import", model, "points", start);
        try (var sql = db.connect()) {
            assertIndex(db, sql, "georoot", "center");
            String object = scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'");
            assertPointHit(db, sql, "moved", "2600100.125", true);
            Path referenceExport = work.resolve("indexed-points-reference.xtf");
            reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "points", referenceExport.toString());
            assertPointXtf(referenceExport, "moved", "2600100.125");

            runReference(db, "update", "points", legacyUpdate);
            Path nativeExport = work.resolve("indexed-points-native.xtf");
            assertCliSuccess(db, "export", model, "points", nativeExport);
            assertPointXtf(nativeExport, "moved", "2600400.500");
            assertPointHit(db, sql, "moved", "2600400.500", true);
            assertPointHit(db, sql, "moved", "2600100.125", false);

            assertCliSuccess(db, "update", model, "points", update);
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'"));
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600500.625", true);
            assertPointHit(db, sql, "moved", "2600400.500", false);
            assertPointHit(db, sql, "cleared", "2600200.250", false);
            assertPointHit(db, sql, "dropped", "2600300.375", false);

            Path malformed = Files.writeString(work.resolve("indexed-points-rollback.xtf"),
                    Files.readString(replacement) + "<trailing/>");
            assertNotEquals(0, cli(db, "replace", model, "points", malformed, CancellationToken.none()).code());
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600500.625", true);
            assertPointHit(db, sql, "moved", "2600600.750", false);
            assertCliSuccess(db, "replace", model, "points", replacement);
            assertNotEquals(object, scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'"));
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600600.750", true);
            assertPointHit(db, sql, "moved", "2600500.625", false);

            if (!db.postgis()) {
                String trigger = scalar(sql, "SELECT sql FROM sqlite_schema WHERE name='rtree_georoot_center_update2'");
                execute(sql, "DROP TRIGGER rtree_georoot_center_update2");
                var failed = cli(db, "update", model, "points", update, CancellationToken.none());
                assertNotEquals(0, failed.code(), failed::diagnostics);
                assertTrue(failed.diagnostics().contains("missing index maintenance trigger"), failed::diagnostics);
                assertPointHit(db, sql, "moved", "2600600.750", true);
                execute(sql, trigger);
            }

            assertCliSuccess(db, "delete", model, "points", null);
            assertIndexRows(db, sql, 0);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("georoot")));
            runReference(db, "import", "tail", start);
            assertIndexRows(db, sql, 3);
            reference.run(db, work.toString(), "--delete", "--dataset", "tail");
            assertIndexRows(db, sql, 0);
        }
    }

    void straightGeometries(TestDatabase db) throws Exception {
        String model = "DbSpatial" + version;
        createReferenceSchema(db, model);
        String line = object(model, "IndexedLine", "line", "<Trace>" + polyline(
                coordinate("2601000.125", "1201000.375") + coordinate("2601020.250", "1201010.500")) + "</Trace>");
        String shell = box("2601050.125", "2601080.250", "1201050.250", "1201080.500");
        String hole = box("2601060.125", "2601065.250", "1201060.250", "1201065.500");
        String plot = object(model, "IndexedPlot", "plot", "<Footprint>" + surface(shell, hole) + "</Footprint>");
        String area = object(model, "IndexedArea", "area", "<Boundary>" + surface(
                box("2601100.125", "2601120.250", "1201100.250", "1201120.500"), "") + "</Boundary>");
        Path input = delivery("indexed-straight", model, "geometries", line + plot + area);
        Path update = delivery("indexed-straight-update", model, "geometries",
                line.replace("2601000.125", "2601200.125").replace("2601020.250", "2601220.250") + plot + area);
        assertCliSuccess(db, "import", model, "straight", input);
        try (var sql = db.connect()) {
            assertIndex(db, sql, "indexedline", "trace");
            assertIndex(db, sql, "indexedplot", "footprint");
            assertIndex(db, sql, "indexedarea", "boundary");
            assertSearch(db, sql, "indexedline", "trace", "2601005", "1201005", true);
            assertSearch(db, sql, "indexedplot", "footprint", "2601062", "1201062", true);
            assertSearch(db, sql, "indexedarea", "boundary", "2601110", "1201110", true);
            assertGeoPackageHeader(db, sql, "indexedplot", "footprint");
            assertGeoPackageHeader(db, sql, "indexedarea", "boundary");
            Path exported = work.resolve("indexed-straight-reference.xtf");
            reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "straight", exported.toString());
            var objects = children(children(readDataSection(exported, version)).getFirst());
            assertEquals(3, objects.size());
            var surface = objects.stream().filter(o -> "plot".equals(id(o, version, "tid"))).findFirst().orElseThrow();
            assertEquals(2, children(children(child(surface, "Footprint")).getFirst()).size());

            runReference(db, "update", "straight", update);
            assertSearch(db, sql, "indexedline", "trace", "2601005", "1201005", false);
            assertSearch(db, sql, "indexedline", "trace", "2601205", "1201005", true);
            Path nativeOutput = work.resolve("indexed-straight-native.xtf");
            assertCliSuccess(db, "export", model, "straight", nativeOutput);
            assertEquals(3, children(children(readDataSection(nativeOutput, version)).getFirst()).size());
        }
    }

    void circularGeometries(TestDatabase db) throws Exception {
        String model = "DbSpatial" + version;
        createReferenceSchema(db, model);
        // Radius 100 about (2602300, 1202300). The east extreme is beyond the three positions.
        String start = coordinate("2602360", "1202220");
        String arc = arc("2602380", "1202360", "2602240", "1202380");
        String line = object(model, "IndexedLine", "arc-line", "<Trace>" + polyline(start + arc) + "</Trace>");
        String plot = object(model, "IndexedPlot", "arc-plot", "<Footprint>"
                + surface(polyline(start + arc + start), "") + "</Footprint>");
        Path input = delivery("indexed-arcs", model, "arcs", line + plot);
        assertCliSuccess(db, "import", model, "curves", input);
        try (var sql = db.connect()) {
            assertGeoPackageHeader(db, sql, "indexedline", "trace");
            assertGeoPackageHeader(db, sql, "indexedplot", "footprint");
            assertSearch(db, sql, "indexedline", "trace", "2602399", "1202300", true);
            assertSearch(db, sql, "indexedplot", "footprint", "2602399", "1202300", true);
            assertSearch(db, sql, "indexedline", "trace", "2602405", "1202300", false);

            Path exported = work.resolve("indexed-arcs-reference.xtf");
            reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "curves", exported.toString());
            assertCurvesInXtf(exported);
            runReference(db, "import", "curves-reference", input);
            Path ownExport = work.resolve("indexed-arcs-native.xtf");
            assertCliSuccess(db, "export", model, "curves-reference", ownExport);
            assertCurvesInXtf(ownExport);
            assertCliSuccess(db, "delete", model, "curves-reference", null);
            assertSearch(db, sql, "indexedline", "trace", "2602399", "1202300", true);
        }
    }

    private void assertCurvesInXtf(Path output) throws Exception {
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

    private static void assertGeoPackageHeader(TestDatabase db, Connection sql, String table, String field) throws SQLException {
        if (db.postgis()) return;
        try (var statement = sql.createStatement(); var rows = statement.executeQuery("SELECT " + field + " FROM " + table)) {
            assertTrue(rows.next());
            assertEquals(1, (rows.getBytes(1)[3] >>> 1) & 7);
        }
    }

    private static void assertSearch(TestDatabase db, Connection sql, String table, String field, String x, String y,
                                     boolean expected) throws SQLException {
        String query = db.postgis()
                ? "SELECT count(*) FROM " + db.table(table) + " WHERE " + field
                  + " && ST_MakeEnvelope(" + x + "," + y + "," + x + "," + y + ",2056)"
                : "SELECT count(*) FROM rtree_" + table + "_" + field
                  + " WHERE minx <= " + x + " AND maxx >= " + x + " AND miny <= " + y + " AND maxy >= " + y;
        assertEquals(expected ? "1" : "0", scalar(sql, query), table + "." + field);
    }

    private void createReferenceSchema(TestDatabase db, String model) throws Exception {
        Path ili = work.resolve(model + ".ili");
        if (Files.notExists(ili)) {
            try (var resource = SpatialIndexCases.class.getResourceAsStream("/" + ili.getFileName())) {
                Files.copy(resource, ili);
            }
        }
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--createGeomIdx", ili.toString());
    }

    private void runReference(TestDatabase db, String operation, String dataset, Path path) throws Exception {
        reference.run(db, work.toString(), "--" + operation, "--dataset", dataset, "--importTid", "--importBid",
                path.toString());
    }

    private Path delivery(String name, String model, String bid, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, bid, objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(name + ".xtf"), text);
    }

    private String feature(String model, String type, String tid, String x) {
        String geometry = x == null ? "" : version == 23
                ? "<Center><COORD><C1>%s</C1><C2>1200000.375</C2></COORD></Center>".formatted(x)
                : "<Center><g:coord><g:c1>%s</g:c1><g:c2>1200000.375</g:c2></g:coord></Center>".formatted(x);
        return object(model, type, tid, geometry);
    }

    private String object(String model, String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private void assertPointXtf(Path path, String tid, String x) throws Exception {
        var objects = children(children(readDataSection(path, version)).getFirst());
        var point = objects.stream().filter(o -> tid.equals(id(o, version, "tid"))).findFirst().orElseThrow();
        var actual = children(children(child(point, "Center")).getFirst()).getFirst().getTextContent();
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(actual)));
    }

    private static void assertIndex(TestDatabase db, Connection sql, String table, String field) throws SQLException {
        String query = db.postgis()
                ? "SELECT count(*) FROM pg_indexes WHERE schemaname='" + db.schema() + "' AND tablename='" + table
                   + "' AND indexdef LIKE '%USING gist%' AND indexdef LIKE '%(" + field + ")%'"
                : "SELECT count(*) FROM gpkg_extensions WHERE table_name='" + table + "' AND column_name='" + field
                   + "' AND extension_name='gpkg_rtree_index' AND scope='write-only'";
        assertEquals("1", scalar(sql, query));
    }

    private static void assertIndexRows(TestDatabase db, Connection sql, int expected) throws SQLException {
        String query = db.postgis() ? "SELECT count(*) FROM " + db.table("georoot") + " WHERE center IS NOT NULL"
                : "SELECT count(*) FROM rtree_georoot_center";
        assertEquals(Integer.toString(expected), scalar(sql, query));
    }

    private static void assertPointHit(TestDatabase db, Connection sql, String tid, String x, boolean expected) throws SQLException {
        String table = db.table("georoot");
        String query = db.postgis() ? "SELECT count(*) FROM " + table + " WHERE t_ili_tid='" + tid
                + "' AND center && ST_MakeEnvelope(" + x + ",1200000," + x + ",1200001,2056)"
                : "SELECT count(*) FROM " + table + " p JOIN rtree_georoot_center idx ON p.t_id=idx.id"
                + " WHERE p.t_ili_tid='" + tid + "' AND idx.minx <= " + x + " AND idx.maxx >= " + x
                + " AND idx.miny <= 1200000.375 AND idx.maxy >= 1200000.375";
        assertEquals(expected ? "1" : "0", scalar(sql, query));
    }
}
