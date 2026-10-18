package guru.interlis.workspace;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngine;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.execution.RunContext;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.source.TextEdit;
import guru.interlis.workspace.resolution.DefaultModelResolutionService;
import guru.interlis.workspace.resolution.IncompleteClosure;
import guru.interlis.workspace.resolution.PreparedClosure;
import guru.interlis.workspace.resolution.ResolutionOutcome;
import guru.interlis.workspace.resolution.ResolutionRequest;
import guru.interlis.workspace.resolution.SourceInventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** In-memory workspace with conservative invalidation and conditional commits. */
final class DefaultWorkspace implements Workspace {
    private final CompilerEngine engine;
    private final Object origin = new Object();
    private final Map<SourceId, SourceText> sources = new LinkedHashMap<>();
    private final Map<SourceId, SourceRevision> revisions = new LinkedHashMap<>();
    private final Map<SourceId, SourceRevision> tombstones = new LinkedHashMap<>();
    private WorkspaceConfiguration configuration;
    private WorkspaceRevision revision = WorkspaceRevision.initial();
    private AnalysisSnapshot lastAnalysis;
    private boolean closed;
    private boolean busy;
    private long parses;
    private long syntaxReuses;
    private long semanticAnalyses;
    private long accepted;
    private long stale;
    private long evictions;
    private long cancellations;

    DefaultWorkspace(CompilerEngine engine, WorkspaceConfiguration configuration) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public synchronized WorkspaceRevision currentRevision() {
        return revision;
    }

    @Override
    public synchronized WorkspaceState state() {
        return new WorkspaceState(revision, revisions,
                configuration.semanticFingerprint(), closed);
    }

