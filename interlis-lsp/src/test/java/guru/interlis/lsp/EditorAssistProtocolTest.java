package guru.interlis.lsp;

import static org.junit.jupiter.api.Assertions.*;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.workspace.*;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class EditorAssistProtocolTest {
    private static final String URI = "untitled:Assist.ili",
            SOURCE = "INTERLIS 2.4;\nMODEL Assist AT \"x\" VERSION \"1\"=\nCLASS C=\n";

    @Test
    void negotiatedPathHasExactCaretAndRejectsOldVersionsWithoutDoubleInsertion() throws Exception {
        var s =
                new InterlisLanguageServer(
                        Workspaces.create(
                                CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        try {
            var init = new InitializeParams();
            init.setInitializationOptions(Map.of("repositories", List.of()));
            var capabilities = new ClientCapabilities();
            capabilities.setExperimental(Map.of("interlis", Map.of("editorAssist", true)));
            init.setCapabilities(capabilities);
            var initialized = s.initialize(init).get(5, TimeUnit.SECONDS);
            assertNull(initialized.getCapabilities().getDocumentOnTypeFormattingProvider());
            s.getTextDocumentService()
                    .didOpen(
                            new DidOpenTextDocumentParams(
                                    new TextDocumentItem(URI, "interlis", 4, SOURCE)));
            var request =
                    new EditorAssistParams(
                            1,
                            new VersionedTextDocumentIdentifier(URI, 4),
                            new Position(3, 0),
                            new FormattingOptions(2, true));
            var result = s.editorAssist(request).get(5, TimeUnit.SECONDS);
            assertEquals(4, result.documentVersion());
            assertEquals(new Position(3, 2), result.caret());
            assertEquals("  \nEND C;", result.edits().getFirst().getNewText());
            assertEquals(1, result.edits().size());
            var standard = new DocumentOnTypeFormattingParams();
            standard.setTextDocument(new TextDocumentIdentifier(URI));
            standard.setPosition(new Position(3, 0));
            standard.setCh("\n");
            standard.setOptions(new FormattingOptions(2, true));
            assertTrue(s.getTextDocumentService().onTypeFormatting(standard).get().isEmpty());
            var error =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    s.editorAssist(
                                                    new EditorAssistParams(
                                                            1,
                                                            new VersionedTextDocumentIdentifier(
                                                                    URI, 3),
                                                            new Position(3, 0),
                                                            new FormattingOptions(2, true)))
                                            .get());
            assertEquals(
                    -32801,
                    assertInstanceOf(ResponseErrorException.class, error.getCause())
                            .getResponseError()
                            .getCode());
            String assignment = SOURCE.replace("CLASS C=", "DOMAIN D=");
            s.getTextDocumentService()
                    .didChange(
                            new DidChangeTextDocumentParams(
                                    new VersionedTextDocumentIdentifier(URI, 5),
                                    List.of(new TextDocumentContentChangeEvent(assignment))));
            assertNull(
                    s.editorAssist(
                                    new EditorAssistParams(
                                            1,
                                            new VersionedTextDocumentIdentifier(URI, 5),
                                            new Position(3, 0),
                                            new FormattingOptions(2, true)))
                            .get());
        } finally {
            s.shutdown().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void unnegotiatedClientKeepsStandardFormattingAndCannotInvokeCustomPath() throws Exception {
        var s = DocumentationProtocolTest.server();
        try {
            assertThrows(
                    ExecutionException.class,
                    () ->
                            s.editorAssist(
                                            new EditorAssistParams(
                                                    1,
                                                    new VersionedTextDocumentIdentifier(URI, 1),
                                                    new Position(0, 0),
                                                    new FormattingOptions(2, true)))
                                    .get());
        } finally {
            s.shutdown().get(5, TimeUnit.SECONDS);
        }
    }
}
