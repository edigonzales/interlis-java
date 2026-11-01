package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.repository.RepositoryCacheContractTest.*;

class Ili2cCachePolicyTest {
    @TempDir Path directory;
    final RepositoryCacheContractTest fixture=new RepositoryCacheContractTest();
    final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-11-01T12:00:00Z"));
    final List<RepositoryCacheObservation> events=new ArrayList<>();
    final List<String> headers=new ArrayList<>();
    final AtomicReference<String> text=new AtomicReference<>("original");
    final AtomicReference<String> vary=new AtomicReference<>("Accept-Encoding");
    final AtomicBoolean privateResponse=new AtomicBoolean();
    @BeforeEach void setup() { fixture.directory=directory; }
    @AfterEach void stop() { fixture.stop(); }
    HostRepositories.Access host() {
        Clock clock=new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { Objects.requireNonNull(zone); return this; }
            public Instant instant() { return now.get(); }
        };
        return HostRepositories.createObserved(directory,RepositoryCacheConfiguration.at(directory.resolve("cache")),
                events::add,new RepositoryProxyConfiguration.Direct(),clock);
    }
    String serve() throws Exception {
        return fixture.serve(e -> {
            headers.add(Objects.toString(e.getRequestHeaders().getFirst("If-None-Match"),"")
                    +Objects.toString(e.getRequestHeaders().getFirst("If-Modified-Since"),""));
            e.getResponseHeaders().set("Vary",vary.get());
            e.getResponseHeaders().set("ETag","\"irrelevant\"");
            e.getResponseHeaders().set("Last-Modified","Wed, 21 Oct 2015 07:28:00 GMT");
            if(privateResponse.get())e.getResponseHeaders().set("Cache-Control","no-store");
            fixture.send(e,text.get(),"\"irrelevant\"");
        })+"source";
    }
    LoadedSource load(String url,RepositoryCacheRequest request) {
        return host().loader().load(new SourceLocator(url),ONLINE,request,TOKEN);
    }
    static String md5(String text) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test void metadataAndModelBoundaryAreInclusiveAndHitsDoNotRenewAge() throws Exception {
        String base=serve(); Instant start=now.get();
        for (var request:List.of(RepositoryCacheRequest.metadata(),RepositoryCacheRequest.model(Optional.of(md5("original"))))) {
            now.set(start);String url=base+request.maxAge().toDays();load(url,request);
            byte[] metadata=Files.readAllBytes(fixture.metadata(url)); int before=fixture.requests.get();
            now.set(start.plus(request.maxAge()).minusNanos(1));load(url,request);
            now.set(start.plus(request.maxAge()));load(url,request);
            assertEquals(before,fixture.requests.get());assertArrayEquals(metadata,Files.readAllBytes(fixture.metadata(url)));
            now.set(start.plus(request.maxAge()).plusNanos(1));load(url,request);
            assertEquals(before+1,fixture.requests.get());
        }
        assertTrue(events.stream().anyMatch(e -> e.event()==RepositoryCacheEvent.FRESH_HIT));
        assertTrue(headers.stream().allMatch(String::isEmpty));
    }

    @Test void matchingMissingAndChangedMd5DecideBeforeAnyModelRequest() throws Exception {
        String url=serve();var plain=RepositoryCacheRequest.model(Optional.empty());load(url,plain);
        int before=fixture.requests.get();
        load(url,RepositoryCacheRequest.model(Optional.of(md5("original").toUpperCase(Locale.ROOT))));
        text.set("new bytes");assertEquals("original",load(url,plain).text());assertEquals(before,fixture.requests.get());
        assertEquals("new bytes",load(url,RepositoryCacheRequest.model(Optional.of(md5("new bytes")))).text());
        assertEquals(before+1,fixture.requests.get());
    }

    @Test void corruptAndMissingContentsAreDownloadedAndFutureTimestampNeverFresh() throws Exception {
        String url=serve();var request=RepositoryCacheRequest.metadata();var first=load(url,request);
        Files.writeString(directory.resolve("cache").resolve(first.byteSha256()),"corrupt");load(url,request);
        Files.delete(directory.resolve("cache").resolve(first.byteSha256()));load(url,request);
        now.set(now.get().minusSeconds(1));load(url,request);assertEquals(4,fixture.requests.get());
    }

    @Test void genericAndZeroAgeAlwaysDownloadAndNoValidatorsArePersisted() throws Exception {
        String url=serve();load(url,RepositoryCacheRequest.metadata());
        host().loader().load(new SourceLocator(url),ONLINE,TOKEN);
        load(url,RepositoryCacheRequest.unconditional());assertEquals(3,fixture.requests.get());
        String metadata=Files.readString(fixture.metadata(url));
        assertFalse(metadata.contains("etag") || metadata.contains("last-modified"));
        assertTrue(metadata.contains("downloaded-at"));assertTrue(headers.stream().allMatch(String::isEmpty));
    }

    @Test void expiredNetworkFailureNeverFallsBackButExplicitOfflineWorks() throws Exception {
        String url=serve();var request=RepositoryCacheRequest.metadata();load(url,request);
        now.set(now.get().plus(Duration.ofDays(2)));fixture.stop();fixture.server=null;
        assertThrows(SourceLoadException.class,()->load(url,request));
        assertEquals("original",host().loader().loadCached(new SourceLocator(url),OFFLINE,TOKEN).text());
    }

    @Test void legacyMetadataIsOfflineOnlyAndNewDownloadsPublishV2() throws Exception {
        String url=serve();var loaded=load(url,RepositoryCacheRequest.metadata());
        String old="<interlis-cache version=\"1\" source=\""+url+"\" response-source=\""+url+"\" sha256=\""+loaded.byteSha256()+"\" etag=\"&quot;one&quot;\"/>";
        Path legacy=directory.resolve("cache/metadata-v1").resolve(fixture.metadata(url).getFileName());
        Files.createDirectories(legacy.getParent());Files.writeString(legacy,old);Files.delete(fixture.metadata(url));
        assertEquals("original",host().loader().loadCached(new SourceLocator(url),OFFLINE,TOKEN).text());assertEquals(1,fixture.requests.get());
        load(url,RepositoryCacheRequest.metadata());assertEquals(2,fixture.requests.get());assertTrue(Files.exists(fixture.metadata(url)));
        Files.writeString(fixture.metadata(url),"corrupt");
        assertThrows(SourceLoadException.class,()->host().loader().loadCached(new SourceLocator(url),OFFLINE,TOKEN));
    }

    @Test void applicationCacheIgnoresVaryButHonorsNoStore() throws Exception {
        String base=serve();
        for (String field:List.of("Accept-Encoding","Accept-Language","*")) {
            vary.set(field);String url=base+field;load(url,RepositoryCacheRequest.metadata());int before=fixture.requests.get();
            load(url,RepositoryCacheRequest.metadata());assertEquals(before,fixture.requests.get());
        }
        privateResponse.set(true);String url=base+"private";load(url,RepositoryCacheRequest.metadata());load(url,RepositoryCacheRequest.metadata());
        assertFalse(Files.exists(fixture.metadata(url)));
        assertThrows(SourceLoadException.class,()->host().loader().loadCached(new SourceLocator(url),OFFLINE,TOKEN));
    }

    @Test void closurePassesModelMd5BeforeDownloadAndRejectsIncorrectDownloadedModel() throws Exception {
        var model=new AtomicReference<>(fixture.model("Dep",""));
        var expected=new AtomicReference<>(md5(model.get()));
        String base=fixture.serve(e -> {
            if(e.getRequestURI().getPath().endsWith("ilimodels.xml"))
                fixture.send(e,fixture.index("Dep").replace("</File>","</File><md5>"+expected.get()+"</md5>"),null);
            else fixture.send(e,model.get(),null);
        });
        assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,fixture.resolve(host(),List.of(base),false,Optional.empty()));
        assertEquals(2,fixture.requests.get());
        now.set(now.get().plus(Duration.ofDays(2))); // index expired; model is still within its seven days
        model.set(fixture.model("Dep","DOMAIN D=TEXT;"));expected.set(md5(model.get()));
        assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,fixture.resolve(host(),List.of(base),false,Optional.empty()));
        assertEquals(4,fixture.requests.get(),"new index MD5 must trigger model download within its TTL");
        now.set(now.get().plus(Duration.ofDays(2)));expected.set("00000000000000000000000000000000");
        var rejected=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,fixture.resolve(host(),List.of(base),false,Optional.empty()));
        assertEquals(RepositoryProblem.Reason.PIN_MISMATCH,rejected.problems().getFirst().reason());
        assertEquals(6,fixture.requests.get());
    }

    @Test void noStoreInvalidationCannotResurrectLegacyOfflineEntry() throws Exception {
        String url=serve();var loaded=load(url,RepositoryCacheRequest.metadata());
        Path legacy=directory.resolve("cache/metadata-v1").resolve(fixture.metadata(url).getFileName());
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy,"<interlis-cache version=\"1\" source=\""+url+"\" response-source=\""+url+"\" sha256=\""+loaded.byteSha256()+"\"/>");
        privateResponse.set(true);load(url,RepositoryCacheRequest.unconditional());
        assertThrows(SourceLoadException.class,()->host().loader().loadCached(new SourceLocator(url),OFFLINE,TOKEN));
    }

    @Test void requestValidationAndFunctionalLoaderCompatibility() {
        assertThrows(NullPointerException.class,()->new RepositoryCacheRequest(null,Optional.empty()));
        assertThrows(NullPointerException.class,()->RepositoryCacheRequest.model(null));
        assertThrows(IllegalArgumentException.class,()->new RepositoryCacheRequest(Duration.ofNanos(-1),Optional.empty()));
        SourceLoader legacy=(locator,policy,token)->new LoadedSource(new guru.interlis.source.SourceId(locator.location()),"x",ContentDigest.sha256(new byte[]{120}).value(),"test");
        assertEquals("x",legacy.load(new SourceLocator("memory:/x"),ONLINE,RepositoryCacheRequest.metadata(),CancellationToken.none()).text());
    }
}
