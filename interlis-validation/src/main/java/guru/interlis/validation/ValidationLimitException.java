package guru.interlis.validation;

/** Validation stopped at a configured object budget, without a report. */
public final class ValidationLimitException extends IllegalStateException {
    ValidationLimitException(String message) {
        super(message);
    }
}
