package guru.interlis.compiler.query;

import guru.interlis.model.SnapshotId;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Completion result with explicit incompleteness.
 *
 * @param candidates the {@code candidates} component
 * @param replacementRange the {@code replacementRange} component
 * @param incomplete the {@code incomplete} component
 * @param snapshotId snapshot identifier
 */
public record CompletionResult(List<CompletionCandidate> candidates,
                               SourceRange replacementRange, boolean incomplete,
                               SnapshotId snapshotId) {
    /** Creates a validated instance. */
    public CompletionResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(replacementRange, "replacementRange");
        Objects.requireNonNull(snapshotId, "snapshotId");
    }
}
