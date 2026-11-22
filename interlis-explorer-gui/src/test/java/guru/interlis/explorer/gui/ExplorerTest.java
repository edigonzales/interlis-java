package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ExplorerTest {
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
            assertEquals(List.of(
                    Map.entry("required", "yes"),
                    Map.entry("details", "LIST OF Reference24.Detail (2)"),
                    Map.entry("count", "5"),
                    Map.entry("points", "Absent"),
                    Map.entry("trace", "Absent"),
                    Map.entry("traces", "Absent"),
                    Map.entry("shape", "Absent"),
                    Map.entry("shapes", "Absent")), dataset.attributes(object));
        }
        assertFalse(Files.exists(dataset.directory()));
    }
}
