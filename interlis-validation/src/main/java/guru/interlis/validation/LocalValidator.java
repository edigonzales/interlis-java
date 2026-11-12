package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferReader;

import java.util.Objects;

/** Entry points for local validation of decoded transfer events. */
public final class LocalValidator {
    private LocalValidator() { }

    /**
     * Reads through EOF before returning a completed report. The caller owns the reader.
     * Reader failures and cancellation are propagated as supplied.
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

    /** Creates an independent, single-caller session from a shareable plan. */
    public static ValidationSession session(ValidationPlan plan, ValidationOptions options) {
        return new ValidationSession(Objects.requireNonNull(plan, "plan"), Objects.requireNonNull(options, "options"));
    }
}
