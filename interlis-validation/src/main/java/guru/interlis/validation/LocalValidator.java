package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferReader;

import java.util.Objects;

/** Entry points for local validation of decoded transfer events. */
public final class LocalValidator {
    private LocalValidator() { }

    /**
     * Reads through EOF before returning a completed report. The caller owns the reader.
     * All inputs are non-null. The reader must provide events for the same
     * schema revision as the plan. Reader failures and cancellation are
     * propagated as supplied.
     *
     * @throws ValidationStreamException if the events lack the required validation context
     * @throws ValidationLimitException if a validation budget is exceeded
     * @throws guru.interlis.execution.OperationCancelledException on cancellation
     */
    public static ValidationReport validate(ValidationPlan plan, TransferReader reader,
                                            ValidationOptions options, CancellationToken cancellation) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(cancellation, "cancellation");
        try (var session = session(plan, options)) {
            for (;;) {
                var event = reader.readNext(cancellation);
                if (event.isEmpty()) {
                    cancellation.throwIfCancellationRequested();
                    return session.finish();
                }
                session.accept(event.orElseThrow(), cancellation);
            }
        }
    }

    /**
     * Validates exactly one decoded object with its original basket as context. All inputs are non-null.
     * The plan must use {@link ValidationProfile#LOCAL_ONLY}. The basket and object must belong to its
     * schema revision. The report counts one object and zero baskets. It retains the object's source
     * if supplied in its identity. Basket and dataset rules are outside the checked scope.
     *
     * @throws IllegalArgumentException if the plan does not use the local profile
     * @throws ValidationStreamException if the basket or object lacks the required validation context
     * @throws ValidationLimitException if a validation budget is exceeded
     * @throws guru.interlis.execution.OperationCancelledException on cancellation
     */
    public static ValidationReport validateObject(ValidationPlan plan, TransferEvent.StartBasket basket,
                                                  TransferEvent.ObjectEvent object, ValidationOptions options,
                                                  CancellationToken cancellation) {
        try (var session = session(plan, options)) {
            return session.validateObject(Objects.requireNonNull(basket, "basket"),
                    Objects.requireNonNull(object, "object"), Objects.requireNonNull(cancellation, "cancellation"));
        }
    }

    /** Creates an independent, single-caller session from a shareable plan. */
    public static ValidationSession session(ValidationPlan plan, ValidationOptions options) {
        Objects.requireNonNull(plan, "plan");
        if (plan.profile() != ValidationProfile.LOCAL_ONLY) {
            throw new IllegalArgumentException("Local validation requires a LOCAL_ONLY plan");
        }
        return new ValidationSession(plan, Objects.requireNonNull(options, "options"));
    }
}
