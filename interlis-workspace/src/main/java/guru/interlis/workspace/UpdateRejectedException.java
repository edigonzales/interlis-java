package guru.interlis.workspace;

import guru.interlis.source.SourceRevision;

import java.util.Objects;
import java.util.Optional;

/** Typed update conflict; contains no source text. */
public final class UpdateRejectedException extends RuntimeException {
    private final UpdateRejectionCode code;
    private final Optional<SourceRevision> expected;
    private final Optional<SourceRevision> observed;

    /**
     * Creates a typed update conflict.
     *
     * @param code rejection code
     * @param message credential-free message
     * @param expected expected revision, if applicable
     * @param observed observed revision, if applicable
     */
    public UpdateRejectedException(UpdateRejectionCode code, String message,
                                   Optional<SourceRevision> expected,
                                   Optional<SourceRevision> observed) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.expected = Objects.requireNonNull(expected, "expected");
        this.observed = Objects.requireNonNull(observed, "observed");
    }

    /**
     * Returns the {@code code} value.
     *
     * @return the value
     */
    public UpdateRejectionCode code() {
        return code;
    }

    /**
     * Returns the {@code expected} value.
     *
     * @return the value
     */
    public Optional<SourceRevision> expected() {
        return expected;
    }

    /**
     * Returns the {@code observed} value.
     *
     * @return the value
     */
    public Optional<SourceRevision> observed() {
        return observed;
    }
}
