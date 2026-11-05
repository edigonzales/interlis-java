package guru.interlis.gui;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import javax.swing.text.BadLocationException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static guru.interlis.gui.GuiCompileProbe.await;
import static guru.interlis.gui.GuiCompileProbe.component;
import static guru.interlis.gui.GuiCompileProbe.drop;
import static guru.interlis.gui.GuiCompileProbe.require;

/** In-process test driver reached through the jpackage native launcher. */
public final class GuiPackagedProbe {
    public static void main(String[] args) throws Exception {
        Path workspace = Path.of(System.getProperty("interlis.smoke.directory")).toRealPath();
        Path expectedHome = workspace.resolve("INTERLIS Compiler.app/Contents/runtime/Contents/Home").toRealPath();
        Path javaHome = Path.of(System.getProperty("java.home")).toRealPath();
        require(expectedHome.equals(javaHome), "embedded java.home: " + javaHome);
        require(System.getenv("JAVA_HOME") == null && System.getenv("CLASSPATH") == null, "isolated launcher environment");
        require("aarch64".equals(System.getProperty("os.arch")), "ARM64 runtime");
        Path repository = Files.createDirectory(workspace.resolve("Local Repository"));
        Files.writeString(repository.resolve("ilimodels.xml"), """
                <TRANSFER>
                  <ModelMetadata><Name>PackageRoot</Name><SchemaLanguage>ili2_4</SchemaLanguage>
                    <File>PackageRoot.ili</File><Version>opaque-1</Version></ModelMetadata>
                  <ModelMetadata><Name>SharedModel</Name><SchemaLanguage>ili2_4</SchemaLanguage>
                    <File>SharedModel.ili</File><Version>1</Version></ModelMetadata>
                </TRANSFER>
                """);
        Files.writeString(repository.resolve("PackageRoot.ili"),
                "INTERLIS 2.4; MODEL PackageRoot AT \"x\" VERSION \"opaque-1\" = IMPORTS SharedModel; END PackageRoot.");
        Files.writeString(repository.resolve("SharedModel.ili"),
                "INTERLIS 2.4; MODEL SharedModel AT \"x\" VERSION \"1\" = DOMAIN TextValue = TEXT*20; END SharedModel.");
        Path invalid = Files.writeString(workspace.resolve("Invalid Local.ili"),
                "!! ä 🦊\r\nINTERLIS 2.4; MODEL InvalidLocal AT \"x\" VERSION \"1\" = IMPORTS MissingModel; END InvalidLocal.");
        GuiMain.main(new String[0]);
        MainFrame frame = waitFor(() -> Arrays.stream(Window.getWindows()).filter(MainFrame.class::isInstance)
                .map(MainFrame.class::cast).filter(MainFrame::isShowing).findFirst().orElse(null));
        try {
            SwingUtilities.invokeAndWait(() -> drop(frame, invalid));
            await(frame, "session-inputs", "1 files");
            SwingUtilities.invokeAndWait(() -> press(frame, "compile"));
            await(frame, "session-status", "Rejected");
            SwingUtilities.invokeAndWait(() -> {
                var table = component(frame, "problems", JTable.class);
                require(table.getRowCount() > 0, "real compiler problems");
                table.setRowSelectionInterval(0, 0);
                var source = component(frame, "source-preview", JTextArea.class);
                require(!source.isEditable() && source.getText().contains("MissingModel"), "diagnostic source preview");
                requireMarkVisible(source, "MissingModel");
                capture(frame, workspace.resolve("check.png"));
                var files = component(frame, "local-files", JList.class);
                files.setSelectedIndex(0);
                press(frame, "remove-files");
                component(frame, "work-areas", JTabbedPane.class).setSelectedIndex(2);
            });
            await(frame, "session-inputs", "0 files");

            SwingUtilities.invokeLater(() -> press(frame, "add-repository"));
            JOptionPane dialog = waitFor(() -> Arrays.stream(frame.getOwnedWindows())
                    .filter(JDialog.class::isInstance).map(JDialog.class::cast)
                    .filter(window -> window.isShowing() && window.getTitle().equals("Add repository"))
                    .map(GuiPackagedProbe::optionPane).findFirst().orElse(null));
            SwingUtilities.invokeAndWait(() -> {
                dialog.setInputValue(repository.toString());
                dialog.setValue(JOptionPane.OK_OPTION);
            });
            await(frame, "session-inputs", "1 enabled");
            SwingUtilities.invokeAndWait(() -> press(frame, "reload-repositories"));
            await(frame, "repository-counts", "1/1 indexes loaded");
            SwingUtilities.invokeAndWait(() -> {
                var catalog = component(frame, "repository-catalog", JTable.class);
                require(catalog.getRowCount() == 2, "indexed model rows");
                for (int row = 0; row < catalog.getRowCount(); row++) {
                    if (catalog.getValueAt(row, 0).equals("PackageRoot")) {
                        catalog.setRowSelectionInterval(row, row);
                        break;
                    }
                }
                require(catalog.getSelectedRow() >= 0, "selected exact root");
                press(frame, "repository-preview");
            });
            waitFor(() -> {
                var panel = (Container) component(frame, "work-areas", JTabbedPane.class).getSelectedComponent();
                var source = component(panel, "source-preview", JTextArea.class);
                return source.getText().contains("IMPORTS SharedModel") ? source : null;
            });
            SwingUtilities.invokeAndWait(() -> {
                capture(frame, workspace.resolve("repositories.png"));
                press(frame, "repository-add-exact-root");
                require(component(frame, "selected-repository-roots", JList.class).getModel().getSize() == 1, "repository-only root");
                require(component(frame, "session-status", JLabel.class).getText().contains("Rejected"), "no implicit compile");
                press(frame, "compile");
            });
            await(frame, "session-status", "Succeeded");
            SwingUtilities.invokeAndWait(() -> {
                component(frame, "work-areas", JTabbedPane.class).setSelectedIndex(1);
                var tree = component(frame, "dependency-tree", JTree.class);
                var model = tree.getModel();
                Object hidden = model.getRoot();
                Object root = model.getChild(hidden, 0);
                require(root.toString().startsWith("PackageRoot"), "explicit package root in captured graph");
                var rootPath = new TreePath(new Object[]{hidden, root});
                tree.expandPath(rootPath);
                Object imported = model.getChild(root, 0);
                require(imported.toString().startsWith("SharedModel"), "actual resolved import");
                tree.setSelectionPath(rootPath.pathByAddingChild(imported));
                String overview = component(frame, "dependency-overview", JTextArea.class).getText();
                require(overview.contains("SharedModel.ili") && overview.contains("Repository"), "actual import source");
                capture(frame, workspace.resolve("dependencies.png"));
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        }
        require(!frame.isDisplayable(), "normal window close");
        System.out.println("GUI_PACKAGED_PASS checkProblems=true sourceMarkVisible=true exactRepositoryRoot=true capturedImport=true nativeWindowClosed=true"
                + " java.home=" + javaHome + " user.dir=" + System.getProperty("user.dir"));
    }

    private static void requireMarkVisible(JTextArea source, String token) {
        var mark = Arrays.stream(source.getHighlighter().getHighlights())
                .filter(span -> span.getEndOffset() > span.getStartOffset()).findFirst().orElseThrow();
        int offset = source.getText().indexOf(token);
        require(mark.getStartOffset() == offset && mark.getEndOffset() == offset + token.length(), "exact source mark");
        try {
            var first = source.modelToView2D(mark.getStartOffset()).getBounds();
            var last = source.modelToView2D(mark.getEndOffset()).getBounds();
            require(source.getVisibleRect().contains(first.union(last)), "complete source mark in viewport");
        } catch (BadLocationException failure) {
            throw new AssertionError("Could not locate source mark", failure);
        }
    }

    private static void press(MainFrame frame, String name) {
        var button = component(frame, name, JButton.class);
        require(button.isEnabled(), name + " enabled");
        button.doClick();
    }

    private static JOptionPane optionPane(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JOptionPane pane) return pane;
            if (child instanceof Container nested) {
                var pane = optionPane(nested);
                if (pane != null) return pane;
            }
        }
        return null;
    }

    private static <T> T waitFor(Supplier<T> supplier) throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        var result = new AtomicReference<T>();
        do {
            SwingUtilities.invokeAndWait(() -> result.set(supplier.get()));
            if (result.get() != null) return result.get();
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for packaged window state");
    }

    private static void capture(MainFrame frame, Path target) {
        var pane = frame.getRootPane();
        var image = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            pane.printAll(graphics);
            require(ImageIO.write(image, "png", target.toFile()), "PNG writer");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        } finally {
            graphics.dispose();
        }
    }
}
