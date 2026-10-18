package guru.interlis.repository;

import java.util.Objects;

/** Typed host IO failure with a credential-free message. */
public final class SourceLoadException extends RuntimeException {
    private final Reason reason;

    /**
     * Creates an instance.
     *
     * @param reason the {@code reason} argument
     * @param safeMessage the {@code safeMessage} argument
     * @param cause the {@code cause} argument
     */
    public SourceLoadException(Reason reason, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns the {@code reason} value.
     *
     * @return the value
     */
    public Reason reason() {
        return reason;
    }

    public enum Reason {
        /** The {@code NOT_FOUND} constant. */
        NOT_FOUND,
        /** The {@code ACCESS_DENIED} constant. */
        ACCESS_DENIED,
        /** The {@code IO_FAILURE} constant. */
        IO_FAILURE,
        /** The {@code TOO_LARGE} constant. */
        TOO_LARGE,
        /** The {@code INVALID_ENCODING} constant. */
        INVALID_ENCODING,
        /** The {@code OFFLINE} constant. */
        OFFLINE,
        /** The {@code REDIRECT_LIMIT} constant. */
        REDIRECT_LIMIT,
        /** The {@code TIMEOUT} constant. */
        TIMEOUT
    }
}
