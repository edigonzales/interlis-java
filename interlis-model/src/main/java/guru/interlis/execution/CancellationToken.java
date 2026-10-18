package guru.interlis.execution;

/**
 * Cooperative cancellation port. The pure compiler core never owns a clock or
 * thread; hosts map deadlines and worker termination to this value.
 */
public interface CancellationToken {
    /** Returns whether cancellation was requested. */
    boolean isCancellationRequested();

    /** Returns the typed reason; defaults to a user request. */
    default CancellationReason reason() {
        return CancellationReason.USER_REQUESTED;
    }

    /** Throws a typed cancellation exception if cancellation was requested. */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new OperationCancelledException(reason());
        }
    }

    /** Returns a token that never requests cancellation. */
    static CancellationToken none() {
        return () -> false;
    }

    /** Returns an already-cancelled token with the given reason. */
    static CancellationToken requested(CancellationReason reason) {
        return new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return true;
            }

            @Override
            public CancellationReason reason() {
                return reason;
            }
        };
    }
}
