package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SmartFixtures {
    private final Path work;
    private final int version;

    SmartFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path changedRegion(Path input) throws Exception {
        return Files.writeString(work.resolve("region-changed.xtf"), Files.readString(input)
                .replace(">child<", ">updated<").replace(">inherited<", ">marker updated<"));
    }

    void assertGeometries(Path file, String model, String prefix) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        assertGeometries(basket, model, prefix);
    }

    void assertGeometries(Element basket, String model, String prefix) {
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var meter = object(basket, model, "Meter", prefix + "-m");
        var smart = object(basket, model, "SmartUnit", prefix + "-s");
        var absent = object(basket, model, "SmartUnit", prefix + "-n");
        var terminal = object(basket, model, "Terminal", prefix + "-t");
        assertPoint(child(meter, "Location"), "2600123.125", "1200456.375");
        assertPoint(child(smart, "Location"), "2600124.125", "1200457.375");
        assertPoint(child(smart, "ExtraLocation"), "2600125.125", "1200458.375");
        assertPoint(child(absent, "Location"), "2600126.125", "1200459.375");
        assertNull(child(meter, "ExtraLocation"));
        assertNull(child(absent, "ExtraLocation"));
        assertNull(child(terminal, "Location"));
    }

    private static void assertPoint(Element point, String x, String y) {
        var coordinates = children(children(point).getFirst());
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(coordinates.get(0).getTextContent())));
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(coordinates.get(1).getTextContent())));
    }

    void assertRegion(Path path, String memo) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        assertEquals("region-b", id(basket, version, "bid"));
        var station = children(basket).stream().filter(element -> "region-s".equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertEquals(memo, child(station, "LocalMemo").getTextContent());
        assertEquals("base", child(station, "Memo").getTextContent());
    }

    Element object(Element basket, String model, String name, String tid) {
        var object = children(basket).stream().filter(element -> tid.equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertEquals(version == 23 ? model + ".Data." + name : name, object.getLocalName());
        return object;
    }

    Path transferWithPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input).replace("fixture-", prefix + "-"));
    }

    Path fixture(String name) throws Exception {
        return fixture("/smart/", name);
    }

    Path fixture(String directory, String name) throws Exception {
        var path = work.resolve(name);
        try (var source = SmartFixtures.class.getResourceAsStream(directory + name)) {
            Files.copy(java.util.Objects.requireNonNull(source, directory + name), path);
        }
        return path;
    }

    void assertReferences(Path file, String model, String prefix) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        var owner = object(basket, model, "Survey", prefix + "-survey");
        var note = children(child(owner, "Details")).getFirst();
        assertEquals(prefix + "-t", id(child(note, "AnyInstrument"), version, "ref"));
        assertEquals(prefix + "-s", id(child(note, "SmartTarget"), version, "ref"));
        assertEquals(prefix + "-m", id(child(owner, "Observed"), version, "ref"));
        object(basket, model, "SmartUnit", prefix + "-s");
        object(basket, model, "Terminal", prefix + "-t");
    }

    void assertPolymorphicReferences(Path file, String model, String prefix) throws Exception {
        assertReferences(file, model, prefix);
        var basket = children(readDataSection(file, version)).getFirst();
        var links = children(basket).stream().filter(element -> element.getLocalName().endsWith("CrossCheck")).toList();
        assertEquals(2, links.size());
        assertEquals(Set.of(prefix + "-t", prefix + "-s"), links.stream()
                .map(link -> id(child(link, "CheckedInstrument"), version, "ref")).collect(Collectors.toSet()));
        for (var link : links) assertEquals(prefix + "-survey", id(child(link, "CheckedSurvey"), version, "ref"));
    }

    void assertExtraSurvey(Path path, String model, String prefix) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        var survey = object(basket, model, "ExtraSurvey", prefix + "-extra");
        assertEquals(prefix + "-m", id(child(survey, "Observed"), version, "ref"));
    }

    void assertScalarObjects(Path path, String model, String prefix, boolean emptyMemo) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        assertEquals(3, children(basket).size());
        var meter = object(basket, model, "Meter", prefix + "-m");
        var smart = object(basket, model, "SmartUnit", prefix + "-s");
        var terminal = object(basket, model, "Terminal", prefix + "-t");
        if (emptyMemo) {
            assertEquals("", child(meter, "Memo").getTextContent());
        } else {
            assertNull(child(meter, "Memo"));
        }
        assertEquals("idle", child(meter, "State").getTextContent());
        assertEquals("5", child(meter, "Counter").getTextContent());
        assertNull(child(meter, "Badge"));
        assertEquals("base", child(smart, "Memo").getTextContent());
        assertEquals("active", child(smart, "State").getTextContent());
        assertEquals("7", child(smart, "Counter").getTextContent());
        assertEquals("tag", child(smart, "Badge").getTextContent());
        assertNull(child(terminal, "Memo"));
        assertEquals("p1", child(terminal, "Socket").getTextContent());
    }

    static void assertScalarStorage(Target db, Connection sql, String model, String layout, String memo, int datasets)
            throws Exception {
        String count = Integer.toString(datasets);
        String meter = db.table("meter");
        if (layout.equals("smart1")) {
            assertEquals(count, scalar(sql, "SELECT count(*) FROM " + meter + " WHERE t_type='meter' AND memo='' AND badge IS NULL"));
            assertEquals(count, scalar(sql, "SELECT count(*) FROM " + meter + " WHERE t_type='smartunit' AND memo='base' AND badge='tag'"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_attrname")
                    + " WHERE iliname='" + model + ".Data.Instrument.Memo' AND colowner IN ('meter','terminal')"));
        } else {
            assertEquals(count, scalar(sql, "SELECT count(*) FROM " + db.table("smartunit")
                    + " WHERE " + memo + "='base' AND counter=7 AND badge='tag'"));
            assertEquals(count, scalar(sql, "SELECT count(*) FROM " + meter + " WHERE memo='' AND counter=5"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("smartunit") + " s JOIN "
                    + meter + " m ON m.t_id=s.t_id"));
        }
        assertEquals(count, scalar(sql, "SELECT count(*) FROM " + db.table("terminal") + " WHERE memo IS NULL"));
    }

    static void assertReferenceStorage(Target db, Connection sql, String layout, int datasets) throws Exception {
        String note = db.table("note");
        String survey = db.table("survey");
        String count = Integer.toString(datasets);
        assertEquals(count, scalar(sql, "SELECT count(*) FROM " + note));
        if (layout.equals("smart1")) {
            String instrument = db.table("instrument");
            assertEquals(count, scalar(sql, "SELECT count(*) FROM " + note + " n JOIN " + instrument
                    + " t ON t.t_id=n.anyinstrument AND t.t_basket=n.t_basket JOIN " + instrument
                    + " s ON s.t_id=n.smarttarget AND s.t_basket=n.t_basket JOIN " + survey
                    + " a ON a.t_id=n.survey_details JOIN " + instrument
                    + " m ON m.t_id=a.observed AND m.t_basket=a.t_basket"
                    + " WHERE t.t_type='terminal' AND s.t_type='smartunit' AND m.t_type='meter'"));
        } else {
            assertEquals(count, scalar(sql, "SELECT count(*) FROM " + note + " n JOIN "
                    + db.table("terminal") + " t ON n.anyinstrument_terminal=t.t_id JOIN "
                    + db.table("smartunit") + " s ON n.smarttarget=s.t_id WHERE n.anyinstrument_meter IS NULL"
                    + " AND n.anyinstrument_smartunit IS NULL AND t.t_basket=s.t_basket AND n.t_basket=t.t_basket"));
            assertEquals(count, scalar(sql, "SELECT count(*) FROM " + survey + " s JOIN " + db.table("meter")
                    + " m ON s.observed_meter=m.t_id WHERE s.observed_terminal IS NULL"
                    + " AND s.observed_smartunit IS NULL AND s.t_basket=m.t_basket"));
            assertEquals(Integer.toString(2 * datasets), scalar(sql, "SELECT count(*) FROM " + db.table("crosscheck")
                    + " WHERE checkedinstrument_meter IS NULL AND ((checkedinstrument_smartunit IS NULL)"
                    + " <> (checkedinstrument_terminal IS NULL))"));
        }
    }
}
