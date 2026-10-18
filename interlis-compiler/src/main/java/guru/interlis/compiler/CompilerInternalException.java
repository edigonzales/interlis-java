package guru.interlis.compiler;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.FailureId;

import java.util.Objects;

/** Unexpected internal defect, not a user model error. */
public final class CompilerInternalException extends RuntimeException {
    private final FailureId failureId;
    private final Diagnostic safeDiagnostic;

    public CompilerInternalException(FailureId failureId, Diagnostic safeDiagnostic,
                                     Throwable cause) {
        super("internal compiler failure " + Objects.requireNonNull(failureId, "failureId"), cause);
        this.failureId = failureId;
        this.safeDiagnostic = Objects.requireNonNull(safeDiagnostic, "safeDiagnostic");
    }

    /**
     * Returns the {@code failureId} value.
     *
     * @return the value
     */
    public FailureId failureId() {
        return failureId;
    }

    /**
     * Returns the {@code safeDiagnostic} value.
     *
     * @return the value
     */
    public Diagnostic safeDiagnostic() {
        return safeDiagnostic;
    }
}
