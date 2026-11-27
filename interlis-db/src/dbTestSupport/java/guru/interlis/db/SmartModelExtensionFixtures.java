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
import static org.junit.jupiter.api.Assertions.assertNull;

final class SmartModelExtensionFixtures {
    final String original;
    final String added;
    final String specialized;
    final Path originalSource;
    final Path addedSource;
    private final ProfileFixtures transfers;
    private final Path work;
    private final int version;
    private final boolean sharedTable;

    SmartModelExtensionFixtures(Path work, int version, boolean sharedDomains, boolean sharedTable) throws Exception {
        this.work = work;
        this.version = version;
        this.sharedTable = sharedTable;
        transfers = new ProfileFixtures(work, version);
        original = "OriginalSmart" + version;
        added = "AddedSmart" + version;
        specialized = "SpecializedSmart" + version;
        originalSource = source(original, false);
        addedSource = source(added, sharedDomains);
    }

    Path specializedSource() throws Exception {
        return specializedSource("Entry");
    }

    Path specializedSource(String base) throws Exception {
        return subtypeSource(specialized, "SpecializedEntry", base, "Extra");
    }

    Path specializedSourceWithoutFields() throws Exception {
        return subtypeSource(specialized, "SpecializedEntry", "Entry", null);
    }

    Path subtypeSource(String model, String type, String base, String field) throws Exception {
        String declaration = field == null ? "" : field + " : TEXT*20;";
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Data EXTENDS %s.Data =
                    CLASS %s EXTENDS %s.Data.%s = %s END %s;
                  END Data;
                END %s.
                """.formatted(version - 20, model, original, original, type, original, base, declaration, type, model));
    }

    Path specializedInput(String prefix) throws Exception {
        return specializedInput(prefix, "<Extra>local</Extra>");
    }

    Path specializedInputWithoutFields(String prefix) throws Exception {
        return specializedInput(prefix, "");
    }

    private Path specializedInput(String prefix, String fields) throws Exception {
        return transfers.data(specialized, prefix, transfers.object(specialized, "SpecializedEntry", prefix + "-specialized",
                inheritedFields() + fields));
    }

    Path fragmentSource(String model, String type) throws Exception {
        String fields = type + "Fields";
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Data EXTENDS %s.Data =
                    CLASS %s (ABSTRACT) EXTENDS %s.Data.Entry = Detail : TEXT*20; END %s;
                    CLASS %s EXTENDS %s = Score : 0..10; END %s;
                  END Data;
                END %s.
                """.formatted(version - 20, model, original, original, fields, original, fields, type, fields, type, model));
    }

    Path fragmentInput(String model, String type, String prefix) throws Exception {
        return transfers.data(model, prefix, transfers.object(model, type, prefix + "-fragment",
                inheritedFields() + "<Detail>branch</Detail><Score>5</Score>"));
    }

    void assertFragmentDelivery(Path output, String model, String type, String prefix) throws Exception {
        var object = transfers.rows(output, prefix, 1).getFirst();
        ModelFixtures.assertName(object, version, model + ".Data." + type);
        assertEquals(prefix + "-fragment", id(object, version, "tid"));
        assertEquals("inherited", child(object, "Label").getTextContent());
        assertEquals("17", child(object, "Quantity").getTextContent());
        assertEquals("branch", child(object, "Detail").getTextContent());
        assertEquals("5", child(object, "Score").getTextContent());
    }

    private String inheritedFields() {
        return version == 23 ? "<Label>inherited</Label><Quantity>17</Quantity>"
                : "<o:Label xmlns:o=\"http://www.interlis.ch/xtf/2.4/" + original + "\">inherited</o:Label>"
                    + "<o:Quantity xmlns:o=\"http://www.interlis.ch/xtf/2.4/" + original + "\">17</o:Quantity>";
    }

    void assertSpecializedDelivery(Path output, String prefix) throws Exception {
        assertSpecializedDelivery(output, prefix, "local");
    }

    void assertSpecializedDeliveryWithoutFields(Path output, String prefix) throws Exception {
        assertSpecializedDelivery(output, prefix, null);
    }

    private void assertSpecializedDelivery(Path output, String prefix, String extra) throws Exception {
        var object = transfers.rows(output, prefix, 1).getFirst();
        assertEquals(version == 23 ? specialized + ".Data.SpecializedEntry" : "SpecializedEntry", object.getLocalName());
        assertEquals(prefix + "-specialized", id(object, version, "tid"));
        assertEquals("inherited", child(object, "Label").getTextContent());
        assertEquals("17", child(object, "Quantity").getTextContent());
        assertEquals(extra, DbTestSupport.text(object, "Extra"));
    }

    Path externalRoleSource(String model, String target) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Data =
                    DEPENDS ON %s.Data;
                    CLASS SurveyRecord = END SurveyRecord;
                    ASSOCIATION AssetLink =
                      Reports -- {0..*} SurveyRecord;
                      Site (EXTERNAL) -- {0..1} %s.Data.%s;
                    END AssetLink;
                    ASSOCIATION ExactAssetLink =
                      ExactReports -- {0..*} SurveyRecord;
                      ConcreteSite (EXTERNAL) -- {0..1} %s.Data.Entry;
                    END ExactAssetLink;
                  END Data;
                END %s.
                """.formatted(version - 20, model, original, original, original, target, original, model));
    }

    Path externalRoleInput(String model, String prefix) throws Exception {
        return transfers.data(model, prefix, transfers.object(model, "SurveyRecord", prefix + "-entry",
                externalReference("Site", "original-entry"))
                + transfers.object(model, "SurveyRecord", prefix + "-alternate",
                externalReference("ConcreteSite", "original-entry") + externalReference("Site", "original-alternate")));
    }

    private String externalReference(String role, String target) {
        String reference = transfers.reference(role, target);
        return version == 23 ? reference.replace("/>", " BID=\"original-basket\"/>") : reference;
    }

    void assertExternalRoleDelivery(Path output, String model, String prefix) throws Exception {
        var rows = transfers.rows(output, prefix, 2);
        for (var row : rows) {
            ModelFixtures.assertName(row, version, model + ".Data.SurveyRecord");
        }
        var alternate = rows.getFirst();
        var entry = rows.getLast();
        assertEquals(prefix + "-alternate", id(alternate, version, "tid"));
        assertEquals(prefix + "-entry", id(entry, version, "tid"));
        assertEquals("original-alternate", id(child(alternate, "Site"), version, "ref"));
        assertEquals("original-entry", id(child(alternate, "ConcreteSite"), version, "ref"));
        assertEquals("original-entry", id(child(entry, "Site"), version, "ref"));
        assertNull(child(entry, "ConcreteSite"));
    }

    private Path source(String name, boolean sharedDomains) throws Exception {
        String domains = sharedDomains ? "IMPORTS " + original + ";" : "DOMAIN Caption = TEXT*30; Amount = 0..100;";
        String qualifier = sharedDomains ? original + "." : "";
        String alternate = sharedTable ? "EXTENDS Entry = BranchNote : TEXT*20;" : "EXTENDS BaseAsset =";
        return Files.writeString(work.resolve(name + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  %s
                  TOPIC Data =
                    CLASS BaseAsset (ABSTRACT) = Label : %sCaption; END BaseAsset;
                    CLASS Entry EXTENDS BaseAsset = Quantity : %sAmount; END Entry;
                    CLASS Alternate %s END Alternate;
                  END Data;
                END %s.
                """.formatted(version - 20, name, domains, qualifier, qualifier, alternate, name));
    }

    Path input(String model, String prefix) throws Exception {
        return transfers.data(model, prefix,
                transfers.object(model, "Entry", prefix + "-entry", "<Label>entry</Label><Quantity>7</Quantity>")
                        + transfers.object(model, "Alternate", prefix + "-alternate", "<Label>alternate</Label>"
                        + (sharedTable ? "<BranchNote>branch</BranchNote>" : "")));
    }

    void leaveOnlyOriginalEntry() throws Exception {
        Files.writeString(originalSource, Files.readString(originalSource).replaceFirst("(?m)^.*CLASS Alternate.*\\R", ""));
    }

    Path entryInput(String prefix) throws Exception {
        return transfers.data(original, prefix,
                transfers.object(original, "Entry", prefix + "-entry", "<Label>entry</Label><Quantity>7</Quantity>"));
    }

    void assertEntryDelivery(Path output, String prefix) throws Exception {
        var row = transfers.rows(output, prefix, 1).getFirst();
        ModelFixtures.assertName(row, version, original + ".Data.Entry");
        assertEquals(prefix + "-entry", id(row, version, "tid"));
        assertEquals("entry", child(row, "Label").getTextContent());
        assertEquals("7", child(row, "Quantity").getTextContent());
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
                if (sharedTable) assertEquals("branch", child(row, "BranchNote").getTextContent());
            }
        }
        assertEquals(2, rows.stream().map(row -> id(row, version, "tid")).distinct().count());
    }

    List<String> originalRows(Target db) throws Exception {
        String entry = tableFor(db, original + ".Data.Entry");
        String alternate = sharedTable ? entry : tableFor(db, original + ".Data.Alternate");
        try (var sql = db.connect()) {
            return List.of(
                    scalar(sql, "SELECT t_id || ':' || t_basket || ':' || t_ili_tid || ':' || label || ':' || quantity FROM "
                            + db.table(entry) + " WHERE t_ili_tid='original-entry'"),
                    scalar(sql, "SELECT t_id || ':' || t_basket || ':' || t_ili_tid || ':' || label FROM " + db.table(alternate)
                            + " WHERE t_ili_tid='original-alternate'"),
                    scalar(sql, "SELECT t_id || ':' || dataset || ':' || t_ili_tid FROM " + db.table("t_ili2db_basket")
                            + " WHERE t_ili_tid='original-basket'"),
                    scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='original'"));
        }
    }
}
