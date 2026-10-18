package guru.interlis.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
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

/**
 * P1 workspace service. File watching remains a no-op adapter hook; the
 * read-only diagram command uses the same analysis snapshot as navigation.
 */
final class InterlisWorkspaceService implements WorkspaceService {
    static final String DIAGRAM_COMMAND = "interlis.diagram";
    static final String COMPILE_COMMAND = "interlis.compile";
    private final Workspace workspace;
    private final InterlisTextDocumentService documents;
    private final ExecutorService compileExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "interlis-lsp-compile");
        thread.setDaemon(true);
        return thread;
    });

    InterlisWorkspaceService(Workspace workspace, InterlisTextDocumentService documents) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.documents = Objects.requireNonNull(documents, "documents");
    }

    void close() {
        compileExecutor.shutdownNow();
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // Configuration changes are host policy; the P1 server uses defaults.
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // Repository-driven refresh is P2.
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if (COMPILE_COMMAND.equals(params.getCommand())) {
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
        if (arguments.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                            new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                                    org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                                    "interlis.diagram expects one object argument", null)));
        }
        JsonObject argument = new Gson().toJsonTree(arguments.getFirst()).getAsJsonObject();
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
        return documents.diagram(new DiagramParams(uri, snapshotId, symbolIds))
                .thenApply(value -> (Object) value);
    }

    private CompletableFuture<Object> compileWorkspace() {
        CompletableFuture<Object> result = new CompletableFuture<>();
        compileExecutor.execute(() -> {
            WorkspaceState state = workspace.state();
            List<ModelSelector> roots = state.sourceRevisions().keySet().stream()
                    .<ModelSelector>map(ModelSelector.BySource::new).toList();
            if (roots.isEmpty()) {
                result.completeExceptionally(new org.eclipse.lsp4j.jsonrpc.ResponseErrorException(
                        new org.eclipse.lsp4j.jsonrpc.messages.ResponseError(
                                org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,
                                "workspace has no sources to compile", null)));
                return;
            }
            CancellationToken token = new CancellationToken() {
                @Override
                public boolean isCancellationRequested() {
                    return result.isCancelled();
                }

                @Override
                public CancellationReason reason() {
                    return CancellationReason.USER_REQUESTED;
                }

                @Override
                public void throwIfCancellationRequested() {
                    if (isCancellationRequested()) {
                        throw new OperationCancelledException(CancellationReason.USER_REQUESTED);
                    }
                }
            };
            try {
                CompilationOutcome outcome = workspace.compile(
                        new WorkspaceCompileRequest(state.revision(), roots), token);
                result.complete(summary(outcome));
            } catch (OperationCancelledException cancelled) {
                result.cancel(true);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
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
