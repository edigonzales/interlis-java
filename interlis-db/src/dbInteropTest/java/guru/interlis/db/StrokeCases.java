package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static guru.interlis.db.ChbaseFixtures.basket;
import static guru.interlis.db.ChbaseFixtures.fixture;
import static guru.interlis.db.ChbaseFixtures.withPrefix;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.emptyCollection;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.SpatialIndexFixtures.assertIndex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrokeCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    StrokeCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void multiPolyline(TestDatabase db) throws Exception {
        var model = "ChbaseLines" + version;
        fixture(work, model + ".ili");
        var input = chbaseDelivery(model, "fixture-lines-b");
        String direct = "StrokeMultiLine" + version;
        var output = work.resolve("output.xtf");
        if (version == 24) source(direct, "MULTIPOLYLINE");
        referenceSchema(db, version == 24 ? model + ";" + direct : model, "--coalesceMultiLine");
        referenceImport(db, "legacy", input);
        assertGeometryColumn(db, "piperoute", "shape", "MULTILINESTRING", 2056);
        assertGeometryColumn(db, "flowroute", "shape", "MULTILINESTRING", 2056);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertWrappedLines(output, "fixture-lines");
        assertCliSuccess(db, "import", model, "native", withPrefix(work, input, "native"));
        referenceExport(db, "native", output);
        assertWrappedLines(output, "native-lines");
        if (version != 24) return;

        var multi = geometry("multipolyline", polyline(coord(600000, 200000) + arc(600005, 200005, 600010, 200000))
                + polyline(coord(600020, 200020) + coord(600030, 200010)));
        referenceImport(db, "legacy-direct", dataset(direct, "legacy-direct", multi));
        assertGeometryColumn(db, "route", "shape", "MULTILINESTRING", 2056);
        assertCliSuccess(db, "export", direct, "legacy-direct", output);
        assertMultiLine(output, "legacy-direct");
        assertCliSuccess(db, "import", direct, "native-direct", dataset(direct, "native-direct", multi));
        referenceExport(db, "native-direct", output);
        assertMultiLine(output, "native-direct");
    }

    private void assertMultiLine(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        var geometry = children(child(objects.get(prefix + "-value"), "Shape")).getFirst();
        assertEquals("multipolyline", geometry.getLocalName());
        var lines = children(geometry).stream().map(this::positions).toList();
        assertEquals(2, lines.size());
        assertEquals(81, lines.getFirst().size());
        assertEquals(List.of(600000.0, 200000.0), lines.getFirst().getFirst());
        assertEquals(List.of(600005.0, 200005.0), lines.getFirst().get(40));
        assertEquals(List.of(600010.0, 200000.0), lines.getFirst().getLast());
        assertEquals(List.of(List.of(600020.0, 200020.0), List.of(600030.0, 200010.0)), lines.getLast());
    }

    private void assertWrappedLines(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        var lines = wrapperMembers(objects.get(prefix + "-value"), "MultiLine", "Lines", "Line").stream()
                .map(this::positions).toList();
        assertEquals(2, lines.size());
        assertEquals(82, lines.getFirst().size());
        assertEquals(List.of(2600000.0, 1200000.0), lines.getFirst().getFirst());
        assertEquals(List.of(2600005.0, 1200005.0), lines.getFirst().get(40));
        assertEquals(List.of(2600020.0, 1200000.0), lines.getFirst().getLast());
        assertEquals(List.of(List.of(2600100.0, 1200100.0), List.of(2600110.0, 1200120.0)), lines.getLast());
        var directed = wrapperMembers(objects.get(prefix + "-directed"), "MultiDirectedLine", "Lines", "Line");
        assertEquals(1, directed.size());
        assertEquals(List.of(List.of(2600210.0, 1200200.0), List.of(2600200.0, 1200200.0)), positions(directed.getFirst()));
    }

    void multiSurface(TestDatabase db) throws Exception {
        var model = "ChbaseSurfaces" + version;
        fixture(work, model + ".ili");
        var input = chbaseDelivery(model, "fixture-surfaces-b");
        var directModels = List.of("StrokeSurfaces", "StrokeAreas");
        if (version == 24) {
            source(directModels.getFirst(), "MULTISURFACE");
            source(directModels.getLast(), "MULTIAREA");
        }
        var output = work.resolve("output.xtf");
        referenceSchema(db, version == 24 ? model + ";" + String.join(";", directModels) : model, "--coalesceMultiSurface");
        referenceImport(db, "legacy", input);
        assertGeometryColumn(db, "landcover", "shape", "MULTIPOLYGON", 21781);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertWrappedSurfaces(output, "fixture-surfaces");
        assertCliSuccess(db, "import", model, "native", withPrefix(work, input, "native"));
        referenceExport(db, "native", output);
        assertWrappedSurfaces(output, "native-surfaces");
        if (version != 24) return;

        var first = surfaceValue(polyline(coord(600000, 200000) + arc(600005, 199995, 600010, 200000)
                + coord(600010, 200010) + coord(600000, 200000)),
                polyline(coord(600006, 200001) + coord(600008, 200003) + coord(600008, 200001) + coord(600006, 200001)));
        var second = surfaceValue(polyline(coord(600020, 200020) + coord(600030, 200020)
                + coord(600030, 200030) + coord(600020, 200020)));
        var shape = geometry("multisurface", first + second);
        for (var direct : directModels) {
            String legacy = direct + "-legacy";
            String nativeDataset = direct + "-native";
            referenceImport(db, legacy, dataset(direct, legacy, shape));
            assertCliSuccess(db, "export", direct, legacy, output);
            assertMultiSurface(output, legacy);
            assertCliSuccess(db, "import", direct, nativeDataset, dataset(direct, nativeDataset, shape));
            referenceExport(db, nativeDataset, output);
            assertMultiSurface(output, nativeDataset);
            try (var sql = db.connect()) {
                var table = scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname")
                        + " WHERE iliname='" + direct + ".Data.Route'");
                assertGeometryColumn(db, table, "shape", "MULTIPOLYGON", 2056);
                execute(sql, "UPDATE " + db.table(table) + " SET shape=" + emptyCollection(db, "MULTIPOLYGON", 6)
                        + " WHERE t_ili_tid='" + nativeDataset + "-null'");
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table(table)
                        + " WHERE t_ili_tid='" + nativeDataset + "-null' AND shape IS NOT NULL"));
            }
            assertCliSuccess(db, "export", direct, nativeDataset, output);
            assertMultiSurface(output, nativeDataset);
        }
    }

    private void assertWrappedSurfaces(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        var members = wrapperMembers(objects.get(prefix + "-value"), "MultiSurface", "Surfaces", "Surface");
        assertEquals(2, members.size());
        var first = surfaceRings(members.getFirst());
        assertEquals(2, first.size());
        assertTrue(first.getFirst().contains(List.of(600010.0, 199995.0)));
        assertEquals(List.of(600000.0, 200000.0), first.getFirst().getFirst());
        assertEquals(first.getFirst().getFirst(), first.getFirst().getLast());
        assertEquals(List.of(List.of(600005.0, 200005.0), List.of(600005.0, 200010.0),
                List.of(600010.0, 200010.0), List.of(600010.0, 200005.0), List.of(600005.0, 200005.0)), first.getLast());
        assertEquals(List.of(List.of(List.of(600100.0, 200100.0), List.of(600110.0, 200100.0),
                List.of(600110.0, 200110.0), List.of(600100.0, 200100.0))), surfaceRings(members.getLast()));
    }

    private void assertMultiSurface(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        var parts = children(children(child(objects.get(prefix + "-value"), "Shape")).getFirst());
        assertEquals(2, parts.size());
        var first = surfaceRings(parts.getFirst());
        assertEquals(2, first.size());
        assertEquals(83, first.getFirst().size());
        assertEquals(List.of(600005.0, 199995.0), first.getFirst().get(40));
        assertEquals(List.of(List.of(600006.0, 200001.0), List.of(600008.0, 200003.0),
                List.of(600008.0, 200001.0), List.of(600006.0, 200001.0)), first.getLast());
        assertEquals(List.of(List.of(List.of(600020.0, 200020.0), List.of(600030.0, 200020.0),
                List.of(600030.0, 200030.0), List.of(600020.0, 200020.0))), surfaceRings(parts.getLast()));
    }

    private List<Element> wrapperMembers(Element object, String wrapperName, String bag, String field) {
        var wrapper = children(child(object, "Shape")).getFirst();
        assertTrue(wrapper.getLocalName().equals(wrapperName) || wrapper.getLocalName().endsWith("." + wrapperName));
        return children(wrapper).stream().flatMap(element -> {
            assertEquals(bag, element.getLocalName());
            return children(element).stream();
        }).map(member -> children(child(member, field)).getFirst()).toList();
    }

    private Path chbaseDelivery(String model, String bid) throws Exception {
        Path combined = fixture(work, "ChbaseGeometry" + version + ".xtf");
        var data = readDataSection(combined, version);
        var baskets = children(data);
        var selected = basket(baskets, version, model, bid);
        for (var other : baskets) {
            if (other != selected) data.removeChild(other);
        }

        var transfer = data.getOwnerDocument().getDocumentElement();
        var header = child(transfer, version == 23 ? "HEADERSECTION" : "headersection");
        var models = child(header, version == 23 ? "MODELS" : "models");
        for (var entry : children(models)) {
            String name = version == 23 ? entry.getAttribute("NAME") : entry.getTextContent();
            if (!model.equals(name)) models.removeChild(entry);
        }

        Path output = work.resolve(model + ".xtf");
        TransformerFactory.newDefaultInstance().newTransformer()
                .transform(new DOMSource(data.getOwnerDocument()), new StreamResult(output.toFile()));
        return output;
    }

    private static void assertSameCatalog(TestDatabase baseline, TestDatabase own) throws Exception {
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
    }

    void smart2(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "StrokeSmart" + version;
        var ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN Vertex = COORD 400000.000 .. 900000.000, 0.000 .. 400000.000;
                  TOPIC Data =
                    CLASS AssetBase (ABSTRACT) =
                      Anchor : Vertex;
                      Shape : POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Vertex;
                    END AssetBase;
                    CLASS SurveyAsset EXTENDS AssetBase =
                      Marker : TEXT*20;
                    END SurveyAsset;
                  END Data;
                END %s.
                """.formatted(version - 20, model, model));
        var referenceOptions = new ArrayList<>(List.of("--smart2Inheritance", "--createGeomIdx"));
        var nativeOptions = new ArrayList<>(List.of("--inheritance", "smart2", "--stroke-arcs", "--create-geom-index"));
        if (own.postgis()) {
            referenceOptions.add("--oneGeomPerTable");
            nativeOptions.add("--one-geom-per-table");
        }
        referenceSchema(baseline, model, referenceOptions.toArray(String[]::new));
        var created = schemaWithIdentityColumns(own, model, ili, CancellationToken.none(), nativeOptions.toArray(String[]::new));
        assertEquals(0, created.code(), created::diagnostics);
        assertSameCatalog(baseline, own);
        var output = work.resolve("output.xtf");
        for (var db : List.of(baseline, own)) {
            assertGeometryColumn(db, "surveyasset_shape", "shape", "LINESTRING", 2056);
            referenceImport(db, "legacy", transfer(model, "legacy", surveyAsset(model, "legacy", 600000, "first")));
            assertCliSuccess(db, "export", model, "legacy", output);
            assertAsset(output, "legacy", 600000, "first");
            assertCliSuccess(db, "import", model, "native",
                    transfer(model, "native", surveyAsset(model, "native", 600200, "second")));
            referenceExport(db, "native", output);
            assertAsset(output, "native", 600200, "second");
            assertCliSuccess(db, "delete", model, "native", null);
            try (var sql = db.connect()) {
                assertIndex(db, sql, "surveyasset", "anchor");
                assertIndex(db, sql, "surveyasset_shape", "shape");
                var identity = "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text) FROM ";
                var asset = db.table("surveyasset");
                var shape = db.table("surveyasset_shape");
                var before = scalar(sql, identity + asset);
                assertNotNull(before);
                assertEquals(before, scalar(sql, identity + shape));
                assertSpatialHit(db, sql, 600005, true);

                assertCliSuccess(db, "update", model, "legacy",
                        transfer(model, "legacy", surveyAsset(model, "legacy", 600100, "updated")));
                assertEquals(before, scalar(sql, identity + asset));
                assertEquals(before, scalar(sql, identity + shape));
                assertSpatialHit(db, sql, 600005, false);
                assertSpatialHit(db, sql, 600105, true);
                referenceExport(db, "legacy", output);
                assertAsset(output, "legacy", 600100, "updated");

                assertCliSuccess(db, "delete", model, "legacy", null);
                for (var table : List.of("surveyasset", "surveyasset_shape", "t_ili2db_basket", "t_ili2db_dataset")) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
                }
                assertSpatialHit(db, sql, 600105, false);
                if (!db.postgis()) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_anchor"));
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_shape_shape"));
                }
            }
        }
    }

    private String surveyAsset(String model, String prefix, int x, String marker) {
        var line = polyline(coord(x, 200000) + arc(x + 5, 199995, x + 10, 200000));
        return object(model, "SurveyAsset", prefix + "-value",
                "<Anchor>" + coord(x, 200000) + "</Anchor><Shape>" + line + "</Shape><Marker>" + marker + "</Marker>");
    }

    private void assertAsset(Path output, String prefix, int x, String marker) throws Exception {
        var asset = objects(output, prefix).get(prefix + "-value");
        assertEquals(marker, child(asset, "Marker").getTextContent());
        var line = linePositions(asset);
        assertEquals(81, line.size());
        assertEquals(List.of((double) x, 200000.0), line.getFirst());
        assertEquals(List.of((double) x + 5, 199995.0), line.get(40));
        assertEquals(List.of((double) x + 10, 200000.0), line.getLast());
    }

    private static void assertSpatialHit(TestDatabase db, Connection sql, int x, boolean expected) throws Exception {
        if (!db.postgis()) {
            assertEquals(expected ? "1" : "0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_shape_shape"
                    + " WHERE minx <= " + x + " AND maxx >= " + x + " AND miny <= 199995 AND maxy >= 199994"));
            return;
        }
        String query = "SELECT count(*) FROM " + db.table("surveyasset_shape")
                + " WHERE shape && ST_MakeEnvelope(" + (x - 1) + ",199994," + (x + 1) + ",199995,2056)";
        assertEquals(expected ? "1" : "0", scalar(sql, query));
        var index = scalar(sql, "SELECT indexname FROM pg_indexes WHERE schemaname='" + db.schema()
                + "' AND tablename='surveyasset_shape' AND indexdef LIKE '%USING gist%'");
        try (var statement = sql.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            try (var rows = statement.executeQuery("EXPLAIN " + query)) {
                var plan = new ArrayList<String>();
                while (rows.next()) plan.add(rows.getString(1));
                assertTrue(plan.stream().anyMatch(line -> line.contains(" using " + index + " ") || line.endsWith(" on " + index)),
                        () -> String.join("\n", plan));
            }
        }
    }

    private List<List<List<Double>>> surfaceRings(Element surface) {
        return children(surface).stream().map(ring -> positions(children(ring).getFirst())).toList();
    }

    private String surfaceValue(String exterior, String... interiors) {
        String contents = geometry(version == 23 ? "boundary" : "exterior", exterior);
        for (String interior : interiors) contents += geometry(version == 23 ? "boundary" : "interior", interior);
        return geometry("surface", contents);
    }

    private List<List<Double>> linePositions(Element object) {
        return positions(children(child(object, "Shape")).getFirst());
    }

    private String route(String model, String tid, String shape) {
        return object(model, "Route", tid, "<Shape>" + shape + "</Shape>");
    }

    private Path source(String model, String kind) throws Exception {
        return source(model, kind, "000");
    }

    private Path source(String model, String kind, String precision) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%1$d;
                MODEL %2$s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN Vertex = COORD 400000.%3$s .. 900000.%3$s, 0.%3$s .. 400000.%3$s;
                  TOPIC Data =
                    CLASS Route =
                      Shape : %4$s WITH (STRAIGHTS, ARCS) VERTEX Vertex%5$s;
                    END Route;
                  END Data;
                END %2$s.
                """.formatted(version - 20, model, precision, kind,
                        kind.endsWith("LINE") ? "" : " WITHOUT OVERLAPS > 0.001"));
    }

    private Path dataset(String model, String prefix, String shape) throws Exception {
        return transfer(model, prefix, route(model, prefix + "-value", shape)
                + object(model, "Route", prefix + "-null", ""));
    }

    private Path transfer(String model, String prefix, String objects) throws Exception {
        var xml = referenceDatasetFixture(model, version, prefix + "-b", objects);
        if (version == 24) xml = xml.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(prefix + ".xtf"), xml);
    }

    private String object(String model, String name, String tid, String fields) {
        String tag = version == 23 ? model + ".Data." + name : name;
        return "<" + tag + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + fields + "</" + tag + ">";
    }

    private String polyline(String contents) { return geometry("polyline", contents); }

    private String coord(int x, int y) { return geometry("coord", geometry("c1", Integer.toString(x)) + geometry("c2", Integer.toString(y))); }

    private String arc(int ax, int ay, int x, int y) {
        String support = geometry("a1", Integer.toString(ax)) + geometry("a2", Integer.toString(ay));
        String end = geometry("c1", Integer.toString(x)) + geometry("c2", Integer.toString(y));
        return geometry("arc", version == 23 ? end + support : support + end);
    }

    private String geometry(String name, String contents) {
        String tag = version == 23 ? name.toUpperCase(java.util.Locale.ROOT) : "g:" + name;
        return "<" + tag + ">" + contents + "</" + tag + ">";
    }

    private List<List<Double>> positions(Element polyline) {
        String coord = version == 23 ? "COORD" : "coord";
        String axis = version == 23 ? "C" : "c";
        return children(polyline).stream().map(point -> {
            assertEquals(coord, point.getLocalName());
            return List.of(Double.valueOf(child(point, axis + "1").getTextContent()),
                    Double.valueOf(child(point, axis + "2").getTextContent()));
        }).toList();
    }

    private Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        return children(basket).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }

    private static void assertGeometryColumn(TestDatabase db, String table, String column, String type, int epsg) throws Exception {
        try (var sql = db.connect()) {
            String query = db.postgis()
                    ? "SELECT type || ':' || srid FROM geometry_columns WHERE f_table_schema='" + db.schema()
                        + "' AND f_table_name='" + table + "' AND f_geometry_column='" + column + "'"
                    : "SELECT geometry_type_name || ':' || srs_id FROM gpkg_geometry_columns WHERE table_name='" + table
                        + "' AND column_name='" + column + "'";
            assertEquals(type + ":" + epsg, scalar(sql, query));
        }
    }

    private void referenceSchema(TestDatabase db, String models, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--strokeArcs", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", models));
        options.addAll(List.of(flags));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }
}
