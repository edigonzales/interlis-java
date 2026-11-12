package guru.interlis.validation;

/** A sequence rejected by the validation session before a report can be produced. */
public final class ValidationStreamException extends IllegalStateException {
    ValidationStreamException(String message) {
        super(message);
    }
}
