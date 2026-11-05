package guru.interlis.gui.application;

import guru.interlis.repository.SourceLoadException;
import guru.interlis.source.SourceId;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Discovery of an explicitly added file, separate from the contents captured at compile time. */
public record LocalInput(Path path, Optional<SourceId> source, List<RootChoice.Local> models, Optional<Problem> problem) {
    public LocalInput {
        Objects.requireNonNull(path);
        Objects.requireNonNull(source);
        models = List.copyOf(models);
        Objects.requireNonNull(problem);
    }

    public record Problem(Path path, SourceLoadException.Reason reason, String message) {
        public Problem {
            Objects.requireNonNull(path);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(message);
        }
    }
}