    @Override
    public synchronized Optional<SourceText> source(SourceId id) {
        requireOpen();
        return Optional.ofNullable(sources.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public synchronized UpdateReceipt update(SourceUpdate update) {
        requireOpen();
        enter();
        try {
            return applyChecked(update);
        } finally {
            exit();
        }
    }

    @Override
    public synchronized BatchUpdateReceipt applyBatch(List<SourceUpdate> updates,
                                                       WorkspaceRevision expectedRevision) {
        requireOpen();
        enter();
        try {
            Objects.requireNonNull(updates, "updates");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            if (!revision.equals(expectedRevision)) {
                throw conflict(UpdateRejectionCode.VERSION_CONFLICT,
                        "workspace revision changed", Optional.empty(), Optional.empty());
            }
            Set<SourceId> seen = new LinkedHashSet<>();
            for (SourceUpdate update : updates) {
                if (!seen.add(update.source())) {
                    throw conflict(UpdateRejectionCode.VERSION_CONFLICT,
                            "source occurs more than once in batch", Optional.empty(),
                            Optional.empty());
                }
            }
            Map<SourceId, SourceText> staged = new LinkedHashMap<>(sources);
            Map<SourceId, SourceRevision> stagedRevisions = new LinkedHashMap<>(revisions);
            List<UpdateReceipt> receipts = new ArrayList<>();
            List<InvalidationReason> reasons = new ArrayList<>();
            WorkspaceRevision previous = revision;
            WorkspaceRevision next = nextWorkspaceRevision();
            boolean changed = false;
            for (SourceUpdate update : updates) {
                UpdateReceipt receipt = applyTo(staged, stagedRevisions, update,
                        previous, next);
                receipts.add(receipt);
                if (receipt.changed()) {
                    changed = true;
                    reasons.addAll(receipt.reasons());
                }
            }
            if (changed) {
                sources.clear();
                sources.putAll(staged);
                revisions.clear();
                revisions.putAll(stagedRevisions);
                revision = next;
                invalidateLastAnalysis();
            }
            return new BatchUpdateReceipt(previous, revision, receipts,
                    List.copyOf(new LinkedHashSet<>(reasons)));
        } finally {
            exit();
        }
    }

    @Override
    public synchronized UpdateReceipt remove(SourceId id, SourceRevision expectedRevision) {
        requireOpen();
        enter();
        try {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            SourceText existing = sources.get(id);
            if (existing == null) {
                throw conflict(UpdateRejectionCode.SOURCE_MISSING, "source is missing",
                        Optional.of(expectedRevision), Optional.empty());
            }
            if (!existing.revision().equals(expectedRevision)) {
                throw conflict(UpdateRejectionCode.VERSION_CONFLICT, "source revision changed",
                        Optional.of(expectedRevision), Optional.of(existing.revision()));
            }
            WorkspaceRevision previous = revision;
            sources.remove(id);
            revisions.remove(id);
            tombstones.put(id, existing.revision());
            revision = nextWorkspaceRevision();
            invalidateLastAnalysis();
            return new UpdateReceipt(id, Optional.of(existing.revision()), Optional.empty(),
                    previous, revision, true, List.of(InvalidationReason.SOURCE_REMOVED));
        } finally {
            exit();
        }
    }

    @Override
    public synchronized ConfigurationReceipt configure(WorkspaceConfiguration newConfiguration,
                                                        WorkspaceRevision expectedRevision) {
        requireOpen();
        enter();
        try {
            Objects.requireNonNull(newConfiguration, "configuration");
            Objects.requireNonNull(expectedRevision, "expectedRevision");
            if (!revision.equals(expectedRevision)) {
                throw conflict(UpdateRejectionCode.VERSION_CONFLICT,
                        "workspace revision changed", Optional.empty(), Optional.empty());
            }
            String oldSemantic = configuration.semanticFingerprint();
            String newSemantic = newConfiguration.semanticFingerprint();
            String oldFull = configuration.fullFingerprint();
            String newFull = newConfiguration.fullFingerprint();
            WorkspaceRevision previous = revision;
            List<InvalidationReason> reasons = new ArrayList<>();
            boolean semanticChanged = !oldSemantic.equals(newSemantic);
            boolean anyChanged = !oldFull.equals(newFull);
            configuration = newConfiguration;
            if (semanticChanged) {
                revision = nextWorkspaceRevision();
                invalidateLastAnalysis();
                reasons.add(InvalidationReason.CONFIGURATION_CHANGED);
            } else if (anyChanged) {
                reasons.add(InvalidationReason.CACHE_LIMIT_CHANGED);
            }
            return new ConfigurationReceipt(previous, revision, oldFull, newFull,
                    anyChanged, reasons);
        } finally {
            exit();
        }
    }

    @Override
    public WorkspaceCapture capture(AnalysisRequest request) {
        return capture(request, CancellationToken.none());
    }

    @Override
    public synchronized WorkspaceCapture capture(AnalysisRequest request, CancellationToken token) {
        requireOpen();
        enter();
        try {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(token, "token");
            if (!revision.equals(request.expectedWorkspaceRevision())) {
                throw conflict(UpdateRejectionCode.VERSION_CONFLICT,
                        "workspace revision changed", Optional.empty(), Optional.empty());
            }
            long totalUtf16 = 0;
            for (SourceText text : sources.values()) {
                if (text.length() > configuration.compilerOptions().budget()
                        .maxSourceUtf16Length()) {
                    throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
                }
                totalUtf16 = Math.addExact(totalUtf16, text.length());
            }
            if (totalUtf16 > configuration.compilerOptions().budget()
                    .maxTotalSourceUtf16Length()) {
                throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
            }
            SourceInventory inventory = new SourceInventory(revision, sources,
                    configuration.resolverCatalog(), configuration.compilerOptions().budget());
            ResolutionOutcome outcome = new DefaultModelResolutionService().prepare(
                    new ResolutionRequest(request.roots(), configuration.resolutionPolicy(),
                            configuration.compilerOptions().budget(),
                            inventory.fingerprint().value()), () -> inventory, token);
            PreparedClosure closure;
            switch (outcome) {
                case ResolutionOutcome.Prepared prepared -> closure = prepared.closure();
                case ResolutionOutcome.ResolutionCancelled cancelled -> {
                    cancellations++;
                    throw new OperationCancelledException(cancelled.reason());
                }
                case ResolutionOutcome.ResolutionFailed failed ->
                        throw new IllegalStateException("model resolution failed: "
                                + failed.failureId());
            }
            CompilationRequest compilationRequest = new CompilationRequest(request.roots(),
                    closure.sourceSet(), configuration.compilerOptions());
            AnalysisReuse reuse = lastAnalysis == null ? AnalysisReuse.none()
                    : new AnalysisReuse(Optional.of(lastAnalysis),
                    lastAnalysis.syntaxSnapshots());
            String fingerprint = inputFingerprint(inventory, request, compilationRequest);
            return new WorkspaceCapture(revision, compilationRequest, reuse, closure,
                    new InputFingerprint("SHA-256/workspace-capture-v1", fingerprint), origin);
        } finally {
            exit();
        }
    }

    @Override
    public synchronized AnalysisAcceptance acceptAnalysis(WorkspaceCapture capture,
                                                           AnalysisSnapshot analysis) {
        requireOpen();
        enter();
        try {
            Objects.requireNonNull(capture, "capture");
            Objects.requireNonNull(analysis, "analysis");
            if (capture.origin() != origin) {
                throw new IllegalArgumentException("capture belongs to another workspace");
            }
            if (!revision.equals(capture.workspaceRevision())) {
                stale++;
                return AnalysisAcceptance.STALE_NO_COMMIT;
            }
            verifySnapshot(capture, analysis);
            lastAnalysis = analysis;
            accepted++;
            return AnalysisAcceptance.ACCEPTED;
        } finally {
            exit();
        }
    }

    private void verifySnapshot(WorkspaceCapture capture, AnalysisSnapshot analysis) {
        Map<SourceId, SourceRevision> expected = new LinkedHashMap<>();
        capture.request().sources().sources().forEach((id, text) -> expected.put(id, text.revision()));
        if (!analysis.sourceRevisions().equals(expected)) {
            throw new IllegalArgumentException("analysis source revisions do not match capture");
        }
        if (!analysis.provenance().optionsFingerprint()
                .equals(configuration.compilerOptions().fingerprint())) {
            throw new IllegalArgumentException("analysis options do not match configuration");
        }
        capture.request().sources().sources().forEach((id, text) -> {
            var syntax = analysis.syntaxSnapshot(id);
            if (syntax.isEmpty()
                    || !syntax.get().source().contentFingerprint()
                    .equals(text.contentFingerprint())) {
                throw new IllegalArgumentException("analysis source content does not match capture");
            }
        });
    }

    @Override
    public AnalysisSnapshot analyze(AnalysisRequest request, CancellationToken token) {
        WorkspaceCapture capture = capture(request, token);
        AnalysisSnapshot analysis = engine.analyze(capture.request(), capture.reuse(), token);
        semanticAnalyses++;
        acceptAnalysis(capture, analysis);
        return analysis;
    }

    @Override
    public AnalysisSnapshot analyze(AnalysisRequest request, RunContext context,
                                    CancellationToken token) {
        WorkspaceCapture capture = capture(request, token);
        AnalysisSnapshot analysis = engine.analyze(capture.request(), capture.reuse(), context,
                token);
        semanticAnalyses++;
        acceptAnalysis(capture, analysis);
        return analysis;
    }

    @Override
    public CompilationOutcome compile(WorkspaceCompileRequest request, CancellationToken token) {
        WorkspaceCapture capture = capture(new AnalysisRequest(
                request.expectedWorkspaceRevision(), request.roots()), token);
        CompilationOutcome outcome = engine.compile(capture.request(), capture.reuse(), token);
        acceptOutcome(capture, outcome);
        return outcome;
    }

    @Override
    public CompilationOutcome compile(WorkspaceCompileRequest request, RunContext context,
                                      CancellationToken token) {
        WorkspaceCapture capture = capture(new AnalysisRequest(
                request.expectedWorkspaceRevision(), request.roots()), token);
        CompilationOutcome outcome = engine.compile(capture.request(), capture.reuse(), context,
                token);
        acceptOutcome(capture, outcome);
        return outcome;
    }

    private void acceptOutcome(WorkspaceCapture capture, CompilationOutcome outcome) {
        switch (outcome) {
            case CompilationOutcome.Succeeded succeeded -> acceptAnalysis(capture, succeeded.analysis());
            case CompilationOutcome.Rejected rejected -> acceptAnalysis(capture, rejected.analysis());
            case CompilationOutcome.Cancelled cancelled -> cancellations++;
            case CompilationOutcome.Failed ignored -> {
            }
        }
    }

    @Override
    public synchronized WorkspaceMetrics metrics() {
        return new WorkspaceMetrics(parses, syntaxReuses, semanticAnalyses, accepted, stale,
                sources.size(), lastAnalysis == null ? 0 : 1, evictions, cancellations);
    }

    @Override
    public synchronized void close() {
        closed = true;
        lastAnalysis = null;
    }

    private UpdateReceipt applyChecked(SourceUpdate update) {
        Map<SourceId, SourceText> staged = new LinkedHashMap<>(sources);
        Map<SourceId, SourceRevision> stagedRevisions = new LinkedHashMap<>(revisions);
        WorkspaceRevision previous = revision;
        WorkspaceRevision next = nextWorkspaceRevision();
        UpdateReceipt receipt = applyTo(staged, stagedRevisions, update, previous, next);
        if (receipt.changed()) {
            sources.clear();
            sources.putAll(staged);
            revisions.clear();
            revisions.putAll(stagedRevisions);
            revision = next;
            invalidateLastAnalysis();
        }
        return receipt;
    }

    private UpdateReceipt applyTo(Map<SourceId, SourceText> staged,
                                  Map<SourceId, SourceRevision> stagedRevisions,
                                  SourceUpdate update, WorkspaceRevision previousRevision,
                                  WorkspaceRevision newRevision) {
        SourceId id = update.source();
        SourceText existing = staged.get(id);
        SourceRevision previousSourceRevision = existing == null ? null : existing.revision();
        validateExpectation(update, existing);
        String text = switch (update.change()) {
            case SourceChange.Replace replace -> replace.text();
            case SourceChange.ApplyEdits edits -> applyEdits(existing, edits.edits());
        };
        if (text.length() > configuration.compilerOptions().budget().maxSourceUtf16Length()) {
            throw conflict(UpdateRejectionCode.SOURCE_LIMIT_EXCEEDED,
                    "source exceeds configured limit", Optional.empty(), Optional.empty());
        }
        if (existing != null && existing.text().equals(text)) {
            return new UpdateReceipt(id, Optional.of(existing.revision()),
                    Optional.of(existing.revision()), previousRevision, previousRevision, false,
                    List.of());
        }
        SourceRevision next = nextSourceRevision(id, existing, stagedRevisions);
        SourceText updated = new SourceText(id, next, text);
        staged.put(id, updated);
        stagedRevisions.put(id, next);
        tombstones.remove(id);
        return new UpdateReceipt(id, Optional.ofNullable(previousSourceRevision), Optional.of(next),
                previousRevision, newRevision, true,
                List.of(existing == null ? InvalidationReason.SOURCE_ADDED
                        : InvalidationReason.SOURCE_CONTENT_CHANGED));
    }

    private void validateExpectation(SourceUpdate update, SourceText existing) {
        SourceExpectation expectation = update.expectation();
        if (expectation instanceof SourceExpectation.Absent) {
            if (existing != null) {
                throw conflict(UpdateRejectionCode.SOURCE_ALREADY_PRESENT,
                        "source already exists", Optional.empty(), Optional.of(existing.revision()));
            }
        } else if (expectation instanceof SourceExpectation.At at) {
            if (existing == null) {
                throw conflict(UpdateRejectionCode.SOURCE_MISSING, "source is missing",
                        Optional.of(at.revision()), Optional.empty());
            }
            if (!existing.revision().equals(at.revision())) {
                throw conflict(UpdateRejectionCode.VERSION_CONFLICT,
                        "source revision changed", Optional.of(at.revision()),
                        Optional.of(existing.revision()));
            }
        }
    }

    private String applyEdits(SourceText existing, List<TextEdit> edits) {
        if (existing == null) {
            throw conflict(UpdateRejectionCode.INVALID_EDIT,
                    "edits require an existing source", Optional.empty(), Optional.empty());
        }
        List<TextEdit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt((TextEdit edit) -> edit.range().startOffset()).reversed());
        int lastStart = Integer.MAX_VALUE;
        for (TextEdit edit : sorted) {
            if (!edit.range().source().equals(existing.id())
                    || !edit.range().revision().equals(existing.revision())
                    || edit.range().endOffset() > existing.length()) {
                throw conflict(UpdateRejectionCode.INVALID_EDIT,
                        "edit refers to another revision or range", Optional.empty(),
                        Optional.of(existing.revision()));
            }
            if (edit.range().startOffset() == lastStart && edit.range().isEmpty()) {
                throw conflict(UpdateRejectionCode.INVALID_EDIT,
                        "multiple insertions at the same offset", Optional.empty(),
                        Optional.of(existing.revision()));
            }
            if (edit.range().endOffset() > lastStart) {
                throw conflict(UpdateRejectionCode.INVALID_EDIT, "overlapping edits",
                        Optional.empty(), Optional.of(existing.revision()));
            }
            lastStart = edit.range().startOffset();
        }
        StringBuilder builder = new StringBuilder(existing.text());
        for (TextEdit edit : sorted) {
            builder.replace(edit.range().startOffset(), edit.range().endOffset(),
                    edit.replacement());
        }
        return builder.toString();
    }

    private SourceRevision nextSourceRevision(SourceId id, SourceText existing,
                                              Map<SourceId, SourceRevision> stagedRevisions) {
        SourceRevision previous = existing != null ? existing.revision()
                : tombstones.getOrDefault(id, SourceRevision.initial());
        long next = previous.value() + 1;
        if (next <= 0) {
            throw conflict(UpdateRejectionCode.REVISION_EXHAUSTED, "source revision exhausted",
                    Optional.of(previous), Optional.empty());
        }
        return new SourceRevision(next);
    }

    /**
     * Returns the {@code nextWorkspaceRevision} value.
     *
     * @return the value
     */
    private WorkspaceRevision nextWorkspaceRevision() {
        long next = revision.value() + 1;
        if (next <= 0) {
            throw conflict(UpdateRejectionCode.REVISION_EXHAUSTED, "workspace revision exhausted",
                    Optional.empty(), Optional.empty());
        }
        return new WorkspaceRevision(next);
    }

    private UpdateRejectedException conflict(UpdateRejectionCode code, String message,
                                              Optional<SourceRevision> expected,
                                              Optional<SourceRevision> observed) {
        return new UpdateRejectedException(code, message, expected, observed);
    }

    /**
     * Returns the {@code invalidateLastAnalysis} value.
     *
     * @return the value
     */
    private void invalidateLastAnalysis() {
        // Keep lastAnalysis for identity reuse; it is never published as current.
    }

    private String inputFingerprint(SourceInventory inventory, AnalysisRequest request,
                                    CompilationRequest compilationRequest) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(inventory.fingerprint().value()).append('|')
                .append(compilationRequest.options().fingerprint()).append('|');
        for (var root : request.roots()) {
            canonical.append(root).append(';');
        }
        byte[] digest = guru.interlis.source.Sha256.digest(
                canonical.toString().getBytes(StandardCharsets.UTF_8));
        return guru.interlis.source.Hex.encode(digest);
    }

    /**
     * Returns the {@code requireOpen} value.
     *
     * @return the value
     */
    private void requireOpen() {
        if (closed) {
            /**
             * Returns the {@code WorkspaceClosedException} value.
             *
             * @return the value
             */
            throw new WorkspaceClosedException();
        }
    }

    /**
     * Returns the {@code enter} value.
     *
     * @return the value
     */
    private void enter() {
        if (busy) {
            throw new IllegalStateException("workspace is not reentrant");
        }
        busy = true;
    }

    /**
     * Returns the {@code exit} value.
     *
     * @return the value
     */
    private void exit() {
        busy = false;
    }
}
