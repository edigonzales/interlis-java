package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
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
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private void assertStraightLine(Path output, String prefix) throws Exception {
        var objects = objects(output, prefix);
        assertNull(child(objects.get(prefix + "-null"), "Shape"));
        var line = children(child(objects.get(prefix + "-value"), "Shape")).getFirst();
        assertEquals(List.of(List.of(600000.0, 200000.0), List.of(600010.0, 200000.0), List.of(600020.0, 200010.0)), positions(line));
    }

    private Path source(String model, String kind) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN Vertex = COORD 400000.000 .. 900000.000, 0.000 .. 400000.000;
                  TOPIC Data =
                    CLASS Route =
                      Shape : %s WITH (STRAIGHTS, ARCS) VERTEX Vertex;
                    END Route;
                  END Data;
                END %s.
                """.formatted(version - 20, model, kind, model));
    }

    private Path dataset(String model, String prefix, String shape) throws Exception {
        return transfer(model, prefix, object(model, "Route", prefix + "-value", "<Shape>" + shape + "</Shape>")
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
