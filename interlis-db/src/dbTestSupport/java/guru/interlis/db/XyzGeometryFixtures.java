package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class XyzGeometryFixtures {
    private final Path work;
    private final int version;
    final String model;
    private final Path source;

    XyzGeometryFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "DbXyz" + version;
        source = ModelFixtures.fixture(work, model + ".ili");
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, model, source, "--create-geom-index");
    }

    Path input() throws Exception {
        String shell = box(2600400, 1200400, 40, "1", "2", "3", "4");
        String hole = box(2600410, 1200410, 10, "11", "12", "13", "14");
        String areas = object("Plot", "plot-1", "<Outline>" + surface(shell, hole) + "</Outline>")
                + object("Parcel", "west", "<Perimeter>" + surface(
                        box(2600500, 1200500, 20, "21", "22", "23", "24"), "") + "</Perimeter>")
                + object("Parcel", "east", "<Perimeter>" + surface(
                        box(2600520, 1200500, 20, "22", "32", "33", "23"), "") + "</Perimeter>");
        String start = position("2602360", "1202220", "100");
        String arc = arc("2602240", "1202380", "340", "2602380", "1202360");
        String curved = object("Track", "arc-track", "<Trace>" + line(start + arc) + "</Trace>")
                + object("Plot", "arc-plot", "<Outline>" + surface(line(start + arc + start), "") + "</Outline>");
        String multi = version == 24 ? collections(start, arc, shell, hole) : "";
        return delivery("xyz", markers("42.875") + track() + areas + curved + multi);
    }

    Path lineWithMissingHeight() throws Exception {
        var broken = object("Track", "broken", "<Trace>" + line(
                position("2600300.125", "1200300.375", "7.500") + position("2600320.375", "1200340.750")) + "</Trace>");
        return delivery("xyz-broken", markers("43.875") + track() + broken);
    }

    Path mixedCollection() throws Exception {
        var broken = object("Survey", "wrong-dimension", "<Locations><g:multicoord>"
                + position("2600600", "1200600", "13") + position("2600620", "1200620") + "</g:multicoord></Locations>");
        return delivery("xyz-mixed", markers("43.875") + broken);
    }

    private String markers(String height) {
        return object("Marker", "above", "<Position>" + position("2600000.125", "1200000.375", height) + "</Position>")
                + object("Marker", "below", "<Position>" + position("2600010.250", "1200020.500", "-31.125") + "</Position>")
                + object("Marker", "absent", "");
    }

    private String track() {
        return object("Track", "track-1", "<Trace>" + line(
                position("2600300.125", "1200300.375", "7.500")
                + position("2600310.250", "1200320.500", "-11.250")
                + position("2600320.375", "1200340.750", "12.875")) + "</Trace>");
    }

    void assertTransfer(Path file) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(model), headerModels(data, version));
        assertEquals(1, children(data).size());
        var basket = basket(data, version, model + ".Data", "xyz-basket");
        var objects = children(basket);
        var expectedIds = new HashSet<>(List.of("above", "below", "absent", "track-1", "plot-1", "west", "east", "arc-track", "arc-plot"));
        if (version == 24) expectedIds.addAll(List.of("survey", "survey-empty", "network", "park", "district"));
        assertEquals(expectedIds.size(), objects.size());
        assertEquals(expectedIds, objects.stream().map(object -> id(object, version, "tid")).collect(Collectors.toSet()));
        for (var object : objects) {
            switch (id(object, version, "tid")) {
                case "above" -> assertHeights(object, "Position", "42.875");
                case "below" -> assertHeights(object, "Position", "-31.125");
                case "absent" -> assertNull(child(object, "Position"));
                case "track-1" -> assertHeights(object, "Trace", "7.5", "-11.25", "12.875");
                case "plot-1" -> assertHeights(object, "Outline", "1", "2", "3", "4", "1", "11", "12", "13", "14", "11");
                case "west" -> assertHeights(object, "Perimeter", "21", "22", "23", "24", "21");
                case "east" -> assertHeights(object, "Perimeter", "22", "32", "33", "23", "22");
                case "arc-track" -> assertHeights(object, "Trace", "100", "340");
                case "arc-plot" -> assertHeights(object, "Outline", "100", "340", "100");
                case "survey" -> assertHeights(object, "Locations", "-113", "502.25");
                case "survey-empty" -> assertNull(child(object, "Locations"));
                case "network" -> assertHeights(object, "Routes", "100", "340", "13", "-14");
                case "park" -> assertHeights(object, "Grounds", "1", "2", "3", "4", "1", "11", "12", "13", "14", "11",
                        "100", "340", "100");
                case "district" -> assertHeights(object, "Territory", "5", "6", "7", "8", "5", "9", "10", "11", "12", "9");
                default -> throw new AssertionError("Unexpected object " + id(object, version, "tid"));
            }
        }
    }

    private void assertHeights(Element object, String field, String... expected) {
        var elements = child(object, field).getElementsByTagNameNS("*", version == 23 ? "C3" : "c3");
        assertEquals(expected.length, elements.getLength(), field);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(0, new BigDecimal(expected[i]).compareTo(new BigDecimal(elements.item(i).getTextContent())), field);
        }
    }

    private String collections(String start, String arc, String shell, String hole) {
        return object("Survey", "survey", "<Locations><g:multicoord>"
                + position("2600600", "1200600", "-113") + position("2600620", "1200620", "502.25")
                + "</g:multicoord></Locations>")
                + object("Survey", "survey-empty", "")
                + object("Network", "network", "<Routes><g:multipolyline>" + line(start + arc)
                        + line(position("2600650", "1200600", "13") + position("2600660", "1200620", "-14"))
                        + "</g:multipolyline></Routes>")
                + object("Park", "park", "<Grounds><g:multisurface>" + surface(shell, hole)
                        + surface(line(start + arc + start), "") + "</g:multisurface></Grounds>")
                + object("District", "district", "<Territory><g:multisurface>"
                        + surface(box(2600700, 1200700, 10, "5", "6", "7", "8"), "")
                        + surface(box(2600800, 1200800, 10, "9", "10", "11", "12"), "")
                        + "</g:multisurface></Territory>");
    }

    private String arc(String x, String y, String z, String supportX, String supportY) {
        if (version == 23) {
            return "<ARC><C1>%s</C1><C2>%s</C2><C3>%s</C3><A1>%s</A1><A2>%s</A2></ARC>"
                    .formatted(x, y, z, supportX, supportY);
        }
        return "<g:arc><g:c1>%s</g:c1><g:c2>%s</g:c2><g:c3>%s</g:c3><g:a1>%s</g:a1><g:a2>%s</g:a2></g:arc>"
                .formatted(x, y, z, supportX, supportY);
    }

    private String box(int x, int y, int width, String first, String second, String third, String fourth) {
        return line(position(Integer.toString(x), Integer.toString(y), first)
                + position(Integer.toString(x + width), Integer.toString(y), second)
                + position(Integer.toString(x + width), Integer.toString(y + width), third)
                + position(Integer.toString(x), Integer.toString(y + width), fourth)
                + position(Integer.toString(x), Integer.toString(y), first));
    }

    private String surface(String shell, String hole) {
        if (version == 23) {
            return "<SURFACE><BOUNDARY>" + shell + "</BOUNDARY>"
                    + (hole.isEmpty() ? "" : "<BOUNDARY>" + hole + "</BOUNDARY>") + "</SURFACE>";
        }
        return "<g:surface><g:exterior>" + shell + "</g:exterior>"
                + (hole.isEmpty() ? "" : "<g:interior>" + hole + "</g:interior>") + "</g:surface>";
    }

    private String line(String positions) {
        String tag = version == 23 ? "POLYLINE" : "g:polyline";
        return "<" + tag + ">" + positions + "</" + tag + ">";
    }

    private Path delivery(String name, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, "xyz-basket", objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(name + ".xtf"), text);
    }

    private String object(String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private String position(String... ordinates) {
        String tag = version == 23 ? "COORD" : "g:coord";
        var xml = new StringBuilder("<").append(tag).append('>');
        for (int index = 0; index < ordinates.length; index++) {
            String axis = (version == 23 ? "C" : "g:c") + (index + 1);
            xml.append('<').append(axis).append('>').append(ordinates[index]).append("</").append(axis).append('>');
        }
        return xml.append("</").append(tag).append('>').toString();
    }
}
