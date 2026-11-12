package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import java.util.Objects;
import java.util.Optional;

/** A failure to build a coherent plan, optionally at a snapshot-bound symbol. */
public record PlanningProblem(Code code, Optional<SymbolRef> symbol, String message) {
    public PlanningProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(message, "message");
    }

    /** Machine-readable planning failure. */
    public enum Code {
        SNAPSHOT_MISMATCH, SCHEMA_FINGERPRINT_MISMATCH, MISSING_MODEL_INFORMATION
    }
}
