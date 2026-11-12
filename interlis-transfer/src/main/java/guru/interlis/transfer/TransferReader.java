package guru.interlis.transfer;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pull reader over one checked transfer sequence. Use from a single caller and close it
 * after reading or abandoning the transfer. Each delivered event is immutable and owned by
 * the caller. The reader keeps model declarations and the active basket context. For
 * uniqueness it keeps only distinct basket ID and FULL object TID strings until completion,
 * failure or close. Memory for this check grows with the number of distinct IDs. These two
 * identity scopes are separate. */
public abstract class TransferReader implements AutoCloseable {
    private final TransferSequence sequence;
    private boolean exhausted;

    /** Binds this reader to a nonnull plan. */
    protected TransferReader(TransferPlan plan) {
        this(plan, false);
    }

    /** Binds to a nonnull plan. If enabled, declared models cover their directed import closure. */
    protected TransferReader(TransferPlan plan, boolean includeModelImports) {
        sequence = new TransferSequence(plan, includeModelImports);
    }

    /** Returns the immutable plan used to decode this reader's events. */
    public final TransferPlan transferPlan() {
        return sequence.plan();
    }

    /**
     * Returns immutable aliases of stable identifier namespaces after the header.
     * Formats with no declared aliases return an empty map.
     */
    public Map<String, QualifiedName> stableIdentityNamespaces() {
        return Map.of();
    }

    /** Reads one event. Empty means the source ended after a valid EndTransfer.
     * Read through the empty result to confirm that the source has no trailing events.
     * A cancellation already requested on entry leaves the reader at its current position.
     * If an exception occurs while reading or checking an event, only close remains available.
     * Adapter errors are propagated as supplied by the adapter.
     * @throws TransferProtocolException if the stream is incomplete, invalid, failed or closed
     * @throws guru.interlis.execution.OperationCancelledException on cancellation
     */
    public final Optional<TransferEvent> readNext(CancellationToken cancellation) {
        sequence.requireOpen();
        Objects.requireNonNull(cancellation, "cancellation").throwIfCancellationRequested();
        if (exhausted) {
            return Optional.empty();
        }
        try {
            var next = Objects.requireNonNull(readEvent(cancellation), "source result");
            cancellation.throwIfCancellationRequested();
            if (next.isPresent()) {
                sequence.accept(next.orElseThrow());
            } else {
                if (!sequence.ended()) {
                    throw new TransferProtocolException(TransferProtocolException.Reason.INCOMPLETE_TRANSFER,
                            "Source ended before EndTransfer");
                }
                exhausted = true;
            }
            return next;
        } catch (RuntimeException failure) {
            sequence.fail();
            try {
                onFailure();
            } catch (RuntimeException cleanup) {
                if (cleanup != failure) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    /** Pulls one immutable event or reports the underlying source's end. Long reads
     * should observe the cancellation token. */
    protected abstract Optional<TransferEvent> readEvent(CancellationToken cancellation);

    /** Allows an adapter to drop its operation state on a terminal failure. */
    protected void onFailure() {}

    /** Releases the underlying source once, including after partial or failed reads. */
    protected abstract void release();

    /** Closes this reader and releases its source. Closing early abandons the transfer.
     * Subsequent closes are no-ops. */
    @Override
    public final void close() {
        if (sequence.beginClose()) {
            release();
        }
    }
}
