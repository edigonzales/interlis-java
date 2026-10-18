package guru.interlis.compiler;

import guru.interlis.diagnostics.DiagnosticId;

import java.util.Objects;

/**
 * Typed resolver problem with a safe, credential-free detail string.
 *
 * @param diagnosticId the {@code diagnosticId} component
 * @param reason the {@code reason} component
 * @param safeDetail the {@code safeDetail} component
 */
public record ResolutionProblem(DiagnosticId diagnosticId, ResolutionReason reason,
                                String safeDetail) {
    /** Creates a validated instance. */
    public ResolutionProblem {
        Objects.requireNonNull(diagnosticId, "diagnosticId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(safeDetail, "safeDetail");
    }
}
