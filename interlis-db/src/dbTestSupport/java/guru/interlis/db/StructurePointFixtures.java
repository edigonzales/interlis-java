package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.StructureMappingFixtures.members;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructurePointFixtures {
    record Tag(String field, int sequence, String label, Double x, Double y) { }

    static final List<Tag> ORIGINAL = List.of(
            new Tag("Marker", 0, "single", 2600001.375, 1200002.625),
            new Tag("Tags", 0, "first", 2600003.625, 1200004.125),
            new Tag("Tags", 1, "missing point", null, null));
    static final List<Tag> UPDATED = List.of(new Tag("Tags", 0, "changed", 2600008.125, 1200009.375));
    static final List<Tag> REJECTED = List.of(new Tag("Marker", 0, "rollback", 2600012.125, 1200014.375));

    private final int version;
    private final ProfileFixtures fixture;
    private final Path source;
    final String model;

    StructurePointFixtures(Path work, int version) throws IOException {
        this.version = version;
        fixture = new ProfileFixtures(work, version);
        model = "StructurePoints" + version;
        source = fixture.source(model, """
                DOMAIN LocalGrid = COORD 2480000.000 .. 2840000.000, 1070000.000 .. 1300000.000;
                STRUCTURE Tag =
                  Label : TEXT*20;
                  Center : LocalGrid;
                END Tag;
                CLASS Item =
                  Marker : Tag;
                  Tags : LIST OF Tag;
                END Item;
                """);
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, model, source, "--create-geom-index");
    }

    Path input(String name, List<Tag> tags) throws IOException {
        return fixture.data(model, name, fixture.object(model, "Item", "item",
                field("Marker", tags) + field("Tags", tags)));
    }

    private String field(String field, List<Tag> tags) {
        String contents = tags.stream().filter(tag -> tag.field().equals(field)).map(this::structure)
                .collect(Collectors.joining());
        return contents.isEmpty() ? "" : "<" + field + ">" + contents + "</" + field + ">";
    }

    private String structure(Tag tag) {
        String type = version == 23 ? model + ".Data.Tag" : "Tag";
        String point = tag.x() == null ? "" : "<Center>" + fixture.point(tag.x().toString(), tag.y().toString()) + "</Center>";
        return "<" + type + "><Label>" + tag.label() + "</Label>" + point + "</" + type + ">";
    }

    void assertTransfer(Path output, List<Tag> expected) throws Exception {
        var item = fixture.basketRows(output, "delivery-basket", 1).getFirst();
        assertEquals("item", id(item, version, "tid"));
        var values = new ArrayList<Tag>();
        for (String field : List.of("Marker", "Tags")) {
            var structures = members(item, field);
            for (int index = 0; index < structures.size(); index++) {
                var structure = structures.get(index);
                assertEquals(version == 23 ? model + ".Data.Tag" : "Tag", structure.getLocalName());
                var geometry = child(structure, "Center");
                var point = geometry == null ? null : child(geometry, version == 23 ? "COORD" : "coord");
                if (geometry != null) assertNotNull(point);
                values.add(new Tag(field, index, child(structure, "Label").getTextContent(),
                        ordinate(point, 1), ordinate(point, 2)));
            }
        }
        assertEquals(expected, values);
    }

    private Double ordinate(Element point, int dimension) {
        if (point == null) return null;
        return Double.valueOf(child(point, (version == 23 ? "C" : "c") + dimension).getTextContent());
    }

    void assertRows(Target db, Connection sql, List<Tag> expected) throws SQLException {
        String geometry = db.postgis() ? "ST_AsBinary(t.center), ST_SRID(t.center)" : "t.center";
        String query = """
                SELECT p.t_ili_tid, b.t_ili_tid, t.item_marker IS NOT NULL, t.item_tags IS NOT NULL,
                       t.t_seq, t.label, %s
                FROM %s t LEFT JOIN %s p ON p.t_id=coalesce(t.item_marker, t.item_tags) AND p.t_basket=t.t_basket
                LEFT JOIN %s b ON b.t_id=t.t_basket
                ORDER BY t.item_marker IS NULL, t.t_seq
                """.formatted(geometry, db.table("tag"), db.table("item"), db.table("t_ili2db_basket"));
        var values = new ArrayList<Tag>();
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) {
                assertEquals("item", rows.getString(1), "structure owner");
                assertEquals("delivery-basket", rows.getString(2));
                boolean marker = rows.getBoolean(3);
                assertTrue(marker != rows.getBoolean(4), "exactly one structure owner column");
                byte[] bytes = rows.getBytes(7);
                Double x = null;
                Double y = null;
                if (bytes != null) {
                    if (db.postgis()) assertEquals(2056, rows.getInt(8));
                    var coordinate = (Coordinate) GeometryBinary.decode(bytes, !db.postgis(), 2056, DbMapping.Kind.POINT, 2);
                    x = coordinate.ordinates().getFirst().exactValue().doubleValue();
                    y = coordinate.ordinates().getLast().exactValue().doubleValue();
                }
                int sequence = rows.getInt(5);
                assertFalse(rows.wasNull(), "structure sequence");
                values.add(new Tag(marker ? "Marker" : "Tags", sequence, rows.getString(6), x, y));
            }
        }
        assertEquals(expected, values);
    }
}
