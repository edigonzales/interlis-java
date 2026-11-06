package guru.interlis.lsp;

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** File admission and request ordering on the existing analysis owner queue. */
final class LspInputReadiness {
    private static final int MAX_REQUESTS = 128;
    private static final int MAX_DOCUMENT_OPERATIONS = 256;

    private final LongSupplier documentGeneration;
    private final Consumer<Runnable> completeOnOwner;
    private final Consumer<String> operations;
    private final Map<String, Long> pendingFiles = new LinkedHashMap<>();
    private final Set<Request<?>> requests = new LinkedHashSet<>();
    private final ArrayDeque<DocumentOperation> deferred = new ArrayDeque<>();
    private FileFailure fileFailure;
    private OpenLookup open;
    private long nextOpen;
    private boolean draining;
    private boolean closed;

    private record FileFailure(long generation, Throwable cause) { }

    private record OpenLookup(long token, CompletableFuture<?> future) { }

    private record DocumentOperation(Runnable action, Request<?> request) { }

    LspInputReadiness(LongSupplier documentGeneration, Consumer<Runnable> completeOnOwner,
                      Consumer<String> operations) {
        this.documentGeneration = documentGeneration;
        this.completeOnOwner = completeOnOwner;
        this.operations = operations;
    }

    void beginExternal(String uri, long token) {
        if (closed) throw shutdownFailure();
        pendingFiles.put(uri, token);
    }

    boolean isCurrentExternal(String uri, long token) {
        return Objects.equals(pendingFiles.get(uri), token);
    }

    void completeExternal(String uri, long token, Throwable failure, Runnable reanalyze) {
        if (!pendingFiles.remove(uri, token)) return;
        settle(failure, reanalyze);
    }

    long beginOpen() {
        if (closed) throw shutdownFailure();
        if (open != null) throw new IllegalStateException("document identity is already being resolved");
        return nextOpen();
    }

    long retryOpen(long token) {
        if (!isCurrentOpen(token)) throw new IllegalStateException("document identity lookup changed");
        return nextOpen();
    }

    private long nextOpen() {
        open = new OpenLookup(++nextOpen, null);
        return open.token();
    }

    boolean isCurrentOpen(long token) {
        return open != null && open.token() == token;
    }

    void trackOpen(long token, CompletableFuture<?> future) {
        if (isCurrentOpen(token)) open = new OpenLookup(token, future);
        else future.cancel(false);
    }

    void completeOpen(long token, Throwable failure, Runnable reanalyze) {
        if (!isCurrentOpen(token)) return;
        open = null;
        try {
            settle(failure, reanalyze);
        } finally {
            drainDocumentOperations();
        }
    }

    boolean isReady() {
        return !closed && !hasPendingFiles() && currentFailure() == null;
    }

    void orderDocumentOperation(Runnable action) {
        order(new DocumentOperation(action, null));
    }

    <T> CompletableFuture<T> awaitReady(Supplier<CompletableFuture<T>> start) {
        if (closed) return CompletableFuture.failedFuture(shutdownFailure());
        requests.removeIf(CompletableFuture::isDone);
        if (requests.size() >= MAX_REQUESTS) {
            return CompletableFuture.failedFuture(LspFailures.error(ResponseErrorCode.RequestFailed, "language request queue is full"));
        }
        var request = new Request<>(start);
        requests.add(request);
        request.whenComplete((ignored, failure) -> completeOnOwner.accept(() -> {
            requests.remove(request);
            deferred.removeIf(operation -> operation.request() == request);
        }));
        try {
            order(new DocumentOperation(() -> awaitFiles(request), request));
        } catch (RuntimeException failure) {
            request.fail(failure);
        }
        return request;
    }

    void cancelPendingOnClose() {
        closed = true;
        var identity = open;
        open = null;
        pendingFiles.clear();
        fileFailure = null;
        deferred.clear();
        var cancelled = List.copyOf(requests);
        requests.clear();
        cancelled.forEach(Request::shutdown);
        if (identity != null && identity.future() != null) identity.future().cancel(false);
    }

