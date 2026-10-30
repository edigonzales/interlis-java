package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structured host rejection, distinct from compiler diagnostics and missing language support. */
public record RepositoryProblem(Reason reason, String modelName, List<SourceId> candidates,
                                Optional<SourceLoadException.Reason> loadReason) {
    public RepositoryProblem {
        Objects.requireNonNull(reason, "reason"); Objects.requireNonNull(modelName, "modelName");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        loadReason = Objects.requireNonNull(loadReason, "loadReason");
    }
    public enum Reason {
        NOT_FOUND, AMBIGUOUS, VERSION_MISMATCH, INVALID_SOURCE, INDEX_FAILURE,
        LOAD_FAILURE, PIN_MISMATCH, LOCK_MISMATCH, CHANGED_SOURCE
    }
}
