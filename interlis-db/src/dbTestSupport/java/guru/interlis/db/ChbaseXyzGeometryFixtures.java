package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.ChbaseFixtures.basket;
import static guru.interlis.db.ChbaseFixtures.fixture;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChbaseXyzGeometryFixtures {
    private final Path work;
    private final int version;
    final String model;
    private final Path source;
    final Path input;

    ChbaseXyzGeometryFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "ChbaseXyzGeometry" + version;
        source = fixture(work, model + ".ili");
        input = fixture(work, model + ".xtf");
    }

    void schema(Target db) {
        var options = new ArrayList<>(List.of("--coalesce-multi-point", "--coalesce-multi-line",
                "--coalesce-multi-surface", "--create-geom-index"));
        if (db.postgis()) options.add("--one-geom-per-table");
        assertSchemaWithIdentityColumns(db, model, source, options.toArray(String[]::new));
    }

    Path withoutPins() throws Exception {
        String transfer = Files.readString(input).replaceFirst("(?s)<Pins>.*?</Pins>", "");
        return Files.writeString(work.resolve("without-pins.xtf"), transfer);
    }

    Path invalidAfterValidAsset() throws Exception {
        String transfer = Files.readString(input);
        String tag = version == 23 ? model + ".Data.Asset" : "Asset";
        String closingTag = "</" + tag + ">";
        int start = transfer.indexOf("<" + tag + " ");
        int end = transfer.indexOf(closingTag, start) + closingTag.length();
        String zTag = version == 23 ? "<C3>30</C3>" : "<g:c3>30</g:c3>";
        String invalid = transfer.substring(start, end).replace("fixture-asset", "broken").replace(zTag, "");
        return Files.writeString(work.resolve("late-invalid.xyz.xtf"), transfer.substring(0, end)
                .replaceFirst("(?s)<Pins>.*?</Pins>", "").replace("10.125", "11.125") + invalid + transfer.substring(end));
    }

    void assertDelivery(Path output, String prefix, boolean pinsPresent) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var objects = children(basket(baskets, version, model, prefix + "-b"));
        assertEquals(1, objects.size());
        var asset = objects.getFirst();
        assertEquals(prefix + "-asset", id(asset, version, "tid"));
        assertHeights(child(asset, "Anchor"), "10.125");
        if (pinsPresent) {
            assertHeights(child(asset, "Pins"), "-15.25", "16.5");
        } else {
            assertNull(child(asset, "Pins"));
        }
        var lines = child(asset, "Paths");
        assertEquals(2, lines.getElementsByTagNameNS("*", version == 23 ? "POLYLINE" : "polyline").getLength());
        assertEquals(1, lines.getElementsByTagNameNS("*", version == 23 ? "ARC" : "arc").getLength());
        assertHeights(lines, "20", "30", "40", "50");
        var surfaces = child(asset, "Tiles");
        assertEquals(2, surfaces.getElementsByTagNameNS("*", version == 23 ? "SURFACE" : "surface").getLength());
        assertEquals(1, surfaces.getElementsByTagNameNS("*", version == 23 ? "ARC" : "arc").getLength());
        assertHeights(surfaces, "60", "70", "80", "90", "60", "100", "110", "120", "100");
    }

    private void assertHeights(Element field, String... expected) {
        var heights = field.getElementsByTagNameNS("*", version == 23 ? "C3" : "c3");
        assertEquals(expected.length, heights.getLength(), field.getLocalName());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(0, new BigDecimal(expected[index]).compareTo(new BigDecimal(heights.item(index).getTextContent())),
                    field.getLocalName());
        }
    }

    static void assertColumn(Target db, Connection sql, String column, String type, int srid, String zMin, String zMax)
            throws SQLException {
        String table = "asset_" + column;
        String physicalColumn = " AND f_table_name='" + table + "' AND f_geometry_column='" + column + "'";
        if (db.postgis()) {
            assertEquals(type + ":" + srid + ":3", scalar(sql, "SELECT upper(type) || ':' || srid || ':' || coord_dimension"
                    + " FROM geometry_columns WHERE f_table_schema='" + db.schema() + "'" + physicalColumn));
        } else {
            assertEquals(type + ":" + srid + ":1", scalar(sql, "SELECT geometry_type_name || ':' || srs_id || ':' || z"
                    + " FROM gpkg_geometry_columns WHERE table_name='" + table + "' AND column_name='" + column + "'"));
        }
        assertEquals(Set.of("ch.ehi.ili2db.c3Min:" + zMin, "ch.ehi.ili2db.c3Max:" + zMax),
                catalogKeys(sql, "SELECT tag || ':' || setting FROM " + db.table("t_ili2db_column_prop")
                        + " WHERE tablename='" + table + "' AND lower(columnname)='" + column + "'"
                        + " AND tag IN ('ch.ehi.ili2db.c3Min','ch.ehi.ili2db.c3Max')"));
    }
}
