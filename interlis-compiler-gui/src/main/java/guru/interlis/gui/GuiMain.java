package guru.interlis.gui;

import guru.interlis.gui.application.CompileService;
import guru.interlis.gui.infrastructure.GuiSettingsStore;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.SwingUtilities;

/** Entry point for the modular Swing compiler application. */
public final class GuiMain {
    private GuiMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("The INTERLIS Compiler GUI does not accept arguments");
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("The INTERLIS Compiler GUI requires a graphical desktop");
        }
        var directory = Path.of("").toAbsolutePath();
        var defaultSettings = Path.of(System.getProperty("user.home"), ".interlis", "gui");
        var store = new GuiSettingsStore(Path.of(System.getProperty("interlis.gui.settingsDir", defaultSettings.toString())));
        var settings = store.load();
        var backend = new CompileService();
        SwingUtilities.invokeAndWait(() -> {
            if (!MainFrame.setupTheme(settings.settings().theme())) {
                throw new IllegalStateException("Could not initialize the FlatLaf look and feel");
            }
            var frame = new MainFrame(directory, backend::compile, backend::inspect,
                    text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null),
                    settings.settings(), settings.writable() ? Optional.of(store) : Optional.empty(), settings.warning());
            frame.setVisible(true);
        });
    }
}
