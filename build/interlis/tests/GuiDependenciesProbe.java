package guru.interlis.gui;

import com.formdev.flatlaf.FlatLightLaf;
import guru.interlis.gui.application.CompileService;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static guru.interlis.gui.GuiCompileProbe.await;
import static guru.interlis.gui.GuiCompileProbe.component;
import static guru.interlis.gui.GuiCompileProbe.drop;
import static guru.interlis.gui.GuiCompileProbe.require;

public final class GuiDependenciesProbe {
    public static void main(String[] args) throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path dir = Files.createTempDirectory(cwd, "dependency-fixtures-");
        Path root = write(dir, "Root", "1", "IMPORTS Left, Right, Missing;");
        Path left = write(dir, "Left", "1", "IMPORTS Shared;");
        Path right = write(dir, "Right", "1", "IMPORTS Shared;");
        Path shared = write(dir, "Shared", "opaque-v2", "DOMAIN Original = TEXT*20;");
        var frame = new AtomicReference<MainFrame>();
        var screenshot = new AtomicReference<BufferedImage>();
        SwingUtilities.invokeAndWait(() -> {
            require(FlatLightLaf.setup(), "look and feel");
            var window = new MainFrame(dir, new CompileService());
            window.setVisible(true);
            frame.set(window);
            window.addFiles(List.of(root, left, right, shared));
        });
        MainFrame window = frame.get();
        try {
            await(window, "session-inputs", "4 files");
            SwingUtilities.invokeAndWait(() -> {
                component(window, "local-models", JList.class).setSelectedIndex(0);
                component(window, "use-selected-roots", JButton.class).doClick();
                component(window, "compile", JButton.class).doClick();
            });
            await(window, "session-status", "Rejected");
            Files.writeString(shared, "This edit must not reach the prior run's preview.");
            SwingUtilities.invokeAndWait(() -> {
                var tabs = component(window, "work-areas", JTabbedPane.class);
                tabs.setSelectedIndex(1);
                require(component(window, "dependency-completeness", JLabel.class).getText().contains("Partial"),
                        "partial graph despite typed rejection");
                var tree = component(window, "dependency-tree", JTree.class);
                Object hidden = tree.getModel().getRoot();
                var rootPath = child(tree, new TreePath(hidden), 0, "Root");
                tree.expandPath(rootPath);
                var leftPath = child(tree, rootPath, 0, "Left");
                var rightPath = child(tree, rootPath, 1, "Right");
                tree.expandPath(leftPath);
                tree.expandPath(rightPath);
                var leftShared = child(tree, leftPath, 0, "Shared");
                var rightShared = child(tree, rightPath, 0, "Shared");
                require(!leftShared.equals(rightShared), "two occurrences of one model");
                tree.setSelectionPath(rightShared);
                var overview = component(window, "dependency-overview", JTextArea.class).getText();
                require(overview.contains("opaque-v2") && overview.contains("Local input") && overview.contains("Shared.ili"),
                        "actual version and source");
                var inspector = component(window, "dependency-inspector", JTabbedPane.class);
                inspector.setSelectedIndex(1);
                var users = component(window, "dependency-used-by", JTextArea.class).getText();
                require(users.contains("Left") && users.contains("Right") && !users.contains("Root"), "direct incoming edges");
                var pane = window.getRootPane();
                var image = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    pane.printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                screenshot.set(image);
                component(window, "dependency-show-source", JButton.class).doClick();
                var dialog = Arrays.stream(window.getOwnedWindows()).filter(JDialog.class::isInstance).map(JDialog.class::cast)
                        .filter(dialogWindow -> "dependency-source-inspector".equals(dialogWindow.getName())).findFirst().orElseThrow();
                require(dialog.isShowing() && !dialog.isModal(), "nonmodal source inspector");
                var preview = component(dialog, "source-preview", JTextArea.class);
                require(!preview.isEditable() && preview.getText().contains("Original"), "captured source after external edit");
                dialog.setVisible(false);

                tree.setSelectionPath(child(tree, rootPath, 2, "Missing"));
                component(window, "dependency-show-problems", JButton.class).doClick();
                require(tabs.getSelectedIndex() == 0, "related problems navigation");
                require(component(window, "dependency-problem-filter", JLabel.class).getText().contains("Root.ili"),
                        "visible source scope");
                var problems = component(window, "problems", JTable.class);
                require(problems.getRowCount() > 0, "diagnostics associated with import range");
                component(window, "reset-diagnostic-filter", JButton.class).doClick();
                require(component(window, "dependency-problem-filter", JLabel.class).getText().isEmpty(), "scope clears");
            });
            require(ImageIO.write(screenshot.get(), "png", cwd.resolve("dependencies.png").toFile()), "PNG writer");
        } finally {
            SwingUtilities.invokeAndWait(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        }
        System.out.println("GUI_DEPENDENCIES_PASS actualGraph=true diamondOccurrences=2 directUsers=2 version=true "
                + "capturedSource=true partial=true problemsNavigation=true");
    }

    private static TreePath child(JTree tree, TreePath parent, int index, String name) {
        Object value = tree.getModel().getChild(parent.getLastPathComponent(), index);
        require(value.toString().startsWith(name), "expected " + name + ", saw " + value);
        return parent.pathByAddingChild(value);
    }

    private static Path write(Path dir, String name, String version, String body) throws Exception {
        return Files.writeString(dir.resolve(name + ".ili"),
                "INTERLIS 2.4; MODEL " + name + " AT \"https://example.invalid\" VERSION \"" + version
                        + "\" = " + body + " END " + name + ".");
    }
}
