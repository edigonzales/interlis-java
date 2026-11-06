package guru.interlis.gui;

import com.formdev.flatlaf.FlatLaf;
import guru.interlis.gui.application.CompileService;
import guru.interlis.gui.infrastructure.GuiSettings;
import guru.interlis.gui.infrastructure.GuiSettingsStore;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static guru.interlis.gui.GuiCompileProbe.await;
import static guru.interlis.gui.GuiCompileProbe.component;
import static guru.interlis.gui.GuiCompileProbe.require;

public final class GuiLifecycleProbe {
    public static void main(String[] args) throws Exception {
        Path fixture = Files.createTempDirectory(Path.of("").toAbsolutePath(), "lifecycle-fixture-");
        Path settingsPath = Files.createDirectory(fixture.resolve("settings"));
        Files.writeString(settingsPath.resolve("settings.properties"), "schemaVersion=93\n");
        var store = new GuiSettingsStore(settingsPath);
        var loaded = store.load();
        Path source = Files.writeString(fixture.resolve("Model.ili"),
                "INTERLIS 2.4; MODEL ModelA AT \"x\" VERSION \"1\" = END ModelA.");
        var service = new CompileService();
        var frame = new AtomicReference<MainFrame>();
        var runCount = new AtomicInteger();
        var secondStarted = new CountDownLatch(1);
        var thirdStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var shutdownObserved = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(() -> {
            require(MainFrame.setupTheme(loaded.settings().theme()), "initial theme");
            var window = new MainFrame(fixture, (request, token) -> {
                int invocation = runCount.incrementAndGet();
                if (invocation == 2) {
                    secondStarted.countDown();
                    waitForRelease(release);
                } else if (invocation == 3) {
                    thirdStarted.countDown();
                    waitForRelease(new CountDownLatch(1));
                    if (token.isCancellationRequested()) {
                        shutdownObserved.countDown();
                    }
                }
                return service.compile(request, token);
            }, service::inspect, ignored -> {}, loaded.settings(), Optional.of(store), loaded.warning());
            window.setVisible(true);
            frame.set(window);
            require(component(window, "settings-warning", JLabel.class).getText().contains("backed up"), "invalid settings notice");
            window.setSize(1000, 700);
            window.validate();
            require(window.getSize().equals(new Dimension(1000, 700)), "minimum dimensions");
            int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            var event = new KeyEvent(window.getRootPane(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                    modifiers, KeyEvent.VK_2, '2');
            require(SwingUtilities.processKeyBindings(event), "keyboard binding dispatched");
            require(component(window, "work-areas", JTabbedPane.class).getSelectedIndex() == 1, "keyboard selected Dependencies");
            window.addFiles(List.of(source));
        });
        MainFrame window = frame.get();
        try {
            await(window, "session-inputs", "1 files");
            SwingUtilities.invokeLater(() -> component(window, "compiler-settings", JButton.class).doClick());
            JDialog settings = waitForDialog(window, "Compiler settings");
            SwingUtilities.invokeAndWait(() -> {
                component(settings, "settings-theme", JComboBox.class).setSelectedItem(GuiSettings.Theme.DARK);
                optionPane(settings).setValue(JOptionPane.OK_OPTION);
            });
            SwingUtilities.invokeAndWait(() -> require(FlatLaf.isLafDark(), "dark theme applied through settings dialog"));

            SwingUtilities.invokeAndWait(() -> component(window, "compile", JButton.class).doClick());
            await(window, "session-status", "Succeeded");
            SwingUtilities.invokeAndWait(() -> {
                component(window, "show-run-log", JButton.class).doClick();
                var log = Arrays.stream(window.getOwnedWindows()).filter(JDialog.class::isInstance).map(JDialog.class::cast)
                        .filter(dialog -> "compiler-run-log".equals(dialog.getName())).findFirst().orElseThrow();
                require(component(log, "compiler-run-log-text", JTextArea.class).getText().contains("Finished"), "captured log");
                log.setVisible(false);
                var warnings = window.getJMenuBar().getMenu(2).getItem(0);
                warnings.doClick();
                require(component(window, "session-status", JLabel.class).getText().contains("Inputs changed"), "stale result");
                component(window, "compile", JButton.class).doClick();
            });
            require(secondStarted.await(8, TimeUnit.SECONDS), "second run started");
            SwingUtilities.invokeAndWait(() -> {
                var bar = component(window, "compile-progress", JProgressBar.class);
                require(bar.isVisible() && bar.isIndeterminate(), "indeterminate progress");
                require(!component(window, "compiler-settings", JButton.class).isEnabled(), "settings frozen during compile");
                component(window, "cancel", JButton.class).doClick();
            });
            release.countDown();
            await(window, "session-status", "Cancelled");
            SwingUtilities.invokeAndWait(() -> component(window, "compile", JButton.class).doClick());
            require(thirdStarted.await(8, TimeUnit.SECONDS), "third run started");
            SwingUtilities.invokeAndWait(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
            require(shutdownObserved.await(8, TimeUnit.SECONDS), "worker observed cancellation on close");
            require(!window.isDisplayable(), "window disposed");
            awaitSavedSettings(store);
            try (var files = Files.list(settingsPath)) {
                require(files.anyMatch(path -> path.getFileName().toString().startsWith("settings.invalid-")), "invalid preferences preserved");
            }
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(window::dispose);
        }
        System.out.println("GUI_LIFECYCLE_PASS keyboard=true darkTheme=true resize=true corruptSettingsRecovered=true "
                + "saved=true runLog=true stale=true cancel=true closeWithActiveWorker=true");
    }

    private static void waitForRelease(CountDownLatch latch) {
        try {
            require(latch.await(10, TimeUnit.SECONDS), "release");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static JDialog waitForDialog(MainFrame window, String title) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        var found = new AtomicReference<JDialog>();
        do {
            SwingUtilities.invokeAndWait(() -> Arrays.stream(window.getOwnedWindows())
                    .filter(JDialog.class::isInstance).map(JDialog.class::cast)
                    .filter(dialog -> dialog.isShowing() && title.equals(dialog.getTitle())).findFirst().ifPresent(found::set));
            if (found.get() != null) {
                return found.get();
            }
            Thread.sleep(25);
        } while (System.nanoTime() < until);
        throw new AssertionError("Settings dialog did not open");
    }

    private static JOptionPane optionPane(Container container) {
        for (var component : container.getComponents()) {
            if (component instanceof JOptionPane pane) {
                return pane;
            }
            if (component instanceof Container nested) {
                var pane = optionPane(nested);
                if (pane != null) {
                    return pane;
                }
            }
        }
        return null;
    }

    private static void awaitSavedSettings(GuiSettingsStore store) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            var saved = store.load().settings();
            if (saved.theme() == GuiSettings.Theme.DARK && saved.window().isPresent()
                    && saved.window().orElseThrow().width() == 1000 && !saved.dividers().isEmpty()) {
                return;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < until);
        throw new AssertionError("Settings were not saved");
    }
}
