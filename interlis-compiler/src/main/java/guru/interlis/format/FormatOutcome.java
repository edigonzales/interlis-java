package guru.interlis.format;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationReason;

import java.util.List;
import java.util.Objects;

/** Complete result of one source-formatting request. */
public sealed interface FormatOutcome {
    /**
     * Successfully formatted source text.
     *
     * @param text formatted source
     * @param notices non-fatal formatting notices
     */
    record Formatted(String text, List<FormattingNotice> notices) implements FormatOutcome {
        /** Creates a validated instance. */
        public Formatted {
            Objects.requireNonNull(text, "text");
            notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
        }
    }

    /**
     * Source could not be formatted without changing its meaning.
     *
     * @param diagnostics parse/syntax diagnostics
     */
    record Rejected(List<Diagnostic> diagnostics) implements FormatOutcome {
        /** Creates a validated instance. */
        public Rejected {
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /**
     * Formatting was cancelled.
     *
     * @param reason cancellation reason
     */
    record Cancelled(CancellationReason reason) implements FormatOutcome {
        /** Creates a validated instance. */
        public Cancelled {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
