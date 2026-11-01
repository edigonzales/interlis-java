package guru.interlis.cli;

import com.google.gson.JsonParser;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.execution.*;
import guru.interlis.repository.*;
import guru.interlis.source.SourceId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VerboseRepositoryCacheTest {
    @TempDir Path directory;
    final DefaultRepositoryCacheTest fixture=new DefaultRepositoryCacheTest();
    @BeforeEach void setup() {fixture.directory=directory;}
    @AfterEach void stop() {fixture.stop();}
    long infos(String text) {return text.lines().filter(s -> s.contains("[ILI-RES-0010]")).count();}
    @Test void allSemanticCommandsUseCommonInfoStyleForEveryHitWithoutChangingResultsOrJson() throws Exception {
        String base=fixture.repository();fixture.success(fixture.run("check","Projekt.ili","--repository",base));
        for(String command:List.of("check","compile","analyze","diagram")) {
            var compact=fixture.run(command,"Projekt.ili","--repository",base);fixture.success(compact);assertEquals(0,infos(compact.err()));
            fixture.events.clear();var verbose=fixture.run(command,"Projekt.ili","--repository",base,"--verbose","--warnings","reject");fixture.success(verbose);
            assertTrue(verbose.err().contains("INFO [ILI-RES-0010]"));assertTrue(verbose.err().contains(base+"Basis.ili"));assertTrue(verbose.err().contains(base+"ilimodels.xml"));
            assertTrue(verbose.err().contains("content used from fresh cache"));
            assertEquals(fixture.events.stream().filter(e->e instanceof RunEvent.CacheLookup).count(),infos(verbose.err()));assertEquals(2,infos(verbose.err()));
            assertEquals(compact.result().diagnostics(),verbose.result().diagnostics());assertTrue(verbose.result().diagnostics().stream().noneMatch(d -> List.of("ILI-RES-0010","ILI-RES-0011","ILI-RES-0012","ILI-RES-0013").contains(d.code().value())));
            assertEquals(compact.result().artifacts(),verbose.result().artifacts());
            var de=fixture.run(command,"Projekt.ili","--repository",base,"--verbose","--diagnostic-language","de");fixture.success(de);assertTrue(de.err().contains("INFO [ILI-RES-0010]"));
            if(!command.equals("diagram")) {
                var json=fixture.run(command,"Projekt.ili","--repository",base,"--json");
                var jsonVerbose=fixture.run(command,"Projekt.ili","--repository",base,"--json","--verbose");
                assertEquals(JsonParser.parseString(json.out()),JsonParser.parseString(jsonVerbose.out()));assertEquals(json.err(),jsonVerbose.err());assertEquals(0,infos(jsonVerbose.err()));
            } else {
                var jsonArtifact=fixture.run("diagram","Projekt.ili","--repository",base,"--format","json","--verbose");
                assertTrue(JsonParser.parseString(jsonArtifact.out()).isJsonObject());assertTrue(infos(jsonArtifact.err())>0);
            }
        }
    }
    @Test void offlineBothPathsOptionalSiteAndRequiredIndexHaveTruthfulMessagesAndNoNetwork() throws Exception {
        String base=fixture.repository();fixture.success(fixture.run("check","Projekt.ili","--repository",base,"--write-lock","projekt.lock"));
        int requests=fixture.requests.get();fixture.stop();fixture.server=null;
        for(boolean lock:List.of(false,true)) {
            var args=new ArrayList<>(List.of("compile","Projekt.ili","--repository",base,"--offline","--verbose"));
            if(lock)args.addAll(List.of("--read-lock","projekt.lock"));
            var out=fixture.run(args.toArray(String[]::new));fixture.success(out);assertEquals(2,infos(out.err()));assertTrue(out.err().contains("content used from cache offline"));
        }
        var optional=fixture.run("check","--root","Absent","--repository",base,"--offline","--verbose");
        assertEquals(CliTermination.INPUT_UNRESOLVED,optional.result().termination());assertTrue(optional.err().contains(base+"ilisite.xml"));assertTrue(optional.err().contains("source unavailable in offline cache"));
        var lock=new ResolutionLockCodec().decode(Files.readString(directory.resolve("projekt.lock")));
        Files.delete(fixture.home().resolve(".interlis-cache").resolve(lock.indexes().get(new SourceId(base+"ilimodels.xml")).value()));
        var missing=fixture.run("check","Projekt.ili","--repository",base,"--offline","--verbose");
        assertEquals(CliTermination.OPERATIONAL_FAILURE,missing.result().termination());assertTrue(missing.err().contains("source unavailable in offline cache"));
        assertTrue(missing.err().contains("ILI-RES-0003"));assertEquals(requests,fixture.requests.get());
    }
    @Test void disabledCacheDownloadsDoNotClaimHitsAndWriteWarningIsNotDuplicatedAsInfo() throws Exception {
        String base=fixture.repository();
        var disabled=fixture.run("check","Projekt.ili","--repository",base,"--no-cache","--verbose");fixture.success(disabled);
        assertEquals(2,infos(disabled.err()));assertTrue(disabled.err().contains("source downloaded"));assertFalse(disabled.err().contains("content used from cache"));
        Files.writeString(directory.resolve("blocked"),"file");fixture.events.clear();
        var unavailable=fixture.run("check","Projekt.ili","--repository",base,"--cache","blocked","--verbose");fixture.success(unavailable);
        assertEquals(1,fixture.events.stream().filter(e->e instanceof RunEvent.CacheLookup c&&c.reason().equals("WRITE_UNAVAILABLE")).count());
        assertEquals(1,unavailable.err().lines().filter(s->s.startsWith("WARNING [ILI-RES-0008]")).count());assertEquals(2,infos(unavailable.err()));
    }
    @Test void commonColorPolicyControlsInfoOutputWithAutoAlwaysAndNever() throws Exception {
        String base=fixture.repository();
        for(var row:List.of(List.of("auto","false"),List.of("auto","true"),List.of("never","true"),List.of("always","false"))) {
            var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
            var env=new CliEnvironment(directory,InputStream.nullInputStream(),new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8),Map.of(),CompilerServices.defaults(),new TerminalCapabilities(Boolean.parseBoolean(row.get(1))),fixture.home());
            var result=new DefaultCompilerCli().run(List.of("check","Projekt.ili","--repository",base,"--verbose","--color",row.get(0)),env,CancellationToken.none());
            assertEquals(0,result.exitCode());boolean colored=row.get(0).equals("always")||row.get(0).equals("auto")&&Boolean.parseBoolean(row.get(1));
            assertEquals(colored,err.toString(StandardCharsets.UTF_8).contains("\u001b[36mINFO\u001b[0m"));
        }
    }
}
