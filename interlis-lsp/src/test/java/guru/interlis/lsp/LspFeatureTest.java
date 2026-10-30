package guru.interlis.lsp;

import com.google.gson.JsonObject;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.workspace.Workspaces;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LspFeatureTest {
    private static final String URI = "file:///tmp/lsp-feature.ili";
    private static final String MODEL = """
            INTERLIS 2.3;
            MODEL M AT "x" VERSION "1" =
              TOPIC T =
                CLASS Alpha =
                  value : TEXT*10;
                END Alpha;
              END T;
            END M.
            """;

    @Test
    void advertisesOnlyImplementedP3Capabilities() throws Exception {
        InterlisLanguageServer server = new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),guru.interlis.workspace.WorkspaceConfiguration.defaults()));
        InitializeResult result = server.initialize(new InitializeParams()).get(2, TimeUnit.SECONDS);
        assertNotNull(result.getCapabilities().getReferencesProvider());
        assertNotNull(result.getCapabilities().getRenameProvider());
        assertNotNull(result.getCapabilities().getCodeActionProvider());
        assertNotNull(result.getCapabilities().getDocumentFormattingProvider());
        assertNotNull(result.getCapabilities().getSemanticTokensProvider());
        assertNotNull(result.getCapabilities().getFoldingRangeProvider());
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void referencesRenameAndFormattingUseTheSameSnapshot() throws Exception {
        InterlisLanguageServer server = server();
        InterlisTextDocumentService documents = server.textDocumentsForTest();
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis",
                1, MODEL)));
        Position position = new Position(3, 10);
        var prepared = documents.prepareRename(new PrepareRenameParams(
                new TextDocumentIdentifier(URI), position)).get(2, TimeUnit.SECONDS);
        assertNotNull(prepared.getFirst());

        var references = documents.references(new ReferenceParams(
                new TextDocumentIdentifier(URI), position, new ReferenceContext(true)))
                .get(2, TimeUnit.SECONDS);
        assertFalse(references.isEmpty());

        WorkspaceEdit edit = documents.rename(new RenameParams(
                new TextDocumentIdentifier(URI), position, "Beta")).get(2, TimeUnit.SECONDS);
        List<TextEdit> edits = edit.getChanges().get(URI);
        assertNotNull(edits);
        assertEquals(2, edits.size());

        List<? extends TextEdit> formatted = documents.formatting(new DocumentFormattingParams(
                new TextDocumentIdentifier(URI), new FormattingOptions(2, true)))
                .get(2, TimeUnit.SECONDS);
        assertTrue(formatted.isEmpty() || formatted.size() == 1);

        var actions = documents.codeAction(new CodeActionParams(
                new TextDocumentIdentifier(URI),
                new Range(new Position(0, 0), new Position(0, 0)),
                new CodeActionContext(List.of()))).get(2, TimeUnit.SECONDS);
        assertTrue(actions.isEmpty() || actions.size() == 1);

        var tokens = documents.semanticTokensFull(new SemanticTokensParams(
                new TextDocumentIdentifier(URI))).get(2, TimeUnit.SECONDS);
        assertFalse(tokens.getData().isEmpty());
        var folding = documents.foldingRange(new FoldingRangeRequestParams(
                new TextDocumentIdentifier(URI))).get(2, TimeUnit.SECONDS);
        assertFalse(folding.isEmpty());
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void renameAcrossOpenImportedSourceProducesMultiFileEdit() throws Exception {
        Path testdata = Path.of(System.getProperty("testdata.dir", "../testdata"));
        Path basePath = testdata.resolve("models/base24.ili");
        Path surveyPath = testdata.resolve("models/survey24.ili");
        String base = Files.readString(basePath);
        String survey = Files.readString(surveyPath);
        String baseUri = basePath.toAbsolutePath().toUri().toString();
        String surveyUri = surveyPath.toAbsolutePath().toUri().toString();
        InterlisLanguageServer server = server();
        InterlisTextDocumentService documents = server.textDocumentsForTest();
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(baseUri, "interlis",
                1, base)));
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(surveyUri, "interlis",
                1, survey)));
        int line = 12;
        int character = survey.split("\n")[line].lastIndexOf("Count");
        WorkspaceEdit edit = documents.rename(new RenameParams(
                new TextDocumentIdentifier(surveyUri), new Position(line, character),
                "RenamedCount")).get(2, TimeUnit.SECONDS);
        assertTrue(edit.getChanges().containsKey(baseUri), edit.getChanges().toString());
        assertTrue(edit.getChanges().containsKey(surveyUri), edit.getChanges().toString());
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void renameOfClosedImportedSourceIsRejectedAsReadOnly() throws Exception {
        Path testdata = Path.of(System.getProperty("testdata.dir", "../testdata"));
        Path basePath = testdata.resolve("models/base24.ili");
        Path surveyPath = testdata.resolve("models/survey24.ili");
        String base = Files.readString(basePath);
        String survey = Files.readString(surveyPath);
        String baseUri = basePath.toAbsolutePath().toUri().toString();
        String surveyUri = surveyPath.toAbsolutePath().toUri().toString();
        InterlisLanguageServer server = server();
        var baseId = new guru.interlis.source.SourceId(baseUri);
        server.workspaceForTest().update(new guru.interlis.workspace.SourceUpdate(baseId,
                new guru.interlis.workspace.SourceExpectation.Absent(),
                new guru.interlis.workspace.SourceChange.Replace(base)));
        InterlisTextDocumentService documents = server.textDocumentsForTest();
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(surveyUri, "interlis",
                1, survey)));
        int line = 12;
        int character = survey.split("\n")[line].lastIndexOf("Count");
        ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class, () -> documents.rename(new RenameParams(
                        new TextDocumentIdentifier(surveyUri), new Position(line, character),
                        "RenamedCount")).get(2, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("read-only"),
                failure.getCause().getMessage());
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void missingImportedSourceCannotPrepareRename() throws Exception {
        Path testdata = Path.of(System.getProperty("testdata.dir", "../testdata"));
        Path surveyPath = testdata.resolve("models/survey24.ili");
        String survey = Files.readString(surveyPath);
        String surveyUri = surveyPath.toAbsolutePath().toUri().toString();
        InterlisLanguageServer server = server();
        InterlisTextDocumentService documents = server.textDocumentsForTest();
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(surveyUri, "interlis",
                1, survey)));
        int line = 12;
        int character = survey.split("\n")[line].lastIndexOf("Count");
        ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class, () -> documents.prepareRename(new PrepareRenameParams(
                        new TextDocumentIdentifier(surveyUri), new Position(line, character)))
                        .get(2, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("analysis is incomplete")
                        || failure.getCause().getMessage().contains("no editable symbol"),
                failure.getCause().getMessage());
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void renameCollisionIsRejected() throws Exception {
        String collisionModel = """
                INTERLIS 2.3;
                MODEL M AT "x" VERSION "1" =
                  TOPIC T =
                    CLASS Alpha = value : TEXT*10; END Alpha;
                    CLASS Beta = value : TEXT*10; END Beta;
                  END T;
                END M.
                """;
        InterlisLanguageServer server = server();
        InterlisTextDocumentService documents = server.textDocumentsForTest();
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis",
                1, collisionModel)));
        ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class, () -> documents.rename(new RenameParams(
                        new TextDocumentIdentifier(URI), new Position(4, 10), "Alpha"))
                        .get(2, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("collision"));
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void renameIsRejectedWhenSourceChangedAfterAnalysis() throws Exception {
        InterlisLanguageServer server = server();
        InterlisTextDocumentService documents = server.textDocumentsForTest();
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis",
                1, MODEL)));
        var sourceId = new guru.interlis.source.SourceId(URI);
        var current = server.workspaceForTest().source(sourceId).orElseThrow();
        server.workspaceForTest().update(new guru.interlis.workspace.SourceUpdate(sourceId,
                new guru.interlis.workspace.SourceExpectation.At(current.revision()),
                new guru.interlis.workspace.SourceChange.Replace(
                        MODEL.replace("Alpha", "Gamma"))));
        ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class, () -> documents.rename(new RenameParams(
                        new TextDocumentIdentifier(URI), new Position(3, 10), "Beta"))
                        .get(2, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("CONTENT_MODIFIED"),
                failure.getCause().getMessage());
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void configurationAndWatchedFilesRefreshAreAccepted() throws Exception {
        InterlisLanguageServer server = server();
        InterlisTextDocumentService documents = server.textDocumentsForTest();
        documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis",
                1, MODEL)));
        server.getWorkspaceService().didChangeConfiguration(
                new DidChangeConfigurationParams(new JsonObject()));

        Path watched = Files.createTempFile("interlis-lsp-watch", ".ili");
        Files.writeString(watched, MODEL.replace("Alpha", "External"));
        String watchedUri = watched.toUri().toString();
        server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(
                List.of(new FileEvent(watchedUri, FileChangeType.Changed))));
        server.getWorkspaceService().executeCommand(new org.eclipse.lsp4j.ExecuteCommandParams("interlis.compile",List.of())).get(5,TimeUnit.SECONDS);
        assertTrue(server.workspaceForTest().source(
                new guru.interlis.source.SourceId(watchedUri)).isPresent());
        server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(
                List.of(new FileEvent(watchedUri, FileChangeType.Deleted))));
        assertTrue(server.workspaceForTest().source(
                new guru.interlis.source.SourceId(watchedUri)).isEmpty());
        Files.delete(watched);
        server.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void oldDocumentVersionsAreIgnoredAndClosingRestoresDiskText() throws Exception {
        Path path = Files.createTempFile("interlis-lsp-close", ".ili");
        Files.writeString(path, MODEL);
        String uri = path.toUri().toString();
        var id = new guru.interlis.source.SourceId(uri);
        InterlisLanguageServer server = server();
        try {
            var documents = server.textDocumentsForTest();
            documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "interlis",
                    3, MODEL.replace("Alpha", "Unsaved"))));
            documents.didChange(new org.eclipse.lsp4j.DidChangeTextDocumentParams(
                    new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(uri, 2),
                    List.of(new org.eclipse.lsp4j.TextDocumentContentChangeEvent(MODEL))));
            assertTrue(server.workspaceForTest().source(id).orElseThrow().text().contains("Unsaved"));
            documents.didClose(new org.eclipse.lsp4j.DidCloseTextDocumentParams(
                    new TextDocumentIdentifier(uri)));
            server.getWorkspaceService().executeCommand(new org.eclipse.lsp4j.ExecuteCommandParams("interlis.compile",List.of())).get(5,TimeUnit.SECONDS);
            assertEquals(MODEL, server.workspaceForTest().source(id).orElseThrow().text());
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
            Files.delete(path);
        }
    }

    private static InterlisLanguageServer server() {
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),
                guru.interlis.workspace.WorkspaceConfiguration.defaults()));
        server.initialize(new InitializeParams()).join(); return server;
    }
}
