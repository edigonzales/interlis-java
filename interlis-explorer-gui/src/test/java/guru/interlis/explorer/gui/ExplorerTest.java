package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.tree.DefaultMutableTreeNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorerTest {
    @TempDir Path temp;

    @Test
    void browsesImportedObjectsInModelOrder() throws Exception {
        var fixtures = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");
        var dataset = ExplorerDataset.open(List.of(fixtures.resolve("Reference24.ili")),
                fixtures.resolve("structures-and-link.xtf"), CancellationToken.none());
        try (dataset) {
            var modelNode = (DefaultMutableTreeNode) ExplorerFrame.modelTree(dataset.plan()).getFirstChild();
            var model = assertInstanceOf(ModelDescriptor.class, modelNode.getUserObject());
            assertEquals(QualifiedName.of("Reference24"), model.name().declared());
            var topicNode = (DefaultMutableTreeNode) modelNode.getFirstChild();
            var topic = assertInstanceOf(TopicDescriptor.class, topicNode.getUserObject());
            assertEquals(QualifiedName.of("Reference24", "Data"), topic.name().declared());
            assertEquals(2, topicNode.getChildCount());

            var itemNode = (DefaultMutableTreeNode) topicNode.getChildAt(1);
            var item = assertInstanceOf(ObjectDescriptor.class, itemNode.getUserObject());
            assertEquals(QualifiedName.of("Reference24", "Data", "Item"), item.name().declared());
            var objects = dataset.objects(item);
            assertEquals(1, objects.size());
            var object = objects.getFirst().object();
            assertEquals("i1", object.identity().tid().orElseThrow().value());
            var inspector = new InspectorTableModel();
            inspector.show(dataset.plan(), object.data());
            var collapsed = List.of(
                    Map.entry("required", "yes"),
                    Map.entry("details", "LIST OF Reference24.Detail (2)"),
                    Map.entry("count", "5"),
                    Map.entry("points", "Absent"),
                    Map.entry("trace", "Absent"),
                    Map.entry("traces", "Absent"),
                    Map.entry("shape", "Absent"),
                    Map.entry("shapes", "Absent"));
            assertEquals(collapsed, rows(inspector));
            assertFalse(inspector.expandable(0));
            assertTrue(inspector.expandable(1));
            assertFalse(inspector.expanded(1));

            inspector.toggle(1);
            assertEquals(List.of(Map.entry("1", "Reference24.Detail"), Map.entry("2", "Reference24.Detail")),
                    rows(inspector).subList(2, 4));
            inspector.toggle(2);
            inspector.toggle(4);
            assertEquals(List.of(Map.entry("1", "Reference24.Detail"), Map.entry("label", "second"),
                    Map.entry("2", "Reference24.Detail"), Map.entry("label", "first")),
                    rows(inspector).subList(2, 6));
            assertEquals(2, inspector.depth(3));
            assertEquals(Map.entry("count", "5"), rows(inspector).get(6));

            inspector.toggle(1);
            assertEquals(collapsed, rows(inspector));
            inspector.toggle(1);
            assertFalse(inspector.expanded(2));
            inspector.show(dataset.plan(), object.data());
            assertFalse(inspector.expanded(1));
        }
        assertFalse(Files.exists(dataset.directory()));
    }

    @Test
    void expandsNestedConcreteStructuresAndKeepsUnrankedBagDuplicates() throws Exception {
        var model = Files.writeString(temp.resolve("Nested.ili"), """
                INTERLIS 2.4;
                MODEL Nested AT "https://example.org" VERSION "1" =
                  XMLNS "urn:nested";
                  STRUCTURE Leaf = label : TEXT; END Leaf;
                  STRUCTURE Base = zeta : TEXT; END Base;
                  STRUCTURE Child EXTENDS Base =
                    alpha : BAG {0..*} OF Leaf;
                  END Child;
                  TOPIC Data =
                    CLASS Item =
                      payload : Base;
                      tail : TEXT;
                    END Item;
                  END Data;
                END Nested.
                """);
        var xtf = Files.writeString(temp.resolve("nested.xtf"), """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:nested">
                  <i:headersection><i:models><i:model>Nested</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><Data i:bid="b1"><Item i:tid="i1">
                    <payload><Child>
                      <zeta>inherited</zeta>
                      <alpha><Leaf><label>duplicate</label></Leaf></alpha>
                      <alpha><Leaf><label>duplicate</label></Leaf></alpha>
                    </Child></payload>
                    <tail>after</tail>
                  </Item></Data></i:datasection>
                </i:transfer>
                """);
        try (var dataset = ExplorerDataset.open(List.of(model), xtf, CancellationToken.none())) {
            var item = dataset.plan().objects().stream()
                    .filter(type -> type.name().declared().equals(QualifiedName.of("Nested", "Data", "Item")))
                    .findFirst().orElseThrow();
            var inspector = new InspectorTableModel();
            inspector.show(dataset.plan(), dataset.objects(item).getFirst().object().data());
            var collapsed = List.of(Map.entry("payload", "Nested.Child"), Map.entry("tail", "after"));
            assertEquals(collapsed, rows(inspector));

            inspector.toggle(0);
            assertEquals(List.of(Map.entry("payload", "Nested.Child"), Map.entry("zeta", "inherited"),
                    Map.entry("alpha", "BAG OF Nested.Leaf (2)"), Map.entry("tail", "after")), rows(inspector));
            inspector.toggle(2);
            inspector.toggle(3);
            inspector.toggle(5);
            assertEquals(List.of(Map.entry("", "Nested.Leaf"), Map.entry("label", "duplicate"),
                    Map.entry("", "Nested.Leaf"), Map.entry("label", "duplicate")),
                    rows(inspector).subList(3, 7));
            assertEquals(3, inspector.depth(4));
            assertEquals(Map.entry("tail", "after"), rows(inspector).getLast());

            inspector.toggle(0);
            assertEquals(collapsed, rows(inspector));
        }
    }

    private static List<Map.Entry<String, String>> rows(InspectorTableModel inspector) {
        return IntStream.range(0, inspector.getRowCount())
                .mapToObj(row -> Map.entry(inspector.getValueAt(row, 0), inspector.getValueAt(row, 1)))
                .toList();
    }

}
