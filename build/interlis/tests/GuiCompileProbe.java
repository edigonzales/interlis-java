package guru.interlis.gui;

import com.formdev.flatlaf.FlatLightLaf;
import guru.interlis.gui.application.CompileService;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GuiCompileProbe {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("").toAbsolutePath(), "compile-fixtures-");
        Path valid = Files.writeString(dir.resolve("Two models.ili"), """
                INTERLIS 2.4;
                MODEL First AT "https://example.invalid" VERSION "1" = END First.
                MODEL Second AT "https://example.invalid" VERSION "1" = END Second.
                """);
        Path invalid = Files.writeString(dir.resolve("Bad header.ili"), "This is not an INTERLIS source.");
        var calls = new AtomicInteger();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        var frame = new AtomicReference<MainFrame>();
        var service = new CompileService();

        SwingUtilities.invokeAndWait(() -> {
            require(FlatLightLaf.setup(), "look and feel");
            var window = new MainFrame(dir, (request, token) -> {
                require(!SwingUtilities.isEventDispatchThread(), "worker thread");
                if (calls.incrementAndGet() == 3) {
                    started.countDown();
                    try {
                        require(release.await(8, TimeUnit.SECONDS), "controlled release");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                    if (token.isCancellationRequested()) {
                        cancelled.countDown();
                    }
                }
                return service.compile(request, token);
            }, service::inspect);
            window.setVisible(true);
            frame.set(window);
            drop(window, valid);
        });
        MainFrame window = frame.get();
        try {
            await(window, "session-inputs", "1 files");
            SwingUtilities.invokeAndWait(() -> {
                var list = component(window, "local-models", JList.class);
                require(list.getModel().getSize() == 2, "two models discovered in one file");
                list.setSelectedIndex(1);
                component(window, "use-selected-roots", JButton.class).doClick();
                require(component(window, "root-mode", JLabel.class).getText().contains("Explicit roots: 1"), "explicit root");
                component(window, "compile", JButton.class).doClick();
            });
            await(window, "session-status", "Succeeded");
            SwingUtilities.invokeAndWait(() -> drop(window, invalid));
            await(window, "session-inputs", "2 files");
            SwingUtilities.invokeAndWait(() -> {
                require(component(window, "session-status", JLabel.class).getText().contains("Inputs changed"), "stale badge");
                component(window, "use-all-inputs", JButton.class).doClick();
                component(window, "compile", JButton.class).doClick();
            });
            await(window, "session-status", "Rejected");
            SwingUtilities.invokeAndWait(() -> component(window, "compile", JButton.class).doClick());
            require(started.await(8, TimeUnit.SECONDS), "controlled compiler start");
            SwingUtilities.invokeAndWait(() -> {
                var tabs = component(window, "work-areas", JTabbedPane.class);
                tabs.setSelectedIndex(2);
                require(tabs.getSelectedIndex() == 2, "repository tab remains available");
                require(!component(window, "compile", JButton.class).isEnabled(), "single run");
                require(!component(window, "add-files", JButton.class).isEnabled(), "inputs frozen");
                component(window, "cancel", JButton.class).doClick();
                require(component(window, "session-status", JLabel.class).getText().contains("Cancelling"), "cancel pending");
            });
            release.countDown();
            require(cancelled.await(8, TimeUnit.SECONDS), "cooperative token reaches the backend");
            await(window, "session-status", "Cancelled");
            require(calls.get() == 3, "three actual invocations");
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        }
        System.out.println("GUI_COMPILE_PASS fileTransfer=true multiModel=2 explicitRoot=1 success=true "
                + "badHeaderRejected=true tabSwitch=true cooperativeCancel=true java.home=" + System.getProperty("java.home"));
    }

    private static void drop(MainFrame window, Path file) {
        var payload = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return List.of(new File(file.toString()));
            }
        };
        var support = new TransferHandler.TransferSupport(window, payload);
        require(window.getTransferHandler().importData(support), "file-list transfer");
    }

    private static void await(MainFrame window, String name, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        var text = new AtomicReference<String>();
        do {
            SwingUtilities.invokeAndWait(() -> text.set(component(window, name, JLabel.class).getText()));
            if (text.get().contains(expected)) {
                return;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Expected " + expected + " in " + name + ", saw " + text.get());
    }

    private static <T extends Component> T component(Container parent, String name, Class<T> type) {
        var found = find(parent, name, type);
        if (found == null) {
            throw new AssertionError("Missing " + name);
        }
        return found;
    }

    private static <T extends Component> T find(Container parent, String name, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                var found = find(container, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
