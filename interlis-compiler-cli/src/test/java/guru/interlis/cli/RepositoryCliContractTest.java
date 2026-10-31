package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryCliContractTest {
    @TempDir Path directory;
    record Run(CliExecutionResult result,String output) { }
    Run run(String... args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        var env=new CliEnvironment(directory,new ByteArrayInputStream(new byte[0]),new PrintStream(out),new PrintStream(err),Map.of());
        return new Run(new DefaultCompilerCli().run(List.of(args),env,CancellationToken.none()),out.toString(StandardCharsets.UTF_8));
    }
    static String model(String version) { return "INTERLIS 2.4; MODEL Dep AT \"https://example.org\" VERSION \""+version+"\"=DOMAIN P=TEXT;END Dep."; }
    static String entry(String version,String file) { return "<ModelMetadata><Name>Dep</Name><SchemaLanguage>ili2_4</SchemaLanguage><File>"+file+"</File><Version>"+version+"</Version></ModelMetadata>"; }
    Path repository(boolean ambiguous) throws Exception {
        Path repo=Files.createDirectory(directory.resolve("repo"));
        Files.writeString(repo.resolve("ilimodels.xml"),"<TRANSFER>"+entry("1","one.ili")+(ambiguous?entry("2","two.ili"):"")+"</TRANSFER>");
        Files.writeString(repo.resolve("one.ili"),model("1"));if(ambiguous)Files.writeString(repo.resolve("two.ili"),model("2"));return repo;
    }
    @Test void checkDiscoversNamedRootWritesLockAndReplaysOfflineAcrossCliInstances() throws Exception {
        Path repo=repository(false);String base=repo.toString();
        var online=run("check","--repository",base,"--root","Dep","--cache","cache","--write-lock","closure.lock.xml","--json");
        assertEquals(CliTermination.SUCCESS,online.result().termination(),online.result().output());assertTrue(online.output().contains("\"status\":\"ok\""));
        var lock=new ResolutionLockCodec().decode(Files.readString(directory.resolve("closure.lock.xml")));assertEquals("1",lock.models().get("Dep").getFirst().modelVersion());
        Files.delete(repo.resolve("ilimodels.xml"));Files.delete(repo.resolve("one.ili"));
        var offline=run("compile","--repository",base,"--root","Dep","--offline","--cache","cache","--read-lock","closure.lock.xml","--json");
        assertEquals(CliTermination.SUCCESS,offline.result().termination(),offline.result().output());
        Files.writeString(directory.resolve("cache").resolve(lock.models().get("Dep").getFirst().digest().value()),"corrupt");
        var corrupt=run("check","--repository",base,"--root","Dep","--offline","--cache","cache","--read-lock","closure.lock.xml","--json");
        assertEquals(CliTermination.OPERATIONAL_FAILURE,corrupt.result().termination());assertTrue(corrupt.output().contains("OFFLINE"));
    }
    @Test void checkDoesNotPickLatestAndExactModelAndIndexPinsAreEnforced() throws Exception {
        Path repo=repository(true);String base=repo.toUri().toString();
        var ambiguous=run("check","--repository",base,"--root","Dep","--json");
        assertEquals(CliTermination.INPUT_UNRESOLVED,ambiguous.result().termination());assertTrue(ambiguous.output().contains("AMBIGUOUS"));
        String uri=repo.resolve("one.ili").toUri().toString(),sha=ContentDigest.sha256(Files.readAllBytes(repo.resolve("one.ili"))).value();
        var exact=run("check","--repository",base,"--root","Dep","--model-pin","Dep","1",uri,sha,"--json");
        assertEquals(0,exact.result().exitCode(),exact.result().output());
        var wrong=run("check","--repository",base,"--root","Dep","--model-pin","Dep","1",uri,"0".repeat(64),"--json");
        assertEquals(CliTermination.INPUT_UNRESOLVED,wrong.result().termination());assertTrue(wrong.output().contains("PIN_MISMATCH"));
        var index=run("check","--repository",base,"--root","Dep","--source-pin",base+"ilimodels.xml","0".repeat(64),"--json");
        assertEquals(CliTermination.INPUT_UNRESOLVED,index.result().termination());
    }
    @Test void lockOutputCannotOverwriteExplicitOrResolvedModelSourcesOrIndices() throws Exception {
        var repo=repository(false);String original=Files.readString(repo.resolve("one.ili"));
        var collision=run("check","--repository",repo.toString(),"--root","Dep","--write-lock",repo.resolve("one.ili").toString());
        assertEquals(CliTermination.USAGE_ERROR,collision.result().termination());assertEquals(original,Files.readString(repo.resolve("one.ili")));
        String index=Files.readString(repo.resolve("ilimodels.xml"));
        assertEquals(CliTermination.USAGE_ERROR,run("check","--repository",repo.toString(),"--root","Dep","--write-lock",repo.resolve("ilimodels.xml").toString()).result().termination());
        assertEquals(index,Files.readString(repo.resolve("ilimodels.xml")));
    }
    @Test void malformedRepositoryOptionsAndLockAreUsageErrors() throws Exception {
        assertEquals(CliTermination.USAGE_ERROR,run("check","--model-pin","Dep").result().termination());
        assertEquals(CliTermination.USAGE_ERROR,run("check","--offline","--root","Dep").result().termination());
        assertEquals(CliTermination.USAGE_ERROR,run("analyze","--repository",directory.toString()).result().termination());
        var repo=repository(false);Files.writeString(directory.resolve("invalid.xml"),"<invalid/>");
        assertEquals(CliTermination.USAGE_ERROR,run("check","--repository",repo.toString(),"--root","Dep","--read-lock","invalid.xml").result().termination());
    }
}
