package guru.interlis.repository;

import com.sun.net.httpserver.*;
import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryCacheRegressionTest {
    @TempDir Path directory;
    HttpServer server;
    String base;
    final AtomicInteger requests=new AtomicInteger();
    static final CancellationToken TOKEN=CancellationToken.none();
    static final FetchPolicy ONLINE=new FetchPolicy(false, 16_777_216, Duration.ofSeconds(2), Duration.ofSeconds(2), java.util.Optional.of(Duration.ofSeconds(2)));
    static final String A="INTERLIS 2.4; MODEL Dep AT \"https://example.org\" VERSION \"1\" = END Dep.";
    static final String INDEX="<TRANSFER><ModelMetadata><Name>Dep</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Dep.ili</File></ModelMetadata></TRANSFER>";
    final AtomicReference<String> content=new AtomicReference<>(A), site=new AtomicReference<>();
    final AtomicBoolean changeAfterRead=new AtomicBoolean();
    final List<String> modelConditions=Collections.synchronizedList(new ArrayList<>());
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    HostRepositories.Access host() { return HostRepositories.create(directory,RepositoryCacheConfiguration.at(directory.resolve("cache")),event -> { }); }
    void send(HttpExchange e,String text) throws IOException {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);
        try(var output=e.getResponseBody()) { output.write(bytes); }
    }
    void start(String mode) throws IOException {
        String modifiedTime=java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'",Locale.ROOT).format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).withNano(0));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e -> {
            requests.incrementAndGet();
            switch(e.getRequestURI().getPath()) {
                case "/ilimodels.xml" -> {
                    e.getResponseHeaders().set("ETag","\"index\"");
                    if("\"index\"".equals(e.getRequestHeaders().getFirst("If-None-Match"))) { e.sendResponseHeaders(304,-1);e.close(); }
                    else send(e,INDEX);
                }
                case "/Dep.ili" -> {
                    String condition=e.getRequestHeaders().getFirst(mode.equals("weak")?"If-None-Match":"If-Modified-Since");
                    modelConditions.add(Objects.toString(condition,""));
                    // The weak ETag denotes semantically equal comment variants. Last-Modified
                    // models a change immediately after the first read, in the same second.
                    if(mode.equals("weak")) e.getResponseHeaders().set("ETag","W/\"logical\"");
                    else {
                        // Fixed server clock for this logical second: Date does not strengthen Last-Modified.
                        e.getResponseHeaders().set("Last-Modified",modifiedTime);e.getResponseHeaders().set("Date",modifiedTime);
                    }
                    if(condition!=null) { e.sendResponseHeaders(304,-1);e.close(); }
                    else { String bytes=content.get();if(changeAfterRead.compareAndSet(true,false))content.set(changed(mode));send(e,bytes); }
                }
                default -> { if(site.get()!=null)send(e,site.get());else {e.sendResponseHeaders(404,-1);e.close();} }
            }
        });server.start();base="http://127.0.0.1:"+server.getAddress().getPort()+"/";
    }
    String changed(String mode) { return mode.equals("weak")?"!! same model, different original bytes\n"+A:A.replace("= END","= DOMAIN D=TEXT; END"); }
    RepositoryResolutionOutcome resolve(String dependency,boolean offline,Optional<ResolutionLock> lock,Map<SourceId,ContentDigest> pins) {
        try(var w=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults())) {
            var id=new SourceId("memory:/root.ili");
            w.update(new SourceUpdate(id,new SourceExpectation.Absent(),new SourceChange.Replace(
                    "INTERLIS 2.4; MODEL Root AT \"https://example.org\" VERSION \"1\" = IMPORTS "+dependency+"; END Root.")));
            var config=new RepositoryConfig(List.of(new RepositorySource(new RepositoryBase(URI.create(base)),new SourceLocator(base+"ilimodels.xml"))),
                    new FetchPolicy(offline,ONLINE.maxBytes(),ONLINE.connectTimeout(),ONLINE.readTimeout(),ONLINE.requestTimeout()));
            return host().closureService().resolve(w,new AnalysisRequest(w.currentRevision(),List.of(new ModelSelector.BySource(id))),
                    new RepositoryResolveOptions(config,Map.of(),pins,lock),TOKEN);
        }
    }
    RepositoryProblem problem(RepositoryResolutionOutcome result) { return assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,result).problems().getFirst(); }
    void changedDuringRead(String mode) throws Exception {
        start(mode);changeAfterRead.set(true);
        assertEquals(RepositoryProblem.Reason.CHANGED_SOURCE,problem(resolve("Dep",false,Optional.empty(),Map.of())).reason());
        assertEquals(List.of("",""),modelConditions,"weak validators must not hide changed original bytes");
    }
    @Test void weakEtagDoesNotHideAChangeDuringFinalVerification() throws Exception { changedDuringRead("weak"); }
    @Test void lastModifiedDoesNotHideAChangeDuringFinalVerification() throws Exception { changedDuringRead("modified"); }
    void pinsAndLocks(String mode) throws Exception {
        start(mode);
        var lock=assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve("Dep",false,Optional.empty(),Map.of())).update().lock();
        content.set(changed(mode));
        assertEquals(RepositoryProblem.Reason.LOCK_MISMATCH,problem(resolve("Dep",false,Optional.of(lock),Map.of())).reason());
        var id=new SourceId(base+"Dep.ili");var digest=ContentDigest.sha256(A.getBytes(StandardCharsets.UTF_8));
        assertEquals(RepositoryProblem.Reason.PIN_MISMATCH,problem(resolve("Dep",false,Optional.empty(),Map.of(id,digest))).reason());
        int before=requests.get();
        assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve("Dep",true,Optional.of(lock),Map.of()));
        assertEquals(before,requests.get(),"exact offline lock replay must remain network-free");
    }
    @Test void weakEtagCannotHideOnlineLockOrPinMismatchButOfflineLockRetainsOldBytes() throws Exception { pinsAndLocks("weak"); }
    @Test void lastModifiedCannotHideOnlineLockOrPinMismatchButOfflineLockRetainsOldBytes() throws Exception { pinsAndLocks("modified"); }
    @Test void optionalOfflineSiteMissIsNotARequiredSourceFailure() throws Exception {
        start("weak");assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,resolve("Dep",false,Optional.empty(),Map.of()));
        int before=requests.get();var rejected=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve("Absent",true,Optional.empty(),Map.of()));
        assertEquals(RepositoryProblem.Reason.NOT_FOUND,rejected.problems().getFirst().reason());assertEquals(before,requests.get());
        assertTrue(rejected.steps().stream().anyMatch(s->s.kind()==RepositorySearchStep.Kind.SITE_UNAVAILABLE));
        assertTrue(rejected.diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-RES-0001")));
        Files.delete(directory.resolve("cache").resolve(ContentDigest.sha256(INDEX.getBytes(StandardCharsets.UTF_8)).value()));
        var required=problem(resolve("Absent",true,Optional.empty(),Map.of()));
        assertEquals(RepositoryProblem.Reason.LOAD_FAILURE,required.reason());assertEquals(SourceLoadException.Reason.OFFLINE,required.loadReason().orElseThrow());
        assertEquals(base+"ilimodels.xml",required.source().orElseThrow().value());
    }
    @Test void malformedOptionalSiteWarnsWithoutBecomingAnIndexFailure() throws Exception {
        start("weak");site.set("<Site>");
        var result=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,resolve("Absent",false,Optional.empty(),Map.of()));
        assertEquals(RepositoryProblem.Reason.NOT_FOUND,result.problems().getFirst().reason());
        assertTrue(result.steps().stream().anyMatch(s->s.kind()==RepositorySearchStep.Kind.SITE_UNAVAILABLE));
    }
    @Test void a304MustConfirmTheExactStrongEtagBeforeReusingBytes() throws Exception {
        var responseTag=new AtomicReference<String>("\"one\"");var conditions=new ArrayList<String>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e -> {
            String condition=e.getRequestHeaders().getFirst("If-None-Match");conditions.add(Objects.toString(condition,""));
            if(responseTag.get()!=null)e.getResponseHeaders().set("ETag",responseTag.get());
            if(condition!=null) { e.sendResponseHeaders(304,-1);e.close(); }else send(e,content.get());
        });server.start();String url="http://127.0.0.1:"+server.getAddress().getPort()+"/model";
        for(String tag:Arrays.asList("W/\"one\"",null,"\"different\"")) {
            responseTag.set("\"one\"");content.set(A);load(url);conditions.clear();
            responseTag.set(tag);content.set("!! comment\n"+A);
            assertEquals(content.get(),load(url).text());assertEquals(List.of("\"one\"",""),conditions);
            Files.deleteIfExists(directory.resolve("cache/metadata-v1").resolve(ContentDigest.sha256(url.getBytes(StandardCharsets.UTF_8)).value()+".xml"));
        }
    }
    LoadedSource load(String url) { return host().loader().load(new SourceLocator(url),ONLINE,TOKEN); }
}
