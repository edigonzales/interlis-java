package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LspServerTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL LspModel AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Building =
                  height : 0..999;
                  label : TEXT*40;
                END Building;
              END Data;
            END LspModel.
            """;

    @Test
    void openChangeDiagnosticsAndQueries() {
        InterlisLanguageServer server = new InterlisLanguageServer(
                Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        AtomicReference<PublishDiagnosticsParams> published = new AtomicReference<>();
        server.textDocumentsForTest().sink(published::set);
        String uri = "memory:/lsp.ili";
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri, "interlis", 1, MODEL)));
        assertNotNull(published.get());
        assertTrue(published.get().getDiagnostics().isEmpty(),
                () -> published.get().getDiagnostics().toString());

        Position hoverPosition = positionOf(MODEL, MODEL.indexOf("height"));
        Hover hover = server.getTextDocumentService().hover(new HoverParams(
                new TextDocumentIdentifier(uri), hoverPosition)).join();
        assertNotNull(hover);
        assertTrue(hover.getContents().getRight().getValue().contains("height"));

        var definition = server.getTextDocumentService().definition(new DefinitionParams(
                new TextDocumentIdentifier(uri), positionOf(MODEL, MODEL.indexOf("Building")))).join();
        assertFalse(definition.getLeft().isEmpty());

        var symbols = server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(
                new TextDocumentIdentifier(uri))).join();
        assertFalse(symbols.isEmpty());
        var completion = server.getTextDocumentService().completion(new CompletionParams(
                new TextDocumentIdentifier(uri), positionOf(MODEL, MODEL.indexOf("label")))).join();
        assertFalse(completion.getLeft().isEmpty());

        String broken = MODEL.replace("height : 0..999;", "height : ;");
        DidChangeTextDocumentParams change = new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 2),
                List.of(new TextDocumentContentChangeEvent(broken)));
        server.getTextDocumentService().didChange(change);
        assertFalse(published.get().getDiagnostics().isEmpty());
        assertTrue(published.get().getDiagnostics().getFirst().getCode().getLeft().startsWith("ILI-SYN-"));
    }

    @Test
    void sequentialChangesAndEmptyDiagnosticsReplacePrevious() {
        InterlisLanguageServer server = new InterlisLanguageServer(
                Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        AtomicReference<PublishDiagnosticsParams> published = new AtomicReference<>();
        server.textDocumentsForTest().sink(published::set);
        String uri = "memory:/sequence.ili";
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem(uri, "interlis", 1, MODEL)));
        assertTrue(published.get().getDiagnostics().isEmpty());

        String broken = MODEL.replace("height : 0..999;", "height : ;");
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 2),
                List.of(new TextDocumentContentChangeEvent(broken))));
        assertFalse(published.get().getDiagnostics().isEmpty());

        int heightStart = broken.indexOf("height : ;");
        int heightEnd = heightStart + "height : ;".length();
        var fixRange = new org.eclipse.lsp4j.Range(
                positionOf(broken, heightStart), positionOf(broken, heightEnd));
        var appendRange = new org.eclipse.lsp4j.Range(
                positionOf(broken, broken.length()), positionOf(broken, broken.length()));
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 3),
                List.of(new TextDocumentContentChangeEvent(fixRange, "height : 0..999;"),
                        new TextDocumentContentChangeEvent(appendRange, "\n!! fixed\n"))));
        assertTrue(published.get().getDiagnostics().isEmpty(),
                () -> published.get().getDiagnostics().toString());
    }

    @Test
    void cancelledCompileLeavesSessionFunctional() throws Exception {
        Workspace real = Workspaces.create(CompilerEngines.create(),
                WorkspaceConfiguration.defaults());
        AtomicBoolean cancellationObserved = new AtomicBoolean();
        Workspace slow = (Workspace) Proxy.newProxyInstance(
                Workspace.class.getClassLoader(), new Class<?>[]{Workspace.class},
                (proxy, method, args) -> {
                    if ("compile".equals(method.getName())) {
                        for (int i = 0; i < 400; i++) {
                            if (args != null && args.length > 1
                                    && args[1] instanceof CancellationToken token
                                    && token.isCancellationRequested()) {
                                cancellationObserved.set(true);
                                throw new OperationCancelledException(
                                        CancellationReason.USER_REQUESTED);
                            }
                            Thread.sleep(5);
                        }
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        InterlisLanguageServer server = new InterlisLanguageServer(slow);
        try {
            String uri = "memory:/cancel.ili";
            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                    new TextDocumentItem(uri, "interlis", 1, MODEL)));
            var service = (InterlisWorkspaceService) server.getWorkspaceService();
            var compile = service.executeCommand(new ExecuteCommandParams(
                    InterlisWorkspaceService.COMPILE_COMMAND, List.of()));
            Thread.sleep(100);
            assertTrue(compile.cancel(true));
            Thread.sleep(100);
            assertTrue(cancellationObserved.get(), "compiler did not observe cancellation");
            assertTrue(compile.isCancelled());

            String diagram = (String) service.executeCommand(new ExecuteCommandParams(
                    InterlisWorkspaceService.DIAGRAM_COMMAND,
                    List.<Object>of(Map.of("uri", uri)))).get(2, TimeUnit.SECONDS);
            assertTrue(diagram.contains("Building"), diagram);
        } finally {
            server.shutdown().join();
        }
    }

    @Test
    void capabilitiesAdvertiseOnlyImplementedFeatures() {
        InterlisLanguageServer server = new InterlisLanguageServer(
                Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        var result = server.initialize(null).join();
        assertTrue(result.getCapabilities().getHoverProvider().getLeft());
        assertEquals(org.eclipse.lsp4j.TextDocumentSyncKind.Incremental,
                result.getCapabilities().getTextDocumentSync().getRight().getChange());
    }

    private static Position positionOf(String text, int offset) {
        int line = 0;
        int character = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                character = 0;
            } else if (text.charAt(i) != '\r') {
                character++;
            }
        }
        return new Position(line, character);
    }
}
