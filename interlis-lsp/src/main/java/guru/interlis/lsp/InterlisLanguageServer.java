package guru.interlis.lsp;

import guru.interlis.workspace.Workspace;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Minimal protocol-conformant LSP server at stdio; no second language logic. */
public final class InterlisLanguageServer implements LanguageServer, LanguageClientAware {
    private final Workspace workspace;
    private final InterlisTextDocumentService textDocuments;
    private final InterlisWorkspaceService workspaceService;
    private volatile boolean shutdown;
    private final java.util.concurrent.atomic.AtomicBoolean initializeStarted=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean ready;
    private java.util.function.IntConsumer processExit=ignored -> { };

    /**
     * Creates an instance.
     *
     * @param workspace the {@code workspace} argument
     */
    public InterlisLanguageServer(Workspace workspace) {
        this(workspace, guru.interlis.compiler.CompilerEngines.create(),
                guru.interlis.compiler.CompilerServices.defaults());
    }

    /** Uses an explicit reentrant engine and run services for detached background work. */
    public InterlisLanguageServer(Workspace workspace, guru.interlis.compiler.CompilerEngine engine,
                                 guru.interlis.compiler.CompilerServices services) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.textDocuments = new InterlisTextDocumentService(workspace, engine, services);
        this.workspaceService = new InterlisWorkspaceService(workspace, textDocuments);
        textDocuments.requestGuard(this::requireReady);
        workspaceService.requestGuard(this::requireReady);
    }

    private void requireReady() {
        if(shutdown) throw LspFailures.error(org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidRequest,"server has shut down");
        if(!ready) throw LspFailures.error(org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.ServerNotInitialized,"server is not initialized");
    }

    void processExit(java.util.function.IntConsumer handler) { processExit=Objects.requireNonNull(handler); }

    @Override
    public void connect(LanguageClient client) {
        Objects.requireNonNull(client, "client");
        textDocuments.sink(client::publishDiagnostics);
        textDocuments.operationSink(message -> client.logMessage(new org.eclipse.lsp4j.MessageParams(
                org.eclipse.lsp4j.MessageType.Error, message)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        if(params==null) return CompletableFuture.failedFuture(LspFailures.error(
                org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidParams,"initialize parameters are required"));
        if(shutdown || !initializeStarted.compareAndSet(false,true)) return CompletableFuture.failedFuture(LspFailures.error(
                org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.InvalidRequest,"initialize may run only once"));
        var client=params.getCapabilities();
        boolean versioned=client!=null && client.getWorkspace()!=null && client.getWorkspace().getWorkspaceEdit()!=null
                && Boolean.TRUE.equals(client.getWorkspace().getWorkspaceEdit().getDocumentChanges());
        textDocuments.versionedEdits(versioned);
        var folders=new java.util.ArrayList<String>();
        if(params.getWorkspaceFolders()!=null) for(var folder:params.getWorkspaceFolders()) folders.add(folder.getUri());
        if(folders.isEmpty() && params.getRootUri()!=null) folders.add(params.getRootUri());
        if(folders.isEmpty() && params.getRootPath()!=null) folders.add(java.nio.file.Path.of(params.getRootPath()).toAbsolutePath().toUri().toString());
        TextDocumentSyncOptions sync = new TextDocumentSyncOptions();
        sync.setOpenClose(true);
        sync.setChange(TextDocumentSyncKind.Incremental);
        CompletionOptions completion = new CompletionOptions();
        completion.setTriggerCharacters(List.of("."));
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setExecuteCommandProvider(new org.eclipse.lsp4j.ExecuteCommandOptions(
                List.of(InterlisWorkspaceService.DIAGRAM_COMMAND,
                        InterlisWorkspaceService.COMPILE_COMMAND)));
        capabilities.setTextDocumentSync(sync);
        capabilities.setHoverProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setCompletionProvider(completion);
        capabilities.setReferencesProvider(true);
        capabilities.setRenameProvider(new RenameOptions(true));
        capabilities.setCodeActionProvider(true);
        capabilities.setDocumentFormattingProvider(true);
        capabilities.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(
                new SemanticTokensLegend(InterlisTextDocumentService.TOKEN_TYPES, List.of()), true, false));
        capabilities.setFoldingRangeProvider(true);
        capabilities.setPositionEncoding("utf-16");
        var result=new InitializeResult(capabilities);
        result.setServerInfo(new org.eclipse.lsp4j.ServerInfo("interlis-java",guru.interlis.model.VersionManifest.current().toolVersion()));
        return LspFutures.map(workspaceService.initializeProject(folders),ignored -> { ready=true; return result; },textDocuments::operation);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        try { requireReady(); } catch(org.eclipse.lsp4j.jsonrpc.ResponseErrorException unavailable) { return CompletableFuture.failedFuture(unavailable); }
        shutdown = true;
        workspaceService.close();
        textDocuments.close();
        workspace.close();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        workspaceService.close();
        textDocuments.close();
        workspace.close();
        processExit.accept(shutdown?0:1);
    }

    /**
     * Executes {@code shutdownRequested}.
     *
     * @return the result
     */
    public boolean shutdownRequested() {
        return shutdown;
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocuments;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    InterlisTextDocumentService textDocumentsForTest() {
        return textDocuments;
    }

    Workspace workspaceForTest() {
        return workspace;
    }
}
