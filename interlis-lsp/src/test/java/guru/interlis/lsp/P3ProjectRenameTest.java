package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** LSP-012/015: closed project sources participate in exact workspace-wide edits. */
class P3ProjectRenameTest {
    @TempDir Path temporary;
    private static final String BASE="INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = DOMAIN Common=TEXT; END Base.";
    private static final String ROOT="INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = IMPORTS Base; TOPIC T= CLASS C= value:Base.Common; END C; END T; END Root.";

    @Test void renameIncludesAClosedDeclarationAndAnUnsavedOpenReferenceWithoutWritingFiles() throws Exception {
        Path folder=project(); Path base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili");
        for(boolean versioned:new boolean[]{false,true}) {
            var server=open(folder,versioned,null,null);
            try {
                var selected=new TextDocumentIdentifier(root.toUri().toString());
                Position cursor=cursor();
                assertNotNull(server.getTextDocumentService().prepareRename(new PrepareRenameParams(selected,cursor)).get(5,TimeUnit.SECONDS));
                var references=server.getTextDocumentService().references(new ReferenceParams(selected,cursor,new ReferenceContext(true))).get(5,TimeUnit.SECONDS);
                var expected=Set.of(base.toUri().toString(),root.toUri().toString());
                assertEquals(expected,references.stream().map(Location::getUri).collect(java.util.stream.Collectors.toSet()));
                var edit=rename(server,root);
                if(versioned) {
                    assertNull(edit.getChanges());
                    assertEquals(expected,edit.getDocumentChanges().stream().map(value->value.getLeft().getTextDocument().getUri()).collect(java.util.stream.Collectors.toSet()));
                    for(var value:edit.getDocumentChanges()) {
                        var document=value.getLeft();
                        assertEquals(document.getTextDocument().getUri().equals(root.toUri().toString())?Integer.valueOf(5):null,document.getTextDocument().getVersion());
                        assertTrue(document.getEdits().stream().allMatch(change->change.getNewText().equals("Renamed")));
                    }
                } else {
                    assertEquals(expected,edit.getChanges().keySet());
                    assertTrue(edit.getChanges().values().stream().flatMap(Collection::stream).allMatch(value->value.getNewText().equals("Renamed")));
                }
                assertEquals(BASE,Files.readString(base)); assertEquals(ROOT,Files.readString(root));
            } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test void anUnannouncedDiskChangeRejectsTheWholeRenameInsteadOfReturningPartialEdits() throws Exception {
        Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili");
        var server=open(folder,true,null,null);
        try {
            ready(server,root);
            String external=BASE.replace("Common=TEXT","Common=TEXT*10"); Files.writeString(base,external);
            var failure=assertThrows(ExecutionException.class,()->rename(server,root));
            var response=assertInstanceOf(ResponseErrorException.class,failure.getCause());
            assertEquals(ResponseErrorCode.ContentModified.getValue(),response.getResponseError().getCode());
            assertNull(response.getResponseError().getData());
            assertEquals(external,Files.readString(base)); assertEquals(ROOT,Files.readString(root));
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void aKnownSourceOutsideTheEditableProjectCannotReceiveAPartialRename() throws Exception {
        Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili");
        Files.delete(base);
        Path outside=temporary.resolve("Library.ili"); Files.writeString(outside,BASE);
        Workspace workspace=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults());
        workspace.update(new SourceUpdate(new SourceId(outside.toUri().toString()),new SourceExpectation.Absent(),new SourceChange.Replace(BASE)));
        var server=open(folder,true,workspace,null);
        try {
            ready(server,root);
            var failure=assertThrows(ExecutionException.class,()->rename(server,root));
            assertEquals(ResponseErrorCode.RequestFailed.getValue(),assertInstanceOf(ResponseErrorException.class,failure.getCause()).getResponseError().getCode());
            assertEquals(BASE,Files.readString(outside)); assertEquals(ROOT,Files.readString(root));
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void changedWritePermissionsRejectTheWholeRename() throws Exception {
        Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili");
        var server=open(folder,true,null,null);
        boolean posix=Files.getFileStore(base).supportsFileAttributeView("posix");
        var permissions=posix?Files.getPosixFilePermissions(base):Set.<java.nio.file.attribute.PosixFilePermission>of();
        try {
            ready(server,root);
            if(posix) Files.setPosixFilePermissions(base,Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ));
            else Files.setAttribute(base,"dos:readonly",true);
            var failure=assertThrows(ExecutionException.class,()->rename(server,root));
            assertEquals(ResponseErrorCode.RequestFailed.getValue(),assertInstanceOf(ResponseErrorException.class,failure.getCause()).getResponseError().getCode());
            assertEquals(BASE,Files.readString(base)); assertEquals(ROOT,Files.readString(root));
        } finally {
            if(posix) Files.setPosixFilePermissions(base,permissions); else Files.setAttribute(base,"dos:readonly",false);
            server.shutdown().get(5,TimeUnit.SECONDS);
        }
    }

    @Test void aClosedDependencyRefreshReplacesDiagnosticsAndKeepsTheUnsavedClientText() throws Exception {
        Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili");
        var publications=new ConcurrentLinkedQueue<PublishDiagnosticsParams>();
        var server=open(folder,true,null,publications);
        try {
            ready(server,root);
            var original=server.workspaceForTest().source(new SourceId(base.toUri().toString())).orElseThrow().revision();
            Files.writeString(base,BASE.replace("DOMAIN Common=TEXT;",""));
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(
                    new FileEvent(base.toUri().toString(),FileChangeType.Changed),new FileEvent(root.toUri().toString(),FileChangeType.Changed))));
            ready(server,root);
            var broken=publications.stream().filter(value->value.getUri().equals(root.toUri().toString())).toList().getLast();
            assertEquals(5,broken.getVersion()); assertFalse(broken.getDiagnostics().isEmpty());
            assertTrue(server.workspaceForTest().source(new SourceId(root.toUri().toString())).orElseThrow().text().contains("unsaved 😀"));
            Files.writeString(base,BASE);
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(base.toUri().toString(),FileChangeType.Changed))));
            ready(server,root);
            var fixed=publications.stream().filter(value->value.getUri().equals(root.toUri().toString())).toList().getLast();
            assertEquals(5,fixed.getVersion()); assertTrue(fixed.getDiagnostics().isEmpty(),fixed.toString());
            assertTrue(server.workspaceForTest().source(new SourceId(base.toUri().toString())).orElseThrow().revision().value()>original.value());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    private Path project() throws Exception {
        Path folder=Files.createTempDirectory(temporary,"Projekt ä ");
        Files.writeString(folder.resolve("Base.ili"),BASE); Files.writeString(folder.resolve("Root.ili"),ROOT); return folder;
    }
    private static Position cursor() { return new Position(0,ROOT.indexOf("Common")+2); }
    private static WorkspaceEdit rename(InterlisLanguageServer server,Path root) throws Exception {
        return server.getTextDocumentService().rename(new RenameParams(new TextDocumentIdentifier(root.toUri().toString()),cursor(),"Renamed")).get(5,TimeUnit.SECONDS);
    }
    private static void ready(InterlisLanguageServer server,Path root) throws Exception {
        server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(root.toUri().toString()))).get(5,TimeUnit.SECONDS);
    }
    private static InterlisLanguageServer open(Path folder,boolean versioned,Workspace workspace,Queue<PublishDiagnosticsParams> diagnostics) throws Exception {
        var server=new InterlisLanguageServer(workspace==null?Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()):workspace);
        if(diagnostics!=null) server.textDocumentsForTest().sink(diagnostics::add);
        var initialize=new InitializeParams(); initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(folder.toUri().toString(),"Project")));
        var capabilities=new ClientCapabilities(); var workspaceCapabilities=new WorkspaceClientCapabilities(); var edit=new WorkspaceEditCapabilities();
        edit.setDocumentChanges(versioned); workspaceCapabilities.setWorkspaceEdit(edit); capabilities.setWorkspace(workspaceCapabilities); initialize.setCapabilities(capabilities);
        server.initialize(initialize).get(5,TimeUnit.SECONDS);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(folder.resolve("Root.ili").toUri().toString(),"interlis",5,ROOT+"\n!! unsaved 😀")));
        return server;
    }
}
