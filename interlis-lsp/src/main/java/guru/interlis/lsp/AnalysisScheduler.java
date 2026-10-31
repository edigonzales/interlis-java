package guru.interlis.lsp;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
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
    private final CompilerEngine engine;
    private final CompilerServices services;
    private final Consumer<AnalysisSnapshot> accepted;
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
                      Consumer<AnalysisSnapshot> accepted, Consumer<String> operations) {
        this.workspace = Objects.requireNonNull(workspace);
        this.engine = Objects.requireNonNull(engine);
        this.services = Objects.requireNonNull(services);
        this.accepted = Objects.requireNonNull(accepted);
        this.operations = Objects.requireNonNull(operations);
    }

    /** Short ordered host action; never waits for a compiler worker. */
    <T> T onOwner(Supplier<T> action) {
        if (Thread.currentThread() == ownerThread) return action.get();
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

    /** Invalidates even when only client generation/version changed, without a core text change. */
    void invalidate() {
        requireOwner();
        generation++;
        current = false;
        captureFailure=null;
        running.forEach(job -> job.cancelled.set(true));
        pending = null;
        for (var query : List.copyOf(queries)) query.result.completeExceptionally(changed());
        queries.clear();
    }

    /** Called on owner after the complete source/configuration update has been accepted. */
    void schedule() {
        requireOwner();
        invalidate();
        if (workspace.state().sourceRevisions().isEmpty()) return;
        var roots = workspace.state().sourceRevisions().keySet().stream()
                .<ModelSelector>map(ModelSelector.BySource::new).toList();
        WorkspaceCapture capture;
        try { capture=workspace.capture(new AnalysisRequest(workspace.currentRevision(), roots)); }
        catch(OperationCancelledException cancelled) {
            captureFailure=error(ResponseErrorCode.RequestCancelled,"analysis capture cancelled: "+cancelled.reason());
            try { operations.accept("Analysis unavailable: "+cancelled.reason()); } catch(RuntimeException unavailable) { }
            return;
        }
        var job = new Job(generation, capture);
        if (running.size() < 2) launch(job);
        else pending = job;
    }

    /** Captures the query generation, promotes current work and propagates request cancellation. */
    <T> CompletableFuture<T> requestNow(Function<CancellationToken, CompletableFuture<T>> action) {
        var result = new CompletableFuture<T>();
        result.whenComplete((ignored,failure) -> {
            Runnable cleanup=() -> { queries.removeIf(query -> query.result==result); activeQueries.remove(result); };
            if(Thread.currentThread()==ownerThread) cleanup.run(); else enqueue(cleanup,null);
        });
        enqueue(() -> {
            if(result.isDone()) return;
            activeQueries.add(result);
            if(current && !workspace.currentRevision().equals(acceptedRevision)) schedule();
            if(captureFailure!=null) { result.completeExceptionally(captureFailure); return; }
            if (current || workspace.state().sourceRevisions().isEmpty()) {
                invoke(new PendingQuery<>(generation, action, result));
            } else {
                if (running.stream().noneMatch(job -> job.generation == generation) && pending == null) schedule();
                if(captureFailure!=null) { result.completeExceptionally(captureFailure); return; }
                if (queries.size() >= 128) {
                    result.completeExceptionally(error(ResponseErrorCode.RequestFailed, "language request queue is full"));
                    return;
                }
                queries.add(new PendingQuery<>(generation, action, result));
            }
        }, result);
        return result;
    }

    /** Runs a pure operation over a host snapshot, outside owner and workspace mutation. */
    <T> CompletableFuture<T> detached(CancellationToken token,Supplier<CompletableFuture<T>> operation) {
        requireOwner();
        var result=new CompletableFuture<T>();
        try { language.execute(() -> {
            try {
                token.throwIfCancellationRequested();
                operation.get().whenComplete((value,failure) -> {
                    if(failure!=null) result.completeExceptionally(LspFailures.response(failure,operations));
                    else result.complete(value);
                });
            } catch(OperationCancelledException cancelled) { result.completeExceptionally(error(ResponseErrorCode.RequestCancelled,"language request cancelled: "+cancelled.reason())); }
            catch(ResponseErrorException expected) { result.completeExceptionally(expected); }
            catch(RuntimeException failure) { result.completeExceptionally(internal(failure)); }
        }); } catch(RejectedExecutionException full) { result.completeExceptionally(error(ResponseErrorCode.RequestFailed,"language worker queue is full or closed")); }
        return result;
    }

    /** Independent compile request, captured on owner and executed with its own cancellable context. */
    CompletableFuture<CompilationOutcome> compile() {
        var result = new CompletableFuture<CompilationOutcome>();
        enqueue(() -> {
            var state = workspace.state();
            if(state.sourceRevisions().isEmpty()) {
                result.completeExceptionally(error(ResponseErrorCode.InvalidParams,"workspace has no sources to compile")); return;
            }
            var roots=state.sourceRevisions().keySet().stream().<ModelSelector>map(ModelSelector.BySource::new).toList();
            var capture=workspace.capture(new AnalysisRequest(state.revision(),roots));
            long capturedGeneration=generation;
            compiles.add(result);
            CancellationToken token=new CancellationToken() {
                public boolean isCancellationRequested() { return closing.get() || result.isCancelled(); }
                public CancellationReason reason() { return closing.get() ? CancellationReason.HOST_SHUTDOWN : CancellationReason.USER_REQUESTED; }
            };
            try { compiler.execute(() -> {
                CompilationOutcome outcome=null; RuntimeException failure=null;
                try { outcome=Objects.requireNonNull(engine.compile(capture.request(),capture.reuse(),services.runContexts().createRoot(),token)); }
                catch(OperationCancelledException cancelled) { outcome=new CompilationOutcome.Cancelled(Optional.empty(),cancelled.reason()); }
                catch(RuntimeException failed) { failure=failed; }
                var value=outcome; var problem=failure;
                if(!closed) enqueue(() -> {
                    compiles.remove(result);
                    if(result.isDone()) return;
                    if(problem!=null) { result.completeExceptionally(internal(problem)); return; }
                    if(capturedGeneration!=generation || !workspace.currentRevision().equals(capture.workspaceRevision())) {
                        result.completeExceptionally(changed()); return;
                    }
                    if(value instanceof CompilationOutcome.Succeeded succeeded) workspace.acceptAnalysis(capture,succeeded.analysis());
                    else if(value instanceof CompilationOutcome.Rejected rejected) workspace.acceptAnalysis(capture,rejected.analysis());
                    result.complete(value);
                },result);
            }); } catch(RejectedExecutionException full) {
                compiles.remove(result);
                result.completeExceptionally(error(ResponseErrorCode.RequestFailed,"compiler queue is full or closed"));
            }
        },result);
        return result;
    }

    private void launch(Job job) {
        running.add(job);
        workers.execute(() -> {
            AnalysisSnapshot analysis = null;
            RuntimeException failure = null;
            try {
                analysis = engine.analyze(job.capture.request(), job.capture.reuse(),
                        services.runContexts().createRoot(), job.token());
            } catch (RuntimeException failed) { failure = failed; }
            var completed = analysis; var problem = failure;
            if (!closed) enqueue(() -> finish(job, completed, problem), null);
        });
    }

    private void finish(Job job, AnalysisSnapshot analysis, RuntimeException failure) {
        requireOwner();
        running.remove(job);
        if (job.generation == generation && !job.cancelled.get()) {
            if (failure == null && workspace.acceptAnalysis(job.capture, analysis) == AnalysisAcceptance.ACCEPTED) {
                current = true;
                acceptedRevision = job.capture.workspaceRevision();
                accepted.accept(analysis);
                for (var query : List.copyOf(queries)) invoke(query);
                queries.clear();
            } else {
                var error = failure instanceof OperationCancelledException cancelled
                        ? error(ResponseErrorCode.RequestCancelled, "analysis cancelled: "+cancelled.reason())
                        : failure == null ? changed() : internal(failure);
                if(failure instanceof OperationCancelledException cancelled) {
                    captureFailure=error;
                    try { operations.accept("Analysis unavailable: "+cancelled.reason()); } catch(RuntimeException unavailable) { }
                }
                for (var query : List.copyOf(queries)) query.result.completeExceptionally(error);
                queries.clear();
            }
        }
        if (pending != null && running.size() < 2) {
            Job next = pending; pending = null; launch(next);
        }
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
            query.action.apply(token).whenComplete((value, failure) -> {
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

    private ResponseErrorException internal(RuntimeException failure) {
        return LspFailures.internal(failure,operations);
    }

    private void enqueue(Runnable action, CompletableFuture<?> result) {
        Runnable task=() -> {
            ownerThread = Thread.currentThread();
            if(closed) { if(result!=null) result.completeExceptionally(error(ResponseErrorCode.RequestFailed,"server is closed")); return; }
            try { action.run(); }
            catch(RuntimeException failure) {
                var response=LspFailures.response(failure,operations);
                if(result!=null) result.completeExceptionally(response);
                else {
                    var waiting=List.copyOf(queries);
                    queries.clear();
                    invalidate();
                    for(var query:waiting) query.result.completeExceptionally(response);
                }
            }
        };
        try {
            if (closed || closing.get()) throw new RejectedExecutionException();
            owner.execute(task);
        } catch (RejectedExecutionException unavailable) {
            if(result!=null) result.completeExceptionally(error(ResponseErrorCode.RequestFailed,"owner queue is full or closed"));
            else if(!closed && !closing.get()) {
                try { owner.getQueue().put(task); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
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
                try { hostCleanup.run(); invalidate(); List.copyOf(activeQueries).forEach(result -> result.cancel(false)); activeQueries.clear();
                    running.clear(); compiles.forEach(result -> result.cancel(false)); compiles.clear(); }
                finally {
                    closed=true; compiler.shutdownNow(); workers.shutdownNow(); language.shutdownNow(); owner.shutdown(); terminated.complete(null);
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
        terminated.join();
    }

    private static Thread daemon(Runnable runnable, String name) {
        var thread = new Thread(runnable, name); thread.setDaemon(true); return thread;
    }
    private static ResponseErrorException changed() { return error(ResponseErrorCode.ContentModified, "CONTENT_MODIFIED: document or configuration changed"); }
    private static ResponseErrorException error(ResponseErrorCode code, String message) {
        return new ResponseErrorException(new ResponseError(code, message, null));
    }
    private record PendingQuery<T>(long generation, Function<CancellationToken, CompletableFuture<T>> action,
                                   CompletableFuture<T> result) { }
    private final class Job {
        final long generation; final WorkspaceCapture capture; final AtomicBoolean cancelled = new AtomicBoolean();
        Job(long generation, WorkspaceCapture capture) { this.generation = generation; this.capture = capture; }
        CancellationToken token() {
            return new CancellationToken() {
                public boolean isCancellationRequested() { return cancelled.get() || closing.get(); }
                public CancellationReason reason() { return closing.get() ? CancellationReason.HOST_SHUTDOWN : CancellationReason.SUPERSEDED; }
            };
        }
    }
}
