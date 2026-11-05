package guru.interlis.gui;

import com.formdev.flatlaf.FlatLightLaf;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.CompileService;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static guru.interlis.gui.GuiCompileProbe.await;
import static guru.interlis.gui.GuiCompileProbe.component;
import static guru.interlis.gui.GuiCompileProbe.require;

public final class GuiRepositoriesProbe {
    public static void main(String[] args) throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path fixtures = Files.createTempDirectory(cwd, "repository-fixtures-");
        Path priority = Files.createDirectory(fixtures.resolve("priority"));
        Path chosen = Files.createDirectory(fixtures.resolve("selected"));
        Files.writeString(priority.resolve("ilimodels.xml"), index(entry("Shared", "A.ili", "raw-new", false)));
        Files.writeString(chosen.resolve("ilimodels.xml"), index(entry("Shared", "B.ili", "raw-new", false)
                + entry("Shared", "Older.ili", "older", false) + entry("Browse", "Browse.ili", "1", true)));
        Files.writeString(priority.resolve("A.ili"), model("Shared", "raw-new", "WinnerA"));
        Path exact = Files.writeString(chosen.resolve("B.ili"), model("Shared", "raw-new", "WinnerB"));
        Files.writeString(chosen.resolve("Older.ili"), model("Shared", "older", "Historical"));
        Files.writeString(chosen.resolve("Browse.ili"), model("Browse", "1", "BrowseOnly"));
        var frame = new AtomicReference<MainFrame>();
        var last = new AtomicReference<CompileResult>();
        var screenshot = new AtomicReference<BufferedImage>();
        var service = new CompileService();
        SwingUtilities.invokeAndWait(() -> {
            require(FlatLightLaf.setup(), "look and feel");
            var window = new MainFrame(fixtures, (request, token) -> {
                var compiled = service.compile(request, token);
                last.set(compiled);
                return compiled;
            }, service::inspect);
            window.setVisible(true);
            frame.set(window);
            var tabs = component(window, "work-areas", JTabbedPane.class);
            tabs.setSelectedIndex(2);
            ((RepositoriesPanel) tabs.getSelectedComponent()).addLocation(priority.toString());
        });
        MainFrame window = frame.get();
        try {
            await(window, "session-inputs", "1 enabled");
            SwingUtilities.invokeAndWait(() -> {
                var tabs = component(window, "work-areas", JTabbedPane.class);
                ((RepositoriesPanel) tabs.getSelectedComponent()).addLocation(chosen.toString());
            });
            await(window, "session-inputs", "2 enabled");
            SwingUtilities.invokeAndWait(() -> component(window, "reload-repositories", JButton.class).doClick());
            await(window, "repository-counts", "2/2 indexes loaded");
            SwingUtilities.invokeAndWait(() -> {
                var table = component(window, "repository-catalog", JTable.class);
                require(table.getRowCount() == 4, "all indexed rows");
                var language = component(window, "repository-language", javax.swing.JComboBox.class);
                var rendered = (javax.swing.JComponent) language.getRenderer().getListCellRendererComponent(
                        new JList<>(), "<html><img src='http://127.0.0.1:1/image'>", 0, false, false);
                require(rendered.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) == null,
                        "repository language text must not become HTML");
                table.setRowSelectionInterval(find(table, "Browse", "1", "selected"), find(table, "Browse", "1", "selected"));
                require(!component(window, "repository-add-exact-root", JButton.class).isEnabled(), "browse-only cannot add");
                component(window, "repository-include-browse-only", JCheckBox.class).doClick();
                require(table.getRowCount() == 3, "browse-only filter");
                component(window, "repository-search", JTextField.class).setText("Shared");
                require(table.getRowCount() == 3, "local search preserves all versions");
                int older = find(table, "Shared", "older", "selected");
                int newer = find(table, "Shared", "raw-new", "selected");
                table.setRowSelectionInterval(older, older);
                table.addRowSelectionInterval(newer, newer);
                component(window, "repository-compare", JButton.class).doClick();
                var compare = component(window, "repository-comparison", JTable.class);
                require(compare.getRowCount() == 8 && compare.getValueAt(1, 0).toString().contains("differs"), "metadata-only comparison");
                var pane = window.getRootPane();
                var image = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    pane.printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                screenshot.set(image);
                table.setRowSelectionInterval(newer, newer);
                component(window, "repository-preview", JButton.class).doClick();
            });
            require(ImageIO.write(screenshot.get(), "png", cwd.resolve("repository-comparison.png").toFile()), "PNG writer");
            waitFor(() -> component((RepositoriesPanel) component(window, "work-areas", JTabbedPane.class).getComponentAt(2),
                    "source-preview", JTextArea.class).getText().contains("WinnerB"));
            SwingUtilities.invokeAndWait(() -> {
                component(window, "repository-add-exact-root", JButton.class).doClick();
                require(last.get() == null, "root addition never compiles");
                require(component(window, "selected-repository-roots", JList.class).getModel().getSize() == 1,
                        "exact root appears separately in Check");
                component(window, "compile", JButton.class).doClick();
            });
            await(window, "session-status", "Succeeded");
            var result = last.get();
            var location = result.capture().orElseThrow().resolution().rootLocations().getFirst();
            require(location.source().value().equals(exact.toUri().toString()) && location.modelVersion().orElseThrow().equals("raw-new"),
                    "compiler used selected source and original version");
            require(result.sources().get(location.source()).source().text().contains("WinnerB"), "compiled selected content");
            SwingUtilities.invokeAndWait(() -> {
                var repositories = component(window, "repository-list", JList.class);
                repositories.setSelectedIndex(2);
                component(window, "repository-up", JButton.class).doClick();
                require(repositories.getModel().getElementAt(1).toString().startsWith("1. selected"), "priority order changed");
                repositories.setSelectedIndex(2);
                component(window, "repository-toggle", JButton.class).doClick();
                require(repositories.getModel().getElementAt(2).toString().contains("Disabled"), "disabled repository visible");
                require(component(window, "session-inputs", javax.swing.JLabel.class).getText().contains("1 enabled"),
                        "enabled order shared with session");
                require(component(window, "session-status", javax.swing.JLabel.class).getText().contains("Inputs changed"),
                        "changed repository policy invalidates current result");
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        }
        System.out.println("GUI_REPOSITORIES_PASS twoIndexes=true browseOnlyBlocked=true metadataCompared=true "
                + "explicitPreview=true exactLowerPriorityRoot=true repositoryOnlyCompile=true priorityAndEnablement=true literalSchema=true");
    }

    private static int find(JTable table, String name, String version, String repository) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (table.getValueAt(row, 0).equals(name) && table.getValueAt(row, 1).equals(version)
                    && table.getValueAt(row, 2).equals(repository)) {
                return row;
            }
        }
        throw new AssertionError("Missing indexed entry " + name + " " + version);
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        do {
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) {
                return;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Repository preview did not become ready");
    }

    private static String entry(String name, String file, String version, boolean browseOnly) {
        return "<ModelMetadata><Name>" + name + "</Name><SchemaLanguage>ili2_4</SchemaLanguage><File>"
                + file + "</File><Version>" + version + "</Version><browseOnly>" + browseOnly + "</browseOnly></ModelMetadata>";
    }

    private static String index(String entries) {
        return "<TRANSFER>" + entries + "</TRANSFER>";
    }

    private static String model(String name, String version, String domain) {
        return "INTERLIS 2.4; MODEL " + name + " AT \"x\" VERSION \"" + version + "\" = DOMAIN " + domain
                + " = TEXT*20; END " + name + ".";
    }
}
