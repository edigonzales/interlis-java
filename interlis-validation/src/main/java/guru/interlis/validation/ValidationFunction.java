package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferValue;

import java.util.List;

/** Trusted, synchronous host implementation with cooperative cancellation. */
@FunctionalInterface
public interface ValidationFunction {
    /**
     * Evaluates immutable arguments in signature order. A returned undefined value
     * is permitted only when the declaration permits it.
     */
    TransferValue evaluate(List<TransferValue> arguments, FunctionContext context, CancellationToken cancellation);
}
