package guru.interlis.transfer;

import java.util.Objects;

/** Failure at the neutral transfer stream boundary. */
public final class TransferProtocolException extends IllegalStateException {
    private final Reason reason;

    TransferProtocolException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** Returns the stable category of the failure. */
    public Reason reason() {
        return reason;
    }

    /** Stream, lifecycle and context failure categories. */
    public enum Reason {
        /** The event cannot occur at the current point in the stream. */
        UNEXPECTED_EVENT,
        /** The stream stopped before a valid end event. */
        INCOMPLETE_TRANSFER,
        /** The reader or writer is closed. */
        CLOSED,
        /** An earlier stream failure prevents further use. */
        FAILED,
        /** A model handle belongs to a different schema snapshot. */
        FOREIGN_SNAPSHOT,
        /** The referenced model element is absent from the plan or has the wrong kind. */
        UNKNOWN_MODEL_ELEMENT,
        /** A basket ID or full-transfer object ID has already occurred in its scope. */
        DUPLICATE_ID,
        /** The event conflicts with its declared model or active basket. */
        CONTEXT_MISMATCH
    }
}
