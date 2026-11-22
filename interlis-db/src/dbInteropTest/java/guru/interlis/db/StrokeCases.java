package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.emptyCollection;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    void polyline(TestDatabase db) throws Exception {
        String model = "StrokeLine" + version;
        source(model, "POLYLINE");
        referenceSchema(db, model);
        String value = polyline(coord(600000, 200000) + coord(600010, 200000) + coord(600020, 200010));
        referenceImport(db, "legacy", dataset(model, "legacy", value));
        var output = work.resolve("output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertStraightLine(output, "legacy");

        String properties = db.table("t_ili2db_column_prop");
        try (var sql = db.connect()) {
            execute(sql, "ALTER TABLE " + db.table("route") + " RENAME COLUMN shape TO stored_shape");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_shape' WHERE sqlname='shape'");
            execute(sql, "UPDATE " + properties + " SET columnname='stored_shape' WHERE columnname='shape'");
            if (!db.postgis()) execute(sql, "UPDATE gpkg_geometry_columns SET column_name='stored_shape' WHERE table_name='route'");
        }
        assertGeometryColumn(db, "route", "stored_shape", "LINESTRING", 2056);
        assertCliSuccess(db, "import", model, "native", dataset(model, "native", value));
        referenceExport(db, "native", output);
        assertStraightLine(output, "native");
        assertCliSuccess(db, "export", model, "native", output);
        assertStraightLine(output, "native");
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("route") + " WHERE stored_shape IS NULL"));
            execute(sql, "UPDATE " + properties + " SET setting='COMPOUNDCURVE' WHERE tag='ch.ehi.ili2db.geomType'");
        }
        String previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, "COLUMN_PROP.geomType for route.stored_shape");
        assertEquals(previous, Files.readString(output));
    }

    void arcs(TestDatabase db) throws Exception {
        String model = "StrokeArc" + version;
        source(model, "POLYLINE");
        source("Fine" + version, "POLYLINE", "00000000000");
        referenceSchema(db, model + ";Fine" + version);
        String normal = polyline(coord(600000, 200000) + arc(600005, 200005, 600010, 200000) + coord(600020, 200000));
        var output = work.resolve("output.xtf");
        referenceImport(db, "legacy", dataset(model, "legacy", normal));
        assertCliSuccess(db, "export", model, "legacy", output);
        var expected = linePositions(objects(output, "legacy").get("legacy-value"));
        assertEquals(82, expected.size());
        assertEquals(List.of(600005.0, 200005.0), expected.get(40));

        assertCliSuccess(db, "import", model, "native", transfer(model, "native",
                route(model, "native-value", normal)
                        + route(model, "native-major", polyline(coord(600010, 200000) + arc(600000, 200010, 600008, 199994)))
                        + route(model, "native-circle", polyline(coord(600010, 200000) + arc(599990, 200000, 600010, 200000)))
                        + route(model, "native-collinear", polyline(coord(600000, 200000) + arc(600005, 200000, 600010, 200000)))));
        assertCliSuccess(db, "export", model, "native", output);
        var objects = objects(output, "native");
        var actual = linePositions(objects.get("native-value"));
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            for (int axis = 0; axis < 2; axis++) assertEquals(expected.get(i).get(axis), actual.get(i).get(axis), 0.000001);
        }
        var major = linePositions(objects.get("native-major"));
        assertCircularPath(major, List.of(600010.0, 200000.0), List.of(600000.0, 200010.0), List.of(600008.0, 199994.0));
        var circle = linePositions(objects.get("native-circle"));
        assertCircularPath(circle, List.of(600010.0, 200000.0), List.of(599990.0, 200000.0), List.of(600010.0, 200000.0));
        assertEquals(List.of(List.of(600000.0, 200000.0), List.of(600010.0, 200000.0)), linePositions(objects.get("native-collinear")));

        referenceExport(db, "native", output);
        var rounded = linePositions(objects(output, "native").get("native-value"));
        assertEquals(expected.size(), rounded.size());
        for (int i = 0; i < expected.size(); i++) {
            for (int axis = 0; axis < 2; axis++) assertEquals(expected.get(i).get(axis), rounded.get(i).get(axis), 0.000500001);
        }

        var fine = "Fine" + version;
        var failing = transfer(fine, "rollback", object(fine, "Route", "written", "")
                + route(fine, "over-limit", normal));
        assertCliFailure(db, "import", fine, "rollback", failing, "100000 stored XY pairs");
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='rollback'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='rollback-b'"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            var table = scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname") + " WHERE iliname='" + fine + ".Data.Route'");
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table)));
        }
    }

    private static void assertCircularPath(List<List<Double>> positions, List<Double> start, List<Double> support, List<Double> end) {
        assertEquals(start, positions.getFirst());
        assertEquals(end, positions.getLast());
        assertTrue(positions.contains(support));
        for (int i = 1; i < positions.size(); i++) {
            var a = positions.get(i - 1);
            var b = positions.get(i);
            assertEquals(10, Math.hypot(b.get(0) - 600000, b.get(1) - 200000), 0.000001);
            double midpointRadius = Math.hypot((a.get(0) + b.get(0)) / 2 - 600000, (a.get(1) + b.get(1)) / 2 - 200000);
            assertTrue(10 - midpointRadius <= 0.001000001, "Chord exceeds model tolerance");
        }
    }

    void surface(TestDatabase db) throws Exception {
        String model = "StrokeSurface" + version;
        source(model, "SURFACE");
        referenceSchema(db, model);
        var outer = polyline(coord(600000, 200000) + arc(600005, 199995, 600010, 200000) + coord(600010, 200010)
                + coord(600000, 200010) + coord(600000, 200000));
        var inner = polyline(coord(600002, 200002) + coord(600002, 200004) + coord(600004, 200002) + coord(600002, 200002));
        String surface = surfaceValue(outer, inner);
        referenceImport(db, "legacy", dataset(model, "legacy", surface));
        assertGeometryColumn(db, "route", "shape", "POLYGON", 2056);
        var output = work.resolve("output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertPolygon(output, "legacy");
        assertCliSuccess(db, "import", model, "native", dataset(model, "native", surface));
        referenceExport(db, "native", output);
        assertPolygon(output, "native");

        String diagnostic;
        try (var sql = db.connect()) {
            var table = db.table("route");
            if (db.postgis()) {
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + table + " WHERE t_ili_tid='native-value'"
                        + " AND ST_IsValid(shape) AND ST_NumInteriorRings(shape)=1 AND ST_SRID(shape)=2056"));
                execute(sql, "ALTER TABLE " + table + " ALTER COLUMN shape TYPE geometry(CurvePolygon,2056) USING ST_ForceCurve(shape)");
                diagnostic = "2D POLYGON column at route.shape";
            } else {
                execute(sql, "UPDATE " + table + " SET shape=CAST(substr(shape,1,9)||X'0000000A'||substr(shape,14) AS BLOB)"
                        + " WHERE t_ili_tid='native-value'");
                diagnostic = "2D WKB POLYGON";
            }
        }
        String previous = Files.readString(output);
        assertCliFailure(db, "export", model, "native", output, diagnostic);
        assertEquals(previous, Files.readString(output));
    }

    void area(TestDatabase db) throws Exception {
        String model = "StrokeArea" + version;
        source(model, "AREA");
        referenceSchema(db, model);
        var start = coord(600000, 200000);
        var end = coord(600010, 200000);
        var top = start + arc(600005, 200005, 600010, 200000);
        var topClosure = coord(600010, 200010) + coord(600000, 200010) + start;
        var bottom = end + arc(600005, 200005, 600000, 200000)
                + coord(600000, 199990) + coord(600010, 199990) + end;
        var upper = version == 23 ? polyline(top) + polyline(end + topClosure) : polyline(top + topClosure);
        var upperSurface = surfaceValue(upper);
        var lowerSurface = surfaceValue(polyline(bottom));
        var output = work.resolve("output.xtf");
        referenceImport(db, "legacy", transfer(model, "legacy",
                route(model, "legacy-value", upperSurface) + route(model, "legacy-bottom", lowerSurface)));
        assertGeometryColumn(db, "route", "shape", "POLYGON", 2056);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertSharedAreaEdge(output, "legacy");
        assertCliSuccess(db, "import", model, "native", transfer(model, "native",
                route(model, "native-value", upperSurface) + route(model, "native-bottom", lowerSurface)));
        assertCliSuccess(db, "export", model, "native", output);
        assertSharedAreaEdge(output, "native");
        referenceExport(db, "native", output);
        assertSharedAreaEdge(output, "native");
        try (var sql = db.connect()) {
            assertEquals("AREA", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                    + " WHERE tablename='route' AND columnname='shape' AND tag='ch.ehi.ili2db.typeKind'"));
            if (db.postgis()) {
                var table = db.table("route");
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + table + " a JOIN " + table
                        + " b ON ST_Touches(a.shape,b.shape) WHERE a.t_ili_tid='native-value' AND b.t_ili_tid='native-bottom'"));
            }
        }
    }

    private void assertSharedAreaEdge(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        var upper = rings(objects.get(prefix + "-value")).getFirst();
        var lower = rings(objects.get(prefix + "-bottom")).getFirst();
        assertEquals(84, upper.size());
        assertEquals(84, lower.size());
        assertEquals(List.of(600000.0, 200000.0), upper.getFirst());
        assertEquals(List.of(600010.0, 200000.0), lower.getFirst());
        assertEquals(List.of(600005.0, 200005.0), upper.get(40));
        assertEquals(upper.getFirst(), upper.getLast());
        assertEquals(lower.getFirst(), lower.getLast());
        assertEquals(upper.subList(0, 81), lower.subList(0, 81).reversed());
    }

    void multiPolyline(TestDatabase db) throws Exception {
        var model = "ChbaseLines" + version;
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
        String direct = "StrokeMultiLine" + version;
        var output = work.resolve("output.xtf");
        if (version == 24) source(direct, "MULTIPOLYLINE");
        referenceSchema(db, version == 24 ? model + ";" + direct : model, "--coalesceMultiLine");
        referenceImport(db, "legacy", input);
        assertGeometryColumn(db, "piperoute", "shape", "MULTILINESTRING", 2056);
        assertGeometryColumn(db, "flowroute", "shape", "MULTILINESTRING", 2056);
        assertCliSuccess(db, "export", model, "legacy", output);
        assertWrappedLines(output, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertWrappedLines(output, "native");
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
        fixture(model + ".ili");
        var input = fixture(model + ".xtf");
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
        assertWrappedSurfaces(output, "fixture");
        assertCliSuccess(db, "import", model, "native", withPrefix(input, "native"));
        referenceExport(db, "native", output);
        assertWrappedSurfaces(output, "native");
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

    void schema(TestDatabase baseline, TestDatabase own) throws Exception {
        String model = "StrokeSchema" + version;
        var ili = source(model, "POLYLINE");
        Files.writeString(ili, Files.readString(ili).replace("END Route;", """
                END Route;
                    CLASS Marker =
                      Anchor : Vertex;
                    END Marker;
                """));
        var xyz = "StrokeXyz" + version;
        var xyzFile = source(xyz, "POLYLINE");
        Files.writeString(xyzFile, Files.readString(xyzFile).replace("0.000 .. 400000.000;",
                "0.000 .. 400000.000, -1000.000 .. 10000.000;"));
        assertCliFailure(own, "schemaimport", xyz, null, xyzFile, "strokeArcs requires XY at " + xyz + ".Data.Route.Shape",
                "--stroke-arcs");

        referenceSchema(baseline, model);
        var result = cli(own, "schemaimport", model, null, ili, CancellationToken.none(), "--stroke-arcs");
        assertEquals(0, result.code(), result::diagnostics);
        assertSameCatalog(baseline, own);
        assertGeometryColumn(own, "marker", "anchor", "POINT", 2056);
        var line = polyline(coord(600000, 200000) + arc(600005, 200005, 600010, 200000));
        var output = work.resolve("output.xtf");
        referenceImport(own, "legacy", dataset(model, "legacy", line));
        assertCliSuccess(own, "export", model, "legacy", output);
        assertSchemaLine(output, "legacy");
        assertCliSuccess(own, "import", model, "native", dataset(model, "native", line));
        referenceExport(own, "native", output);
        assertSchemaLine(output, "native");
        referenceImport(own, "tail", dataset(model, "tail", line));
        assertCliSuccess(own, "export", model, "tail", output);
        assertSchemaLine(output, "tail");
        try (var sql = own.connect()) {
            var ids = List.of("route", "t_ili2db_basket", "t_ili2db_dataset").stream()
                    .map(table -> "SELECT t_id FROM " + own.table(table)).collect(Collectors.joining(" UNION ALL "));
            assertEquals("12", scalar(sql, "SELECT count(DISTINCT t_id) FROM (" + ids + ") ids"));
        }

        var next = "StrokeAppend" + version;
        var extra = source(next, "SURFACE");
        var append = cli(own, "schemaimport", next, null, extra, CancellationToken.none(), "--add-models");
        assertEquals(0, append.code(), append::diagnostics);
        try (var sql = own.connect()) {
            var table = scalar(sql, "SELECT sqlname FROM " + own.table("t_ili2db_classname")
                    + " WHERE iliname='" + next + ".Data.Route'");
            assertGeometryColumn(own, table, "shape", "POLYGON", 2056);
        }
        var polygon = surfaceValue(polyline(coord(600000, 200000) + arc(600005, 199995, 600010, 200000)
                + coord(600010, 200010) + coord(600000, 200000)));
        assertCliSuccess(own, "import", next, "appended", dataset(next, "appended", polygon));
        referenceExport(own, "appended", output);
        assertEquals(List.of(600005.0, 199995.0), rings(objects(output, "appended").get("appended-value")).getFirst().get(40));
    }

    private void assertSchemaLine(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        var line = linePositions(objects.get(prefix + "-value"));
        assertEquals(81, line.size());
        assertEquals(List.of(600005.0, 200005.0), line.get(40));
        assertEquals(List.of(600010.0, 200000.0), line.getLast());
    }

    private static void assertSameCatalog(TestDatabase baseline, TestDatabase own) throws Exception {
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
    }

    private void assertPolygon(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        var rings = rings(objects.get(prefix + "-value"));
        assertEquals(2, rings.size());
        assertEquals(84, rings.getFirst().size());
        assertEquals(List.of(600000.0, 200000.0), rings.getFirst().getFirst());
        assertEquals(rings.getFirst().getFirst(), rings.getFirst().getLast());
        assertEquals(List.of(600005.0, 199995.0), rings.getFirst().get(40));
        assertEquals(List.of(List.of(600002.0, 200002.0), List.of(600002.0, 200004.0), List.of(600004.0, 200002.0),
                List.of(600002.0, 200002.0)), rings.getLast());
    }

    private List<List<List<Double>>> rings(Element object) {
        var surface = children(child(object, "Shape")).getFirst();
        return surfaceRings(surface);
    }

    private List<List<List<Double>>> surfaceRings(Element surface) {
        return children(surface).stream().map(ring -> positions(children(ring).getFirst())).toList();
    }

    private String surfaceValue(String exterior, String... interiors) {
        String contents = geometry(version == 23 ? "boundary" : "exterior", exterior);
        for (String interior : interiors) contents += geometry(version == 23 ? "boundary" : "interior", interior);
        return geometry("surface", contents);
    }

    private void assertStraightLine(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        assertEquals(List.of(List.of(600000.0, 200000.0), List.of(600010.0, 200000.0), List.of(600020.0, 200010.0)),
                linePositions(objects.get(prefix + "-value")));
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
