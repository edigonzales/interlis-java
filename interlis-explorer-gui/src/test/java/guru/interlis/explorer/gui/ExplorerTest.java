package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.explorer.DuckDbStore.StoredObject;
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
import java.util.OptionalLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorerTest {
    @TempDir Path temp;

    @Test
    void resolvesLocalModelImportsFromNeighbouringSources() throws Exception {
        Files.writeString(temp.resolve("Dependency.ili"), """
                INTERLIS 2.4;
                TYPE MODEL Dependency AT "https://example.org" VERSION "1" =
                  DOMAIN Counter = 0 .. 10;
                END Dependency.
                TYPE MODEL OtherHeaderModel AT "https://example.org" VERSION "1" =
                END OtherHeaderModel.
                """);
        var model = Files.writeString(temp.resolve("Root.ili"), """
                INTERLIS 2.4;
                MODEL Root AT "https://example.org" VERSION "1" =
                  XMLNS "urn:explorer-import";
                  IMPORTS Dependency;
                  TOPIC Data =
                    CLASS Item =
                      count: MANDATORY Dependency.Counter;
                    END Item;
                  END Data;
                END Root.
                """);
        var transfer = Files.writeString(temp.resolve("import.xtf"), """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:explorer-import">
                  <i:headersection><i:models><i:model>Dependency</i:model><i:model>Root</i:model>
                    <i:model>OtherHeaderModel</i:model></i:models>
                    <i:sender>test</i:sender></i:headersection>
                  <i:datasection><Data i:bid="basket"><Item i:tid="example"><count>5</count></Item></Data></i:datasection>
                </i:transfer>
                """);
        try (var dataset = ExplorerDataset.open(List.of(model), transfer, CancellationToken.none())) {
            assertTrue(dataset.plan().models().stream()
                    .anyMatch(descriptor -> descriptor.name().declared().equals(QualifiedName.of("OtherHeaderModel"))));
            var type = dataset.plan().objects().stream()
                    .filter(descriptor -> descriptor.name().declared().equals(QualifiedName.of("Root", "Data", "Item")))
                    .findFirst().orElseThrow();
            assertEquals("example", dataset.objects(type).getFirst().object().identity().tid().orElseThrow().value());
        }
    }

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
            inspector.show(dataset.plan(), object.data(), InspectorRelationships.EMPTY);
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
            inspector.show(dataset.plan(), object.data(), InspectorRelationships.EMPTY);
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
            var data = dataset.objects(item).getFirst().object().data();
            inspector.show(dataset.plan(), data, InspectorRelationships.EMPTY);
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
            String alphaPath = "/Nested.Data.Item.payload/Nested.Child.alpha/";
            assertEquals(-1, inspector.revealOccurrence(data, alphaPath + "2/Nested.Leaf.label"));
            assertEquals(collapsed, rows(inspector));
            int secondDuplicate = inspector.revealOccurrence(data, alphaPath + "1/Nested.Leaf.label");
            assertEquals(List.of(0, 1, 1, 0), inspector.pathTo(secondDuplicate));
            assertEquals(Map.entry("label", "duplicate"), rows(inspector).get(secondDuplicate));
        }
    }

    @Test
    void navigatesNestedReferenceAndShowsBothAssociationFormsOnEachEnd() throws Exception {
        var fixtures = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");
        try (var dataset = ExplorerDataset.open(List.of(fixtures.resolve("RelationsDemo.ili")),
                fixtures.resolve("relations-demo.xtf"), CancellationToken.none())) {
            var items = dataset.store().objects(QualifiedName.of("RelationsDemo", "Data", "Item"), 2);
            var item = items.getFirst();
            var inspector = new InspectorTableModel();
            show(dataset, inspector, item);
            inspector.toggle(1);
            assertEquals(Map.entry("owner", "TID p1"), rows(inspector).get(2));
            var path = inspector.pathTo(2);
            var parent = dataset.store().objectAt(inspector.target(2).orElseThrow()).orElseThrow();

            show(dataset, inspector, parent);
            assertEquals(List.of(Map.entry("title", "Parent"), Map.entry("Beziehungen", "2"),
                    Map.entry("Link", ""), Map.entry("Review", "")), rows(inspector));
            inspector.toggle(2);
            inspector.toggle(6);
            assertEquals(List.of(Map.entry("parent", "TID p1"), Map.entry("items", "TID i1"),
                    Map.entry("note", "linked")), rows(inspector).subList(3, 6));
            assertEquals(List.of(Map.entry("subject", "TID i1"), Map.entry("reviewer", "TID p1"),
                    Map.entry("remark", "reviewed")), rows(inspector).subList(7, 10));
            assertEquals(OptionalLong.of(item.position()), inspector.target(4));
            assertEquals(OptionalLong.of(item.position()), inspector.target(7));

            show(dataset, inspector, item);
            int restored = inspector.reveal(path);
            assertEquals(Map.entry("owner", "TID p1"), rows(inspector).get(restored));
            assertEquals(OptionalLong.of(parent.position()), inspector.target(restored));
            assertEquals(List.of(Map.entry("Beziehungen", "2"), Map.entry("Link", ""), Map.entry("Review", "")),
                    rows(inspector).subList(3, 6));

            show(dataset, inspector, items.getLast());
            inspector.toggle(1);
            assertEquals(Map.entry("owner", "TID p404"), rows(inspector).getLast());
            assertEquals(OptionalLong.empty(), inspector.target(2));
        }
    }

    @Test
    void includesTheExactReferenceTargetOutsideTheVisibleClassLimit() throws Exception {
        var fixtures = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");
        var extraParents = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> "<Parent i:tid=\"extra" + index + "\"/>").toList();
        var transfer = Files.readString(fixtures.resolve("relations-demo.xtf"));
        var xtf = Files.writeString(temp.resolve("beyond-list.xtf"),
                transfer.replace("<Parent i:tid=\"p1\">", String.join("\n", extraParents) + "<Parent i:tid=\"p1\">"));
        try (var dataset = ExplorerDataset.open(List.of(fixtures.resolve("RelationsDemo.ili")), xtf,
                CancellationToken.none())) {
            var item = dataset.store().objects(QualifiedName.of("RelationsDemo", "Data", "Item"), 1).getFirst();
            var inspector = new InspectorTableModel();
            show(dataset, inspector, item);
            inspector.toggle(1);
            var target = dataset.store().objectAt(inspector.target(2).orElseThrow()).orElseThrow();
            var parentType = dataset.plan().object(target.object().type()).orElseThrow();
            assertFalse(dataset.objects(parentType).contains(target));
            var visible = dataset.objectsIncluding(parentType, target);
            assertEquals(100, visible.size());
            assertEquals(target, visible.getFirst());
            assertEquals(dataset.objects(parentType).subList(0, 99), visible.subList(1, 100));
        }
    }

    private static void show(ExplorerDataset dataset, InspectorTableModel inspector,
                             StoredObject object) throws Exception {
        var relationships = InspectorRelationships.read(dataset.plan(), dataset.store(), object);
        inspector.show(dataset.plan(), object.object().data(), relationships);
    }

    private static List<Map.Entry<String, String>> rows(InspectorTableModel inspector) {
        return IntStream.range(0, inspector.getRowCount())
                .mapToObj(row -> Map.entry(inspector.getValueAt(row, 0), inspector.getValueAt(row, 1)))
                .toList();
    }

}
