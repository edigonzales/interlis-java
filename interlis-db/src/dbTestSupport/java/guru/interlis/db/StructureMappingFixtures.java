package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StructureMappingFixtures {
    private static final String VALUES = "<Label>é &amp; a</Label><Flag>true</Flag><Code>high</Code><Quantity>-2</Quantity>";

    private final Path work;
    private final int version;

    StructureMappingFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path expandModel(String model) throws Exception {
        return source(model, """
                    STRUCTURE Wrapper =
                      !!@ili2db.mapping=EXPAND
                      Detail : Entry;
                    END Wrapper;
                """, """
                    CLASS Asset =
                      Detail0_Label : TEXT*31;
                      !!@ili2db.mapping=EXPAND
                      Detail : Entry;
                      !!@ili2db.mapping=EXPAND
                      Items : LIST {0..5} OF Entry;
                      !!@ili2db.mapping=EXPAND
                      Bagged : BAG {0..4} OF Entry;
                      !!@ili2db.mapping=EXPAND
                      Larger : LIST {0..6} OF Entry;
                      !!@ili2db.mapping=EXPAND
                      Nest : Wrapper;
                    END Asset;
                """);
    }

    Path smartModel(String model) throws Exception {
        return source(model, "", """
                    CLASS AssetBase (ABSTRACT) =
                      !!@ili2db.mapping=EXPAND
                      Items : LIST {0..2} OF Entry;
                    END AssetBase;
                    CLASS FirstAsset EXTENDS AssetBase =
                      Marker : TEXT*20;
                    END FirstAsset;
                    CLASS OtherAsset EXTENDS AssetBase =
                      Caption : TEXT*20;
                    END OtherAsset;
                """);
    }

    Path mixedModel(String model) throws Exception {
        return source(model, """
                    !!@ili2db.mapping=EXPAND
                    Items : LIST {0..2} OF Entry;
                    !!@ili2db.mapping=JSON
                    Doc : Entry;
                    !!@ili2db.mapping=JSON
                    Copies : BAG {0..2} OF Entry;
                """);
    }

    Path collectionsModel(String model) throws Exception {
        return source(model, "STRUCTURE Wrapper = Detail : Entry; END Wrapper;", """
                    CLASS Asset =
                      !!@ili2db.mapping=JSON
                      Items : LIST {0..3} OF Entry;
                      !!@ili2db.mapping=JSON
                      Bagged : BAG {0..3} OF Entry;
                      !!@ili2db.mapping=JSON
                      Nest : Wrapper;
                    END Asset;
                """);
    }

    String fiveItems(String model) {
        return entry(model, field("Label", "c")) + entry(model, field("Label", "a")) + entry(model, "")
                + entry(model, field("Label", "d")) + entry(model, field("Label", "b"));
    }

    Path expandDataset(String model, String prefix) throws Exception {
        String duplicate = entry(model, field("Label", "duplicate"));
        String payload = field("Detail0_Label", "ordinary") + field("Detail", entry(model, VALUES))
                + field("Items", fiveItems(model)) + field("Bagged", duplicate + duplicate + entry(model, ""))
                + field("Larger", entry(model, field("Label", "separate row")))
                + field("Nest", field(type(model, "Wrapper"), field("Detail", entry(model, VALUES))));
        return dataset(model, prefix, asset(model, prefix + "-value", payload)
                + asset(model, prefix + "-detail-empty", field("Detail", entry(model, "")))
                + asset(model, prefix + "-without-inner", field("Nest", field(type(model, "Wrapper"), "")))
                + asset(model, prefix + "-nested-empty", field("Nest", field(type(model, "Wrapper"), field("Detail", entry(model, "")))))
                + asset(model, prefix + "-absent", ""));
    }

    void assertExpanded(Path output, String prefix) throws Exception {
        var rows = objects(output, prefix);
        assertEquals(5, rows.size());
        var value = rows.get(prefix + "-value");
        assertEquals("ordinary", child(value, "Detail0_Label").getTextContent());
        assertEntry(members(value, "Detail").getFirst());
        assertEntry(members(members(value, "Nest").getFirst(), "Detail").getFirst());
        assertEquals("separate row", child(members(value, "Larger").getFirst(), "Label").getTextContent());
        assertEquals(List.of("c", "a", "", "d", "b"), members(value, "Items").stream()
                .map(item -> item.getTextContent().strip()).toList());
        assertEquals(List.of("", "duplicate", "duplicate"), members(value, "Bagged").stream()
                .map(item -> item.getTextContent().strip()).sorted().toList());
        assertEquals(List.of(), children(members(rows.get(prefix + "-detail-empty"), "Detail").getFirst()));
        assertNull(child(members(rows.get(prefix + "-without-inner"), "Nest").getFirst(), "Detail"));
        var nestedEmpty = members(rows.get(prefix + "-nested-empty"), "Nest").getFirst();
        assertEquals(List.of(), children(members(nestedEmpty, "Detail").getFirst()));
        assertEquals(List.of(), children(rows.get(prefix + "-absent")));
    }

    Path smartDataset(String model, String prefix, List<String> values) throws Exception {
        var entries = values.stream().map(value -> entry(model, field("Label", value))).collect(Collectors.joining());
        return dataset(model, prefix, object(model, "FirstAsset", prefix + "-first",
                (entries.isEmpty() ? "" : field("Items", entries)) + field("Marker", "primary"))
                + object(model, "OtherAsset", prefix + "-other", field("Items", entry(model, field("Label", "other")))
                + field("Caption", "secondary")));
    }

    void assertSmart(Path output, String prefix, List<String> labels) throws Exception {
        var rows = objects(output, prefix);
        assertEquals(2, rows.size());
        assertEquals(labels, members(rows.get(prefix + "-first"), "Items").stream()
                .map(value -> child(value, "Label").getTextContent()).toList());
        assertEquals("other", child(members(rows.get(prefix + "-other"), "Items").getFirst(), "Label").getTextContent());
        assertEquals("primary", child(rows.get(prefix + "-first"), "Marker").getTextContent());
        assertEquals("secondary", child(rows.get(prefix + "-other"), "Caption").getTextContent());
    }

    Path jsonCollectionDataset(String model, String prefix, boolean includeNested) throws Exception {
        var first = entry(model, field("Label", "z"));
        var last = entry(model, field("Label", "a"));
        var empty = entry(model, "");
        var nested = field(type(model, "Wrapper"), field("Detail", empty));
        return dataset(model, prefix, asset(model, prefix + "-many", field("Items", first + empty + last)
                + field("Bagged", first + first + empty) + (includeNested ? field("Nest", nested) : ""))
                + asset(model, prefix + "-one", field("Items", entry(model, VALUES)))
                + asset(model, prefix + "-missing", ""));
    }

    void assertJsonCollections(Path output, String prefix, boolean includeNested) throws Exception {
        var objects = objects(output, prefix);
        var many = objects.get(prefix + "-many");
        assertEquals(List.of("z", "", "a"), members(many, "Items").stream().map(item -> item.getTextContent().strip()).toList());
        assertEquals(List.of("", "z", "z"), members(many, "Bagged").stream().map(item -> item.getTextContent().strip()).sorted().toList());
        if (includeNested) {
            assertEquals(List.of(), children(members(members(many, "Nest").getFirst(), "Detail").getFirst()));
        } else {
            assertNull(child(many, "Nest"));
        }
        assertEntry(members(objects.get(prefix + "-one"), "Items").getFirst());
        assertEquals(List.of(), children(objects.get(prefix + "-missing")));
    }

    Path mixedDataset(String model, String prefix, boolean updated) throws Exception {
        String first = entry(model, field("Label", updated ? "updated" : "first"));
        String second = entry(model, field("Label", "second"));
        String payload = field("Items", first + (updated ? "" : second))
                + (updated ? "" : field("Doc", entry(model, VALUES))) + field("Copies", first + first);
        return dataset(model, prefix, asset(model, prefix + "-value", payload)
                + asset(model, prefix + "-empty-doc", field("Doc", entry(model, "")))
                + asset(model, prefix + "-absent", ""));
    }

    void assertMixed(Path output, String prefix, boolean updated) throws Exception {
        var rows = objects(output, prefix);
        assertEquals(3, rows.size());
        var value = rows.get(prefix + "-value");
        assertNull(child(rows.get(prefix + "-absent"), "Doc"));
        assertEquals(List.of(), children(members(rows.get(prefix + "-empty-doc"), "Doc").getFirst()));
        assertEquals(updated ? List.of("updated") : List.of("first", "second"), members(value, "Items").stream()
                .map(item -> child(item, "Label").getTextContent()).toList());
        if (updated) assertNull(child(value, "Doc"));
        else assertEntry(members(value, "Doc").getFirst());
        assertEquals(updated ? List.of("updated", "updated") : List.of("first", "first"), members(value, "Copies").stream()
                .map(item -> child(item, "Label").getTextContent()).toList());
    }

    private static void assertEntry(Element item) {
        assertEquals("é & a", child(item, "Label").getTextContent());
        assertEquals("true", child(item, "Flag").getTextContent());
        assertEquals("high", child(item, "Code").getTextContent());
        assertEquals("-2", child(item, "Quantity").getTextContent());
    }

    Path source(String model, String fields) throws Exception {
        return source(model, "", "CLASS Asset =\n" + fields + "\nEND Asset;");
    }

    Path source(String model, String structures, String classes) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    STRUCTURE Entry =
                      Label : TEXT*30;
                      Flag : BOOLEAN;
                      Code : (low, high);
                      Quantity : -20 .. 20;
                    END Entry;
                %s
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, structures, classes, model));
    }

    Path dataset(String model, String prefix, String contents) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-b", contents));
    }

    private String type(String model, String type) { return version == 23 ? model + ".Data." + type : type; }

    String asset(String model, String tid, String contents) {
        return object(model, "Asset", tid, contents);
    }

    private String object(String model, String name, String tid, String contents) {
        String tag = type(model, name);
        return "<" + tag + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + contents + "</" + tag + ">";
    }

    String entry(String model, String fields) { return field(type(model, "Entry"), fields); }

    static String field(String name, String contents) { return "<" + name + ">" + contents + "</" + name + ">"; }

    Map<String, Element> objects(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-b", id(baskets.getFirst(), version, "bid"));
        return children(baskets.getFirst()).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }

    static List<Element> members(Element object, String field) {
        return children(object).stream().filter(value -> value.getLocalName().equals(field))
                .flatMap(value -> children(value).stream()).toList();
    }

    static String jsonKey(Target db, String column, String key) {
        return db.postgis() ? column + "->>'" + key + "'" : "json_extract(" + column + ",'$." + key + "')";
    }

}
