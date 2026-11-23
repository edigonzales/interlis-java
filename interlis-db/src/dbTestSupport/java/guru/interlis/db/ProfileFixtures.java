package guru.interlis.db;

import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProfileFixtures {
    static final Set<String> LINKED_KEYS = Set.of("asset:t_basket:t_ili2db_basket", "asset:refasset:target",
            "target:t_basket:t_ili2db_basket", "note:t_basket:t_ili2db_basket", "note:asset_notes:asset",
            "t_ili2db_basket:dataset:t_ili2db_dataset");
    static final Set<String> LINKED_INDEXES = Set.of("asset:t_basket", "asset:refasset", "target:t_basket",
            "note:t_basket", "note:asset_notes", "t_ili2db_basket:dataset");

    private final Path work;
    private final int version;

    ProfileFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path source(String model, String declarations) throws IOException {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, declarations, model));
    }

    Path linkedModel(String model) throws IOException {
        return source(model, """
                STRUCTURE Note = Message : TEXT*24; END Note;
                CLASS Target = Label : TEXT*24; END Target;
                CLASS Asset = Label : TEXT*24; Notes : LIST OF Note; END Asset;
                ASSOCIATION Assignment =
                  RefAsset -- {0..1} Target;
                  Sources -- {0..*} Asset;
                END Assignment;
                """);
    }

    Path linkedData(String model, String prefix) throws IOException {
        String note = version == 23 ? model + ".Data.Note" : "Note";
        return data(model, prefix, object(model, "Asset", prefix + "-asset",
                "<Label>linked</Label><Notes><" + note + "><Message>present</Message></" + note + "></Notes>"
                        + reference("RefAsset", prefix + "-target"))
                + object(model, "Target", prefix + "-target", "<Label>target</Label>"));
    }

    Path data(String model, String prefix, String objects) throws IOException {
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    String object(String model, String type, String tid, String fields) {
        String tag = version == 23 ? model + ".Data." + type : type;
        String attribute = version == 23 ? "TID" : "i:tid";
        return "<" + tag + " " + attribute + "=\"" + tid + "\">" + fields + "</" + tag + ">";
    }

    String reference(String field, String tid) {
        return "<" + field + (version == 23 ? " REF=\"" : " i:ref=\"") + tid + "\"/>";
    }

    String point(String x, String y) {
        return version == 23 ? "<COORD><C1>" + x + "</C1><C2>" + y + "</C2></COORD>"
                : "<g:coord xmlns:g=\"http://www.interlis.ch/geometry/1.0\"><g:c1>" + x + "</g:c1><g:c2>" + y + "</g:c2></g:coord>";
    }

    List<Element> rows(Path path, String prefix, int count) throws Exception {
        return basketRows(path, prefix + "-basket", count);
    }

    List<Element> basketRows(Path path, String bid, int count) throws Exception {
        var baskets = children(readDataSection(path, version));
        assertEquals(1, baskets.size());
        assertEquals(bid, id(baskets.getFirst(), version, "bid"));
        var rows = children(baskets.getFirst()).stream().sorted(Comparator.comparing(row -> id(row, version, "tid"))).toList();
        assertEquals(count, rows.size());
        return rows;
    }

    void assertLink(Element row, String prefix) {
        assertEquals(prefix + "-asset", id(row, version, "tid"));
        var role = child(row, "RefAsset");
        assertEquals(prefix + "-target", id(role, version, "ref"));
        assertEquals("present", child(children(child(row, "Notes")).getFirst(), "Message").getTextContent());
    }
}
