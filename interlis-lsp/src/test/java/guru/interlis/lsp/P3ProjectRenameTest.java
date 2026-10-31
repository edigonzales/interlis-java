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

    @Test void watcherAliasesReferToTheExistingPhysicalSourceAndKeepOpenClientAuthority() throws Exception {
        Path folder=project(),alias=temporary.resolve("project-alias");
        Files.createSymbolicLink(alias,folder);
        var publications=new ConcurrentLinkedQueue<PublishDiagnosticsParams>();
        var server=open(alias,true,null,publications);
        Path root=alias.resolve("Root.ili"),base=alias.resolve("Base.ili");
        try {
            ready(server,root);
            assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
            assertTrue(publications.stream().filter(value->value.getUri().equals(root.toUri().toString())).toList().getLast().getDiagnostics().isEmpty());
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(
                    new FileEvent(base.toRealPath().toUri().toString(),FileChangeType.Changed),
                    new FileEvent(root.toRealPath().toUri().toString(),FileChangeType.Changed))));
            ready(server,root);
            assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
            assertTrue(server.workspaceForTest().source(new SourceId(root.toUri().toString())).orElseThrow().text().contains("unsaved 😀"));
            var latest=publications.stream().filter(value->value.getUri().equals(root.toUri().toString())).toList().getLast();
            assertTrue(latest.getDiagnostics().isEmpty(),latest.toString());
            assertEquals(5,latest.getVersion());
            assertEquals(2,rename(server,root).getDocumentChanges().size());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void reopeningThroughARealSingleSlashUriPreservesClientVersionsAndEditUris() throws Exception {
        Path folder=project(),alias=temporary.resolve("reopen-alias"); Files.createSymbolicLink(alias,folder);
        Path root=alias.resolve("Root.ili"); var publications=new ConcurrentLinkedQueue<PublishDiagnosticsParams>();
        var server=open(alias,true,null,publications);
        String clientUri=root.toRealPath().toUri().toString().replace("file:///","file:/");
        try {
            ready(server,root);
            server.getTextDocumentService().didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(root.toUri().toString())));
            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(clientUri,"interlis",7,ROOT+"\n!! real client 😀")));
            var selected=new TextDocumentIdentifier(clientUri);
            assertFalse(server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(selected)).get(5,TimeUnit.SECONDS).isEmpty());
            assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
            String current=server.workspaceForTest().source(new SourceId(root.toUri().toString())).orElseThrow().text();
            assertTrue(current.contains("real client 😀"),current+"\n"+server.workspaceForTest().state());
            var latest=publications.stream().filter(value->value.getUri().equals(clientUri)).toList().getLast();
            assertEquals(7,latest.getVersion()); assertTrue(latest.getDiagnostics().isEmpty(),latest.toString());
            var edit=server.getTextDocumentService().rename(new RenameParams(selected,cursor(),"Renamed")).get(5,TimeUnit.SECONDS);
            var opened=edit.getDocumentChanges().stream().map(value->value.getLeft().getTextDocument()).filter(value->value.getVersion()!=null).findFirst().orElseThrow();
            assertEquals(clientUri,opened.getUri()); assertEquals(7,opened.getVersion());
            assertNotNull(server.getTextDocumentService().hover(new HoverParams(new TextDocumentIdentifier(root.toUri().toString()),cursor())).get(5,TimeUnit.SECONDS));
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void unknownWatcherAliasesOfOpenFilesWithoutAProjectNeverReplaceClientBuffers() throws Exception {
        Path folder=project(),alias=temporary.resolve("unmapped-alias"); Files.createSymbolicLink(alias,folder);
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        server.initialize(new InitializeParams()).get(5,TimeUnit.SECONDS);
        Path root=alias.resolve("Root.ili"),base=alias.resolve("Base.ili");
        try {
            for(Path file:List.of(base,root)) server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(file.toUri().toString(),"interlis",1,Files.readString(file)+"\n!! open 😀")));
            ready(server,root);
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(root.toRealPath().toUri().toString(),FileChangeType.Changed))));
            ready(server,root);
            assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
            assertTrue(server.workspaceForTest().source(new SourceId(root.toUri().toString())).orElseThrow().text().contains("open 😀"));
            assertEquals(2,rename(server,root).getChanges().size());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void hardlinkWatcherAliasesDoNotDuplicateOrDeleteTheCanonicalProjectSource() throws Exception {
        Path folder=project(),root=folder.resolve("Root.ili"),base=folder.resolve("Base.ili"),link=temporary.resolve("Hardlink.ili");
        var server=open(folder,true,null,null);
        try {
            ready(server,root); Files.createLink(link,base);
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(link.toUri().toString(),FileChangeType.Changed))));
            ready(server,root); assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
            assertEquals(2,rename(server,root).getDocumentChanges().size());
            Files.delete(link);
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(link.toUri().toString(),FileChangeType.Deleted))));
            ready(server,root); assertEquals(BASE,server.workspaceForTest().source(new SourceId(base.toUri().toString())).orElseThrow().text());
            assertEquals(2,rename(server,root).getDocumentChanges().size());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void oversizedProjectInputHasAControlledSourceLimitFailure() throws Exception {
        Path folder=Files.createDirectory(temporary.resolve("large-project"));
        Files.writeString(folder.resolve("Large.ili"),"INTERLIS 2.4; MODEL Large AT \"x\" VERSION \"1\" = END Large.\n!!"+"x".repeat(2_100_000));
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        var initialize=new InitializeParams();initialize.setWorkspaceFolders(List.of(new WorkspaceFolder(folder.toUri().toString(),"Large")));
        try {
            var failure=assertThrows(ExecutionException.class,()->server.initialize(initialize).get(5,TimeUnit.SECONDS));
            var response=assertInstanceOf(ResponseErrorException.class,failure.getCause());
            assertEquals(ResponseErrorCode.RequestFailed.getValue(),response.getResponseError().getCode());
            assertTrue(response.getMessage().contains("SOURCE_LIMIT_EXCEEDED")); assertNull(response.getResponseError().getData());
            assertTrue(server.workspaceForTest().state().sourceRevisions().isEmpty());
        } finally { server.exit(); }
        var configured=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        initialize.setInitializationOptions(com.google.gson.JsonParser.parseString("{\"interlis\":{\"analysisBudget\":{\"maxSourceUtf16Length\":2200000}}}"));
        try {
            assertNotNull(configured.initialize(initialize).get(5,TimeUnit.SECONDS));
            assertEquals(1,configured.workspaceForTest().state().sourceRevisions().size());
        } finally { configured.exit(); }
    }

    @Test void projectDiscoveryCoalescesHardlinksBeforeInstallingCompilerSources() throws Exception {
        Path folder=project(); Files.createLink(folder.resolve("HardBase.ili"),folder.resolve("Base.ili"));
        var server=open(folder,true,null,null);
        try {
            ready(server,folder.resolve("Root.ili"));
            assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
            assertEquals(2,rename(server,folder.resolve("Root.ili")).getDocumentChanges().size());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void deletingASortedFirstSymlinkKeepsItsRealPrimaryAndLaterChanges() throws Exception {
        Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili"),alias=folder.resolve("AliasBase.ili");
        Files.createSymbolicLink(alias,base); var server=open(folder,true,null,null);
        try {
            ready(server,root);
            assertTrue(server.workspaceForTest().source(new SourceId(base.toUri().toString())).isPresent());
            Files.delete(alias);
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(alias.toUri().toString(),FileChangeType.Deleted))));
            ready(server,root);
            assertEquals(2,rename(server,root).getDocumentChanges().size());
            String updated=BASE+"\n!! surviving primary"; Files.writeString(base,updated);
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(base.toUri().toString(),FileChangeType.Changed))));
            ready(server,root);
            assertEquals(updated,server.workspaceForTest().source(new SourceId(base.toUri().toString())).orElseThrow().text());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void unknownOpenAliasesCannotCreateASecondSourceOrReplaceTheFirstClientBuffer() throws Exception {
        for(boolean hardlink:new boolean[]{false,true}) {
            Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili"),alias=folder.resolve("AliasRoot.ili");
            if(hardlink) Files.createLink(alias,root); else Files.createSymbolicLink(alias,root);
            var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
            server.initialize(new InitializeParams()).get(5,TimeUnit.SECONDS);
            try {
                for(Path file:List.of(base,root,alias)) server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                        new TextDocumentItem(file.toUri().toString(),"interlis",1,Files.readString(file)+(file.equals(root)?"\n!! authoritative unsaved":""))));
                // No sleep: the request must wait for identity IO and ordered notifications.
                ready(server,root);
                assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
                assertTrue(server.workspaceForTest().source(new SourceId(root.toUri().toString())).orElseThrow().text().contains("authoritative unsaved"));
                assertEquals(2,rename(server,root).getChanges().size());
            } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test void aSurvivingHardlinkBecomesTheClosedEditAddressWhenItsPrimaryDisappears() throws Exception {
        Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili"),link=folder.resolve("HardBase.ili");
        Files.createLink(link,base); var server=open(folder,true,null,null);
        try {
            ready(server,root); Files.delete(base);
            server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(base.toUri().toString(),FileChangeType.Deleted))));
            ready(server,root);
            var edits=rename(server,root).getDocumentChanges(); assertEquals(2,edits.size());
            assertEquals(Set.of(root.toUri().toString(),link.toUri().toString()),edits.stream().map(edit -> edit.getLeft().getTextDocument().getUri()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void caseAliasesUsePhysicalIdentityOnHostsWhereBothSpellingsExist() throws Exception {
        Path folder=project(),root=folder.resolve("Root.ili"),alias=folder.resolve("ROOT.ili");
        var server=open(folder,true,null,null);
        try {
            ready(server,root);
            if(!Files.exists(alias)) Files.createLink(alias,root); // case-sensitive host control, same physical identity
            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(alias.toUri().toString(),"interlis",9,ROOT)));
            ready(server,root);
            assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
            assertTrue(server.workspaceForTest().source(new SourceId(root.toUri().toString())).orElseThrow().text().contains("unsaved 😀"));
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void equalFileBytesDoNotCoalesceDistinctPhysicalFiles() throws Exception {
        Path folder=project(); Files.writeString(folder.resolve("CopyBase.ili"),BASE);
        var server=open(folder,true,null,null);
        try {
            ready(server,folder.resolve("Root.ili"));
            assertEquals(3,server.workspaceForTest().state().sourceRevisions().size());
            var response=server.getWorkspaceService().executeCommand(new ExecuteCommandParams("interlis.compile",List.of())).get(5,TimeUnit.SECONDS);
            assertEquals("rejected",((Map<?,?>)response).get("outcome"));
            assertTrue(((List<?>)((Map<?,?>)response).get("reasons")).contains("AMBIGUOUS_INPUT"));
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void changesQueuedBehindIdentityLookupRetainClientOrderAndVersion() throws Exception {
        Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili");
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        server.initialize(new InitializeParams()).get(5,TimeUnit.SECONDS);
        try {
            for(Path file:List.of(base,root)) server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
                    new TextDocumentItem(file.toUri().toString(),"interlis",1,Files.readString(file))));
            String updated=ROOT+"\n!! queued change 😀";
            server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(root.toUri().toString(),2),List.of(new TextDocumentContentChangeEvent(updated))));
            ready(server,root);
            assertEquals(updated,server.workspaceForTest().source(new SourceId(root.toUri().toString())).orElseThrow().text());
            var edit=rename(server,root); assertEquals(2,edit.getChanges().size());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void primaryDeletionReportedThroughTheSurvivingAliasPreservesTheCoreIdentity() throws Exception {
        for(var kind:List.of(FileChangeType.Deleted,FileChangeType.Changed)) {
            Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili"),alias=folder.resolve("HardBase.ili");
            Files.createLink(alias,base);var server=open(folder,true,null,null);
            try {
                ready(server,root);Files.delete(base);
                server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(new FileEvent(alias.toUri().toString(),kind))));
                ready(server,root);
                assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
                assertTrue(server.workspaceForTest().source(new SourceId(base.toUri().toString())).isPresent());
                var edits=rename(server,root).getDocumentChanges();assertEquals(2,edits.size());
                assertTrue(edits.stream().anyMatch(edit -> edit.getLeft().getTextDocument().getUri().equals(alias.toUri().toString())));
            } finally {server.shutdown().get(5,TimeUnit.SECONDS);}
        }
    }

    @Test void aValidReopenThroughAnotherAliasRecoversRejectedOpenAndChangeGenerations() throws Exception {
        for(boolean reverse:List.of(false,true)) for(boolean change:List.of(false,true)) {
            Path folder=project(),base=folder.resolve("Base.ili"),root=folder.resolve("Root.ili"),alias=folder.resolve("HardRoot.ili");Files.createLink(alias,root);
            String rejected=(reverse?root:alias).toUri().toString(),accepted=(reverse?alias:root).toUri().toString();
            var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));server.initialize(new InitializeParams()).get(5,TimeUnit.SECONDS);
            try {
                server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(base.toUri().toString(),"interlis",1,BASE)));
                if(change) server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(rejected,"interlis",1,ROOT)));
                String excessive=ROOT+"\n!!"+"x".repeat(2_100_000);
                if(change) server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(rejected,2),List.of(new TextDocumentContentChangeEvent(excessive))));
                else server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(rejected,"interlis",2,excessive)));
                var failure=assertThrows(ExecutionException.class,() -> server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(rejected))).get(5,TimeUnit.SECONDS));
                assertEquals(ResponseErrorCode.ContentModified.getValue(),assertInstanceOf(ResponseErrorException.class,failure.getCause()).getResponseError().getCode());
                server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(accepted,"interlis",3,ROOT)));
                ready(server,Path.of(java.net.URI.create(accepted)));
                assertEquals(2,server.workspaceForTest().state().sourceRevisions().size());
                assertEquals("succeeded",((Map<?,?>)server.getWorkspaceService().executeCommand(new ExecuteCommandParams("interlis.compile",List.of())).get(5,TimeUnit.SECONDS)).get("outcome"));
                assertEquals(2,server.getTextDocumentService().rename(new RenameParams(new TextDocumentIdentifier(accepted),cursor(),"Renamed")).get(5,TimeUnit.SECONDS).getChanges().size());
            } finally {server.shutdown().get(5,TimeUnit.SECONDS);}
        }
    }

    @Test void aSmallTokenBudgetProducesResourceCancellationAndTheSameSessionRecovers() throws Exception {
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        var initialize=new InitializeParams();initialize.setInitializationOptions(com.google.gson.JsonParser.parseString("{\"analysisBudget\":{\"maxTokens\":1}}"));
        server.initialize(initialize).get(5,TimeUnit.SECONDS);
        String uri="memory:/small-token-budget";
        try {
            assertDoesNotThrow(() -> server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"interlis",1,BASE))));
            var failure=assertThrows(ExecutionException.class,() -> server.getWorkspaceService().executeCommand(new ExecuteCommandParams("interlis.compile",List.of())).get(5,TimeUnit.SECONDS));
            var response=assertInstanceOf(ResponseErrorException.class,failure.getCause());
            assertEquals(ResponseErrorCode.RequestCancelled.getValue(),response.getResponseError().getCode());
            assertTrue(response.getMessage().contains("RESOURCE_LIMIT"));assertNull(response.getResponseError().getData());
            server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(com.google.gson.JsonParser.parseString("{\"analysisBudget\":{\"maxTokens\":500000}}")));
            assertFalse(server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri))).get(5,TimeUnit.SECONDS).isEmpty());
            assertEquals("succeeded",((Map<?,?>)server.getWorkspaceService().executeCommand(new ExecuteCommandParams("interlis.compile",List.of())).get(5,TimeUnit.SECONDS)).get("outcome"));
        } finally {server.shutdown().get(5,TimeUnit.SECONDS);}
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
