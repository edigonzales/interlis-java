package guru.interlis.transfer;

import guru.interlis.execution.CancellationToken;

import java.util.Objects;

/** Push writer over one checked transfer sequence. Use from a single caller and close it
 * to release the destination. Each accepted event is delivered synchronously. The writer
 * keeps model declarations and the active basket context. For uniqueness it keeps only
 * distinct basket ID and FULL object TID strings until completion, failure or close. Memory
 * for this check grows with the number of distinct IDs. These two identity scopes are separate. */
public abstract class TransferWriter implements AutoCloseable {
    private final TransferSequence sequence;

    /** Binds this writer to a nonnull plan. */
    protected TransferWriter(TransferPlan plan) {
        sequence = new TransferSequence(plan);
    }

    /** Writes an event without a cancellation request. */
    public final void write(TransferEvent event) {
        write(event, CancellationToken.none());
    }

    /** Checks and writes one nonnull event. Pre-cancellation and protocol rejection leave the
     * writer at its current position. If writing to the destination throws, only close remains
     * available, since the adapter may have written part of the event. The failure is propagated.
     * @throws TransferProtocolException on invalid event order or context, or a failed or closed writer
     * @throws guru.interlis.execution.OperationCancelledException on cancellation
     */
    public final void write(TransferEvent event, CancellationToken cancellation) {
        sequence.requireOpen();
        Objects.requireNonNull(cancellation, "cancellation").throwIfCancellationRequested();
        sequence.accept(Objects.requireNonNull(event, "event"));
        try {
            writeEvent(event, cancellation);
        } catch (RuntimeException failure) {
            sequence.fail();
            throw failure;
        }
    }

    /** Writes one checked event to the destination. Long writes should observe cancellation. */
    protected abstract void writeEvent(TransferEvent event, CancellationToken cancellation);

    /** Releases the destination once, including after partial or failed writes. */
    protected abstract void release();

    /** Closes the writer and releases its destination. Subsequent closes are no-ops.
     * @throws TransferProtocolException if the destination was released successfully
     *         and the event stream had neither ended nor previously failed
     */
    @Override
    public final void close() {
        boolean incomplete = !sequence.ended() && !sequence.failed();
        if (sequence.beginClose()) {
            release();
            if (incomplete) {
                throw new TransferProtocolException(TransferProtocolException.Reason.INCOMPLETE_TRANSFER,
                        "Writer closed before EndTransfer");
            }
        }
    }
}
