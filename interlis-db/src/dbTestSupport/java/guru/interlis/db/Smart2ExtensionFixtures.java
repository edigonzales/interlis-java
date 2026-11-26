package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class Smart2ExtensionFixtures {
    final String original;
    final String added;
    final Path originalSource;
    final Path addedSource;
    private final ProfileFixtures transfers;
    private final Path work;
    private final int version;

    Smart2ExtensionFixtures(Path work, int version, boolean sharedDomains) throws Exception {
        this.work = work;
        this.version = version;
        transfers = new ProfileFixtures(work, version);
        original = "OriginalSmart" + version;
        added = "AddedSmart" + version;
        originalSource = source(original, false);
        addedSource = source(added, sharedDomains);
    }

    private Path source(String name, boolean sharedDomains) throws Exception {
        String domains = sharedDomains ? "IMPORTS " + original + ";" : "DOMAIN Caption = TEXT*30; Amount = 0..100;";
        String qualifier = sharedDomains ? original + "." : "";
        return Files.writeString(work.resolve(name + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  %s
                  TOPIC Data =
                    CLASS BaseAsset (ABSTRACT) = Label : %sCaption; END BaseAsset;
                    CLASS Entry EXTENDS BaseAsset = Quantity : %sAmount; END Entry;
                    CLASS Alternate EXTENDS BaseAsset = END Alternate;
                  END Data;
                END %s.
                """.formatted(version - 20, name, domains, qualifier, qualifier, name));
    }

    Path input(String model, String prefix) throws Exception {
        return transfers.data(model, prefix,
                transfers.object(model, "Entry", prefix + "-entry", "<Label>entry</Label><Quantity>7</Quantity>")
                        + transfers.object(model, "Alternate", prefix + "-alternate", "<Label>alternate</Label>"));
    }

    void assertDelivery(Path output, String model, String prefix) throws Exception {
        var rows = transfers.rows(output, prefix, 2);
        String expectedPrefix = version == 23 ? model + ".Data." : "";
        for (var row : rows) {
            String tid = id(row, version, "tid");
            if (tid.equals(prefix + "-entry")) {
                assertEquals(expectedPrefix + "Entry", row.getLocalName());
                assertEquals("entry", child(row, "Label").getTextContent());
                assertEquals("7", child(row, "Quantity").getTextContent());
            } else {
                assertEquals(prefix + "-alternate", tid);
                assertEquals(expectedPrefix + "Alternate", row.getLocalName());
                assertEquals("alternate", child(row, "Label").getTextContent());
            }
        }
        assertEquals(2, rows.stream().map(row -> id(row, version, "tid")).distinct().count());
    }

    List<String> originalRows(Target db) throws Exception {
        String entry = tableFor(db, original + ".Data.Entry");
        String alternate = tableFor(db, original + ".Data.Alternate");
        try (var sql = db.connect()) {
            return List.of(
                    scalar(sql, "SELECT t_id || ':' || t_basket || ':' || t_ili_tid || ':' || label || ':' || quantity FROM "
                            + db.table(entry)),
                    scalar(sql, "SELECT t_id || ':' || t_basket || ':' || t_ili_tid || ':' || label FROM " + db.table(alternate)),
                    scalar(sql, "SELECT t_id || ':' || dataset || ':' || t_ili_tid FROM " + db.table("t_ili2db_basket")
                            + " WHERE t_ili_tid='original-basket'"),
                    scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='original'"));
        }
    }
}
