package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.source.SourceId;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** LSP-003/004/006/016: accepted client text and published versions stay exact. */
class P3DocumentSynchronizationTest {
    private static final String URI = "memory:/synchronization.ili";
    private static final String TEXT = "INTERLIS 2.4;\nMODEL F AT \"x\" VERSION \"1\" = END F.\n";

    @Test void protocolColumnsClampAtLineEndWhileNegativeAndSplitSurrogatePositionsAreRejected() {
        var source=new guru.interlis.source.SourceText(new SourceId(URI),new guru.interlis.source.SourceRevision(1),"a😀\r\nb\n");
        assertEquals(3,PositionMapper.toOffset(source,new Position(0,Integer.MAX_VALUE)));
        assertEquals(6,PositionMapper.toOffset(source,new Position(1,100)));
        assertEquals(7,PositionMapper.toOffset(source,new Position(2,100)));
        assertThrows(ResponseErrorException.class,()->PositionMapper.toOffset(source,new Position(0,2)));
        assertThrows(ResponseErrorException.class,()->PositionMapper.toOffset(source,new Position(-1,0)));
        assertThrows(ResponseErrorException.class,()->PositionMapper.toOffset(source,new Position(0,-1)));
    }

    @Test void anInvalidBatchIsAtomicAndBlocksDeltasUntilAFullReplacement() throws Exception {
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            var server = new InterlisLanguageServer(workspace);
        server.initialize(new org.eclipse.lsp4j.InitializeParams()).join();
            try {
                var service = server.getTextDocumentService();
                var publications = new LinkedBlockingQueue<PublishDiagnosticsParams>();
                server.textDocumentsForTest().sink(publications::add);
                service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis", 1, TEXT)));
                assertNotNull(publications.poll(5, TimeUnit.SECONDS));
                var revision = workspace.currentRevision();
                service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI, 2), List.of(
                        new TextDocumentContentChangeEvent(new Range(new Position(0, 0), new Position(0, 0)), "!! transient\n"),
                        new TextDocumentContentChangeEvent(new Range(new Position(99, 0), new Position(0, 0)), "CORRUPTION"))));
                assertEquals(TEXT, workspace.source(new SourceId(URI)).orElseThrow().text());
                assertEquals(revision, workspace.currentRevision());
                service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI, 3), List.of(
                        new TextDocumentContentChangeEvent(new Range(new Position(0, 0), new Position(0, 0)), "IGNORED"))));
                assertEquals(TEXT, workspace.source(new SourceId(URI)).orElseThrow().text());
                assertNotNull(error(() -> service.formatting(new DocumentFormattingParams(
                        new TextDocumentIdentifier(URI), new FormattingOptions(2, true)))));
                String replacement = TEXT + "!! full synchronization\n";
                service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI, 4),
                        List.of(new TextDocumentContentChangeEvent(replacement))));
                assertEquals(replacement, workspace.source(new SourceId(URI)).orElseThrow().text());
                service.formatting(new DocumentFormattingParams(new TextDocumentIdentifier(URI), new FormattingOptions(2, true))).get(5, TimeUnit.SECONDS);
            } finally { server.shutdown().get(5, TimeUnit.SECONDS); }
        }
    }

    @Test void diagnosticReplacementCarriesTheExactClientVersionIncludingNoOpChanges() throws Exception {
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            var server = new InterlisLanguageServer(workspace);
        server.initialize(new org.eclipse.lsp4j.InitializeParams()).join();
            try {
                var publications = new LinkedBlockingQueue<PublishDiagnosticsParams>();
                server.textDocumentsForTest().sink(publications::add);
                server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis", 7, TEXT)));
                assertEquals(7, publications.poll(5, TimeUnit.SECONDS).getVersion());
                var revision = workspace.currentRevision();
                server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI, 8),
                        List.of(new TextDocumentContentChangeEvent(TEXT))));
                assertEquals(8, publications.poll(5, TimeUnit.SECONDS).getVersion());
                assertEquals(revision, workspace.currentRevision());
            } finally { server.shutdown().get(5, TimeUnit.SECONDS); }
        }
    }

    @Test void anUnknownDocumentIsAnInvalidRequestWithoutInternalFailureData() throws Exception {
        var server = new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        server.initialize(new org.eclipse.lsp4j.InitializeParams()).join();
        try {
            var failure = error(() -> server.getTextDocumentService().hover(new HoverParams(
                    new TextDocumentIdentifier("memory:/never-opened.ili"), new Position(0, 0))));
            assertEquals(ResponseErrorCode.InvalidParams.getValue(), failure.getResponseError().getCode());
            assertNull(failure.getResponseError().getData());
            assertFalse(failure.getMessage().contains("IllegalStateException"));
        } finally { server.shutdown().get(5, TimeUnit.SECONDS); }
    }

    private static ResponseErrorException error(Supplier<CompletableFuture<?>> request) {
        var failed = CompletableFuture.supplyAsync(request).thenCompose(value -> value);
        var exception = assertThrows(CompletionException.class, failed::join);
        return assertInstanceOf(ResponseErrorException.class, exception.getCause());
    }
}
