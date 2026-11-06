package guru.interlis.gui;

import guru.interlis.gui.infrastructure.GuiSettings;
import guru.interlis.gui.infrastructure.GuiSettingsStore;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class WindowPreferences {
    private final GuiSettingsStore store;
    private final Consumer<String> warning;
    private final ThreadPoolExecutor writes = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), Thread.ofPlatform().name("interlis-settings").factory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private GuiSettings lastQueued;

    WindowPreferences(GuiSettingsStore store, Consumer<String> warning) {
        this.store = store;
        this.warning = warning;
    }

    void save(GuiSettings settings) {
        if (settings.equals(lastQueued)) {
            return;
        }
        lastQueued = settings;
        writes.execute(() -> {
            try {
                store.save(settings);
                SwingUtilities.invokeLater(() -> {
                    if (settings.equals(lastQueued)) {
                        warning.accept("");
                    }
                });
            } catch (IOException | IllegalArgumentException failure) {
                SwingUtilities.invokeLater(() -> {
                    if (settings.equals(lastQueued)) {
                        lastQueued = null;
                        warning.accept("Settings could not be saved. The previous settings were kept.");
                    }
                });
            }
        });
    }

    void close() {
        writes.shutdown();
    }

    static void restore(JFrame window, GuiSettings settings) {
        GraphicsConfiguration preferred = window.getGraphicsConfiguration();
        Rectangle usable = screen(preferred);
        if (settings.window().isEmpty()) {
            window.setSize(Math.min(window.getWidth(), usable.width), Math.min(window.getHeight(), usable.height));
            window.setLocationRelativeTo(null);
        } else {
            var state = settings.window().orElseThrow();
            var saved = new Rectangle(state.x(), state.y(), state.width(), state.height());
            for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle candidate = screen(device.getDefaultConfiguration());
                if (candidate.intersects(saved)) {
                    usable = candidate;
                    break;
                }
            }
            int width = Math.min(usable.width, Math.max(window.getMinimumSize().width, saved.width));
            int height = Math.min(usable.height, Math.max(window.getMinimumSize().height, saved.height));
            int x = Math.clamp(saved.x, usable.x, usable.x + usable.width - width);
            int y = Math.clamp(saved.y, usable.y, usable.y + usable.height - height);
            window.setBounds(x, y, width, height);
        }
        SwingUtilities.invokeLater(() -> splitters(window).forEach(split -> {
            Integer saved = settings.dividers().get(split.getName());
            if (saved != null) {
                int maximum = split.getMaximumDividerLocation();
                int minimum = split.getMinimumDividerLocation();
                if (maximum >= minimum) {
                    split.setDividerLocation(Math.clamp(saved, minimum, maximum));
                }
            }
        }));
    }

    static Map<String, Integer> dividers(JFrame frame) {
        var values = new LinkedHashMap<String, Integer>();
        splitters(frame).filter(split -> split.getDividerLocation() >= 0)
                .forEach(split -> values.put(split.getName(), split.getDividerLocation()));
        return Map.copyOf(values);
    }

    static Optional<GuiSettings.Bounds> bounds(JFrame frame) {
        var bounds = frame.getBounds();
        return bounds.width <= 0 || bounds.height <= 0 ? Optional.empty()
                : Optional.of(new GuiSettings.Bounds(bounds.x, bounds.y, bounds.width, bounds.height));
    }

    private static Rectangle screen(GraphicsConfiguration configuration) {
        Rectangle bounds = configuration.getBounds();
        var insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    }

    private static Stream<JSplitPane> splitters(Container parent) {
        return java.util.Arrays.stream(parent.getComponents()).flatMap(component -> {
            Stream<JSplitPane> self = component instanceof JSplitPane split && split.getName() != null
                    ? Stream.of(split) : Stream.empty();
            return component instanceof Container nested ? Stream.concat(self, splitters(nested)) : self;
        });
    }
}
