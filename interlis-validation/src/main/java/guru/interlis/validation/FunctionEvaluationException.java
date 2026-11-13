package guru.interlis.validation;

import guru.interlis.model.SymbolRef;

/** Reports a provider failure or violation of a bound function contract. */
public final class FunctionEvaluationException extends RuntimeException {
    private final SymbolRef function;

    FunctionEvaluationException(SymbolRef function, String message, Throwable cause) {
        super(message, cause);
        this.function = function;
    }

    public SymbolRef function() {
        return function;
    }
}
