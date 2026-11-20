package guru.interlis.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.WorkspaceService;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.source.SourceId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Workspace adapter for configuration refresh and watched-file reload. */
final class InterlisWorkspaceService implements WorkspaceService {
    private final InterlisTextDocumentService documents;
    private final ProjectSourceLoader files=new ProjectSourceLoader();
    private final java.util.concurrent.ThreadPoolExecutor io=new java.util.concurrent.ThreadPoolExecutor(2,2,0,
            java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(32),runnable -> {
        var thread=new Thread(runnable,"interlis-lsp-files"); thread.setDaemon(true); return thread;
    });
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong nextFileRequest=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.ConcurrentMap<String,Long> fileRequests=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<CompletableFuture<?>> fileOperations=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile List<java.nio.file.Path> roots=List.of();
    InterlisWorkspaceService(InterlisTextDocumentService documents) {
        this.documents = Objects.requireNonNull(documents, "documents");
        documents.fileServices(this::reloadFile,(expected,token) -> requestIo(signal -> {
            files.verify(expected,combined(token,signal)); return null;
        }), (uri,known) -> requestIo(token -> files.identify(uri,known,token)));
    }

    CompletableFuture<Void> initializeProject(List<String> folders) {
        long request=nextFileRequest.incrementAndGet();
        return requestIo(token -> {
            var project=files.scan(List.copyOf(folders),token); token.throwIfCancellationRequested();
            roots=project.roots();
            documents.installProject(project,uri -> !token.isCancellationRequested() && fileRequests.getOrDefault(uri,request)<=request);
            return null;
        });
    }

    void close() {
        if(closed.compareAndSet(false,true)) {
            fileOperations.forEach(future -> future.cancel(false)); fileOperations.clear(); io.shutdownNow();
        }
    }

    private <T> CompletableFuture<T> requestIo(java.util.function.Function<CancellationToken,T> operation) {
        var result=new CompletableFuture<T>(); fileOperations.add(result);
        result.whenComplete((ignored,failure) -> fileOperations.remove(result));
        CancellationToken token=new CancellationToken() {
            public boolean isCancellationRequested() { return result.isCancelled() || closed.get(); }
            public CancellationReason reason() { return closed.get() ? CancellationReason.HOST_SHUTDOWN : CancellationReason.USER_REQUESTED; }
        };
        try {
            if(closed.get()) throw new java.util.concurrent.RejectedExecutionException();
            io.execute(() -> {
                try { token.throwIfCancellationRequested(); T value=operation.apply(token); token.throwIfCancellationRequested(); result.complete(value); }
                catch(OperationCancelledException cancelled) { result.completeExceptionally(LspFailures.error(
                        org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.RequestCancelled,"file operation cancelled: "+cancelled.reason())); }
                catch(org.eclipse.lsp4j.jsonrpc.ResponseErrorException expected) { result.completeExceptionally(expected); }
                catch(guru.interlis.workspace.UpdateRejectedException rejected) { result.completeExceptionally(LspFailures.error(
                        org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.RequestFailed,"workspace update rejected: "+rejected.code())); }
                catch(RuntimeException failure) { result.completeExceptionally(LspFailures.internal(failure,documents::operation)); }
            });
        } catch(java.util.concurrent.RejectedExecutionException full) {
            result.completeExceptionally(LspFailures.error(org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.RequestFailed,"file queue is full or closed"));
        }
        return result;
    }

    private static CancellationToken combined(CancellationToken request,CancellationToken host) {
        return new CancellationToken() {
            public boolean isCancellationRequested() { return request.isCancellationRequested() || host.isCancellationRequested(); }
            public CancellationReason reason() { return host.isCancellationRequested() ? host.reason() : request.reason(); }
        };
    }

    InterlisTextDocumentService.FileReload beginFileReload(String requestedUri, long request) {
        return documents.beginFileReload(requestedUri, request, canonical -> {
            String address = LspFileIdentityRegistry.path(requestedUri).orElseThrow().toUri().toString();
            if (fileRequests.getOrDefault(address, 0L) > request) return false;
            if (fileRequests.merge(canonical, request, Math::max) != request) return false;
            fileRequests.put(address, request);
            return true;
        });
    }

