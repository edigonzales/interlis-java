package guru.interlis.gui.application;

import java.nio.file.Path;
import java.util.List;

/** Ordered, immutable input paths and explicit roots for a compile request. */
public record SessionInputs(List<Path> files, List<RootChoice> roots) {
    public SessionInputs {
        files = List.copyOf(files);
        roots = List.copyOf(roots);
    }

    public boolean isEmpty() {
        return files.isEmpty() && roots.isEmpty();
    }
}
