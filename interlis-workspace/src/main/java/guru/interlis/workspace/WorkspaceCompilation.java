package guru.interlis.workspace;

import guru.interlis.compiler.CompilationOutcome;

import java.util.Objects;
import java.util.Optional;

/** Immutable outcome and strict input capture of the same Workspace compiler invocation. */
public record WorkspaceCompilation(CompilationOutcome outcome, Optional<WorkspaceCapture> capture) {
    public WorkspaceCompilation {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(capture, "capture");
    }
}
