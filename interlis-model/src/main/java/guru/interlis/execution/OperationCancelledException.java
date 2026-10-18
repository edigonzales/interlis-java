package guru.interlis.execution;

import java.util.Objects;

/** Controlled cancellation; carries no compiler-specific snapshot. */
public final class OperationCancelledException extends RuntimeException {
    private final CancellationReason reason;

    /**
     * Creates a cancellation exception with the given reason.
     *
     * @param reason typed cancellation reason
     */
    public OperationCancelledException(CancellationReason reason) {
        super("operation cancelled: " + Objects.requireNonNull(reason, "reason"));
        this.reason = reason;
    }

    /** Returns the typed cancellation reason. */
    public CancellationReason reason() {
        return reason;
    }
}
