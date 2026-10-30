package guru.interlis.workspace;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunContext;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;

import java.util.List;
import java.util.Optional;

/**
 * Synchronous in-memory workspace. Mutating methods are not reentrant on one
 * instance; independent instances may run in parallel.
 */
public interface Workspace extends AutoCloseable {
    /** Returns the current immutable workspace revision.
     *
     * @return current revision
     */
    WorkspaceRevision currentRevision();

    /** Returns an immutable metadata snapshot; source text is not included.
     *
     * @return metadata snapshot
     */
    WorkspaceState state();

    /** Returns the immutable current configuration, without IO. */
    WorkspaceConfiguration configuration();

    /** Returns the current text of one source, if present.
     *
     * @param id source identity
     * @return current source text, or empty
     */
    Optional<SourceText> source(SourceId id);

    /**
     * Applies one source update atomically.
     *
     * @param update single source update
     * @return update receipt with the new revision
     * @throws UpdateRejectedException if the expected revision/change expectation fails
     */
    UpdateReceipt update(SourceUpdate update);

    /**
     * Applies a batch atomically; any rejected member leaves the workspace unchanged.
     *
     * @param updates ordered source updates
     * @param expectedRevision revision the batch is based on
     * @return batch receipt
     * @throws UpdateRejectedException if a member conflicts or the revision is stale
     */
    BatchUpdateReceipt applyBatch(List<SourceUpdate> updates,
                                  WorkspaceRevision expectedRevision);

    /**
     * Atomically applies sources and the complete resolver/compiler configuration.
     * A stale revision or any invalid update leaves both sources and configuration
     * unchanged. IO and parsing must finish before this short conditional commit.
     */
    BatchUpdateReceipt applyBatch(List<SourceUpdate> updates, WorkspaceRevision expectedRevision,
                                  WorkspaceConfiguration configuration);

    /** Also rejects any intervening configuration change, including cache-only settings. */
    BatchUpdateReceipt applyBatch(List<SourceUpdate> updates, WorkspaceRevision expectedRevision,
                                  WorkspaceConfiguration configuration, String expectedConfigurationFingerprint);

    /** Removes a source under an explicit revision check.
     *
     * @param id source identity
     * @param expectedRevision expected current revision
     * @return update receipt
     */
    UpdateReceipt remove(SourceId id, SourceRevision expectedRevision);

    /** Changes host/compiler configuration under an explicit revision check.
     *
     * @param configuration new configuration
     * @param expectedRevision expected current revision
     * @return configuration receipt
     */
    ConfigurationReceipt configure(WorkspaceConfiguration configuration,
                                   WorkspaceRevision expectedRevision);

    /** Captures a reusable analysis snapshot without publishing current diagnostics.
     *
     * @param request analysis request
     * @return detached capture
     */
    WorkspaceCapture capture(AnalysisRequest request);

    /** Cancellable variant of {@link #capture(AnalysisRequest)}.
     *
     * @param request analysis request
     * @param token cancellation signal
     * @return detached capture
     */
    WorkspaceCapture capture(AnalysisRequest request, CancellationToken token);

    /** Accepts a capture only if its revision and configuration still match.
     *
     * @param capture detached capture
     * @param analysis analysis computed for the capture
     * @return acceptance receipt
     */
    AnalysisAcceptance acceptAnalysis(WorkspaceCapture capture, AnalysisSnapshot analysis);

    /** Analyzes the current revision and returns an immutable snapshot.
     *
     * @param request analysis request
     * @param token cancellation signal
     * @return immutable analysis snapshot
     */
    AnalysisSnapshot analyze(AnalysisRequest request, CancellationToken token);

    /** Analyzes with an explicit run context for observation/cancellation.
     *
     * @param request analysis request
     * @param context run identity/observer
     * @param token cancellation signal
     * @return immutable analysis snapshot
     */
    AnalysisSnapshot analyze(AnalysisRequest request, RunContext context,
                             CancellationToken token);

    /** Compiles the requested closure at a pinned revision.
     *
     * @param request compile request
     * @param token cancellation signal
     * @return closed compilation outcome
     */
    CompilationOutcome compile(WorkspaceCompileRequest request, CancellationToken token);

    /** Compiles with an explicit run context.
     *
     * @param request compile request
     * @param context run identity/observer
     * @param token cancellation signal
     * @return closed compilation outcome
     */
    CompilationOutcome compile(WorkspaceCompileRequest request, RunContext context,
                               CancellationToken token);

    /** Returns accumulated workspace metrics; values are advisory, not a snapshot.
     *
     * @return current counters
     */
    WorkspaceMetrics metrics();

    /** Closes the workspace; later mutating calls are rejected. */
    @Override
    void close();
}
