package guru.interlis.gui;

import com.formdev.flatlaf.FlatLightLaf;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.CompileService;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static guru.interlis.gui.GuiCompileProbe.await;
import static guru.interlis.gui.GuiCompileProbe.component;
import static guru.interlis.gui.GuiCompileProbe.drop;
import static guru.interlis.gui.GuiCompileProbe.require;

public final class GuiDiagnosticsProbe {
    public static void main(String[] args) throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path dir = Files.createTempDirectory(cwd, "diagnostic-fixtures-");
        Path conflict = Files.writeString(dir.resolve("Zulu.ili"),
                "!! ä 🦊\r\nINTERLIS 2.4;\r\nMODEL Conflict AT \"https://example.invalid\" VERSION \"1\" =\r\n"
                + "  TOPIC Office =\r\n    CLASS Office = END Office;\r\n  END Office;\r\nEND Conflict.\r\n");
        Path other = Files.writeString(dir.resolve("Alpha.ili"),
                "INTERLIS 2.4; MODEL Other AT \"https://example.invalid\" VERSION \"1\" = "
                + "TOPIC T = CLASS C = value: Missing; END C; END T; END Other.");
        var copied = new AtomicReference<String>();
        var frame = new AtomicReference<MainFrame>();
        var screenshot = new AtomicReference<BufferedImage>();
        var service = new CompileService();
        var lastResult = new AtomicReference<CompileResult>();
        SwingUtilities.invokeAndWait(() -> {
            require(FlatLightLaf.setup(), "look and feel");
            var window = new MainFrame(dir, (request, token) -> {
                var compiled = service.compile(request, token);
                lastResult.set(compiled);
                return compiled;
            }, service::inspect, copied::set);
            window.setVisible(true);
            frame.set(window);
            drop(window, conflict);
        });

        MainFrame window = frame.get();
        try {
            await(window, "session-inputs", "1 files");
            SwingUtilities.invokeAndWait(() -> drop(window, other));
            await(window, "session-inputs", "2 files");
            SwingUtilities.invokeAndWait(() -> component(window, "compile", JButton.class).doClick());
            await(window, "session-status", "Rejected");
            SwingUtilities.invokeAndWait(() -> {
                var table = component(window, "problems", JTable.class);
                require(table.getRowCount() >= 2, "two distinct diagnostic sources");
                var count = component(window, "diagnostic-counts", JLabel.class);
                int allRows = table.getRowCount();
                table.getRowSorter().toggleSortOrder(3);
                require(table.getValueAt(0, 3).toString().contains("Alpha.ili"), "ascending source sort");
                table.getRowSorter().toggleSortOrder(3);
                require(table.getValueAt(0, 3).toString().contains("Zulu.ili"), "descending source sort");
                component(window, "diagnostic-search", JTextField.class).setText("Zulu.ili");
                require(table.getRowCount() < allRows && table.getRowCount() > 0, "search filter");
                require(count.getText().contains(" of " + allRows + " visible"), "canonical total");
                require(component(window, "session-status", JLabel.class).getText().contains("Rejected"), "filter preserves outcome");
                component(window, "reset-diagnostic-filter", JButton.class).doClick();
                int row = findCode(table, "ILI-SEM-0010");
                require(table.convertRowIndexToModel(row) != row, "selection uses a moved row");
                require(table.getValueAt(row, 3).toString().contains("Zulu.ili"), "conflict row source");
                table.setRowSelectionInterval(row, row);
                var preview = component(window, "source-preview", JTextArea.class);
                require(!preview.isEditable() && preview.getText().contains("ä 🦊"), "read-only original text");
                var result = lastResult.get();
                var diagnostic = result.diagnostics().stream().filter(item -> item.code().value().equals("ILI-SEM-0010"))
                        .findFirst().orElseThrow();
                var range = diagnostic.primaryRange().orElseThrow();
                var original = result.sources().get(range.source()).source().text();
                var span = highlight(preview);
                int start = original.substring(0, range.startOffset()).replace("\r\n", "\n").length();
                int end = original.substring(0, range.endOffset()).replace("\r\n", "\n").length();
                require(span.getStartOffset() == start && span.getEndOffset() == end, "primary range follows sorted row");
                var related = component(window, "related-locations", JComboBox.class);
                require(related.getItemCount() == 1, "related location count");
                component(window, "open-related-location", JButton.class).doClick();
                require(highlight(preview).getStartOffset() == preview.getText().indexOf("Office"), "related exact range");
                component(window, "copy-diagnostic-message", JButton.class).doClick();
                require(copied.get().contains("local type name 'Office'"), "English copied message");
                var settings = window.getJMenuBar().getMenu(2);
                var languages = (JMenu) settings.getItem(1);
                languages.getItem(1).doClick();
                component(window, "copy-diagnostic-details", JButton.class).doClick();
                require(copied.get().contains("Der lokale Typname 'Office'") && copied.get().contains("Hint:")
                        && copied.get().contains("Kollidierende Deklaration"), "German copied details and shared hint");
                var pane = window.getRootPane();
                var image = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    pane.printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                screenshot.set(image);
            });
            require(ImageIO.write(screenshot.get(), "png", cwd.resolve("diagnostics.png").toFile()), "PNG writer");

            Files.writeString(conflict, "INTERLIS 2.4; MODEL Conflict AT \"https://example.invalid\" VERSION \"1\" = END Conflict.");
            Files.writeString(other, "INTERLIS 2.4; MODEL Other AT \"https://example.invalid\" VERSION \"1\" = END Other.");
            SwingUtilities.invokeAndWait(() -> component(window, "compile", JButton.class).doClick());
            await(window, "session-status", "Succeeded");
            SwingUtilities.invokeAndWait(() -> {
                require(component(window, "problems", JTable.class).getRowCount() == 0, "new successful result clears old rows");
                require(component(window, "source-preview", JTextArea.class).getText().isEmpty(), "old preview clears with old run");
                require(!component(window, "copy-diagnostic-details", JButton.class).isEnabled(), "no stale clipboard data");
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        }
        System.out.println("GUI_DIAGNOSTICS_PASS sortedTwoSources=true filterCounts=true exactRelatedRange=true "
                + "localizedCopy=true readOnly=true nextRunCleared=true");
    }

    private static int findCode(JTable table, String code) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (table.getValueAt(row, 1).equals(code)) {
                return row;
            }
        }
        throw new AssertionError("Missing real compiler diagnostic " + code);
    }

    private static javax.swing.text.Highlighter.Highlight highlight(JTextArea preview) {
        return Arrays.stream(preview.getHighlighter().getHighlights())
                .filter(span -> span.getEndOffset() > span.getStartOffset()).findFirst().orElseThrow();
    }
}
