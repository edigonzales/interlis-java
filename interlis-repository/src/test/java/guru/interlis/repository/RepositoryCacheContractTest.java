package guru.interlis.repository;

import com.sun.net.httpserver.*;
import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryCacheContractTest {
    @TempDir Path directory;
    HttpServer server;
    final AtomicInteger requests=new AtomicInteger(), bodies=new AtomicInteger();
    final List<String> conditions=Collections.synchronizedList(new ArrayList<>());
    static final CancellationToken TOKEN=CancellationToken.none();
    static final FetchPolicy ONLINE=new FetchPolicy(false, 16_777_216, Duration.ofSeconds(2), Duration.ofSeconds(2), java.util.Optional.of(Duration.ofSeconds(2)));
    static final FetchPolicy OFFLINE=new FetchPolicy(true, 16_777_216, Duration.ofSeconds(2), Duration.ofSeconds(2), java.util.Optional.of(Duration.ofSeconds(2)));
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    String serve(HttpHandler handler) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> { requests.incrementAndGet(); handler.handle(exchange); });server.start();
        return "http://127.0.0.1:"+server.getAddress().getPort()+"/";
    }
    void send(HttpExchange e,String body,String etag) throws java.io.IOException {
        if(etag!=null)e.getResponseHeaders().set("ETag",etag);
        conditions.add(Objects.toString(e.getRequestHeaders().getFirst("If-None-Match"),""));
        if(etag!=null && etag.equals(e.getRequestHeaders().getFirst("If-None-Match"))) { e.sendResponseHeaders(304,-1);e.close();return; }
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);bodies.incrementAndGet();e.sendResponseHeaders(200,bytes.length);
        try(var output=e.getResponseBody()) { output.write(bytes); }
    }
    HostRepositories.Access host() { return host(new ArrayList<>()); }
    HostRepositories.Access host(List<RepositoryCacheEvent> events) {
        return HostRepositories.create(directory,RepositoryCacheConfiguration.at(directory.resolve("cache")),events::add);
    }
    LoadedSource load(HostRepositories.Access access,String url,FetchPolicy policy) { return access.loader().load(new SourceLocator(url),policy,TOKEN); }
    Path metadata(String url) { return directory.resolve("cache/metadata-v1/"+ContentDigest.sha256(url.getBytes(StandardCharsets.UTF_8)).value()+".xml"); }

    @Test void persistsRevalidatesAcrossInstancesAndOfflineNeverConnects() throws Exception {
        var text=new AtomicReference<>("INTERLIS 2.4;\r\n!! ä");var etag=new AtomicReference<>("\"one\"");
        String url=serve(e -> send(e,text.get(),etag.get()))+"file.ili?key=secret";
        var first=load(host(),url,ONLINE);assertEquals(1,bodies.get());
        var events=new ArrayList<RepositoryCacheEvent>();assertEquals(first.text(),load(host(events),url,ONLINE).text());
        assertEquals(2,requests.get());assertEquals(1,bodies.get());assertTrue(events.contains(RepositoryCacheEvent.NOT_MODIFIED));
        text.set("new content");etag.set("\"two\"");load(host(),url,ONLINE);assertEquals(2,bodies.get());
        assertTrue(host().contentStore().lookup(new ContentDigest("sha256",first.byteSha256())).isPresent());
        int before=requests.get();assertEquals("new content",load(host(),url,OFFLINE).text());assertEquals(before,requests.get());
        server.stop(0);server=null;
        assertEquals("new content",load(host(),url,OFFLINE).text());
        assertThrows(SourceLoadException.class,()->load(host(),url,ONLINE));
        assertEquals(SourceLoadException.Reason.OFFLINE,assertThrows(SourceLoadException.class,()->load(host(),url+"different",OFFLINE)).reason());
    }

    @Test void corruptBody304RetriesUnconditionallyAndCorruptMetadataIsAMiss() throws Exception {
        String url=serve(e -> send(e,"original","\"one\""))+"file";
        var first=load(host(),url,ONLINE);
        Path body=directory.resolve("cache").resolve(first.byteSha256());Files.writeString(body,"corrupt");
        assertEquals(SourceLoadException.Reason.OFFLINE,assertThrows(SourceLoadException.class,()->load(host(),url,OFFLINE)).reason());
        assertEquals("original",load(host(),url,ONLINE).text());
        assertEquals(List.of("","\"one\"",""),conditions);assertEquals(2,bodies.get());
        Files.writeString(metadata(url),"<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///private/unknown'>]><interlis-cache>&e;</interlis-cache>");
        assertThrows(SourceLoadException.class,()->load(host(),url,OFFLINE));
        assertEquals("original",load(host(),url,ONLINE).text());assertEquals("",conditions.getLast());
        String xml=Files.readString(metadata(url));
        Files.writeString(metadata(url),xml.replace("etag=\"&quot;one&quot;\"","etag=\"&#256;\""));
        assertThrows(SourceLoadException.class,()->load(host(),url,OFFLINE));
        assertEquals("original",load(host(),url,ONLINE).text());assertEquals("",conditions.getLast());
        Files.writeString(metadata(url),xml.replace("version=\"1\"","version=\"99\""));
        assertThrows(SourceLoadException.class,()->load(host(),url,OFFLINE));
    }

    @Test void lastModifiedNoValidatorsRedirectsAndUnsupportedVariants() throws Exception {
        var modifiedHeaders=new ArrayList<String>();
        String base=serve(e -> {
            switch(e.getRequestURI().getPath()) {
                case "/redirect" -> { assertNull(e.getRequestHeaders().getFirst("If-None-Match"));e.getResponseHeaders().set("Location","/target");e.sendResponseHeaders(302,-1);e.close(); }
                case "/target" -> send(e,"target","\"t\"");
                case "/modified" -> {
                    String since=e.getRequestHeaders().getFirst("If-Modified-Since");modifiedHeaders.add(since);
                    e.getResponseHeaders().set("Last-Modified","Wed, 21 Oct 2015 07:28:00 GMT");
                    if(since!=null) {e.sendResponseHeaders(304,-1);e.close();}else send(e,"date",null);
                }
                case "/no-store" -> {e.getResponseHeaders().set("Cache-Control","no-store");send(e,"private","\"p\"");}
                case "/vary" -> {e.getResponseHeaders().set("Vary","Accept-Language");send(e,"variant","\"v\"");}
                default -> send(e,"plain",null);
            }
        });
        for(String path:List.of("redirect","modified","plain")) { load(host(),base+path,ONLINE);load(host(),base+path,ONLINE); }
        assertEquals(Arrays.asList(null,null),modifiedHeaders,"Last-Modified does not prove byte identity and must not cause conditional reuse");
        assertEquals("target",load(host(),base+"redirect",OFFLINE).text());
        for(String path:List.of("no-store","vary")) {
            load(host(),base+path,ONLINE);assertFalse(Files.exists(metadata(base+path)));
            assertThrows(SourceLoadException.class,()->load(host(),base+path,OFFLINE));
        }
    }

    @Test void unavailableWritesWarnOnceDisabledCacheDoesNoDiskIOAndLimitsStillApply() throws Exception {
        String url=serve(e -> send(e,"some text","\"tag\""));
        Files.writeString(directory.resolve("blocked"),"file");var events=new ArrayList<RepositoryCacheEvent>();
        var access=HostRepositories.create(directory,RepositoryCacheConfiguration.at(directory.resolve("blocked")),events::add);
        assertEquals("some text",load(access,url,ONLINE).text());load(access,url,ONLINE);
        assertEquals(1,events.stream().filter(e -> e==RepositoryCacheEvent.WRITE_UNAVAILABLE).count());
        var disabled=HostRepositories.create(directory,RepositoryCacheConfiguration.disabled(),event -> { });
        load(disabled,url,ONLINE);assertFalse(Files.exists(directory.resolve("cache")));assertThrows(SourceLoadException.class,()->load(disabled,url,OFFLINE));
        load(host(),url,ONLINE);
        assertEquals(SourceLoadException.Reason.TOO_LARGE,assertThrows(SourceLoadException.class,()->load(host(),url,new FetchPolicy(true, 1, Duration.ofSeconds(2), Duration.ofSeconds(2), java.util.Optional.of(Duration.ofSeconds(2))))).reason());
        assertThrows(OperationCancelledException.class,()->host().loader().load(new SourceLocator(url),ONLINE,()->true));
    }

    @Test void parallelWritersPublishOnlyCompleteMatchingEntries() throws Exception {
        String url=serve(e -> send(e,"shared bytes","\"tag\""));
        try(var pool=Executors.newFixedThreadPool(4)) {
            var futures=new ArrayList<Future<LoadedSource>>();
            for(int i=0;i<12;i++)futures.add(pool.submit(()->load(host(),url,ONLINE)));
            for(var future:futures)assertEquals("shared bytes",future.get(10,TimeUnit.SECONDS).text());
        }
        assertEquals("shared bytes",load(host(),url,OFFLINE).text());
        try(var files=Files.walk(directory.resolve("cache"))) { assertTrue(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp"))); }
    }

    String model(String name,String imports) { return "INTERLIS 2.4; MODEL "+name+" AT \"https://example.org\" VERSION \"1\" = "+imports+" END "+name+"."; }
    String index(String name) { return "<TRANSFER><ModelMetadata><Name>"+name+"</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>"+name+".ili</File></ModelMetadata></TRANSFER>"; }
    RepositoryResolutionOutcome resolve(HostRepositories.Access access,List<String> bases,boolean offline,Optional<ResolutionLock> lock) {
        try(var workspace=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults())) {
            workspace.update(new SourceUpdate(new SourceId("memory:/root.ili"),new SourceExpectation.Absent(),new SourceChange.Replace(model("Root","IMPORTS Dep;"))));
            var config=new RepositoryConfig(bases.stream().map(b->new RepositorySource(new RepositoryBase(URI.create(b)),new SourceLocator(b+"ilimodels.xml"))).toList(),offline?OFFLINE:ONLINE);
            return access.closureService().resolve(workspace,new AnalysisRequest(workspace.currentRevision(),List.of(new ModelSelector.ByName("Root",Optional.empty(),Optional.empty()))),
                    new RepositoryResolveOptions(config,Map.of(),Map.of(),lock),TOKEN);
        }
    }

    @Test void offlineDiscoveryUsesConfiguredRepositoriesAndLockPinsRetainOldBytes() throws Exception {
        var content=new AtomicReference<>(model("Dep",""));
        String base=serve(e -> {
            if(e.getRequestURI().getPath().endsWith("ilimodels.xml"))send(e,index("Dep"),"\"idx\"");
            else if(e.getRequestURI().getPath().endsWith("Dep.ili"))send(e,content.get(),"\""+content.get().hashCode()+"\"");
            else {e.sendResponseHeaders(404,-1);e.close();}
        });
        var first=assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve(host(),List.of(base),false,Optional.empty()));
        int before=requests.get();
        assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve(host(),List.of(base),true,Optional.empty()));assertEquals(before,requests.get());
        assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve(host(),List.of(base+"uncached/",base),true,Optional.empty()));assertEquals(before,requests.get());
        content.set(model("Dep","DOMAIN D=TEXT;"));
        assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve(host(),List.of(base),false,Optional.empty()));
        var replay=assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve(host(),List.of(base),true,Optional.of(first.update().lock())));
        assertEquals(first.update().lock(),replay.update().lock());
        var mismatch=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve(host(),List.of(base),false,Optional.of(first.update().lock())));
        assertEquals(RepositoryProblem.Reason.LOCK_MISMATCH,mismatch.problems().getFirst().reason());
        var latest=load(host(),base+"Dep.ili",OFFLINE);Files.delete(directory.resolve("cache").resolve(latest.byteSha256()));
        var missing=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve(host(),List.of(base),true,Optional.empty())).problems().getFirst();
        assertEquals(SourceLoadException.Reason.OFFLINE,missing.loadReason().orElseThrow());assertEquals(base+"Dep.ili",missing.source().orElseThrow().value());
        assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve(host(),List.of(base+"unrelated/"),true,Optional.empty()));
        var indexDigest=first.update().lock().indexes().get(new SourceId(base+"ilimodels.xml"));
        Files.delete(directory.resolve("cache").resolve(indexDigest.value()));
        var missingIndex=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,
                resolve(host(),List.of(base),true,Optional.of(first.update().lock()))).problems().getFirst();
        assertEquals(SourceLoadException.Reason.OFFLINE,missingIndex.loadReason().orElseThrow());
        assertEquals(base+"ilimodels.xml",missingIndex.source().orElseThrow().value());
    }

    @Test void closureNeverPersistsNoStoreModelBytes() throws Exception {
        String dep=model("Dep","");
        String base=serve(e -> {
            if(e.getRequestURI().getPath().endsWith("ilimodels.xml"))send(e,index("Dep"),"\"idx\"");
            else {e.getResponseHeaders().set("Cache-Control","no-store");send(e,dep,"\"dep\"");}
        });
        assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve(host(),List.of(base),false,Optional.empty()));
        assertFalse(Files.exists(directory.resolve("cache").resolve(ContentDigest.sha256(dep.getBytes(StandardCharsets.UTF_8)).value())));
        assertFalse(Files.exists(metadata(base+"Dep.ili")));
        assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve(host(),List.of(base),true,Optional.empty()));
    }

    @Test void legacyFunctionalLoadersAreNeverInvokedForUnpinnedOfflineRequests() {
        var calls=new AtomicInteger();
        SourceLoader provider=(locator,policy,token)-> {calls.incrementAndGet();throw new AssertionError("live provider invoked offline");};
        var access=new HostRepositories.Access(provider,new InMemoryContentStore(),event -> { });
        assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve(access,List.of("https://example.org/"),true,Optional.empty()));
        assertEquals(0,calls.get());
    }

    @Test void conditionalFinalCheckDetectsChangesAndNoStoreIsNotPersistedByClosure() throws Exception {
        var depReads=new AtomicInteger();var noStore=new AtomicBoolean();
        String base=serve(e -> {
            if(e.getRequestURI().getPath().endsWith("ilimodels.xml"))send(e,index("Dep"),"\"idx\"");
            else {
                int read=depReads.incrementAndGet();
                if(noStore.get())e.getResponseHeaders().set("Cache-Control","no-store");
                send(e,model("Dep",read==1?"":"DOMAIN D=TEXT;"),"\""+read+"\"");
            }
        });
        var changed=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve(host(),List.of(base),false,Optional.empty()));
        assertEquals(RepositoryProblem.Reason.CHANGED_SOURCE,changed.problems().getFirst().reason());
        noStore.set(true);load(host(),base+"Dep.ili",ONLINE);assertFalse(Files.exists(metadata(base+"Dep.ili")));
    }
}