    private void order(DocumentOperation operation) {
        if (closed) throw shutdownFailure();
        if (open == null && !draining && deferred.isEmpty()) {
            operation.action().run();
            return;
        }
        deferred.removeIf(queued -> queued.request() != null && queued.request().isDone());
        if (deferred.size() >= MAX_DOCUMENT_OPERATIONS) {
            throw LspFailures.error(ResponseErrorCode.RequestFailed, "document operation queue is full");
        }
        deferred.addLast(operation);
    }

    private void drainDocumentOperations() {
        if (draining) return;
        draining = true;
        try {
            while (!closed && open == null && !deferred.isEmpty()) {
                var operation = deferred.removeFirst();
                var request = operation.request();
                if (request != null && request.isDone()) continue;
                try {
                    operation.action().run();
                } catch (RuntimeException failure) {
                    if (request != null) request.fail(failure);
                    else if (failure instanceof ResponseErrorException expected) operations.accept(expected.getResponseError().getMessage());
                    else LspFailures.response(failure, operations);
                }
            }
        } finally {
            draining = false;
        }
    }

    private void awaitFiles(Request<?> request) {
        if (request.isDone()) return;
        request.generation = documentGeneration.getAsLong();
        if (hasPendingFiles()) request.waitingForFiles = true;
        else release(request);
    }

    private void settle(Throwable failure, Runnable reanalyze) {
        if (failure != null && currentFailure() == null) {
            fileFailure = new FileFailure(documentGeneration.getAsLong(), failure);
        }
        if (hasPendingFiles()) return;
        var waiting = requests.stream().filter(request -> request.waitingForFiles).toList();
        waiting.forEach(request -> request.waitingForFiles = false);
        try {
            reanalyze.run();
        } catch (RuntimeException problem) {
            if (currentFailure() == null) fileFailure = new FileFailure(documentGeneration.getAsLong(), problem);
        } finally {
            waiting.forEach(this::release);
        }
    }

    private boolean hasPendingFiles() {
        return open != null || !pendingFiles.isEmpty();
    }

    private Throwable currentFailure() {
        return fileFailure != null && fileFailure.generation() == documentGeneration.getAsLong()
                ? fileFailure.cause() : null;
    }

    private void release(Request<?> request) {
        if (request.isDone()) return;
        if (request.generation != documentGeneration.getAsLong()) {
            request.fail(LspFailures.error(ResponseErrorCode.ContentModified, "CONTENT_MODIFIED: workspace inputs changed"));
        } else if (currentFailure() != null) {
            request.fail(currentFailure());
        } else {
            request.start();
        }
    }

    private static ResponseErrorException shutdownFailure() {
        return LspFailures.error(ResponseErrorCode.RequestCancelled, "server is closing");
    }

    private final class Request<T> extends CompletableFuture<T> {
        private Supplier<CompletableFuture<T>> start;
        private long generation;
        private boolean waitingForFiles;
        // Only the owner assigns the stage. A client can cancel it from any thread.
        private volatile CompletableFuture<T> active;

        Request(Supplier<CompletableFuture<T>> start) {
            this.start = start;
        }

        void start() {
            var action = start;
            start = null;
            try {
                var stage = Objects.requireNonNull(action.get());
                active = stage;
                if (isDone()) stage.cancel(false);
                else stage.whenComplete((value, failure) -> {
                    if (failure == null) complete(value);
                    else fail(failure);
                });
            } catch (RuntimeException failure) {
                fail(failure);
            }
        }

        void fail(Throwable failure) {
            completeExceptionally(LspFailures.response(failure, operations));
        }

        void shutdown() {
            completeExceptionally(shutdownFailure());
            cancelActive(false);
        }

        @Override public boolean cancel(boolean interrupt) {
            boolean cancelled = super.cancel(interrupt);
            if (cancelled) cancelActive(interrupt);
            return cancelled;
        }

        private void cancelActive(boolean interrupt) {
            var stage = active;
            if (stage != null) stage.cancel(interrupt);
        }
    }
}
