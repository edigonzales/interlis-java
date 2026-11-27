package guru.interlis.transfer;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Pull reader over one checked transfer sequence. Use from a single caller and close it
 * after reading or abandoning the transfer. Each delivered event is immutable and owned by
 * the caller. The reader keeps model declarations and the active basket context. For
 * uniqueness it keeps only distinct basket ID and FULL object TID strings until completion,
 * failure or close. Memory for this check grows with the number of distinct IDs. The two
 * identity scopes are separate by default. */
public abstract class TransferReader implements AutoCloseable {
    /** Encoding of the supplied XTF stream, independent of the model language. */
    public enum XtfEncoding { XTF23, XTF24 }

    /** Selects the namespaces used by the sequence to check transfer-local identities. */
    protected enum IdentityScope {
        /** Assigns basket IDs and FULL object TIDs to separate namespaces. */
        SEPARATE,
        /** Assigns basket IDs and FULL object TIDs to one namespace. */
        SHARED_BID_FULL_TID
    }

    private final TransferSequence sequence;
    private BiConsumer<? super TransferEvent, ? super CancellationToken> observer;
    private boolean exhausted;

    /** Binds this reader to a nonnull plan. */
    protected TransferReader(TransferPlan plan) {
        this(plan, false);
    }

    /** Binds to a nonnull plan. If enabled, declared models cover their directed import closure. */
    protected TransferReader(TransferPlan plan, boolean includeModelImports) {
        this(plan, includeModelImports, IdentityScope.SEPARATE);
    }

    /** Binds to a nonnull plan and identity scope. If enabled, declared models cover their
     * directed import closure. */
    protected TransferReader(TransferPlan plan, boolean includeModelImports, IdentityScope identityScope) {
        sequence = new TransferSequence(plan, includeModelImports,
                Objects.requireNonNull(identityScope, "identityScope") == IdentityScope.SHARED_BID_FULL_TID);
    }

    /** Returns the immutable plan used to decode this reader's events. */
    public final TransferPlan transferPlan() {
        return sequence.plan();
    }

    /** Returns the source XTF encoding, if known, without reading the stream. */
    public Optional<XtfEncoding> xtfEncoding() {
        return Optional.empty();
    }

    /**
     * Returns immutable aliases of stable identifier namespaces after the header.
     * Formats with no declared aliases return an empty map.
     */
    public Map<String, QualifiedName> stableIdentityNamespaces() {
        return Map.of();
    }

    /** Observes future accepted events synchronously in read order, including EndTransfer.
     * The nonnull observer receives the immutable event and the current read's cancellation
     * token. It must not advance or close this reader. An observer exception during reading
     * makes this reader terminal. The observer is released at EndTransfer, failure or close.
     * @throws IllegalStateException if an observer is already registered or the reader is
     *         ended, failed or closed
     */
    public final void observeEvents(BiConsumer<? super TransferEvent, ? super CancellationToken> observer) {
        sequence.requireOpen();
        if (this.observer != null || sequence.ended()) {
            throw new IllegalStateException("Cannot register an observer after registration or EndTransfer");
        }
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    /** Reads one event. Empty means the source ended after a valid EndTransfer.
     * Read through the empty result to confirm that the source has no trailing events.
     * A cancellation already requested on entry leaves the reader at its current position.
     * If an exception occurs while reading, checking or observing an event, only close remains available.
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
                var event = next.orElseThrow();
                sequence.accept(event);
                var activeObserver = observer;
                if (activeObserver != null) {
                    if (sequence.ended()) {
                        observer = null;
                    }
                    activeObserver.accept(event, cancellation);
                    cancellation.throwIfCancellationRequested();
                }
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
            observer = null;
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

    /** Returns whether this reader's shared identity scope already contains the nonnull wire
     * spelling. An adapter may use this to report a duplicate before decoding the event body.
     * The sequence still checks and registers every accepted event.
     * @throws IllegalStateException if the reader uses separate identity scopes
     */
    protected final boolean isSharedIdentityRegistered(String id) {
        return sequence.hasSharedIdentity(Objects.requireNonNull(id, "id"));
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
            observer = null;
            release();
        }
    }
}
