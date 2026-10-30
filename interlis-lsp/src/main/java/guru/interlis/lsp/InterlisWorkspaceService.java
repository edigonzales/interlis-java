package guru.interlis.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.source.SourceId;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceCompileRequest;
import guru.interlis.workspace.WorkspaceState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Workspace adapter: commands, configuration refresh and watched-file reload. */
final class InterlisWorkspaceService implements WorkspaceService {
    static final String DIAGRAM_COMMAND = "interlis.diagram";
    static final String COMPILE_COMMAND = "interlis.compile";
    private final Workspace workspace;
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
    private Runnable requestGuard=() -> { };
    void requestGuard(Runnable guard) { requestGuard=Objects.requireNonNull(guard); }
    InterlisWorkspaceService(Workspace workspace, InterlisTextDocumentService documents) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.documents = Objects.requireNonNull(documents, "documents");
        documents.fileServices(this::reloadFile,(expected,token) -> requestIo(signal -> {
            files.verify(expected,combined(token,signal)); return null;
        }));
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

    private void reloadFile(String uri) {
        if(uri==null || !uri.startsWith("file:") || documents.isOpen(uri)) return;
        long request=nextFileRequest.incrementAndGet(); fileRequests.put(uri,request);
        documents.beginExternal(uri,request);
        requestIo(token -> {
            var loaded=files.read(uri,roots,token); token.throwIfCancellationRequested();
            documents.applyExternal(uri,request,loaded,() -> !token.isCancellationRequested() && fileRequests.getOrDefault(uri,0L)==request);
            return null;
        }).exceptionally(failure -> {
            try { documents.externalFailed(uri,request,failure); } catch(RuntimeException closed) { }
            documents.operation("External source refresh failed; "
                +(failure instanceof org.eclipse.lsp4j.jsonrpc.ResponseErrorException expected?expected.getResponseError().getMessage():"internal file error")); return null;
        });
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        if(!documents.notificationReady()) return;
        documents.configurationChanged(params==null?null:params.getSettings());
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        if(!documents.notificationReady()) return;
        for (var event : params.getChanges()) {
            if(documents.isOpen(event.getUri())) continue;
            if (event.getType() == FileChangeType.Deleted) {
                fileRequests.put(event.getUri(),nextFileRequest.incrementAndGet());
                documents.deleteExternal(event.getUri());
                continue;
            }
            reloadFile(event.getUri());
        }
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        try { requestGuard.run(); } catch(org.eclipse.lsp4j.jsonrpc.ResponseErrorException invalid) { return CompletableFuture.failedFuture(invalid); }
        if(params==null || params.getCommand()==null) return invalidCommand();
        if (COMPILE_COMMAND.equals(params.getCommand())) {
            if(params.getArguments()!=null && !params.getArguments().isEmpty()) return invalidCommand();
            return compileWorkspace();
        }
        if (!DIAGRAM_COMMAND.equals(params.getCommand())) {
            return CompletableFuture.failedFuture(
                    new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                            new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                                    org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.MethodNotFound,
                                    "unsupported command: " + params.getCommand(), null)));
        }
        List<Object> arguments = params.getArguments() == null ? List.of() : params.getArguments();
        if (arguments.size()!=1) {
            return CompletableFuture.failedFuture(
                    new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                            new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                                    org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                                    "interlis.diagram expects one object argument", null)));
        }
        var encoded=new Gson().toJsonTree(arguments.getFirst());
        if(!encoded.isJsonObject()) return invalidCommand();
        JsonObject argument = encoded.getAsJsonObject();
        if(!java.util.Set.of("uri","snapshotId","symbolIds").containsAll(argument.keySet())) return invalidCommand();
        for(String field:List.of("uri","snapshotId")) if(argument.has(field)
                && (!argument.get(field).isJsonPrimitive() || !argument.getAsJsonPrimitive(field).isString())) return invalidCommand();
        if(argument.has("symbolIds")) {
            if(!argument.get("symbolIds").isJsonArray()) return invalidCommand();
            for(var id:argument.getAsJsonArray("symbolIds")) if(!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) return invalidCommand();
        }
        String uri = argument.has("uri") && !argument.get("uri").isJsonNull()
                ? argument.get("uri").getAsString() : null;
        if (uri == null || uri.isBlank()) {
            return CompletableFuture.failedFuture(
                    new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                            new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                                    org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                                    "interlis.diagram uri is required", null)));
        }
        String snapshotId = argument.has("snapshotId") && !argument.get("snapshotId").isJsonNull()
                ? argument.get("snapshotId").getAsString() : null;
        List<String> symbolIds = new ArrayList<>();
        if (argument.has("symbolIds") && argument.get("symbolIds").isJsonArray()) {
            for (var id : argument.getAsJsonArray("symbolIds")) {
                symbolIds.add(id.getAsString());
            }
        }
        return LspFutures.map(documents.diagram(new DiagramParams(uri,snapshotId,symbolIds)),value -> (Object)value,documents::operation);
    }

    private static CompletableFuture<Object> invalidCommand() {
        return CompletableFuture.failedFuture(LspFailures.error(org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,"invalid INTERLIS command arguments"));
    }

    private CompletableFuture<Object> compileWorkspace() {
        return LspFutures.map(documents.compileWorkspace(),InterlisWorkspaceService::summary,documents::operation);
    }

    private static Map<String, Object> summary(CompilationOutcome outcome) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (outcome instanceof CompilationOutcome.Succeeded succeeded) {
            summary.put("outcome", "succeeded");
            summary.put("snapshotId", succeeded.schema().snapshotId().value());
            summary.put("modelCount", succeeded.schema().models().size());
        } else if (outcome instanceof CompilationOutcome.Rejected rejected) {
            summary.put("outcome", "rejected");
            summary.put("reasons", rejected.rejection().reasons().stream()
                    .map(Enum::name).toList());
            summary.put("diagnostics", rejected.rejection().diagnosticIds().stream()
                    .map(id -> id.value()).toList());
        } else if (outcome instanceof CompilationOutcome.Cancelled cancelled) {
            summary.put("outcome", "cancelled");
            summary.put("reason", cancelled.reason().name());
        } else if (outcome instanceof CompilationOutcome.Failed failed) {
            summary.put("outcome", "failed");
            summary.put("failureId", failed.failureId().value());
            summary.put("code", failed.error().code().value());
        }
        return Map.copyOf(summary);
    }
}
