package guru.interlis.cli;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.*;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.execution.*;
import guru.interlis.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultRepositoryCacheTest {
    @TempDir Path directory;
    HttpServer server;
    final List<RunEvent> events=new ArrayList<>();
    final AtomicInteger requests=new AtomicInteger(),bodies=new AtomicInteger();
    Path home() { return directory.resolve("injected-home"); }
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    record Run(CliExecutionResult result,String out,String err) { }
    Run run(String...args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        var services=new CompilerServices(events::add,ClockSource.system(),RunContextFactory.defaults());
        var environment=new CliEnvironment(directory,new ByteArrayInputStream(new byte[0]),new PrintStream(out,true,StandardCharsets.UTF_8),
                new PrintStream(err,true,StandardCharsets.UTF_8),Map.of(),services,new TerminalCapabilities(false),home());
        var result=new DefaultCompilerCli().run(List.of(args),environment,CancellationToken.none());
        return new Run(result,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    String repository() throws Exception {
        Files.writeString(directory.resolve("Projekt.ili"),"INTERLIS 2.4; MODEL Projekt AT \"https://example.org\" VERSION \"1\" = IMPORTS Basis; END Projekt.");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            requests.incrementAndGet();
            String text=switch(exchange.getRequestURI().getPath()) {
                case "/ilimodels.xml" -> "<TRANSFER><ModelMetadata><Name>Basis</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Basis.ili</File></ModelMetadata></TRANSFER>";
                case "/Basis.ili" -> "INTERLIS 2.4; MODEL Basis AT \"https://example.org\" VERSION \"1\" = END Basis.";
                default -> null;
            };
            if(text==null) {exchange.sendResponseHeaders(404,-1);exchange.close();return;}
            exchange.getResponseHeaders().set("ETag","\""+text.hashCode()+"\"");
            if(exchange.getRequestHeaders().getFirst("If-None-Match")!=null) {exchange.sendResponseHeaders(304,-1);exchange.close();return;}
            bodies.incrementAndGet();byte[] bytes=text.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()) {output.write(bytes);}
        });server.start();return "http://127.0.0.1:"+server.getAddress().getPort()+"/";
    }
    void success(Run run) { assertEquals(CliTermination.SUCCESS,run.result.termination(),run.out+run.err); }

    @Test void defaultCacheServesImportsOfflineWithAndWithoutLockAndEmitsCorrelatedEvents() throws Exception {
        String base=repository();success(run("check","Projekt.ili","--repository",base,"--write-lock","projekt.lock"));
        assertTrue(Files.isDirectory(home().resolve(".interlis-cache/metadata-v1")));assertEquals(2,bodies.get());
        success(run("check","Projekt.ili","--repository",base));assertEquals(2,bodies.get());
        int before=requests.get();server.stop(0);server=null;
        for(var extra:List.of(List.of("--offline"),List.of("--offline","--read-lock","projekt.lock"))) {
            var args=new ArrayList<>(List.of("compile","Projekt.ili","--repository",base));args.addAll(extra);success(run(args.toArray(String[]::new)));
        }
        assertEquals(before,requests.get());
        var observed=events.stream().filter(e->e instanceof RunEvent.CacheLookup).map(e->(RunEvent.CacheLookup)e).toList();
        assertTrue(observed.stream().anyMatch(e->e.reason().equals("NOT_MODIFIED")));
        assertTrue(observed.stream().anyMatch(e->e.reason().equals("OFFLINE_HIT")));
        for(var context:events.stream().map(e->e.header().runId()).distinct().toList()) {
            var run=events.stream().filter(e->e.header().runId().equals(context)).toList();
            assertInstanceOf(RunEvent.Started.class,run.getFirst());assertInstanceOf(RunEvent.Finished.class,run.getLast());
            long previous=0;for(var event:run) {assertTrue(event.header().sequence()>previous);previous=event.header().sequence();}
        }
    }

    @Test void customCacheNoCacheAndLocalOnlyRunsHaveThePromisedDiskEffects() throws Exception {
        String base=repository();success(run("check","Projekt.ili","--repository",base,"--cache","alternate"));
        assertTrue(Files.isDirectory(directory.resolve("alternate/metadata-v1")));assertFalse(Files.exists(home()));
        int before=bodies.get();success(run("check","Projekt.ili","--repository",base,"--no-cache"));assertEquals(before+4,bodies.get());
        assertFalse(Files.exists(home()));
        assertEquals(CliTermination.OPERATIONAL_FAILURE,run("check","Projekt.ili","--repository",base,"--offline","--no-cache").result.termination());
        Files.writeString(directory.resolve("Local.ili"),"INTERLIS 2.4; MODEL Local AT \"https://example.org\" VERSION \"1\" = END Local.");
        success(run("check","Local.ili"));success(run("check","Local.ili","--repository",base));assertFalse(Files.exists(home()));
    }

    @Test void configurationPrecedenceExplainsEffectivePathAndRejectsConflicts() throws Exception {
        var defaults=run("check","--explain-config");success(defaults);
        var settings=JsonParser.parseString(defaults.out).getAsJsonObject().getAsJsonObject("settings");
        assertEquals(home().resolve(".interlis-cache").toString(),settings.getAsJsonObject("--cache").getAsJsonArray("values").get(0).getAsString());
        assertEquals("PROFILE_DEFAULT",settings.getAsJsonObject("--cache").get("origin").getAsString());assertFalse(Files.exists(home()));
        Files.writeString(directory.resolve("settings.properties"),"no-cache=true\n");
        var disabled=run("check","--config","settings.properties","--explain-config");success(disabled);assertFalse(disabled.out.contains("\"--cache\""));
        var override=run("check","--config","settings.properties","--cache","chosen","--explain-config");success(override);
        assertTrue(override.out.contains("chosen"));assertFalse(override.out.contains("\"--no-cache\""));
        Files.writeString(directory.resolve("settings.properties"),"cache=config-cache\n");
        var noCache=run("check","--config","settings.properties","--no-cache","--explain-config");success(noCache);assertFalse(noCache.out.contains("config-cache"));
        assertEquals(CliTermination.USAGE_ERROR,run("check","--cache","x","--no-cache","--explain-config").result.termination());
        assertEquals(CliTermination.USAGE_ERROR,run("check","--cache","bad\u0000path","--explain-config").result.termination());
        for(String config:List.of("cache=x\nno-cache=true\n","no-cache=maybe\n")) {
            Files.writeString(directory.resolve("settings.properties"),config);
            assertEquals(CliTermination.USAGE_ERROR,run("check","--config","settings.properties","--explain-config").result.termination());
        }
    }

    @Test void unwritableCacheWarnsOnceWithoutFailingAndSecretsNeverReachObservations() throws Exception {
        String base=repository();Files.writeString(directory.resolve("blocked"),"file");
        var result=run("check","Projekt.ili","--repository",base,"--cache","blocked");success(result);
        assertEquals(1,result.err.split("CACHE_WRITE_UNAVAILABLE",-1).length-1,result.err);
        assertEquals(1,events.stream().filter(e->e instanceof RunEvent.CacheLookup c && c.reason().equals("WRITE_UNAVAILABLE")).count());
        assertFalse(events.toString().contains(base));
    }
}
