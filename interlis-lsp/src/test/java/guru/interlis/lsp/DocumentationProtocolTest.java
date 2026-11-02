package guru.interlis.lsp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.workspace.*;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class DocumentationProtocolTest {
    static final String URI = "untitled:Catalog.ili",
            TEXT =
                    "INTERLIS 2.4;MODEL Catalog AT \"x\" VERSION \"1\"=TOPIC T=CLASS"
                            + " C=value:TEXT*12;END C;END T;END Catalog.";

    static InterlisLanguageServer server() throws Exception {
        var s =
                new InterlisLanguageServer(
                        Workspaces.create(
                                CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        var p = new InitializeParams();
        p.setInitializationOptions(Map.of("repositories", List.of()));
        var cap = new ClientCapabilities();
        var workspace = new WorkspaceClientCapabilities();
        var edit = new WorkspaceEditCapabilities();
        edit.setDocumentChanges(true);
        workspace.setWorkspaceEdit(edit);
        cap.setWorkspace(workspace);
        p.setCapabilities(cap);
        s.initialize(p).get(5, TimeUnit.SECONDS);
        return s;
    }

    static void open(InterlisLanguageServer s, int version, String text) {
        s.getTextDocumentService()
                .didOpen(
                        new DidOpenTextDocumentParams(
                                new TextDocumentItem(URI, "interlis", version, text)));
    }

    @Test
    void allFormatsComeFromCurrentCheckedRootAndOptionsAreStrict() throws Exception {
        var s = server();
        try {
            open(s, 7, TEXT);
            for (String format : LspExportService.FORMATS) {
                var a =
                        s.exportArtifact(
                                        new ExportArtifactParams(
                                                1,
                                                new VersionedTextDocumentIdentifier(URI, 7),
                                                format,
                                                null,
                                                null,
                                                JsonParser.parseString(
                                                                "{\"language\":\"IT\",\"headers\":{\"catalog\":\"Custom"
                                                                    + " <title>\"}}")
                                                        .getAsJsonObject()))
                                .get(10, TimeUnit.SECONDS);
                assertEquals(7, a.sourceSnapshot().documentVersion());
                assertEquals(URI, a.sourceSnapshot().rootUri());
                assertNotNull(a.sourceSnapshot().snapshotId());
                assertTrue(a.content().length() > 20);
                assertEquals(format.equals("DOCX") ? "base64" : "utf8", a.encoding());
                if (Set.of("MARKDOWN", "HTML").contains(format)) {
                    assertTrue(a.content().contains("Custom &lt;title&gt;"));
                    assertTrue(
                            a.content()
                                    .contains(format.equals("MARKDOWN") ? "TEXT\\*12" : "TEXT*12"),
                            format);
                }
            }
            var stale =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    s.exportArtifact(
                                                    new ExportArtifactParams(
                                                            1,
                                                            new VersionedTextDocumentIdentifier(
                                                                    URI, 6),
                                                            "HTML",
                                                            null,
                                                            null,
                                                            null))
                                            .get());
            assertInstanceOf(ResponseErrorException.class, stale.getCause());
            assertEquals(
                    -32801,
                    ((ResponseErrorException) stale.getCause()).getResponseError().getCode());
            assertThrows(
                    ExecutionException.class,
                    () ->
                            s.exportArtifact(
                                            new ExportArtifactParams(
                                                    1,
                                                    new VersionedTextDocumentIdentifier(URI, 7),
                                                    "HTML",
                                                    null,
                                                    null,
                                                    JsonParser.parseString("{\"unknown\":true}")
                                                            .getAsJsonObject()))
                                    .get());
            s.getTextDocumentService()
                    .didChange(
                            new DidChangeTextDocumentParams(
                                    new VersionedTextDocumentIdentifier(URI, 8),
                                    List.of(
                                            new TextDocumentContentChangeEvent(
                                                    TEXT.replace("TEXT*12", "Unknown")))));
            assertThrows(
                    ExecutionException.class,
                    () ->
                            s.exportArtifact(
                                            new ExportArtifactParams(
                                                    1,
                                                    new VersionedTextDocumentIdentifier(URI, 8),
                                                    "DOCX",
                                                    null,
                                                    null,
                                                    null))
                                    .get());
        } finally {
            s.shutdown().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void quickFixHasExactOpenVersionWithoutUnversionedChanges() throws Exception {
        var s = server();
        try {
            String bad = TEXT.replace("END C;", "END Wrong;");
            open(s, 3, bad);
            var p =
                    new CodeActionParams(
                            new TextDocumentIdentifier(URI),
                            new Range(new Position(0, 0), new Position(0, bad.length())),
                            new CodeActionContext(List.of()));
            var actions =
                    s.getTextDocumentService().codeAction(p).get(5, TimeUnit.SECONDS).stream()
                            .filter(e -> e.isRight() && "quickfix".equals(e.getRight().getKind()))
                            .toList();
            assertEquals(1, actions.size());
            var change =
                    actions.getFirst()
                            .getRight()
                            .getEdit()
                            .getDocumentChanges()
                            .getFirst()
                            .getLeft();
            assertEquals(3, change.getTextDocument().getVersion());
            assertEquals("C", change.getEdits().getFirst().getNewText());
            assertNull(actions.getFirst().getRight().getEdit().getChanges());
        } finally {
            s.shutdown().get(5, TimeUnit.SECONDS);
        }
    }
}
