package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbInteropTest.schemaWithIdentityColumns;
import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static guru.interlis.db.SpatialIndexCases.assertIndex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseGeometryCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ChbaseGeometryCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void points(TestDatabase db) throws Exception {
        String model = "ChbasePoints" + version;
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        referenceSchema(db, model, "--coalesceMultiPoint");
        referenceImport(db, "legacy", input);
        var output = work.resolve("points-output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertPoints(output, model, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertPoints(output, model, "native");

        try (var sql = db.connect()) {
            assertGeometryColumn(db, "pinset", "shape", "MULTIPOINT", 2056);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("pingroup")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("pinpart")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("pinset") + " WHERE shape IS NULL"));
            if (db.postgis()) assertEquals("3", scalar(sql, "SELECT ST_NumGeometries(shape) FROM "
                    + db.table("pinset") + " WHERE t_ili_tid='native-value'"));
        }
    }

    void lines(TestDatabase db) throws Exception {
        String model = "ChbaseLines" + version;
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        referenceSchema(db, model, "--coalesceMultiLine");
        referenceImport(db, "legacy", input);
        var output = work.resolve("lines-output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertLines(output, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertLines(output, "native");
        assertGeometryColumn(db, "piperoute", "shape", "MULTICURVE", 2056);
        assertGeometryColumn(db, "flowroute", "shape", "MULTICURVE", 2056);
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("linestructure")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("piperoute") + " WHERE shape IS NULL"));
            if (db.postgis()) {
                assertEquals("2", scalar(sql, "SELECT ST_NumGeometries(shape) FROM " + db.table("piperoute")
                        + " WHERE t_ili_tid='native-value' AND ST_HasArc(shape)"));
            }
        }
    }

    void surfaces(TestDatabase db) throws Exception {
        String model = "ChbaseSurfaces" + version;
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        referenceSchema(db, model, "--coalesceMultiSurface");
        referenceImport(db, "legacy", input);
        var output = work.resolve("surfaces-output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSurfaces(output, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertSurfaces(output, "native");
        assertGeometryColumn(db, "landcover", "shape", "MULTISURFACE", 21781);
        String problem;
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("surfacestructure")));
            String covers = db.table("landcover");
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + covers + " WHERE shape IS NULL"));
            if (db.postgis()) {
                assertEquals("2", scalar(sql, "SELECT ST_NumGeometries(shape) FROM " + covers
                        + " WHERE t_ili_tid='native-value' AND ST_HasArc(shape)"));
                execute(sql, "ALTER TABLE " + covers + " ALTER COLUMN shape TYPE geometry(MultiSurface, 2056)"
                        + " USING ST_SetSRID(shape, 2056)");
                problem = "Expected an EPSG:21781";
            } else {
                execute(sql, "UPDATE " + covers + " SET shape=X'00' WHERE t_ili_tid='native-value'");
                problem = "GeoPackage";
            }
        }
        String previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, problem);
        assertEquals(previous, Files.readString(output));
    }

    void schema(TestDatabase baseline, TestDatabase own) throws Exception {
        String points = "ChbasePoints" + version;
        String lines = "ChbaseLines" + version;
        String surfaces = "ChbaseSurfaces" + version;
        var source = fixture(points + ".ili");
        var lineSource = fixture(lines + ".ili");
        var surfaceSource = fixture(surfaces + ".ili");
        referenceSchema(baseline, String.join(";", points, lines, surfaces),
                "--coalesceMultiPoint", "--coalesceMultiLine", "--coalesceMultiSurface");
        var result = schemaWithIdentityColumns(own, points, source, CancellationToken.none(),
                "--model", lines, "--model", surfaces, "--model-file", lineSource.toString(), "--model-file", surfaceSource.toString(),
                "--coalesce-multi-point", "--coalesce-multi-line", "--coalesce-multi-surface");
        assertEquals(0, result.code(), result::diagnostics);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            var catalog = DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema());
            if (!baseline.postgis()) {
                catalog = catalog.expectGeoPackageGeometryExtension("piperoute", "shape", "MULTICURVE")
                        .expectGeoPackageGeometryExtension("flowroute", "shape", "MULTICURVE")
                        .expectGeoPackageGeometryExtension("linestructure", "line", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("directedlinestructure", "line", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("landcover", "shape", "MULTISURFACE")
                        .expectGeoPackageGeometryExtension("surfacestructure", "surface", "CURVEPOLYGON");
            }
            catalog.assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        for (var model : List.of(points, lines, surfaces)) {
            var input = fixture(model + ".xtf");
            var output = work.resolve(model + "-output.xtf");
            var legacy = model + "-legacy";
            referenceImport(own, legacy, withPrefix(input, legacy));
            assertCliSuccess(own, "export", model, legacy, output);
            assertGeometry(output, model, legacy);
            var nativeDataset = model + "-native";
            assertCliSuccess(own, "import", model, nativeDataset, withPrefix(input, nativeDataset));
            referenceExport(own, nativeDataset, output);
            assertGeometry(output, model, nativeDataset);
            var tail = model + "-tail";
            referenceImport(own, tail, withPrefix(input, tail));
            assertCliSuccess(own, "export", model, tail, output);
            assertGeometry(output, model, tail);
        }
        try (var sql = own.connect()) {
            var ids = List.of("pinset", "piperoute", "flowroute", "landcover", "t_ili2db_basket", "t_ili2db_dataset").stream()
                    .map(table -> "SELECT t_id FROM " + own.table(table)).collect(Collectors.joining(" UNION ALL "));
            assertEquals("39", scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + ids + ") ids"));
        }
    }

    void smart2(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "ChbaseSmart" + version;
        var source = fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        var update = fixture(model + "-update.xtf");
        var referenceOptions = new ArrayList<>(List.of("--smart2Inheritance", "--coalesceMultiPoint",
                "--expandMultilingual", "--createGeomIdx"));
        var nativeOptions = new ArrayList<>(List.of("--inheritance", "smart2", "--coalesce-multi-point",
                "--expand-multilingual", "--create-geom-index"));
        if (own.postgis()) {
            referenceOptions.add("--oneGeomPerTable");
            nativeOptions.add("--one-geom-per-table");
        }
        referenceSchema(baseline, model, referenceOptions.toArray(String[]::new));
        var result = schemaWithIdentityColumns(own, model, source, CancellationToken.none(), nativeOptions.toArray(String[]::new));
        assertEquals(0, result.code(), result::diagnostics);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        for (var db : List.of(baseline, own)) {
            var output = work.resolve("smart2-output.xtf");
            referenceImport(db, "legacy", input);
            assertCliSuccess(db, "export", model, "legacy", output);
            assertSmart2(output, model, "fixture", "Alt", "Pont", 2600100, 1200100);
            assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
            referenceExport(db, "native", output);
            assertSmart2(output, model, "native", "Alt", "Pont", 2600100, 1200100);
            assertCliSuccess(db, "delete", model, "native", null);

            try (var sql = db.connect()) {
                assertIndex(db, sql, "surveyasset", "anchor");
                assertIndex(db, sql, "surveyasset_shape", "shape");
                var asset = db.table("surveyasset");
                var shape = db.table("surveyasset_shape");
                String object = scalar(sql, "SELECT t_id FROM " + asset);
                String basket = scalar(sql, "SELECT t_basket FROM " + asset);
                assertEquals(object, scalar(sql, "SELECT t_id FROM " + shape));
                assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + shape));
                assertSpatialHit(db, sql, "surveyasset_shape", "shape", 2600100, true);
                assertCliSuccess(db, "update", model, "legacy", update);
                assertEquals(object, scalar(sql, "SELECT t_id FROM " + asset));
                assertEquals(object, scalar(sql, "SELECT t_id FROM " + shape));
                assertEquals(basket, scalar(sql, "SELECT t_basket FROM " + shape));
                assertEquals("Neu", scalar(sql, "SELECT caption_de FROM " + asset));
                assertNull(scalar(sql, "SELECT caption_fr FROM " + asset));
                assertSpatialHit(db, sql, "surveyasset_shape", "shape", 2600100, false);
                assertSpatialHit(db, sql, "surveyasset_shape", "shape", 2600300, true);

                referenceExport(db, "legacy", output);
                assertSmart2(output, model, "fixture", "Neu", null, 2600300, 1200300);
                assertCliSuccess(db, "delete", model, "legacy", null);
                for (var table : List.of("surveyasset", "surveyasset_shape", "t_ili2db_basket", "t_ili2db_dataset")) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
                }
                assertSpatialHit(db, sql, "surveyasset_shape", "shape", 2600300, false);
                if (!db.postgis()) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_shape_shape"));
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_surveyasset_anchor"));
                }
            }
        }
    }

    private void assertSmart2(Path output, String model, String prefix, String german, String french, int x, int y) throws Exception {
        var object = objects(output, prefix).get(prefix + "-value");
        assertEquals("survey", child(object, "Marker").getTextContent());
        var wrapper = children(child(object, "Caption")).getFirst();
        var texts = children(wrapper).stream().flatMap(field -> children(field).stream())
                .collect(Collectors.toMap(entry -> child(entry, "Language").getTextContent(), entry -> child(entry, "Text").getTextContent()));
        assertEquals(french == null ? Map.of("de", german) : Map.of("de", german, "fr", french), texts);
        assertEquals(xy(Integer.toString(x), Integer.toString(y)), coordinate(children(child(object, "Anchor")).getFirst()));
        var components = parts(object, model, "PinGroup", "Members", "PinPart");
        assertEquals(List.of(xy(Integer.toString(x), Integer.toString(y)), xy(Integer.toString(x + 10), Integer.toString(y + 10))),
                components.stream().map(part -> coordinate(children(child(part, "Position")).getFirst())).toList());
    }

    private static void assertSpatialHit(TestDatabase db, Connection sql, String table, String column, int x, boolean expected)
            throws Exception {
        String query = db.postgis()
                ? "SELECT count(*) FROM " + db.table(table) + " WHERE " + column
                    + " && ST_MakeEnvelope(" + (x - 1) + ", 1200000, " + (x + 1) + ", 1201000, 2056)"
                : "SELECT count(*) FROM rtree_" + table + "_" + column + " WHERE minx <= " + (x + 1) + " AND maxx >= " + (x - 1);
        assertEquals(expected ? "1" : "0", scalar(sql, query));
    }

    private void assertGeometry(Path output, String model, String prefix) throws Exception {
        if (model.startsWith("ChbasePoints")) assertPoints(output, model, prefix);
        else if (model.startsWith("ChbaseLines")) assertLines(output, prefix);
        else assertSurfaces(output, prefix);
    }

    private void assertSurfaces(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        String types = version == 23 ? "GeometryCHLV03_V1" : "ChbaseSurfaceTypes24";
        var items = parts(objects.get(prefix + "-value"), types, "MultiSurface", "Surfaces", "SurfaceStructure");
        assertEquals(2, items.size());
        var first = children(children(child(items.getFirst(), "Surface")).getFirst());
        assertEquals(2, first.size());
        var exterior = children(children(first.getFirst()).getFirst());
        assertEquals(5, exterior.size());
        assertEquals(xy("600000", "200000"), coordinate(exterior.getFirst()));
        var arc = exterior.get(1);
        assertEquals(version == 23 ? "ARC" : "arc", arc.getLocalName());
        assertEquals(xy("600010", "199995"), position(arc, version == 23 ? "A" : "a"));
        assertEquals(xy("600020", "200000"), coordinate(arc));
        assertEquals(xy("600000", "200000"), coordinate(exterior.getLast()));
        var hole = children(children(first.getLast()).getFirst());
        assertEquals(List.of(xy("600005", "200005"), xy("600005", "200010"), xy("600010", "200010"),
                xy("600010", "200005"), xy("600005", "200005")), hole.stream().map(this::coordinate).toList());
        var second = children(children(child(items.getLast(), "Surface")).getFirst());
        assertEquals(1, second.size());
        var vertices = children(children(second.getFirst()).getFirst());
        assertEquals(List.of(xy("600100", "200100"), xy("600110", "200100"), xy("600110", "200110"), xy("600100", "200100")),
                vertices.stream().map(this::coordinate).toList());
    }

    private void assertLines(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        String types = version == 23 ? "GeometryCHLV95_V1" : "ChbaseLineTypes24";
        var lines = parts(objects.get(prefix + "-value"), types, "MultiLine", "Lines", "LineStructure");
        assertEquals(2, lines.size());
        var mixed = children(children(child(lines.getFirst(), "Line")).getFirst());
        assertEquals(3, mixed.size());
        assertEquals(xy("2600000", "1200000"), coordinate(mixed.getFirst()));
        var arc = mixed.get(1);
        assertEquals(version == 23 ? "ARC" : "arc", arc.getLocalName());
        assertEquals(xy("2600005", "1200005"), position(arc, version == 23 ? "A" : "a"));
        assertEquals(xy("2600010", "1200000"), coordinate(arc));
        assertEquals(xy("2600020", "1200000"), coordinate(mixed.get(2)));
        var straight = children(children(child(lines.getLast(), "Line")).getFirst());
        assertEquals(List.of(xy("2600100", "1200100"), xy("2600110", "1200120")),
                straight.stream().map(this::coordinate).toList());
        var directed = parts(objects.get(prefix + "-directed"), types, "MultiDirectedLine", "Lines", "DirectedLineStructure");
        assertEquals(1, directed.size());
        var steps = children(children(child(directed.getFirst(), "Line")).getFirst());
        assertEquals(List.of(xy("2600210", "1200200"), xy("2600200", "1200200")),
                steps.stream().map(this::coordinate).toList());
    }

    private void assertPoints(Path output, String model, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-empty"), "Shape"));
        var grouped = parts(objects.get(prefix + "-value"), model, "PinGroup", "Members", "PinPart");
        assertEquals(List.of(
                List.of(new BigDecimal("2600000.125"), new BigDecimal("1200000.25")),
                List.of(new BigDecimal("2600000.125"), new BigDecimal("1200000.25")),
                List.of(new BigDecimal("2600400.5"), new BigDecimal("1200200.75"))),
                grouped.stream().map(part -> coordinate(children(child(part, "Position")).getFirst())).toList());
    }

    private List<BigDecimal> coordinate(Element element) {
        return position(element, version == 23 ? "C" : "c");
    }

    private static List<BigDecimal> position(Element element, String prefix) {
        return xy(child(element, prefix + "1").getTextContent(), child(element, prefix + "2").getTextContent());
    }

    private static List<BigDecimal> xy(String x, String y) {
        return List.of(new BigDecimal(x).stripTrailingZeros(), new BigDecimal(y).stripTrailingZeros());
    }

    private List<Element> parts(Element object, String structureModel, String type, String bag, String itemType) {
        var structure = children(child(object, "Shape")).getFirst();
        assertStructure(structure, structureModel, type);
        var items = new ArrayList<Element>();
        for (var field : children(structure)) {
            assertEquals(bag, field.getLocalName());
            for (var item : children(field)) {
                assertStructure(item, structureModel, itemType);
                items.add(item);
            }
        }
        return items;
    }

    private void assertStructure(Element structure, String model, String type) {
        assertEquals(version == 23 ? model + "." + type : type, structure.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, structure.getNamespaceURI());
    }

    private Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        return children(basket).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }

    private static void assertGeometryColumn(TestDatabase db, String table, String column, String kind, int epsg) throws Exception {
        try (var sql = db.connect()) {
            String metadata = db.postgis() ? "SELECT type || ':' || srid FROM geometry_columns WHERE f_table_schema = '"
                    + db.schema() + "' AND f_table_name='" + table + "' AND f_geometry_column='" + column + "'"
                    : "SELECT geometry_type_name || ':' || srs_id FROM gpkg_geometry_columns WHERE table_name='" + table
                    + "' AND column_name='" + column + "'";
            assertEquals(kind + ":" + epsg, scalar(sql, metadata));
        }
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var source = getClass().getResourceAsStream("/chbase/" + name)) {
            Files.copy(source, path);
        }
        return path;
    }

    private Path withPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input).replace("fixture-", prefix + "-"));
    }

    private void referenceSchema(TestDatabase db, String model, String... flags) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
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
