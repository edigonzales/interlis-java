package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class Smart1StructureExtensionFixtures {
    final String original;
    final String subtype;
    final Path originalSource;
    final Path subtypeSource;
    private final ProfileFixtures transfer;
    private final int version;

    Smart1StructureExtensionFixtures(Path work, int version) throws Exception {
        this.version = version;
        transfer = new ProfileFixtures(work, version);
        original = "SharedMemo" + version;
        subtype = "DetailedMemo" + version;
        originalSource = transfer.source(original, """
                STRUCTURE MemoPart = Caption : TEXT*20; END MemoPart;
                CLASS Entry = OwnMemo : MemoPart; END Entry;
                CLASS Alternate EXTENDS Entry = END Alternate;
                """);
        subtypeSource = Files.writeString(work.resolve(subtype + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Data EXTENDS %s.Data =
                    CLASS DetailEntry EXTENDS %s.Data.Entry =
                      MainMemo : %s.Data.MemoPart;
                      Entries : LIST OF %s.Data.MemoPart;
                    END DetailEntry;
                  END Data;
                END %s.
                """.formatted(version - 20, subtype, original, original, original, original, original, subtype));
    }

    Path originalInput() throws Exception {
        return transfer.data(original, "original", transfer.object(original, "Entry", "original-entry",
                "<OwnMemo>" + note("existing") + "</OwnMemo>"));
    }

    Path subtypeInput(String prefix, List<String> entries) throws Exception {
        String list = version == 23
                ? entries.stream().map(this::note).collect(Collectors.joining("", "<Entries>", "</Entries>"))
                : entries.stream().map(value -> "<Entries>" + note(value) + "</Entries>").collect(Collectors.joining());
        return transfer.data(subtype, prefix, transfer.object(subtype, "DetailEntry", prefix + "-entry",
                "<MainMemo>" + note("single") + "</MainMemo>" + list)
                + transfer.object(subtype, "DetailEntry", prefix + "-empty", ""));
    }

    private String note(String caption) {
        if (version == 23) {
            String type = original + ".Data.MemoPart";
            return "<" + type + "><Caption>" + caption + "</Caption></" + type + ">";
        }
        return "<m:MemoPart xmlns:m=\"http://www.interlis.ch/xtf/2.4/" + original
                + "\"><m:Caption>" + caption + "</m:Caption></m:MemoPart>";
    }

    void assertOriginal(Path output) throws Exception {
        var row = transfer.rows(output, "original", 1).getFirst();
        ModelFixtures.assertName(row, version, original + ".Data.Entry");
        assertEquals("original-entry", id(row, version, "tid"));
        assertEquals("existing", child(children(child(row, "OwnMemo")).getFirst(), "Caption").getTextContent());
    }

    void assertSubtype(Path output, String prefix, List<String> entries) throws Exception {
        var rows = transfer.rows(output, prefix, 2);
        for (var row : rows) ModelFixtures.assertName(row, version, subtype + ".Data.DetailEntry");
        var filled = rows.stream().filter(row -> id(row, version, "tid").equals(prefix + "-entry")).findFirst().orElseThrow();
        assertEquals("single", child(children(child(filled, "MainMemo")).getFirst(), "Caption").getTextContent());
        assertEquals(entries, children(filled).stream().filter(field -> field.getLocalName().equals("Entries"))
                .flatMap(field -> children(field).stream()).map(note -> child(note, "Caption").getTextContent()).toList());
        var empty = rows.stream().filter(row -> id(row, version, "tid").equals(prefix + "-empty")).findFirst().orElseThrow();
        assertNull(child(empty, "MainMemo"));
        assertNull(child(empty, "Entries"));
    }
}
