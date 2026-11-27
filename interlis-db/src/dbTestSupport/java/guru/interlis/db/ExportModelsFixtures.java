package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.headerModels;
import static guru.interlis.db.ModelFixtures.mergeDeliveries;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExportModelsFixtures {
    final String grand;
    final String parent;
    final String child;
    final String otherBase;
    final String otherChild;
    final Path source;
    final Path delivery;
    private final int version;

    ExportModelsFixtures(Path work, int version) throws Exception {
        this.version = version;
        grand = "NorthGrand" + version;
        parent = "NorthParent" + version;
        child = "NorthChild" + version;
        otherBase = "SouthParent" + version;
        otherChild = "SouthChild" + version;
        source = Files.writeString(work.resolve("RegionalModels.ili"), """
                INTERLIS 2.%d;
                MODEL %s AT "https://example.org/export" VERSION "1" =
                  TOPIC Core = CLASS NorthNote = Label : TEXT*20; END NorthNote; END Core;
                END %s.
                MODEL %s AT "https://example.org/export" VERSION "1" =
                  IMPORTS %s;
                  TOPIC County EXTENDS %s.Core = CLASS CountyNote EXTENDS %s.Core.NorthNote = END CountyNote; END County;
                END %s.
                MODEL %s AT "https://example.org/export" VERSION "1" =
                  IMPORTS %s;
                  TOPIC North EXTENDS %s.County =
                    CLASS RegionNote EXTENDS %s.County.CountyNote = Local : TEXT*20; END RegionNote;
                  END North;
                END %s.
                MODEL %s AT "https://example.org/export" VERSION "1" =
                  TOPIC Core = CLASS SouthNote = Label : TEXT*20; END SouthNote; END Core;
                END %s.
                MODEL %s AT "https://example.org/export" VERSION "1" =
                  IMPORTS %s;
                  TOPIC South EXTENDS %s.Core =
                    CLASS LocalNote EXTENDS %s.Core.SouthNote = Local : TEXT*20; END LocalNote;
                  END South;
                END %s.
                """.formatted(version - 20, grand, grand, parent, grand, grand, grand, parent,
                child, parent, parent, parent, child, otherBase, otherBase, otherChild, otherBase, otherBase, otherBase, otherChild));
        var north = Files.writeString(work.resolve("north.xtf"), transfer(child, grand, "North", "RegionNote", "north"));
        var south = Files.writeString(work.resolve("south.xtf"), transfer(otherChild, otherBase, "South", "LocalNote", "south"));
        delivery = mergeDeliveries(work.resolve("regions.xtf"), version, north, south);
    }

    List<String> targets() {
        return List.of(grand, otherBase, parent);
    }

    void createSchema(DbTestSupport.Target db, List<String> models) {
        assertSchemaWithIdentityColumns(db, models.getFirst(), source, flags("--model", models.subList(1, models.size())));
    }

    String[] exportOptions(List<String> models) {
        var options = new ArrayList<>(List.of("--model", otherChild));
        options.addAll(List.of(flags("--export-model", models)));
        return options.toArray(String[]::new);
    }

    static String[] flags(String option, List<String> names) {
        var options = new ArrayList<String>();
        for (var name : names) options.addAll(List.of(option, name));
        return options.toArray(String[]::new);
    }

    void assertDelivery(Path output, List<String> targets) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(targets, headerModels(data, version));
        assertEquals(2, children(data).size());
        assertNote(basket(data, version, parent + ".County", "north-b"), parent + ".County.CountyNote", "north");
        assertNote(basket(data, version, otherBase + ".Core", "south-b"), otherBase + ".Core.SouthNote", "south");
    }

    private void assertNote(org.w3c.dom.Element basket, String name, String label) {
        assertEquals(1, children(basket).size());
        var object = children(basket).getFirst();
        assertName(object, version, name);
        assertEquals(label + "-t", id(object, version, "tid"));
        assertEquals(label, child(object, "Label").getTextContent());
        assertEquals(1, children(object).size());
    }

    private String transfer(String model, String base, String topic, String type, String label) {
        if (version == 23) {
            String tag = model + "." + topic + "." + type;
            String object = "<%1$s TID=\"%2$s-t\"><Label>%2$s</Label><Local>regional</Local></%1$s>".formatted(tag, label);
            return referenceDatasetFixture(model, version, label + "-b", object).replace(model + ".Data", model + "." + topic);
        }
        var object = "<%1$s i:tid=\"%2$s-t\"><b:Label>%2$s</b:Label><Local>regional</Local></%1$s>".formatted(type, label);
        return referenceDatasetFixture(model, version, label + "-b", object)
                .replace("<i:transfer ", "<i:transfer xmlns:b=\"http://www.interlis.ch/xtf/2.4/" + base + "\" ")
                .replace("<Data ", "<" + topic + " ").replace("</Data>", "</" + topic + ">");
    }
}
