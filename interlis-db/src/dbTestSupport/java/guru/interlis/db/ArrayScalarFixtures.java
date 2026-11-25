package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static guru.interlis.db.ArrayFixtures.fields;
import static guru.interlis.db.ArrayFixtures.objects;
import static guru.interlis.db.ArrayFixtures.wrappedItems;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.ModelFixtures.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ArrayScalarFixtures {
    static final List<String> LABELS = List.of("é, \"A\"", "a & b", "a & b", "last");
    private static final List<String> FLAGS = List.of("true", "false", "true");
    private static final List<String> NUMBERS = List.of("-5", "0", "12", "-5", "8");
    static final List<String> GRADES = List.of("Family.High", "Low", "Family.High", "Family.Higher");

    private final Path work;
    private final int version;
    private final Path source;
    final String model;
    final String wrappedText;
    final String wrappedGrades;
    final Path input;

    ArrayScalarFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "ArrayScalars" + version;
        wrappedText = version == 23 ? "Labels" : "TextItems";
        wrappedGrades = version == 23 ? "Grades" : "WrappedGrades";
        source = fixture(work, model + ".ili");
        input = fixture(work, model + ".xtf");
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, model, source, "--coalesce-array", "--expand-struct", "--coalesce-json");
    }

    void assertDelivery(Path output, String prefix, boolean referenceExport) throws Exception {
        var rows = objects(output, version, prefix);
        assertEquals(2, rows.size());
        assertEquals(List.of(), children(rows.get(prefix + "-missing")));
        var value = rows.get(prefix + "-value");
        assertEquals(LABELS, scalarItems(value, "Labels"));
        assertEquals(List.of("false", "true", "true"), scalarItems(value, "Flags").stream().sorted().toList());
        assertEquals(NUMBERS, scalarItems(value, "Numbers"));
        assertEquals(GRADES, scalarItems(value, "Grades"));
        assertEquals(FLAGS, wrappedItems(value, "FlagItems"));
        if (version == 24) {
            assertEquals(LABELS, wrappedItems(value, "TextItems"));
            assertEquals(NUMBERS, wrappedItems(value, "NumberItems"));
            assertEquals(GRADES, wrappedItems(value, "WrappedGrades"));
            if (referenceExport) {
                var bag = fields(value, "WrappedBag");
                assertEquals(1, bag.size());
                assertEquals(3, children(bag.getFirst()).size());
            }
        }
        assertEquals(List.of("first line\nsecond line"), wrappedItems(value, "Notes"));
        assertEquals(List.of("other", "repeat", "repeat"), wrappedItems(value, "WrappedBag").stream().sorted().toList());
        assertEquals(List.of("one"), wrappedItems(value, "Single"));
        assertEquals(List.of("expanded"), wrappedItems(value, "Detail"));
        assertEquals(List.of("json"), wrappedItems(value, "Doc"));
    }

    private List<String> scalarItems(Element object, String name) {
        return version == 23 ? wrappedItems(object, name) : fields(object, name).stream().map(Element::getTextContent).toList();
    }

    Path nullInput() throws Exception {
        String elem = version == 23 ? model + ".Data.LabelElem" : "LabelElem";
        String contents = "<" + wrappedText + "><" + elem + "><Entry>before</Entry></" + elem + "><" + elem + "/>"
                + "<" + elem + "><Entry>after</Entry></" + elem + "></" + wrappedText + ">";
        String asset = version == 23 ? model + ".Data.Asset" : "Asset";
        String tid = version == 23 ? "TID" : "i:tid";
        String rows = "<" + asset + " " + tid + "=\"null-value\">" + contents + "</" + asset + ">"
                + "<" + asset + " " + tid + "=\"null-missing\"/>";
        return Files.writeString(work.resolve("null-input.xtf"), referenceDatasetFixture(model, version, "null-b", rows));
    }

    void assertNullDelivery(Path output) throws Exception {
        var rows = objects(output, version, "null");
        assertNull(child(rows.get("null-missing"), wrappedText));
        assertEquals(Arrays.asList("before", null, "after"), wrappedItems(rows.get("null-value"), wrappedText));
    }
}
