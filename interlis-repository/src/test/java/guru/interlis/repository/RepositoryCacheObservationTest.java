package guru.interlis.repository;

import guru.interlis.source.*;
import guru.interlis.workspace.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.repository.RepositoryCacheContractTest.ONLINE;
import static guru.interlis.repository.RepositoryCacheContractTest.OFFLINE;
import static guru.interlis.repository.RepositoryClosureContractTest.*;

class RepositoryCacheObservationTest {
    @TempDir Path directory;
    final RepositoryCacheContractTest fixture=new RepositoryCacheContractTest();
    @BeforeEach void setup() { fixture.directory=directory; }
    @AfterEach void stop() { fixture.stop(); }
    HostRepositories.Access host(List<RepositoryCacheObservation> events) {
        return HostRepositories.createObserved(directory,RepositoryCacheConfiguration.at(directory.resolve("cache")),events::add);
    }
    List<RepositoryCacheEvent> kinds(List<RepositoryCacheObservation> observations) {return observations.stream().map(RepositoryCacheObservation::event).toList();}
    @Test void actualHttpAndOfflineAccessesCarrySanitizedSourcesAndLegacyCallbacksAreNotDuplicated() throws Exception {
        String url=fixture.serve(e -> fixture.send(e,"text","\"one\""))+"model.ili?token=secret#fragment";
        var events=new ArrayList<RepositoryCacheObservation>();var access=host(events);
        fixture.load(access,url,ONLINE);fixture.load(access,url,ONLINE);int requests=fixture.requests.get();fixture.load(access,url,OFFLINE);
        assertEquals(requests,fixture.requests.get());
        assertEquals(List.of(RepositoryCacheEvent.DOWNLOAD,RepositoryCacheEvent.FRESH_HIT,RepositoryCacheEvent.OFFLINE_HIT),kinds(events));
        for(var event:events)assertEquals(url.substring(0,url.indexOf('?')),event.safeSource().orElseThrow());
        var legacy=new ArrayList<RepositoryCacheEvent>();fixture.load(fixture.host(legacy),url,ONLINE);
        assertEquals(List.of(RepositoryCacheEvent.FRESH_HIT),legacy);
    }
    @Test void corruptBytesGiveMissThenDownloadAndNeverAFalseHit() throws Exception {
        String url=fixture.serve(e -> fixture.send(e,"original","\"one\""))+"model.ili";
        var events=new ArrayList<RepositoryCacheObservation>();var access=host(events);var loaded=fixture.load(access,url,ONLINE);
        Files.writeString(directory.resolve("cache").resolve(loaded.byteSha256()),"corrupt");events.clear();
        assertThrows(SourceLoadException.class,()->fixture.load(access,url,OFFLINE));
        fixture.load(access,url,ONLINE);
        assertEquals(List.of(RepositoryCacheEvent.OFFLINE_MISS,RepositoryCacheEvent.CACHE_MISS,RepositoryCacheEvent.DOWNLOAD),kinds(events));
        assertEquals(2,fixture.requests.get());
    }
    @Test void freshCacheNeverContactsServerAndUnsolicited304NeverClaimsAHit() throws Exception {
        var always304=new AtomicBoolean();
        String url=fixture.serve(e -> {
            if(always304.get()) {e.sendResponseHeaders(304,-1);e.close();}
            else fixture.send(e,"text","\"one\"");
        })+"model.ili";
        var events=new ArrayList<RepositoryCacheObservation>();var access=host(events);
        fixture.load(access,url,ONLINE);events.clear();always304.set(true);
        fixture.load(access,url,ONLINE);assertEquals(List.of(RepositoryCacheEvent.FRESH_HIT),kinds(events));
        events.clear();int before=fixture.requests.get();
        assertThrows(SourceLoadException.class,()->access.loader().load(new SourceLocator(url),ONLINE,TOKEN));
        assertEquals(before+1,fixture.requests.get());assertTrue(events.isEmpty());
    }
    @Test void observationAndLegacyObserverFailuresDoNotChangeLoadingAndNoStoreDoesNotClaimPersistence() throws Exception {
        String url=fixture.serve(e -> {e.getResponseHeaders().set("Cache-Control","no-store");fixture.send(e,"text","\"one\"");})+"model.ili";
        var throwing=HostRepositories.createObserved(directory,RepositoryCacheConfiguration.at(directory.resolve("cache")),event -> {throw new IllegalStateException("observer");});
        assertEquals("text",fixture.load(throwing,url,ONLINE).text());
        var legacy=HostRepositories.create(directory,RepositoryCacheConfiguration.disabled(),event -> {throw new IllegalStateException("observer");});
        assertEquals("text",fixture.load(legacy,url,ONLINE).text());
        var events=new ArrayList<RepositoryCacheObservation>();fixture.load(host(events),url,ONLINE);fixture.load(host(events),url,ONLINE);
        assertEquals(List.of(RepositoryCacheEvent.DOWNLOAD,RepositoryCacheEvent.DOWNLOAD),kinds(events));
        assertThrows(SourceLoadException.class,()->fixture.load(host(events),url,OFFLINE));
    }
    @Test void digestBasedOfflineReplayReportsEachActualSourceOnce() throws Exception {
        String root=model("Root","1","IMPORTS Dep;");
        String url=fixture.serve(e -> fixture.send(e,e.getRequestURI().getPath().endsWith("ilimodels.xml")?index(entry("Dep","1","dep.ili")):model("Dep","1",""),"\""+e.getRequestURI().getPath()+"\""));
        var observations=new ArrayList<RepositoryCacheObservation>();var access=host(observations);ResolutionLock lock;
        try(var w=workspace(root)) {lock=prepared(access.closureService().resolve(w,request(w),RepositoryResolveOptions.defaults(config(url)),TOKEN)).lock();}
        fixture.stop();fixture.server=null;observations.clear();int requests=fixture.requests.get();
        var offline=new RepositoryConfig(config(url).sources(),OFFLINE);
        try(var w=workspace(root)) {prepared(access.closureService().resolve(w,request(w),new RepositoryResolveOptions(offline,Map.of(),Map.of(),Optional.of(lock)),TOKEN));}
        assertEquals(requests,fixture.requests.get());assertEquals(List.of(RepositoryCacheEvent.OFFLINE_HIT,RepositoryCacheEvent.OFFLINE_HIT),kinds(observations));
        assertEquals(Set.of(url+"ilimodels.xml",url+"dep.ili"),observations.stream().map(o -> o.safeSource().orElseThrow()).collect(java.util.stream.Collectors.toSet()));
        var legacy=new ArrayList<RepositoryCacheEvent>();
        for(var service:List.of(new HostRepositories.Access(access.loader(),access.contentStore(),legacy::add).closureService(),
                new RepositoryClosureService(access.loader(),access.contentStore(),legacy::add))) {
            legacy.clear();
            try(var w=workspace(root)) {prepared(service.resolve(w,request(w),new RepositoryResolveOptions(offline,Map.of(),Map.of(),Optional.of(lock)),TOKEN));}
            assertEquals(List.of(RepositoryCacheEvent.OFFLINE_HIT,RepositoryCacheEvent.OFFLINE_HIT),legacy);
        }
        assertEquals(requests,fixture.requests.get());
    }
    @Test void publicObservationSanitizesAtConstructionAndGlobalWritesCanOmitSource() {
        var o=new RepositoryCacheObservation(RepositoryCacheEvent.DOWNLOAD,Optional.of("https://user:password@example.org/model.ili?secret=value#private"));
        assertEquals(Optional.of("https://example.org/model.ili"),o.safeSource());
        assertTrue(RepositoryCacheObservation.global(RepositoryCacheEvent.WRITE_UNAVAILABLE).safeSource().isEmpty());
    }
}