    /** The result may identify an alias of a different dispatch key. Claim its actual source too. */
    boolean claimFileRequest(String dispatchedUri, String resolvedUri, long request) {
        return fileRequests.getOrDefault(dispatchedUri, 0L) == request
                && fileRequests.merge(resolvedUri, request, Math::max) == request;
    }

    private void reloadFile(String requestedUri) {
        long request = nextFileRequest.incrementAndGet();
        var capture = beginFileReload(requestedUri, request);
        if (capture == null) return;
        String uri = capture.canonicalUri();
        String address = LspFileIdentityRegistry.path(requestedUri).orElseThrow().toUri().toString();
        var capturedRoots = roots;
        requestIo(token -> {
            var loaded = files.read(requestedUri, capturedRoots, token);
            boolean requestedMissing = loaded.isEmpty();
            var previous = capture.closedFiles().get(uri);
            if (requestedMissing) {
                for (String alias : capture.aliases()) {
                    var candidate = files.read(alias, capturedRoots, token);
                    if (candidate.isPresent() && survivingAddress(previous, candidate.orElseThrow())) {
                        loaded = candidate;
                        break;
                    }
                }
            }
            var known = capture.knownSources();
            var verified = loaded.flatMap(file -> known.entrySet().stream()
                    .filter(entry -> files.sameFile(file, entry.getValue(), token)).map(Map.Entry::getKey).findFirst());
            if (verified.isEmpty() && loaded.isPresent()) {
                var file = loaded.orElseThrow();
                verified = file.fileKey().flatMap(key -> capture.closedFiles().entrySet().stream()
                        .filter(entry -> entry.getValue().fileKey().map(key::equals).orElse(false))
                        .map(entry -> new SourceId(entry.getKey())).findFirst());
                // A provider without a key can retain a previously proved alias after its primary disappears.
                var source = new SourceId(uri);
                if (verified.isEmpty() && file.fileKey().isEmpty() && known.containsKey(source)
                        && capture.aliases().contains(file.path().toUri().toString())
                        && files.read(known.get(source), capturedRoots, token).isEmpty()) verified = java.util.Optional.of(source);
            }
            if (verified.isPresent() && loaded.isPresent()) {
                String primary = known.get(verified.orElseThrow());
                var observed = loaded.orElseThrow();
                if (primary != null && LspFileIdentityRegistry.path(primary).filter(observed.path()::equals).isEmpty()
                        && files.sameFile(observed, primary, token)) {
                    var preferred = files.read(primary, capturedRoots, token);
                    if (preferred.isPresent() && files.sameFile(observed, primary, token)) loaded = preferred;
                }
            }
            token.throwIfCancellationRequested();
            documents.applyExternal(uri, requestedUri, request, loaded, verified, requestedMissing,
                    resolved -> !token.isCancellationRequested() && fileRequests.getOrDefault(address, 0L) == request
                            && claimFileRequest(uri, resolved, request));
            return null;
        }).exceptionally(failure -> {
            try { documents.externalFailed(uri, request, failure); } catch(RuntimeException closed) { }
            documents.operation("External source refresh failed; "
                    +(failure instanceof org.eclipse.lsp4j.jsonrpc.ResponseErrorException expected
                    ?expected.getResponseError().getMessage():"internal file error"));
            return null;
        });
    }

    private static boolean survivingAddress(ProjectSourceLoader.FileContent previous, ProjectSourceLoader.FileContent candidate) {
        return previous == null || previous.path().equals(candidate.path())
                || previous.fileKey().isEmpty() || candidate.fileKey().isEmpty()
                || previous.fileKey().equals(candidate.fileKey());
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        if(!documents.notificationReady()) return;
        documents.configurationChanged(params==null?null:params.getSettings());
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        if(!documents.notificationReady()) return;
        for (var event : params.getChanges()) reloadFile(event.getUri());
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        return CompletableFuture.failedFuture(LspFailures.error(ResponseErrorCode.MethodNotFound, "unsupported command"));
    }
}
