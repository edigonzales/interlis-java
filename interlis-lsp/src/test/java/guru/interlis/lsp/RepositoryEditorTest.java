package guru.interlis.lsp;

import com.google.gson.Gson;
import com.sun.net.httpserver.*;
import guru.interlis.compiler.*;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryEditorTest {
    @TempDir Path directory;
    HttpServer http;
    volatile CountDownLatch indexEntered, releaseIndex;
    final AtomicInteger requests=new AtomicInteger(),bodies=new AtomicInteger();
    static final String ROOT="INTERLIS 2.4;\nMODEL Root AT \"x\" VERSION \"1\" =\nIMPORTS Dep;\nTOPIC T= CLASS C= item:Dep.Value; END C; END T;\nEND Root.\n";
    static final String DEP="INTERLIS 2.4;\nMODEL Dep AT \"x\" VERSION \"1\" =\nIMPORTS Base;\nDOMAIN Value=0..10;\nTOPIC T= CLASS C= item:Base.Count; END C; END T;\nEND Dep.\n";
    static final String BASE="INTERLIS 2.4;\nMODEL Base AT \"x\" VERSION \"1\" =\nDOMAIN Count=0..10;\nEND Base.\n";
    static final String URI="untitled:Root.ili";
    @AfterEach void stopHttp() { if(http!=null)http.stop(0); }
    String serve() throws Exception {
        return serve(DEP);
    }
    private String serve(String dependency) throws Exception {
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String index="<TRANSFER>"+List.of("Dep","Base").stream().map(n -> "<ModelMetadata><Name>"+n+"</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>"+n+".ili</File></ModelMetadata>").reduce("",String::concat)+"</TRANSFER>";
        http.createContext("/",e -> {
            if (releaseIndex != null && e.getRequestURI().getPath().equals("/ilimodels.xml")) {
                indexEntered.countDown();
                try { if (!releaseIndex.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("controlled index release timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
            }
            requests.incrementAndGet();String body=switch(e.getRequestURI().getPath()) {case "/ilimodels.xml" -> index;case "/Dep.ili" -> dependency;case "/Base.ili" -> BASE;default -> null;};
            if(body==null) {e.sendResponseHeaders(404,-1);e.close();return;}
            e.getResponseHeaders().set("ETag","\"frozen\"");
            if("\"frozen\"".equals(e.getRequestHeaders().getFirst("If-None-Match"))) {e.sendResponseHeaders(304,-1);e.close();return;}
            bodies.incrementAndGet();byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()) {out.write(bytes);}
        });http.start();return "http://127.0.0.1:"+http.getAddress().getPort()+"/";
    }
    InterlisLanguageServer server(String base,boolean offline,Path cache) throws Exception {
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        var init=new InitializeParams();init.setInitializationOptions(Map.of("repositories",base.isEmpty()?List.of():List.of(base),"offline",offline,"cacheDirectory",cache.toString()));
        server.initialize(init).get(5,TimeUnit.SECONDS);return server;
    }
    void open(InterlisLanguageServer s,String uri,int version,String text) { s.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"interlis",version,text))); }
    List<? extends Location> definitions(InterlisLanguageServer s,String uri,int line,int col) throws Exception { var result=s.getTextDocumentService().definition(new DefinitionParams(new TextDocumentIdentifier(uri),new Position(line,col))).get(10,TimeUnit.SECONDS).getLeft(); assertFalse(result.isEmpty(),() -> s.workspaceForTest().analyze(new AnalysisRequest(s.workspaceForTest().currentRevision(),List.of(new ModelSelector.BySource(new SourceId(uri)))),guru.interlis.execution.CancellationToken.none()).diagnostics().toString());return result; }
    CompileReport compile(InterlisLanguageServer s,String uri,int version) throws Exception { return s.compileModel(new CompileModelParams("current",new VersionedTextDocumentIdentifier(uri,version),null,null)).get(10,TimeUnit.SECONDS); }


    @Test void repositoryBatchAndConfigurationStayUnchangedWhenOnlyOneHandleFits() throws Exception {
        String base = serve();
        var engine = CompilerEngines.create();
        var workspace = Workspaces.create(engine, WorkspaceConfiguration.defaults());
        var handles = new ExternalSourceRegistry(1);
        var server = new InterlisLanguageServer(workspace, engine, CompilerServices.defaults(), handles);
        var init = new InitializeParams();
        init.setInitializationOptions(Map.of("repositories", List.of(base),
                "cacheDirectory", directory.resolve("batch-cache").toString()));
        server.initialize(init).get(5, TimeUnit.SECONDS);
        try {
            var configuration = workspace.configuration();
            var dep = new SourceId(base + "Dep.ili");
            var transitive = new SourceId(base + "Base.ili");
            open(server, URI, 1, ROOT);
            var docs = server.textDocumentsForTest();
            var outline = new DocumentSymbolParams(new TextDocumentIdentifier(URI));
            for (var request : List.<java.util.function.Supplier<CompletableFuture<?>>>of(
                    () -> docs.documentSymbol(outline), docs::compileWorkspace)) {
                var failure = assertThrows(ExecutionException.class,
                        () -> request.get().get(10, TimeUnit.SECONDS));
                var response = assertInstanceOf(ResponseErrorException.class, failure.getCause());
                assertEquals(ResponseErrorCode.RequestFailed.getValue(), response.getResponseError().getCode());
                assertTrue(response.getMessage().contains("readonly source registry limit"));
                assertEquals(Set.of(new SourceId(URI)), workspace.state().sourceRevisions().keySet());
                assertEquals(configuration, workspace.configuration());
                assertFalse(handles.contains(dep));
                assertFalse(handles.contains(transitive));
            }
            // A smaller request now succeeds using exactly the capacity the rejected batch left intact.
            String updated = ROOT.replace("IMPORTS Dep;", "IMPORTS Base;").replace("Dep.Value", "Base.Count");
            server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                    new VersionedTextDocumentIdentifier(URI, 2), List.of(new TextDocumentContentChangeEvent(updated))));
            var target = definitions(server, URI, 3, ROOT.split("\n")[3].indexOf("Value")).getFirst();
            assertTrue(target.getUri().startsWith("interlis-source:/"));
            String revision = Long.toString(workspace.source(transitive).orElseThrow().revision().value());
            assertEquals(BASE, server.sourceContent(new SourceContentParams(target.getUri(), revision))
                    .get(5, TimeUnit.SECONDS).text());
            assertFalse(handles.contains(dep));
        } finally {
            server.shutdown().get(5, TimeUnit.SECONDS);
        }
    }

    @Test void repeatedIdenticalConfigurationDoesNotCancelInitialRepositoryAnalysis() throws Exception {
        String base = serve();
        indexEntered = new CountDownLatch(1);
        releaseIndex = new CountDownLatch(1);
        var ready = new CountDownLatch(1);
        var s = server(base, false, directory.resolve("cache"));
        s.textDocumentsForTest().analysisReadySink(event -> {
            if (Integer.valueOf(7).equals(event.documentVersions().get(URI))) ready.countDown();
        });
        try {
            open(s, URI, 7, ROOT);
            assertTrue(indexEntered.await(5, TimeUnit.SECONDS), "initial repository load never started");
            var revision = s.workspaceForTest().currentRevision();
            // The real client repeats its unchanged initialization settings after didOpen.
            s.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(Map.of("offline", false)));
            assertEquals(revision, s.workspaceForTest().currentRevision(), "identical settings must not change the workspace");
            releaseIndex.countDown();
            assertTrue(ready.await(5, TimeUnit.SECONDS), "identical configuration cancelled the only accepted analysis");
            assertFalse(definitions(s, URI, 3, ROOT.split("\n")[3].indexOf("Value")).isEmpty());
        } finally { releaseIndex.countDown(); s.shutdown().get(5, TimeUnit.SECONDS); }
    }

    @Test void transitiveSourcesRemainReadonlyAfterOpenAndPersistOfflineAcrossServerInstances() throws Exception {
        String base=serve();Path cache=directory.resolve("cache");
        for(boolean offline:List.of(false,false,true)) {
            int before=requests.get(),beforeBodies=bodies.get();
            var s=server(base,offline,cache);
            try {
                open(s,URI,7,ROOT);
                var target=definitions(s,URI,3,ROOT.split("\n")[3].indexOf("Value")).getFirst();
                assertTrue(target.getUri().startsWith("interlis-source:/"));assertEquals(new Position(3,7),target.getRange().getStart());
                String revision=java.net.URI.create(target.getUri()).getQuery();
                var content=s.sourceContent(new SourceContentParams(target.getUri(),revision)).get(5,TimeUnit.SECONDS);
                assertEquals(DEP,content.text());assertTrue(content.readonly());assertEquals("SHA-256/UTF-16BE",content.digestAlgorithm());
                assertEquals(new SourceText(new SourceId("memory:/check"),SourceRevision.initial(),DEP).contentFingerprint().value(),content.digest());
                open(s,target.getUri(),1,content.text());
                var transitive=definitions(s,target.getUri(),4,DEP.split("\n")[4].indexOf("Count")).getFirst();assertTrue(transitive.getUri().startsWith("interlis-source:/"));
                assertEquals(BASE,s.sourceContent(new SourceContentParams(transitive.getUri(),java.net.URI.create(transitive.getUri()).getQuery())).get().text());
                assertThrows(ExecutionException.class,()->s.getTextDocumentService().rename(new RenameParams(new TextDocumentIdentifier(URI),new Position(3,ROOT.split("\n")[3].indexOf("Value")),"Renamed")).get(5,TimeUnit.SECONDS));
                assertThrows(ExecutionException.class,()->s.getTextDocumentService().formatting(new DocumentFormattingParams(new TextDocumentIdentifier(target.getUri()),new FormattingOptions(2,true))).get(5,TimeUnit.SECONDS));
                s.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(target.getUri(),2),List.of(new TextDocumentContentChangeEvent("tampered"))));
                assertEquals(DEP,s.sourceContent(new SourceContentParams(target.getUri(),revision)).get().text());
                assertEquals("succeeded",compile(s,URI,7).outcome());
                assertThrows(ExecutionException.class,()->s.sourceContent(new SourceContentParams(target.getUri(),"999")).get());
                assertThrows(ExecutionException.class,()->s.sourceContent(new SourceContentParams(base+"Dep.ili",revision)).get());
                if(offline) assertEquals(before,requests.get());
                else if(beforeBodies>0) { assertEquals(before,requests.get(),"fresh application cache must avoid revalidation requests");assertEquals(beforeBodies,bodies.get()); }
                else assertTrue(bodies.get()>=3,"cold resolution must fetch the index and both transitive models");
            } finally { s.shutdown().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test void localDirtySourcesWinAndCurrentCompileExcludesBrokenNeighbour() throws Exception {
        String base=serve();var s=server(base,false,directory.resolve("cache"));
        try {
            // Avoid an intentionally incomplete intermediate inventory: Dep imports Base.
            // The zero-network assertion applies once all local closure members exist.
            open(s,"untitled:Base.ili",3,BASE+"!! dirty 😀\n");open(s,"untitled:Dep.ili",2,DEP);
            open(s,"untitled:Broken.ili",1,"INTERLIS 2.4; MODEL Broken AT \"x\" VERSION \"1\" = DOMAIN Invalid=10..0; END Broken.");
            open(s,URI,9,ROOT);
            assertEquals("untitled:Dep.ili",definitions(s,URI,3,ROOT.split("\n")[3].indexOf("Value")).getFirst().getUri());
            var rootReport=compile(s,URI,9);assertEquals("succeeded",rootReport.outcome());assertTrue(rootReport.summary().diagnostics().isEmpty());assertEquals(0,requests.get());
            assertEquals("rejected",s.compileModel(new CompileModelParams("workspace",null,null,null)).get(10,TimeUnit.SECONDS).outcome());
            assertThrows(ExecutionException.class,()->compile(s,URI,8));
        } finally { s.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void offlineMissRetainsSpecificDiagnosticAndInvalidConfigurationIsAtomic() throws Exception {
        var s=server("http://127.0.0.1:1/",true,directory.resolve("empty"));
        var published=new CopyOnWriteArrayList<PublishDiagnosticsParams>();
        s.textDocumentsForTest().sink(published::add);
        try {
            open(s,URI,4,ROOT);s.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(10,TimeUnit.SECONDS);
            assertTrue(published.stream().filter(p->URI.equals(p.getUri())).flatMap(p->p.getDiagnostics().stream()).anyMatch(d->d.getMessage().contains("offline") || d.getMessage().contains("Offline")),published.toString());
            var previous=s.workspaceForTest().configuration();
            s.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(Map.of("diagnosticLanguage","de","warningPolicy","REJECT_WARNINGS","repositories",List.of("https://user:secret@example.org/"))));
            assertEquals(previous,s.workspaceForTest().configuration());
            assertThrows(ExecutionException.class,()->compile(s,URI,4));
        } finally { s.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void outlineUsesSemanticOwnersAndLocalizedRelatedDiagnosticsUseExactCrlfRanges() throws Exception {
        String text="INTERLIS 2.4;\r\n!! ä 😀\r\nMODEL M AT \"x\" VERSION \"1\" =\r\nTOPIC T =\r\nCLASS A =\r\nv:TEXT*10;\r\nEND A;\r\nCLASS A =\r\nw:TEXT*10;\r\nEND A;\r\nEND T;\r\nEND M.\r\n";
        var s=server("",false,directory.resolve("cache"));var publications=new CopyOnWriteArrayList<PublishDiagnosticsParams>();s.textDocumentsForTest().sink(publications::add);
        try {
            s.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(Map.of("diagnosticLanguage","de")));
            open(s,URI,8,text);
            var outline=s.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS);
            assertEquals(1,outline.size());var model=outline.getFirst().getRight();assertEquals("M",model.getName());
            var topic=model.getChildren().getFirst();assertEquals("T",topic.getName());assertEquals("A",topic.getChildren().getFirst().getName());assertEquals("v",topic.getChildren().getFirst().getChildren().getFirst().getName());
            var diagnosis=publications.stream().filter(p->Integer.valueOf(8).equals(p.getVersion())).flatMap(p->p.getDiagnostics().stream()).filter(d->!d.getRelatedInformation().isEmpty()).findFirst().orElseThrow();
            assertFalse(diagnosis.getMessage().contains("diagnostic."));assertTrue(diagnosis.getMessage().contains("Name") || diagnosis.getMessage().contains("Typname"));assertEquals(7,diagnosis.getRange().getStart().getLine());assertEquals(4,diagnosis.getRelatedInformation().getFirst().getLocation().getRange().getStart().getLine());
            s.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(Map.of("diagnosticLanguage","en")));
            s.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS);
            assertTrue(publications.getLast().getDiagnostics().stream().anyMatch(d->d.getMessage().contains("name")));
        } finally { s.shutdown().get(5,TimeUnit.SECONDS); }
    }
    @Test void reconfigurationDropsOldRemoteAuthorityAndLocalOverridesKeepPriority() throws Exception {
        String base=serve();var s=server(base,false,directory.resolve("cache"));
        try {
            open(s,URI,1,ROOT);var remote=definitions(s,URI,3,ROOT.split("\n")[3].indexOf("Value")).getFirst();
            var opened=s.sourceContent(new SourceContentParams(remote.getUri(),java.net.URI.create(remote.getUri()).getQuery())).get();
            open(s,remote.getUri(),1,opened.text());
            int online=requests.get();
            s.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(Map.of("offline",true)));
            assertEquals("succeeded",compile(s,URI,1).outcome());assertEquals(online,requests.get());
            var refreshed=definitions(s,URI,3,ROOT.split("\n")[3].indexOf("Value")).getFirst();
            var freshText=s.sourceContent(new SourceContentParams(refreshed.getUri(),java.net.URI.create(refreshed.getUri()).getQuery())).get();
            open(s,refreshed.getUri(),2,freshText.text());
            assertFalse(definitions(s,refreshed.getUri(),4,DEP.split("\n")[4].indexOf("Count")).isEmpty());
            assertThrows(ExecutionException.class,()->s.sourceContent(new SourceContentParams(remote.getUri(),java.net.URI.create(remote.getUri()).getQuery())).get());
            open(s,"untitled:local-dependency.ili",5,DEP+"!! dirty local override\n");
            assertEquals("untitled:local-dependency.ili",definitions(s,URI,3,ROOT.split("\n")[3].indexOf("Value")).getFirst().getUri());
            assertEquals("succeeded",compile(s,URI,1).outcome());
        } finally { s.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void remoteDiagnosticsFollowOpenVersionsAndClearWhenTheHandleExpires() throws Exception {
        String duplicate = "STRUCTURE Duplicate = END Duplicate;";
        String dependency = DEP.replace("DOMAIN Value=0..10;\n",
                "DOMAIN Value=0..10;\n" + duplicate + "\n" + duplicate + "\n").replace("\n", "\r\n");
        var s = server(serve(dependency), false, directory.resolve("cache"));
        var publications = new CopyOnWriteArrayList<PublishDiagnosticsParams>();
        s.textDocumentsForTest().sink(publications::add);
        try {
            open(s, URI, 7, ROOT);
            String remoteUri = definitions(s, URI, 3, ROOT.split("\n")[3].indexOf("Value")).getFirst().getUri();
            assertTrue(remoteUri.startsWith("interlis-source:/"));
            String revision = java.net.URI.create(remoteUri).getQuery();
            var content = s.sourceContent(new SourceContentParams(remoteUri, revision)).get(5, TimeUnit.SECONDS);
            assertEquals(dependency, content.text());
            assertTrue(content.readonly());
            var closed = publications.stream().filter(p -> remoteUri.equals(p.getUri())).findFirst().orElseThrow();
            assertNull(closed.getVersion());
            var diagnostic = closed.getDiagnostics().stream()
                    .filter(d -> !d.getRelatedInformation().isEmpty()).findFirst().orElseThrow();
            assertEquals(new Range(new Position(5, 0), new Position(5, duplicate.length())), diagnostic.getRange());
            assertEquals(new Location(remoteUri, new Range(new Position(4, 10), new Position(4, 19))),
                    diagnostic.getRelatedInformation().getFirst().getLocation());

            var service = s.getTextDocumentService();
            var remote = new TextDocumentIdentifier(remoteUri);
            open(s, remoteUri, 11, content.text());
            service.documentSymbol(new DocumentSymbolParams(remote)).get(5, TimeUnit.SECONDS);
            var opened = publications.stream().filter(p -> remoteUri.equals(p.getUri())
                    && Integer.valueOf(11).equals(p.getVersion())).findFirst().orElseThrow();
            assertTrue(opened.getDiagnostics().contains(diagnostic));

            int beforeClose = publications.size();
            service.didClose(new DidCloseTextDocumentParams(remote));
            var rootOutline = new DocumentSymbolParams(new TextDocumentIdentifier(URI));
            service.documentSymbol(rootOutline).get(5, TimeUnit.SECONDS);
            var afterClose = publications.stream().skip(beforeClose).filter(p -> remoteUri.equals(p.getUri())).toList();
            assertTrue(afterClose.getFirst().getDiagnostics().isEmpty());
            assertTrue(afterClose.stream().allMatch(p -> p.getVersion() == null));
            assertTrue(afterClose.getLast().getDiagnostics().contains(diagnostic));

            int beforeConfiguration = publications.size();
            s.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(
                    Map.of("offline", true, "analysisBudget", Map.of("maxTokens", 1))));
            var invalidated = publications.stream().skip(beforeConfiguration)
                    .filter(p -> remoteUri.equals(p.getUri())).toList();
            assertEquals(1, invalidated.size());
            assertNull(invalidated.getFirst().getVersion());
            assertTrue(invalidated.getFirst().getDiagnostics().isEmpty());
            var unavailable = assertThrows(ExecutionException.class,
                    () -> service.documentSymbol(rootOutline).get(5, TimeUnit.SECONDS));
            assertEquals(ResponseErrorCode.RequestCancelled.getValue(),
                    assertInstanceOf(ResponseErrorException.class, unavailable.getCause()).getResponseError().getCode());
            assertThrows(ExecutionException.class, () ->
                    s.sourceContent(new SourceContentParams(remoteUri, revision)).get(5, TimeUnit.SECONDS));
        } finally { s.shutdown().get(5, TimeUnit.SECONDS); }
    }

    @Test void multipleModelsRequireExplicitSelectionAndExactPinsAreCheckedForLoadedSources() throws Exception {
        var s=server("",false,directory.resolve("cache"));
        String valid="INTERLIS 2.4; MODEL Good AT \"x\" VERSION \"1\" = END Good.\n";
        String text=valid+"MODEL Broken AT \"x\" VERSION \"1\" = DOMAIN Bad=10..0; END Broken.\n";
        try {
            open(s,URI,2,text);var selected=compile(s,URI,2);
            assertEquals("selectionRequired",selected.outcome());assertEquals(List.of("Good","Broken"),selected.modelChoices().stream().map(CompileReport.ModelChoice::modelName).toList());
            assertEquals("succeeded",s.compileModel(new CompileModelParams("current",new VersionedTextDocumentIdentifier(URI,2),"Good","1")).get(5,TimeUnit.SECONDS).outcome());
            var broken=s.compileModel(new CompileModelParams("current",new VersionedTextDocumentIdentifier(URI,2),"Broken","1")).get(5,TimeUnit.SECONDS);
            assertEquals("rejected",broken.outcome());assertFalse(broken.summary().diagnostics().isEmpty());
            assertTrue(broken.summary().diagnostics().stream().allMatch(d -> !d.message().contains("diagnostic.") && d.range().size()==2 && d.uri().equals(URI)));
            s.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(Map.of("sourcePins",List.of(Map.of("sourceId",URI,"sha256","0".repeat(64))))));
            assertThrows(ExecutionException.class,()->s.compileModel(new CompileModelParams("current",new VersionedTextDocumentIdentifier(URI,2),"Good","1")).get(5,TimeUnit.SECONDS));
        } finally { s.shutdown().get(5,TimeUnit.SECONDS); }
    }

}
