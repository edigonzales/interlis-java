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

    /**
     * Creates an instance.
     *
     * @param workspace the {@code workspace} argument
     */
    public InterlisLanguageServer(Workspace workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.textDocuments = new InterlisTextDocumentService(workspace);
        this.workspaceService = new InterlisWorkspaceService(workspace, textDocuments);
    }

    @Override
    public void connect(LanguageClient client) {
        Objects.requireNonNull(client, "client");
        textDocuments.sink(client::publishDiagnostics);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
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
                new SemanticTokensLegend(List.of("keyword", "number", "string", "enumMember",
                        "function", "variable", "property"), List.of()), true, false));
        capabilities.setFoldingRangeProvider(true);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdown = true;
        workspaceService.close();
        workspace.close();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        workspace.close();
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
