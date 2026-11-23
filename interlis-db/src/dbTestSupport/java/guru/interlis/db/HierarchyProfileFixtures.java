package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class HierarchyProfileFixtures {
    private final Path work;
    private final int version;
    private final ProfileFixtures fixtures;

    HierarchyProfileFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
        fixtures = new ProfileFixtures(work, version);
    }

    String hierarchyModel() { return "SmartPoly" + version; }

    Path hierarchySource() throws IOException {
        String name = hierarchyModel() + ".ili";
        return Files.writeString(work.resolve(name), resource(name).replace("  END Data;", """
                    DOMAIN Position = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                    CLASS Marker = MainPoint : Position; CheckPoint : Position; END Marker;
                    ASSOCIATION Calibration =
                      Applying -- {0..1} Terminal;
                      Applied -- {0..*} Marker;
                      Score : 0..9;
                    END Calibration;
                    STRUCTURE PointerValue = Destination : REFERENCE TO (EXTERNAL) Instrument; END PointerValue;
                    CLASS Pointer = Link : PointerValue; END Pointer;
                  END Data;"""));
    }

    Path hierarchyData(String prefix) throws IOException {
        String model = hierarchyModel();
        String content = resource(model + ".xtf").replace("fixture-", prefix + "-");
        String endBasket = version == 23 ? "</" + model + ".Data>" : "</Data>";
        String marker = fixtures.object(model, "Marker", prefix + "-point", "<MainPoint>" + fixtures.point("12.000", "13.000")
                + "</MainPoint><CheckPoint>" + fixtures.point("14.000", "15.000") + "</CheckPoint>"
                + fixtures.reference("Applying", prefix + "-t"));
        return Files.writeString(work.resolve("hierarchy-" + prefix + ".xtf"), content.replace(endBasket, marker + endBasket));
    }

    Path incomingData() throws IOException {
        String model = hierarchyModel();
        String structure = version == 23 ? model + ".Data.PointerValue" : "PointerValue";
        String destination = version == 23 ? "<Destination REF=\"native-s\" BID=\"native-b\"/>"
                : "<Destination i:ref=\"native-s\"/>";
        return fixtures.data(model, "incoming", fixtures.object(model, "Pointer", "incoming-p",
                "<Link><" + structure + ">" + destination + "</" + structure + "></Link>"));
    }

    void assertHierarchy(Path output, String prefix, String memo) throws Exception {
        var rows = fixtures.basketRows(output, prefix + "-b", 7);
        var smart = rows.stream().filter(row -> (prefix + "-s").equals(id(row, version, "tid"))).findFirst().orElseThrow();
        assertEquals(memo, child(smart, "Memo").getTextContent());
        var marker = rows.stream().filter(row -> (prefix + "-point").equals(id(row, version, "tid"))).findFirst().orElseThrow();
        var coordinate = child(children(child(marker, "CheckPoint")).getFirst(), version == 23 ? "C1" : "c1");
        assertEquals(0, new BigDecimal("14").compareTo(new BigDecimal(coordinate.getTextContent())));
        assertEquals(prefix + "-t", id(child(marker, "Applying"), version, "ref"));
        var links = rows.stream().filter(row -> row.getLocalName().endsWith("CrossCheck")).toList();
        assertEquals(Set.of(prefix + "-s", prefix + "-t"), links.stream()
                .map(row -> id(child(row, "CheckedInstrument"), version, "ref")).collect(Collectors.toSet()));
    }

    String discriminatorModel() { return "ProfileType" + version; }

    Path discriminatorSource() throws IOException {
        return fixtures.source(discriminatorModel(), """
                STRUCTURE Note = Message : TEXT*24; END Note;
                CLASS Asset = Label : TEXT*24; Notes : LIST OF Note; END Asset;
                CLASS Base (ABSTRACT) = Caption : TEXT*24; END Base;
                CLASS Leaf EXTENDS Base = NumberValue : 0..12; END Leaf;
                ASSOCIATION Survey =
                  AssetTarget -- {0..1} Leaf;
                  Surveyed -- {0..*} Asset;
                  Detail : TEXT*20;
                END Survey;
                """);
    }

    Path discriminatorData(String prefix) throws IOException {
        String model = discriminatorModel();
        String note = version == 23 ? model + ".Data.Note" : "Note";
        return fixtures.data(model, prefix, fixtures.object(model, "Asset", prefix + "-asset",
                "<Label>typed</Label><Notes><" + note + "><Message>present</Message></" + note + "></Notes>"
                        + fixtures.reference("AssetTarget", prefix + "-leaf"))
                + fixtures.object(model, "Leaf", prefix + "-leaf", "<Caption>subtype</Caption><NumberValue>7</NumberValue>"));
    }

    void assertTypedTransfer(Path output, String prefix) throws Exception {
        List<Element> rows = fixtures.rows(output, prefix, 2);
        assertEquals("typed", child(rows.getFirst(), "Label").getTextContent());
        assertEquals(prefix + "-leaf", id(child(rows.getFirst(), "AssetTarget"), version, "ref"));
        assertEquals("7", child(rows.getLast(), "NumberValue").getTextContent());
    }

    static void assertStoredDiscriminators(Target db, Connection sql, String prefix) throws SQLException {
        String asset = " FROM " + db.table("asset") + " WHERE t_ili_tid='" + prefix + "-asset'";
        String basket = "(SELECT t_basket" + asset + ")";
        assertEquals("asset", scalar(sql, "SELECT t_type" + asset));
        assertEquals("leaf", scalar(sql, "SELECT t_type FROM " + db.table("base") + " WHERE t_ili_tid='" + prefix + "-leaf'"));
        assertEquals("survey", scalar(sql, "SELECT t_type FROM " + db.table("survey") + " WHERE t_basket=" + basket));
        assertEquals("note", scalar(sql, "SELECT t_type FROM " + db.table("note") + " WHERE t_basket=" + basket));
    }

    private static String resource(String name) throws IOException {
        try (var stream = HierarchyProfileFixtures.class.getResourceAsStream("/smart/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
