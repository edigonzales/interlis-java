package guru.interlis.lsp;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.repository.RepositoryResolutionOutcome;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/**
 * One host owner queue and two bounded analysis slots. At most one superseding
 * capture waits; cancelled or stale results never enter workspace reuse data.
 * Pure compiler work never executes on the owner queue or under a host lock.
 */
final class AnalysisScheduler implements AutoCloseable {
    private final Workspace workspace;
    private final RepositoryHostCoordinator repositories;
    private Consumer<RepositoryResolutionOutcome.Prepared> repositoryPrepared = ignored -> { };
    private Predicate<guru.interlis.source.SourceId> localSource=ignored -> true;
    private final CompilerEngine engine;
    private final CompilerServices services;
    private final BiConsumer<AnalysisSnapshot, Optional<RepositoryResolutionOutcome.Rejected>> accepted;
    private final Consumer<String> operations;
    private final ThreadPoolExecutor owner = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256),r -> daemon(r,"interlis-lsp-owner"));
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> daemon(r, "interlis-lsp-analysis"));
    private final ExecutorService compiler = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16),r -> daemon(r,"interlis-lsp-compile"));
    private final ExecutorService language = new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128),r -> daemon(r,"interlis-lsp-query"));
    private volatile Thread ownerThread;
    private volatile boolean closed;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    // The following state belongs exclusively to owner.
    private final Set<Job> running = new LinkedHashSet<>();
    private final List<PendingQuery<?>> queries = new ArrayList<>();
    private volatile long generation;
    private final Set<CompletableFuture<?>> activeQueries=new LinkedHashSet<>();
    private Job pending;
    private boolean current;
    private WorkspaceRevision acceptedRevision;
    private ResponseErrorException captureFailure;
    private final Set<CompletableFuture<CompilationOutcome>> compiles = new LinkedHashSet<>();

    AnalysisScheduler(Workspace workspace, CompilerEngine engine, CompilerServices services,
                      BiConsumer<AnalysisSnapshot, Optional<RepositoryResolutionOutcome.Rejected>> accepted,
                      Consumer<String> operations) {
        this.workspace = Objects.requireNonNull(workspace);
        this.engine = Objects.requireNonNull(engine);
        this.services = Objects.requireNonNull(services);
        this.accepted = Objects.requireNonNull(accepted);
        this.operations = Objects.requireNonNull(operations);
        this.repositories=new RepositoryHostCoordinator(operations);
    }

    void repositorySettings(RepositorySettings settings) { requireOwner(); repositories.reconfigure(settings); }
    void repositorySources(Consumer<RepositoryResolutionOutcome.Prepared> listener,
                           Predicate<guru.interlis.source.SourceId> local) { repositoryPrepared=listener;localSource=local; }

    /** Short ordered host action; never waits for a compiler worker. */
    <T> T onOwner(Supplier<T> action) {
        if (Thread.currentThread() == ownerThread) {
            if (closing.get()) throw stopped();
            return action.get();
        }
        var result = new CompletableFuture<T>();
        enqueue(() -> {
            try { result.complete(action.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        }, result);
        try { return result.join(); }
        catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw failure;
        }
    }

    /** Delivers completion of admitted work without competing for a new request slot. */
    void completeOnOwner(Runnable action) {
        if (Thread.currentThread() == ownerThread) {
            if (!closing.get()) action.run();
        } else enqueue(action, null);
    }

    /** Invalidates even when only client generation/version changed, without a core text change. */
    void invalidate() {
        requireOwner();
        generation++;
        current = false;
        captureFailure=null;
        running.forEach(job -> job.cancelled.set(true));
        pending = null;
        for (var result : List.copyOf(activeQueries)) result.completeExceptionally(changed());
        queries.clear();
        for (var result : List.copyOf(compiles)) result.completeExceptionally(changed());
    }

    /** Called on owner after the complete source/configuration update has been accepted. */
    void schedule() {
        requireOwner();
        invalidate();
        var state = workspace.state();
        var roots = localRoots(state);
        if (roots.isEmpty()) return;
        var job = new Job(generation, new AnalysisRequest(state.revision(), roots), workspace.configuration());
        if (running.size() < 2) launch(job);
        else pending = job;
    }

    /** Captures the query generation, promotes current work and propagates request cancellation. */
    <T> CompletableFuture<T> requestNow(Function<CancellationToken, CompletableFuture<T>> action) {
        var result = new CompletableFuture<T>();
        enqueue(() -> {
            if(result.isDone()) return;
            if(current && !workspace.currentRevision().equals(acceptedRevision)) schedule();
            if(captureFailure!=null) { result.completeExceptionally(captureFailure); return; }
            if (current || workspace.state().sourceRevisions().isEmpty()) {
                trackQuery(result);
                invoke(new PendingQuery<>(generation, action, result));
            } else {
                if (!analysisScheduled()) {
                    schedule();
                    if (!analysisScheduled()) {
                        result.completeExceptionally(changed());
                        return;
                    }
                }
                if (queries.size() >= 128) {
                    result.completeExceptionally(error(ResponseErrorCode.RequestFailed, "language request queue is full"));
                    return;
                }
                queries.add(new PendingQuery<>(generation, action, result));
                trackQuery(result);
            }
        }, result);
        return result;
    }

    private void trackQuery(CompletableFuture<?> result) {
        activeQueries.add(result);
        result.whenComplete((ignored, failure) -> completeOnOwner(() -> {
            queries.removeIf(query -> query.result == result);
            activeQueries.remove(result);
        }));
    }

    private boolean analysisScheduled() {
        return pending != null || running.stream().anyMatch(job -> job.generation == generation);
    }

    /** Runs a pure operation over a host snapshot, outside owner and workspace mutation. */
    <T> CompletableFuture<T> detached(CancellationToken token,Supplier<CompletableFuture<T>> operation) {
        requireOwner();
        var result=new CompletableFuture<T>();
        try { language.execute(() -> {
            try {
                if (result.isDone()) return;
                token.throwIfCancellationRequested();
                var active = operation.get();
                result.whenComplete((ignored, failure) -> {
                    if (result.isCancelled()) active.cancel(false);
                });
                active.whenComplete((value,failure) -> {
                    if(failure!=null) result.completeExceptionally(LspFailures.response(failure,operations));
                    else result.complete(value);
                });
            } catch(OperationCancelledException cancelled) { result.completeExceptionally(error(ResponseErrorCode.RequestCancelled,"language request cancelled: "+cancelled.reason())); }
            catch(ResponseErrorException expected) { result.completeExceptionally(expected); }
            catch(RuntimeException failure) { result.completeExceptionally(internal(failure)); }
        }); } catch(RejectedExecutionException full) { result.completeExceptionally(error(ResponseErrorCode.RequestFailed,"language worker queue is full or closed")); }
        return result;
    }

    /** Independent compile request with a pinned revision and its own cancellation context. */
    CompletableFuture<CompilationOutcome> compile() { return compileRoots(null); }

    CompletableFuture<CompilationOutcome> compileRoots(List<ModelSelector> selection) { return compileRoots(selection, () -> { }); }

    CompletableFuture<CompilationOutcome> compileRoots(List<ModelSelector> selection, Runnable validate) {
        var result = new CompletableFuture<CompilationOutcome>();
        enqueue(() -> {
            if (result.isDone()) return;
            validate.run();
            var state = workspace.state();
            var roots = selection == null ? localRoots(state) : List.copyOf(selection);
            if (roots.isEmpty()) {
                result.completeExceptionally(error(ResponseErrorCode.InvalidParams, "workspace has no local roots to compile"));
                return;
            }
            var request = new AnalysisRequest(state.revision(), roots);
            var configuration = workspace.configuration();
            boolean verifiesLoadedSources = repositories.verifiesLoadedSources();
            long initialGeneration = generation;
            compiles.add(result);
            result.whenComplete((value, failure) -> completeOnOwner(() -> compiles.remove(result)));
            CancellationToken token = new CancellationToken() {
                public boolean isCancellationRequested() {
                    return closing.get() || result.isCancelled() || generation != initialGeneration;
                }
                public CancellationReason reason() {
                    return closing.get() ? CancellationReason.HOST_SHUTDOWN
                            : generation != initialGeneration ? CancellationReason.SUPERSEDED : CancellationReason.USER_REQUESTED;
                }
            };
            try {
                compiler.execute(() -> {
                    if (result.isDone()) return;
                    final WorkspaceCapture initial;
                    try {
                        initial = capture(request, token);
                    } catch (RuntimeException failure) {
                        result.completeExceptionally(LspFailures.response(failure, operations));
                        return;
                    }
                    if (initial.resolution().complete() && !verifiesLoadedSources) {
                        compileCaptured(result, initialGeneration, initial, initial.workspaceRevision(), token);
                        return;
                    }
                    enqueue(() -> {
                        if (result.isDone()) return;
                        requireCurrent(initialGeneration, initial.workspaceRevision());
                        repositories.resolve(workspace, initial, configuration, token)
                                .whenComplete((prepared, problem) -> enqueue(() -> completeCompilePreparation(
                                        result, initialGeneration, initial, roots, token, prepared, problem), result));
                    }, result);
                });
            } catch (RejectedExecutionException full) {
                result.completeExceptionally(compileQueueFull());
            }
        }, result);
        return result;
    }

    private void completeCompilePreparation(CompletableFuture<CompilationOutcome> result, long initialGeneration,
            WorkspaceCapture initial, List<ModelSelector> roots, CancellationToken token,
            RepositoryResolutionOutcome prepared, Throwable failure) {
        if (result.isDone()) return;
        if (failure != null) {
            result.completeExceptionally(LspFailures.response(failure, operations));
            return;
        }
        requireCurrent(initialGeneration, initial.workspaceRevision());
        switch (prepared) {
            case RepositoryResolutionOutcome.Cancelled cancelled -> {
                result.complete(new CompilationOutcome.Cancelled(Optional.empty(), cancelled.reason()));
                return;
            }
            case RepositoryResolutionOutcome.Stale stale -> throw changed();
            case RepositoryResolutionOutcome.Rejected rejected -> {
                // Keep the compile gate's AMBIGUOUS_INPUT outcome for purely local ambiguities.
                boolean localAmbiguity = rejected.problems().stream().allMatch(
                        problem -> problem.reason() == guru.interlis.repository.RepositoryProblem.Reason.AMBIGUOUS)
                        && rejected.sources().keySet().stream().allMatch(initial.request().sources().sources()::containsKey);
                if (!localAmbiguity) {
                    throw error(ResponseErrorCode.RequestFailed, "repository closure rejected: "
                            + rejected.problems().stream().map(problem -> problem.loadReason().map(Enum::name)
                                    .orElse(problem.reason().name())).distinct().toList());
                }
                runCompile(result, initialGeneration, initial, null, token);
            }
            case RepositoryResolutionOutcome.Prepared success -> {
                success.update().apply(workspace);
                repositoryPrepared.accept(success);
                var request = new AnalysisRequest(workspace.currentRevision(), roots);
                runCompile(result, initialGeneration, initial, request, token);
            }
        }
    }

    private void runCompile(CompletableFuture<CompilationOutcome> result, long initialGeneration,
            WorkspaceCapture initial, AnalysisRequest updatedRequest, CancellationToken token) {
        try {
            compiler.execute(() -> {
                if (result.isDone()) return;
                final WorkspaceCapture prepared;
                try {
                    prepared = updatedRequest == null ? initial : capture(updatedRequest, token);
                } catch (RuntimeException failure) {
                    result.completeExceptionally(LspFailures.response(failure, operations));
                    return;
                }
                compileCaptured(result, initialGeneration, prepared, initial.workspaceRevision(), token);
            });
        } catch (RejectedExecutionException full) {
            result.completeExceptionally(compileQueueFull());
        }
    }

    private void compileCaptured(CompletableFuture<CompilationOutcome> result, long initialGeneration,
            WorkspaceCapture capture, WorkspaceRevision initialRevision, CancellationToken token) {
        if (result.isDone()) return;
        CompilationOutcome value;
        try {
            token.throwIfCancellationRequested();
            value = Objects.requireNonNull(engine.compile(capture.request(), capture.reuse(),
                    services.runContexts().createRoot(), token));
        } catch (OperationCancelledException cancelled) {
            value = new CompilationOutcome.Cancelled(Optional.empty(), cancelled.reason());
        } catch (RuntimeException failure) {
            result.completeExceptionally(LspFailures.response(failure, operations));
            return;
        }
        var outcome = value;
        enqueue(() -> {
            if (result.isDone()) return;
            requireCurrent(initialGeneration, capture.workspaceRevision());
            if (outcome instanceof CompilationOutcome.Succeeded success) {
                workspace.acceptAnalysis(capture, success.analysis());
            } else if (outcome instanceof CompilationOutcome.Rejected rejected) {
                workspace.acceptAnalysis(capture, rejected.analysis());
            }
            result.complete(outcome);
            if (!capture.workspaceRevision().equals(initialRevision)) schedule();
        }, result);
    }

    private List<ModelSelector> localRoots(WorkspaceState state) {
        return state.sourceRevisions().keySet().stream()
                .filter(localSource).<ModelSelector>map(ModelSelector.BySource::new).toList();
    }

    private void requireCurrent(long expectedGeneration, WorkspaceRevision revision) {
        if (generation != expectedGeneration || !workspace.currentRevision().equals(revision)) throw changed();
    }

    private WorkspaceCapture capture(AnalysisRequest request, CancellationToken token) {
        token.throwIfCancellationRequested();
        return workspace.capture(request, token);
    }

    private void launch(Job job) {
        running.add(job);
        CompletableFuture.supplyAsync(() -> capture(job.request, job.token), workers)
                .whenComplete((capture, failure) -> enqueue(() -> {
                    if (failure != null) {
                        finish(job, null, null, failure);
                        return;
                    }
                    try {
                        requireCurrent(job.generation, capture.workspaceRevision());
                        if (!repositories.needsResolution(capture)) {
                            analyze(job, capture, null);
                            return;
                        }
                        repositories.resolve(workspace, capture, job.configuration, job.token)
                                .whenComplete((outcome, problem) -> enqueue(
                                        () -> completeAnalysisPreparation(job, capture, outcome, problem), null));
                    } catch (RuntimeException problem) {
                        finish(job, null, null, problem);
                    }
                }, null));
    }

    private void completeAnalysisPreparation(Job job, WorkspaceCapture capture,
            RepositoryResolutionOutcome outcome, Throwable failure) {
        if (failure != null) {
            finish(job, null, null, failure);
            return;
        }
        try {
            requireCurrent(job.generation, capture.workspaceRevision());
            switch (outcome) {
                case RepositoryResolutionOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
                case RepositoryResolutionOutcome.Stale stale -> throw changed();
                case RepositoryResolutionOutcome.Rejected rejected -> {
                    job.repositoryFailure = Optional.of(rejected);
                    analyze(job, capture, null);
                }
                case RepositoryResolutionOutcome.Prepared prepared -> {
                    prepared.update().apply(workspace);
                    repositoryPrepared.accept(prepared);
                    analyze(job, capture, new AnalysisRequest(workspace.currentRevision(), job.request.roots()));
                }
            }
        } catch (RuntimeException problem) {
            finish(job, null, null, problem);
        }
    }

    private void analyze(Job job, WorkspaceCapture initial, AnalysisRequest updatedRequest) {
        workers.execute(() -> {
            WorkspaceCapture prepared;
            AnalysisSnapshot analysis;
            try {
                prepared = updatedRequest == null ? initial : capture(updatedRequest, job.token);
                analysis = engine.analyze(prepared.request(), prepared.reuse(),
                        services.runContexts().createRoot(), job.token);
            } catch (RuntimeException failure) {
                enqueue(() -> finish(job, null, null, failure), null);
                return;
            }
            enqueue(() -> finish(job, prepared, analysis, null), null);
        });
    }

    private void finish(Job job, WorkspaceCapture capture, AnalysisSnapshot analysis, Throwable failure) {
        requireOwner();
        running.remove(job);
        try {
            if (job.generation != generation || job.cancelled.get()) return;
            if (failure == null && workspace.acceptAnalysis(capture, analysis) == AnalysisAcceptance.ACCEPTED) {
                current = true;
                acceptedRevision = capture.workspaceRevision();
                accepted.accept(analysis, job.repositoryFailure);
                for (var query : List.copyOf(queries)) invoke(query);
                queries.clear();
            } else {
                var cause = failure instanceof CompletionException completed ? completed.getCause() : failure;
                var response = cause == null ? changed() : LspFailures.response(cause, operations);
                if (cause instanceof OperationCancelledException cancelled) {
                    captureFailure = (ResponseErrorException) response;
                    try { operations.accept("Analysis unavailable: " + cancelled.reason()); } catch (RuntimeException unavailable) { }
                }
                for (var query : List.copyOf(queries)) query.result.completeExceptionally(response);
                queries.clear();
            }
        } finally {
            if (pending != null && running.size() < 2) {
                Job next = pending;
                pending = null;
                launch(next);
            }
        }
    }

    private static ResponseErrorException compileQueueFull() {
        return error(ResponseErrorCode.RequestFailed, "compiler queue is full or closed");
    }

    private <T> void invoke(PendingQuery<T> query) {
        if (query.result.isDone()) return;
        if (query.generation != generation) { query.result.completeExceptionally(changed()); return; }
        var revision=workspace.currentRevision();
        String configuration=workspace.configuration().fullFingerprint();
        CancellationToken token = new CancellationToken() {
            public boolean isCancellationRequested() { return query.result.isCancelled() || closing.get() || query.generation!=generation; }
            public CancellationReason reason() { return closing.get()?CancellationReason.HOST_SHUTDOWN:
                    query.generation!=generation?CancellationReason.SUPERSEDED:CancellationReason.USER_REQUESTED; }
        };
        try {
            token.throwIfCancellationRequested();
            var active = query.action.apply(token);
            query.result.whenComplete((ignored, failure) -> {
                if (failure != null) active.cancel(false);
            });
            active.whenComplete((value, failure) -> {
                if (query.result.isDone()) return;
                enqueue(() -> {
                    if(query.generation!=generation || !workspace.currentRevision().equals(revision)
                            || !workspace.configuration().fullFingerprint().equals(configuration)) query.result.completeExceptionally(changed());
                    else if(failure!=null) query.result.completeExceptionally(LspFailures.response(failure,operations));
                    else query.result.complete(value);
                },query.result);
            });
        } catch (ResponseErrorException expected) { query.result.completeExceptionally(expected); }
        catch (OperationCancelledException cancelled) { query.result.cancel(false); }
        catch (RuntimeException failure) { query.result.completeExceptionally(internal(failure)); }
    }

    private ResponseErrorException internal(Throwable failure) {
        return LspFailures.internal(failure,operations);
    }

    private void enqueue(Runnable action, CompletableFuture<?> result) {
        Runnable task=() -> {
            ownerThread = Thread.currentThread();
            if (closing.get()) {
                if (result != null) result.completeExceptionally(stopped());
                return;
            }
            try { action.run(); }
            catch(RuntimeException failure) {
                var response=LspFailures.response(failure,operations);
                if(result!=null) result.completeExceptionally(response);
                else {
                    var waiting=List.copyOf(queries);
                    queries.clear();
                    for(var query:waiting) query.result.completeExceptionally(response);
                    invalidate();
                }
            }
        };
        try {
            if (closed || closing.get()) throw new RejectedExecutionException();
            owner.execute(task);
        } catch (RejectedExecutionException unavailable) {
            if (result != null) result.completeExceptionally(closing.get() ? stopped()
                    : error(ResponseErrorCode.RequestFailed, "owner queue is full"));
            else if(!closed && !closing.get()) {
                if (Thread.currentThread() == ownerThread) task.run();
                else {
                    try { owner.getQueue().put(task); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("language state requires owner queue");
    }

    @Override public void close() {
        close(() -> { });
    }

    void close(Runnable hostCleanup) {
        if(closing.compareAndSet(false,true)) {
            Runnable shutdown=() -> {
                ownerThread=Thread.currentThread();
                try {
                    hostCleanup.run();
                    List.copyOf(activeQueries).forEach(result -> result.cancel(false));
                    List.copyOf(compiles).forEach(result -> result.cancel(false));
                    invalidate();
                    activeQueries.clear(); running.clear(); compiles.clear();
                }
                finally {
                    closed=true; repositories.close(); compiler.shutdownNow(); workers.shutdownNow(); language.shutdownNow(); owner.shutdown(); terminated.complete(null);
                }
            };
            if(Thread.currentThread()==ownerThread) shutdown.run();
            else {
                try { owner.execute(shutdown); }
                catch(RejectedExecutionException full) {
                    try { owner.getQueue().put(shutdown); }
                    catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("shutdown interrupted",interrupted); }
                }
            }
        }
        if (Thread.currentThread() != ownerThread) terminated.join();
    }

    private static Thread daemon(Runnable runnable, String name) {
        var thread = new Thread(runnable, name); thread.setDaemon(true); return thread;
    }
    private static ResponseErrorException changed() { return error(ResponseErrorCode.ContentModified, "CONTENT_MODIFIED: document or configuration changed"); }
    private static ResponseErrorException stopped() { return error(ResponseErrorCode.RequestCancelled, "server is closing"); }
    private static ResponseErrorException error(ResponseErrorCode code, String message) {
        return new ResponseErrorException(new ResponseError(code, message, null));
    }
    private record PendingQuery<T>(long generation, Function<CancellationToken, CompletableFuture<T>> action,
                                   CompletableFuture<T> result) { }
    private final class Job {
        final long generation;
        final AnalysisRequest request;
        final WorkspaceConfiguration configuration;
        final AtomicBoolean cancelled = new AtomicBoolean();
        Optional<RepositoryResolutionOutcome.Rejected> repositoryFailure = Optional.empty();
        final CancellationToken token = new CancellationToken() {
            public boolean isCancellationRequested() { return cancelled.get() || closing.get(); }
            public CancellationReason reason() {
                return closing.get() ? CancellationReason.HOST_SHUTDOWN : CancellationReason.SUPERSEDED;
            }
        };

        Job(long generation, AnalysisRequest request, WorkspaceConfiguration configuration) {
            this.generation = generation;
            this.request = request;
            this.configuration = configuration;
        }
    }
}
