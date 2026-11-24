package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlternateCrsFixtures {
    private final Path work;

    AlternateCrsFixtures(Path work) {
        this.work = work;
    }

    static void assertStorage(Target db, Connection sql, String suffix) throws Exception {
        String basket = db.table("t_ili2db_basket");
        String where = " WHERE t_ili_tid='basket-old-" + suffix + "'";
        assertEquals("Old23.Data", scalar(sql, "SELECT topic FROM " + basket + where));
        assertEquals("", scalar(sql, "SELECT domains FROM " + basket + where));
        String station = db.table("station");
        String old = " WHERE t_ili_tid='old-" + suffix + "'";
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + station + old + " AND location_2056 IS NULL"));
        CrsFixtures.assertStoredEpsg(db, sql, "station", "location_2056", "current-" + suffix, 2056);
        String table = db.table(db.postgis() ? "station" : "station_location_21781");
        CrsFixtures.assertStoredSrid(db, sql, " FROM " + table + " WHERE t_id=(SELECT t_id FROM " + station + old + ")",
                "location_21781", 21781);
    }

    Path switchTopic(Path original) throws Exception {
        return Files.writeString(work.resolve("alternate-switch.xtf"), Files.readString(original).replace("Old23.Data", "Current23.Data")
                .replace("600100.125", "2620000.125").replace("200200.375", "1220000.375"));
    }

    static void assertSwitchedTopic(Path output, String suffix) throws Exception {
        var switched = basket(output, "basket-old-" + suffix);
        assertEquals("Current23.Data", switched.getLocalName());
        assertEquals("Current23.Data.Station", children(switched).getFirst().getLocalName());
        assertCoordinate(switched, "2620000.125", "1220000.375");
    }

    static Element basket(Path output, String bid) throws Exception {
        return children(readDataSection(output, 23)).stream().filter(b -> bid.equals(id(b, 23, "bid"))).findFirst().orElseThrow();
    }

    static void assertCoordinate(Element basket, String x, String y) {
        var coord = child(child(children(basket).getFirst(), "Location"), "COORD");
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(child(coord, "C1").getTextContent())));
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(child(coord, "C2").getTextContent())));
    }

    static void createOwn(Target db, Path model, String... extra) {
        var options = new ArrayList<>(List.of("--multi-srs", "--alt-srs-model", "Current23=Old23"));
        options.addAll(List.of(extra));
        assertSchemaWithIdentityColumns(db, "Current23", model, options.toArray(String[]::new));
    }

    Path modelFile() throws Exception {
        Path path = work.resolve("AlternateCrs23.ili");
        try (var resource = AlternateCrsFixtures.class.getResourceAsStream("/AlternateCrs23.ili")) {
            Files.copy(resource, path);
        }
        return path;
    }

    Path delivery(String suffix) throws Exception {
        return Files.writeString(work.resolve("alternate-" + suffix + ".xtf"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="interop"><MODELS>
                    <MODEL NAME="Current23" VERSION="1" URI="https://example.org/interlis/db"/>
                    <MODEL NAME="Old23" VERSION="1" URI="https://example.org/interlis/db"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION>
                    <Current23.Data BID="basket-current-%1$s"><Current23.Data.Station TID="current-%1$s">
                      <Name>Current</Name><Location><COORD><C1>2600001.625</C1><C2>1200002.125</C2></COORD></Location>
                    </Current23.Data.Station></Current23.Data>
                    <Old23.Data BID="basket-old-%1$s"><Old23.Data.Station TID="old-%1$s">
                      <Name>Old</Name><Location><COORD><C1>600100.125</C1><C2>200200.375</C2></COORD></Location>
                    </Old23.Data.Station></Old23.Data>
                  </DATASECTION>
                </TRANSFER>
                """.formatted(suffix));
    }

    static void assertDelivery(Path output, String suffix) throws Exception {
        var section = readDataSection(output, 23);
        var header = child((Element) section.getParentNode(), "HEADERSECTION");
        var names = children(child(header, "MODELS")).stream().map(element -> element.getAttribute("NAME")).toList();
        assertTrue(names.containsAll(List.of("Current23", "Old23")), names.toString());
        var baskets = children(section);
        assertEquals(2, baskets.size());
        for (String model : List.of("Current23", "Old23")) {
            boolean current = model.equals("Current23");
            String tid = (current ? "current-" : "old-") + suffix;
            var basket = baskets.stream().filter(b -> ("basket-" + tid).equals(id(b, 23, "bid"))).findFirst().orElseThrow();
            assertEquals(model + ".Data", basket.getLocalName());
            var object = children(basket).getFirst();
            assertEquals(model + ".Data.Station", object.getLocalName());
            assertEquals(tid, id(object, 23, "tid"));
            assertEquals(current ? "Current" : "Old", child(object, "Name").getTextContent());
            assertCoordinate(basket, current ? "2600001.625" : "600100.125", current ? "1200002.125" : "200200.375");
        }
    }
}
