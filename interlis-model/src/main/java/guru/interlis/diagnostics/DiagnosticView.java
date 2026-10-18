package guru.interlis.diagnostics;

import java.util.List;
import java.util.Objects;

/** Projection of the canonical diagnostics for one display policy. */
public record DiagnosticView(List<Diagnostic> visibleDiagnostics,
                             List<SuppressedDiagnostic> suppressedDiagnostics,
                             int hiddenCount) {
    /** Creates a validated instance. */
    public DiagnosticView {
        visibleDiagnostics = List.copyOf(Objects.requireNonNull(visibleDiagnostics, "visibleDiagnostics"));
        suppressedDiagnostics = List.copyOf(
                Objects.requireNonNull(suppressedDiagnostics, "suppressedDiagnostics"));
        if (hiddenCount < 0) {
            throw new IllegalArgumentException("hiddenCount must be non-negative");
        }
    }

    public record SuppressedDiagnostic(Diagnostic diagnostic, SuppressionReason reason,
                                       DiagnosticId visibleCause) {
        /** Creates a validated instance. */
        public SuppressedDiagnostic {
            Objects.requireNonNull(diagnostic, "diagnostic");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(visibleCause, "visibleCause");
        }
    }

    public enum SuppressionReason {
        /** The {@code DEPENDENT_ON_VISIBLE_ERROR} constant. */
        DEPENDENT_ON_VISIBLE_ERROR,
        /** The {@code DUPLICATE_PRESENTATION} constant. */
        DUPLICATE_PRESENTATION,
        /** The {@code DISPLAY_LIMIT} constant. */
        DISPLAY_LIMIT,
        /** The {@code USER_FILTER} constant. */
        USER_FILTER
    }
}
