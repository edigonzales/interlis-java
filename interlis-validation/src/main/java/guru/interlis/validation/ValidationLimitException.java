package guru.interlis.validation;

/** Validation stopped at a configured validation or storage budget, without a report. */
public final class ValidationLimitException extends IllegalStateException {
    /** Creates a limit failure with a non-null explanatory message. */
    public ValidationLimitException(String message) {
        super(java.util.Objects.requireNonNull(message, "message"));
    }
}
