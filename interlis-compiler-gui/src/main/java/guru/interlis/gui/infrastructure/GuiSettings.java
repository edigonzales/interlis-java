package guru.interlis.gui.infrastructure;

import guru.interlis.gui.application.ConfiguredRepository;
import guru.interlis.gui.application.SessionOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** User preferences only. No sources, diagnostics, workspaces or compiler results are retained. */
public record GuiSettings(Theme theme, SessionOptions session, List<ConfiguredRepository> repositories,
                          Optional<Bounds> window, Map<String, Integer> dividers, Optional<Path> lastDirectory) {
    public enum Theme {
        LIGHT, DARK;

        @Override
        public String toString() {
            return this == LIGHT ? "Light" : "Dark";
        }
    }

    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Invalid saved window size");
            }
        }
    }

    public GuiSettings {
        Objects.requireNonNull(theme);
        Objects.requireNonNull(session);
        repositories = List.copyOf(repositories);
        Objects.requireNonNull(window);
        dividers = Map.copyOf(dividers);
        lastDirectory = lastDirectory.map(path -> path.toAbsolutePath().normalize());
        if (!session.repositories().sources().equals(repositories.stream().filter(ConfiguredRepository::enabled)
                .map(ConfiguredRepository::source).toList())) {
            throw new IllegalArgumentException("Configured and active repositories differ");
        }
    }

    public static GuiSettings defaults() {
        return new GuiSettings(Theme.LIGHT, SessionOptions.defaults(), List.of(), Optional.empty(), Map.of(), Optional.empty());
    }
}
