package guru.interlis.diagnostics;

import java.util.Objects;

/**
 * Identity of one concrete diagnostic inside one analysis report.
 * Deterministically derived from the canonical diagnostic input; it never
 * depends on run ids, wall time, threads or process-global counters.
 */
public record DiagnosticId(String value) {
    /** Creates a validated instance. */
    public DiagnosticId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("DiagnosticId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
