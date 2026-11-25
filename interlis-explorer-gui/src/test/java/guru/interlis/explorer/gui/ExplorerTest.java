package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.explorer.DuckDbStore.StoredObject;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorerTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final Path FIXTURES = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");

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
                  TOPIC Base =
                    CLASS Inherited = END Inherited;
                  END Base;
                  TOPIC Data EXTENDS Base =
                    CLASS Item =
                      count: MANDATORY Dependency.Counter;
                    END Item;
                  END Data;
                  TOPIC Empty =
                    CLASS Spare = END Spare;
                  END Empty;
                END Root.
                """);
        var transfer = Files.writeString(temp.resolve("import.xtf"), """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:explorer-import">
                  <i:headersection><i:models><i:model>Dependency</i:model><i:model>Root</i:model>
                    <i:model>OtherHeaderModel</i:model></i:models>
                    <i:sender>test</i:sender></i:headersection>
                  <i:datasection>
                    <Empty i:bid="empty"/>
                    <Data i:bid="basket"><Item i:tid="example"><count>5</count></Item></Data>
                    <Data i:bid="duplicateTopic"/>
                  </i:datasection>
                </i:transfer>
                """);
        try (var dataset = ExplorerDataset.open(List.of(model), transfer, CancellationToken.none())) {
            assertTrue(dataset.plan().models().stream()
                    .anyMatch(descriptor -> descriptor.name().declared().equals(QualifiedName.of("OtherHeaderModel"))));
            var type = dataset.plan().objects().stream()
                    .filter(descriptor -> descriptor.name().declared().equals(QualifiedName.of("Root", "Data", "Item")))
                    .findFirst().orElseThrow();
            assertEquals("example", dataset.objects(type).getFirst().object().identity().tid().orElseThrow().value());

            var plan = dataset.plan();
            var data = plan.topic(type.topic().orElseThrow()).orElseThrow();
            var empty = plan.topics().stream().filter(topic -> topic.name().declared().last().equals("Empty"))
                    .findFirst().orElseThrow();
            var topics = dataset.store().basketTopics();
            assertEquals(Set.of(data.symbol(), empty.symbol()), topics);
            SwingUtilities.invokeAndWait(() -> checkModelTree(plan, topics));
        }
    }

    private static void checkModelTree(TransferPlan plan, Set<SymbolRef> topics) {
        var filtered = ExplorerFrame.modelTree(plan, Optional.of(topics), Set.of());
        assertEquals(1, filtered.getChildCount());
        var model = (DefaultMutableTreeNode) filtered.getFirstChild();
        assertEquals(QualifiedName.of("Root"), ((ModelDescriptor) model.getUserObject()).name().declared());
        assertEquals(2, model.getChildCount());
        var data = (DefaultMutableTreeNode) model.getChildAt(0);
        assertEquals(QualifiedName.of("Root", "Data"), ((TopicDescriptor) data.getUserObject()).name().declared());
        var empty = (DefaultMutableTreeNode) model.getChildAt(1);
        assertEquals(QualifiedName.of("Root", "Empty"), ((TopicDescriptor) empty.getUserObject()).name().declared());
        assertEquals(1, empty.getChildCount());
        assertEquals(2, data.getChildCount());
        var item = (DefaultMutableTreeNode) data.getFirstChild();
        assertEquals(QualifiedName.of("Root", "Data", "Item"),
                ((ObjectDescriptor) item.getUserObject()).name().declared());
        var inherited = (DefaultMutableTreeNode) data.getLastChild();
        assertEquals(QualifiedName.of("Root", "Base", "Inherited"),
                ((ObjectDescriptor) inherited.getUserObject()).name().declared());

        var tree = new JTree(filtered);
        tree.setRootVisible(false);
        tree.expandPath(new TreePath(empty.getPath()));
        tree.setSelectionPath(new TreePath(inherited.getPath()));
        var full = ExplorerFrame.modelTree(plan, Optional.empty(), Set.of());
        assertEquals(plan.models().size(), full.getChildCount());
        assertTrue(ExplorerFrame.replaceModelTree(tree, full));
        var selected = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        assertEquals(inherited.getUserObject(), selected.getUserObject());
        assertEquals(data.getUserObject(), ((DefaultMutableTreeNode) selected.getParent()).getUserObject());
        var fullModel = (DefaultMutableTreeNode) selected.getParent().getParent();
        assertEquals(3, fullModel.getChildCount());
        var fullEmpty = (DefaultMutableTreeNode) fullModel.getChildAt(2);
        assertTrue(tree.isExpanded(new TreePath(fullEmpty.getPath())));

        var hiddenBase = (DefaultMutableTreeNode) fullModel.getFirstChild();
        tree.setSelectionPath(new TreePath(hiddenBase.getPath()));
        assertFalse(ExplorerFrame.replaceModelTree(tree, ExplorerFrame.modelTree(plan, Optional.of(topics), Set.of())));
        assertTrue(tree.isSelectionEmpty());
        assertEquals(0, ExplorerFrame.modelTree(plan, Optional.of(Set.of()), Set.of()).getChildCount());
    }

    @Test
    void browsesImportedObjectsInModelOrder() throws Exception {
        var fixtures = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");
        var dataset = ExplorerDataset.open(List.of(fixtures.resolve("Reference24.ili")),
                fixtures.resolve("structures-and-link.xtf"), CancellationToken.none());
        try (dataset) {
            var modelNode = (DefaultMutableTreeNode) ExplorerFrame
                    .modelTree(dataset.plan(), Optional.of(dataset.store().basketTopics()), dataset.storedObjectTypes()).getFirstChild();
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
            assertEquals("TID i1", ExplorerDataset.objectLabel(objects.getFirst()));
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
            assertEquals(101, visible.size());
            assertEquals(target, visible.getLast());
            assertEquals(dataset.objects(parentType), visible.subList(0, 100));
            assertEquals(100, dataset.objectsIncluding(parentType, visible.getFirst()).size());
        }
    }

    @Test
    void capturesTheOriginalGenericDomainForEachObject() throws Exception {
        ExplorerDataset.ObjectValidation first;
        ExplorerDataset.ObjectValidation second;
        try (var dataset = ExplorerDataset.open(List.of(FIXTURES.resolve("Generic24.ili")),
                FIXTURES.resolve("generic-domains.xtf"), RUN)) {
            var objects = dataset.objects(type(dataset, "Generic24", "Item"));
            first = dataset.objectValidation(objects.getFirst());
            second = dataset.objectValidation(objects.getLast());
            assertEquals("b1", first.basket().id().value());
            assertEquals("b2", second.basket().id().value());
            assertFalse(first.basket().domainBindings().equals(second.basket().domainBindings()));
            assertTrue(dataset.store().objectContext(Long.MAX_VALUE).isEmpty());
        }
        for (var captured : List.of(first, second)) {
            var report = captured.validate(RUN);
            assertEquals(1, report.inspectedObjects());
            assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, report.result());
            assertTrue(report.coverage().missingRuntimeContext().isEmpty());
        }
    }

    @Test
    void browsesAndValidatesStandaloneAssociationsWithoutIdentifiers() throws Exception {
        var model = Files.writeString(temp.resolve("Relations.ili"), """
                INTERLIS 2.4;
                MODEL Relations AT "https://example.org" VERSION "1" =
                  DOMAIN Point = COORD 0.00..100.00, 0.00..100.00;
                  TOPIC Data =
                    CLASS Item = viaPoint : REFERENCE TO Waypoint; END Item;
                    CLASS Waypoint = OID AS INTERLIS.UUIDOID; END Waypoint;
                    ASSOCIATION Peer =
                      source -- {0..*} Item;
                      destination -- {0..*} Item;
                      via -- {0..1} Waypoint;
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01;
                    END Peer;
                    ASSOCIATION Unused =
                      firstItem -- {0..*} Item;
                      secondItem -- {0..*} Item;
                    END Unused;
                  END Data;
                END Relations.
                """);
        var transfer = Files.writeString(temp.resolve("peers.xtf"), """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                    xmlns="http://www.interlis.ch/xtf/2.4/Relations" xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>Relations</i:model></i:models>
                    <i:sender>validation fixture</i:sender></i:headersection>
                  <i:datasection><Data i:bid="b1">
                    <Item i:tid="first"><viaPoint i:ref="be15e471-c885-46c3-88a5-ec07b309311b"/></Item><Item i:tid="second"/>
                    <Peer><source i:ref="first"/><destination i:ref="second"/>
                      <via i:ref="be15e471-c885-46c3-88a5-ec07b309311b"/></Peer>
                    <Peer><source i:ref="second"/><destination i:ref="missing"/>
                      <shape><g:surface><g:exterior><g:polyline>
                        <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                        <g:coord><g:c1>2</g:c1><g:c2>2</g:c2></g:coord>
                        <g:coord><g:c1>2</g:c1><g:c2>0</g:c2></g:coord>
                        <g:coord><g:c1>0</g:c1><g:c2>2</g:c2></g:coord>
                        <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                      </g:polyline></g:exterior></g:surface></shape>
                    </Peer>
                    <Waypoint i:tid="be15e471-c885-46c3-88a5-ec07b309311b"/>
                  </Data></i:datasection>
                </i:transfer>
                """);
        try (var dataset = ExplorerDataset.open(List.of(model), transfer, RUN)) {
            var root = ExplorerFrame.modelTree(dataset.plan(), Optional.of(dataset.store().basketTopics()), dataset.storedObjectTypes());
            var modelNode = (DefaultMutableTreeNode) root.getFirstChild();
            var topicNode = (DefaultMutableTreeNode) modelNode.getFirstChild();
            assertEquals(3, topicNode.getChildCount());
            var itemNode = (DefaultMutableTreeNode) topicNode.getFirstChild();
            assertEquals(type(dataset, "Relations", "Item"), itemNode.getUserObject());
            var peerNode = (DefaultMutableTreeNode) topicNode.getLastChild();
            var peer = assertInstanceOf(ObjectDescriptor.class, peerNode.getUserObject());
            assertEquals(QualifiedName.of("Relations", "Data", "Peer"), peer.name().declared());
            var objects = dataset.objects(peer);
            assertEquals(List.of(3L, 4L), objects.stream().map(StoredObject::position).toList());
            var labels = dataset.associationLabels(peer, objects);
            assertEquals(List.of(
                    "Object 3  source → TID first, destination → TID second, via → OID be15e471-c885-46c3-88a5-ec07b309311b",
                    "Object 4  source → TID second, destination → TID missing, via → Absent"),
                    objects.stream().map(row -> labels.get(row.position()).text()).toList());
            var firstLinks = labels.get(3L).links();
            assertEquals(List.of(1L, 2L, 5L), firstLinks.stream().map(ExplorerDataset.Link::targetPosition).toList());
            assertEquals(List.of("source → TID first", "destination → TID second",
                            "via → OID be15e471-c885-46c3-88a5-ec07b309311b"),
                    firstLinks.stream().map(ExplorerDataset.Link::menuLabel).toList());
            var first = labels.get(3L).text();
            assertEquals(List.of("→ TID first", "→ TID second", "→ OID be15e471-c885-46c3-88a5-ec07b309311b"),
                    firstLinks.stream().map(link -> first.substring(link.start(), link.end())).toList());
            assertEquals(List.of(2L), labels.get(4L).links().stream().map(ExplorerDataset.Link::targetPosition).toList());
            var inspector = new InspectorTableModel();
            show(dataset, inspector, dataset.store().objectAt(1).orElseThrow());
            assertEquals(Map.entry("viaPoint", "OID be15e471-c885-46c3-88a5-ec07b309311b"), rows(inspector).getFirst());
            assertEquals(OptionalLong.of(5), inspector.target(0));
            show(dataset, inspector, objects.getFirst());
            assertEquals(Map.entry("shape", "Absent"), rows(inspector).getFirst());
            inspector.toggle(2);
            assertEquals(List.of("shape", "Beziehungen", "Peer", "source", "destination", "via"),
                    rows(inspector).stream().map(Map.Entry::getKey).toList());
            for (var row : objects) {
                assertTrue(row.object().identity().tid().isEmpty());
                assertTrue(row.object().identity().oid().isEmpty());
            }
            assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE,
                    dataset.objectValidation(objects.getFirst()).validate(RUN).result());
            var report = dataset.objectValidation(objects.getLast()).validate(RUN);
            assertEquals(1, report.inspectedObjects());
            assertEquals(List.of(ValidationCode.GEOMETRY_SELF_INTERSECTION),
                    report.examples().stream().map(finding -> finding.code()).toList());
            var finding = report.examples().getFirst();
            assertEquals(new DataLocation.Slot(peer.slots().stream().filter(slot -> slot.name().declared().last().equals("shape"))
                    .findFirst().orElseThrow().symbol()), finding.data().path().getFirst());
            assertEquals(transfer.toUri().toString(), finding.data().source().orElseThrow().value());
        }
    }

    @Test
    void opensExternalReferenceBeyondTheRegularObjectList() throws Exception {
        var model = Files.writeString(temp.resolve("Navigation.ili"), """
                INTERLIS 2.4;
                MODEL Navigation AT "https://example.org" VERSION "1" =
                  TOPIC Data =
                    CLASS Target = OID AS INTERLIS.UUIDOID; END Target;
                    CLASS Source =
                      local : REFERENCE TO Target;
                      remote : REFERENCE TO (EXTERNAL) Target;
                    END Source;
                  END Data;
                END Navigation.
                """);
        String oid = "00000000-0000-4000-8000-%012d";
        String targets = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> "<Target i:tid=\"" + oid.formatted(index) + "\"/>").collect(Collectors.joining());
        var transfer = Files.writeString(temp.resolve("navigation.xtf"), """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                    xmlns="http://www.interlis.ch/xtf/2.4/Navigation">
                  <i:headersection><i:models><i:model>Navigation</i:model></i:models><i:sender>fixture</i:sender></i:headersection>
                  <i:datasection><Data i:bid="source">
                    <Source i:tid="s1"><local i:ref="%s"/><remote i:ref="%s"/></Source>
                  </Data><Data i:bid="targets">%s</Data></i:datasection>
                </i:transfer>
                """.formatted(oid.formatted(101), oid.formatted(101), targets));
        try (var dataset = ExplorerDataset.open(List.of(model), transfer, RUN)) {
            var source = dataset.objects(type(dataset, "Navigation", "Source")).getFirst();
            var inspector = new InspectorTableModel();
            show(dataset, inspector, source);
            assertEquals(List.of(Map.entry("local", "OID " + oid.formatted(101)),
                    Map.entry("remote", "OID " + oid.formatted(101))), rows(inspector));
            assertEquals(OptionalLong.empty(), inspector.target(0));
            var target = dataset.store().objectAt(inspector.target(1).orElseThrow()).orElseThrow();
            assertEquals(oid.formatted(101), target.object().identity().oid().orElseThrow().value());
            var targetType = type(dataset, "Navigation", "Target");
            assertEquals(100, dataset.objects(targetType).size());
            var listed = dataset.objectsIncluding(targetType, target);
            assertEquals(101, listed.size());
            assertEquals(target, listed.getLast());
            assertEquals(100, dataset.objectsIncluding(targetType, listed.getFirst()).size());
        }
    }

    private static ObjectDescriptor type(ExplorerDataset dataset, String model, String type) {
        var name = QualifiedName.of(model, "Data", type);
        return dataset.plan().objects().stream().filter(object -> object.name().declared().equals(name))
                .findFirst().orElseThrow();
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
