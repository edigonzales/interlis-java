package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.execution.RunEvent;
import guru.interlis.source.SourceId;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** LSP-003/004/006/016: accepted client text and published versions stay exact. */
class DocumentSynchronizationTest {
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

    @Test void anUnregisteredClientUriKeepsItsOriginalCoreSourceIdentity() throws Exception {
        var workspace=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults());
        var server=new InterlisLanguageServer(workspace);server.initialize(new InitializeParams()).get(5,TimeUnit.SECONDS);
        String uri="file:/p3-unregistered-client.ili";
        try {
            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"interlis",1,TEXT)));
            assertTrue(workspace.source(new SourceId(uri)).isPresent());
            assertFalse(server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri))).get(5,TimeUnit.SECONDS).isEmpty());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
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

                server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                        new TextDocumentItem(URI, "interlis", 8, TEXT + "!! stale reopen\n")));
                assertEquals(TEXT, workspace.source(new SourceId(URI)).orElseThrow().text());
                assertEquals(revision, workspace.currentRevision());
            } finally { server.shutdown().get(5, TimeUnit.SECONDS); }
        }
    }

    @Test void diagnosticsForANewerCoreRevisionDoNotBecomeUnversionedOpenDiagnostics() throws Exception {
        var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults());
        var server = new InterlisLanguageServer(workspace);
        server.initialize(new InitializeParams()).get(5, TimeUnit.SECONDS);
        var publications = new LinkedBlockingQueue<PublishDiagnosticsParams>();
        server.textDocumentsForTest().sink(publications::add);
        try {
            var service = server.getTextDocumentService();
            var sourceId = new SourceId(URI);
            service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis", 7, TEXT)));
            var opened = publications.poll(5, TimeUnit.SECONDS);
            assertNotNull(opened);
            assertEquals(7, opened.getVersion());
            var revision = workspace.source(sourceId).orElseThrow().revision();
            workspace.update(new SourceUpdate(sourceId, new SourceExpectation.At(revision),
                    new SourceChange.Replace(TEXT.replace("= END F.", "= DOMAIN D = Missing; END F."))));

            String otherUri = "memory:/other.ili";
            String otherText = TEXT.replace("MODEL F", "MODEL G").replace("END F.", "END G.");
            service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(otherUri, "interlis", 2, otherText)));
            assertFalse(service.documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(otherUri)))
                    .get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(publications.stream().anyMatch(p -> otherUri.equals(p.getUri()) && Integer.valueOf(2).equals(p.getVersion())));
            assertTrue(publications.stream().noneMatch(p -> URI.equals(p.getUri()) && !p.getDiagnostics().isEmpty()));
            assertEquals(ResponseErrorCode.ContentModified.getValue(), error(() -> service.documentSymbol(
                    new DocumentSymbolParams(new TextDocumentIdentifier(URI)))).getResponseError().getCode());
        } finally { server.shutdown().get(5, TimeUnit.SECONDS); }
    }

    @Test void closingWhileAnalysisIsInFlightDiscardsTheAnswerAndAllowsANewVersionSequence() throws Exception {
        var oldAnalysisComputed = new CountDownLatch(1);
        var releaseOldAnalysis = new CountDownLatch(1);
        var holdFirstAnalysis = new AtomicBoolean(true);
        var defaults = CompilerServices.defaults();
        var services = new CompilerServices(event -> {
            if (event instanceof RunEvent.Finished && holdFirstAnalysis.compareAndSet(true, false)) {
                oldAnalysisComputed.countDown();
                try {
                    assertTrue(releaseOldAnalysis.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }, defaults.clock(), defaults.runContexts());
        var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults());
        var server = new InterlisLanguageServer(workspace, CompilerEngines.create(services), services);
        server.initialize(new InitializeParams()).get(5, TimeUnit.SECONDS);
        var publications = new LinkedBlockingQueue<PublishDiagnosticsParams>();
        server.textDocumentsForTest().sink(publications::add);
        try {
            var service = server.getTextDocumentService();
            var document = new TextDocumentIdentifier(URI);
            var sourceId = new SourceId(URI);
            var symbols = new DocumentSymbolParams(document);
            String broken = TEXT.replace("= END F.", "= DOMAIN D = Missing; END F.");
            service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis", 7, broken)));
            assertTrue(oldAnalysisComputed.await(5, TimeUnit.SECONDS));
            var oldRevision = workspace.source(sourceId).orElseThrow().revision();
            var obsolete = service.documentSymbol(symbols);

            service.didClose(new DidCloseTextDocumentParams(document));
            var failure = assertThrows(ExecutionException.class, () -> obsolete.get(5, TimeUnit.SECONDS));
            assertEquals(ResponseErrorCode.ContentModified.getValue(),
                    assertInstanceOf(ResponseErrorException.class, failure.getCause()).getResponseError().getCode());
            assertTrue(workspace.source(sourceId).isEmpty());
            var cleared = publications.poll(5, TimeUnit.SECONDS);
            assertNotNull(cleared);
            assertEquals(URI, cleared.getUri());
            assertNull(cleared.getVersion());
            assertTrue(cleared.getDiagnostics().isEmpty());

            String reopened = TEXT.replace("MODEL F", "MODEL Reopened").replace("END F.", "END Reopened.");
            service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis", 1, reopened)));
            var current = service.documentSymbol(symbols).get(5, TimeUnit.SECONDS);
            assertEquals(List.of("Reopened"), current.stream().map(symbol -> symbol.getRight().getName()).toList());
            assertTrue(workspace.source(sourceId).orElseThrow().revision().value() > oldRevision.value());
            var accepted = publications.poll(5, TimeUnit.SECONDS);
            assertNotNull(accepted);
            assertEquals(URI, accepted.getUri());
            assertEquals(1, accepted.getVersion());
            assertTrue(accepted.getDiagnostics().isEmpty());
        } finally {
            releaseOldAnalysis.countDown();
            server.shutdown().get(5, TimeUnit.SECONDS);
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

    @Test void sourceLimitRejectionsNeverServeOldAnswersAndRecoverThroughFullReplacement() throws Exception {
        var defaults=WorkspaceConfiguration.defaults();var options=defaults.compilerOptions();
        var limited=new guru.interlis.compiler.CompilerOptions(options.semanticProfile(),options.allowedLanguages(),options.warningPolicy(),options.budget().withMaxSourceUtf16Length(100),options.overlays());
        for(boolean opened:new boolean[]{false,true}) {
            var workspace=Workspaces.create(CompilerEngines.create(),new WorkspaceConfiguration(limited,defaults.resolutionPolicy(),defaults.resolverCatalog(),defaults.cacheLimits()));
            var server=new InterlisLanguageServer(workspace);server.initialize(new InitializeParams()).get(5,TimeUnit.SECONDS);
            var messages=new java.util.ArrayList<String>();server.textDocumentsForTest().operationSink(messages::add);
            try {
                var service=server.getTextDocumentService();
                if(opened) {
                    service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI,"interlis",1,TEXT)));
                    service.documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS);
                }
                String oversized="!! SECRET_PAYLOAD_"+"x".repeat(200);
                assertDoesNotThrow(()-> {
                    if(opened) service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI,2),List.of(new TextDocumentContentChangeEvent(oversized))));
                    else service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI,"interlis",2,oversized)));
                });
                assertEquals(ResponseErrorCode.ContentModified.getValue(),error(()->service.documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI)))).getResponseError().getCode());
                assertEquals(ResponseErrorCode.ContentModified.getValue(),error(()->server.getWorkspaceService().executeCommand(new ExecuteCommandParams("interlis.compile",List.of()))).getResponseError().getCode());
                assertTrue(messages.stream().anyMatch(message->message.contains("SOURCE_LIMIT_EXCEEDED")));
                assertTrue(messages.stream().noneMatch(message->message.contains("SECRET_PAYLOAD")));
                assertEquals(opened?TEXT:null,workspace.source(new SourceId(URI)).map(value->value.text()).orElse(null));
                service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI,3),List.of(new TextDocumentContentChangeEvent(new Range(new Position(0,0),new Position(0,0)),"ignored"))));
                service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI, 3),
                        List.of(new TextDocumentContentChangeEvent(TEXT + "!! stale full replacement\n"))));
                assertEquals(ResponseErrorCode.ContentModified.getValue(), error(() -> service.documentSymbol(
                        new DocumentSymbolParams(new TextDocumentIdentifier(URI)))).getResponseError().getCode());
                assertEquals(opened ? TEXT : null, workspace.source(new SourceId(URI)).map(value -> value.text()).orElse(null));

                var publications = new LinkedBlockingQueue<PublishDiagnosticsParams>();
                server.textDocumentsForTest().sink(publications::add);
                service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI,4),List.of(new TextDocumentContentChangeEvent(TEXT))));
                assertFalse(service.documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS).isEmpty());
                assertEquals(TEXT,workspace.source(new SourceId(URI)).orElseThrow().text());
                var accepted = publications.poll(5, TimeUnit.SECONDS);
                assertNotNull(accepted);
                assertEquals(URI, accepted.getUri());
                assertEquals(4, accepted.getVersion());
            } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
        }
    }

    private static ResponseErrorException error(Supplier<CompletableFuture<?>> request) {
        var failed = CompletableFuture.supplyAsync(request).thenCompose(value -> value);
        var exception = assertThrows(CompletionException.class, failed::join);
        return assertInstanceOf(ResponseErrorException.class, exception.getCause());
    }
}
