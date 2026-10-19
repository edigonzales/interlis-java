package guru.interlis.repository;

import java.util.Objects;

/** Typed repository-index failure; messages contain no credentials. */
public final class RepositoryIndexException extends RuntimeException {
    private final Reason reason;

    /**
     * Creates an instance.
     *
     * @param reason typed failure reason
     * @param safeMessage safe message
     * @param cause optional cause
     */
    public RepositoryIndexException(Reason reason, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns the typed reason.
     *
     * @return reason
     */
    public Reason reason() {
        return reason;
    }

    /** Failure classes. */
    public enum Reason {
        /** Malformed index document. */
        MALFORMED,
        /** Required model metadata is missing. */
        MISSING_METADATA,
        /** Model file URI escapes the repository base or uses a forbidden scheme. */
        UNSAFE_LOCATION
    }
}
