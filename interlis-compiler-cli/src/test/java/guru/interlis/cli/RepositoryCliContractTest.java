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
    record Run(CliExecutionResult result,String output,String stderr) { }
    Run run(String... args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        var env=new CliEnvironment(directory,new ByteArrayInputStream(new byte[0]),new PrintStream(out),new PrintStream(err),Map.of());
        return new Run(new DefaultCompilerCli().run(List.of(args),env,CancellationToken.none()),
                out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
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
        assertEquals(CliTermination.SUCCESS,ambiguous.result().termination());assertTrue(ambiguous.output().contains("ILI-RES-0008"));
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
    @Test void listSearchesDistinctIndexesInTheSameDirectoryAndReplaysTheSelectedIndex() throws Exception {
        Files.writeString(directory.resolve("first.xml"), "<TRANSFER>" + entry("1", "one.ili") + "</TRANSFER>");
        Files.writeString(directory.resolve("second [index].xml"), "<TRANSFER>" + entry("2", "two.ili") + "</TRANSFER>");
        Files.writeString(directory.resolve("one.ili"), model("1"));
        Files.writeString(directory.resolve("two.ili"), model("2"));
        Files.writeString(directory.resolve("root.ili"),
                "INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = IMPORTS Dep; END Root.");
        Files.writeString(directory.resolve("settings.properties"), "repository=absent\n");
        var result = run("check", "--config", "settings.properties", "--repositories", "first.xml;second [index].xml",
                "root.ili", "--model-pin", "Dep", "2", directory.resolve("two.ili").toUri().toString(),
                ContentDigest.sha256(Files.readAllBytes(directory.resolve("two.ili"))).value(),
                "--cache", "cache", "--write-lock", "closure.xml");
        assertEquals(CliTermination.SUCCESS, result.result().termination(), result.result().output());
        var lock = new ResolutionLockCodec().decode(Files.readString(directory.resolve("closure.xml")));
        assertEquals("2", lock.models().get("Dep").getFirst().modelVersion());
        var indexId = new guru.interlis.source.SourceId(directory.resolve("second [index].xml").toUri().toString());
        assertTrue(lock.indexes().containsKey(indexId));
        assertEquals(CliTermination.SUCCESS, run("check", "--repositories", "second [index].xml",
                "root.ili", "--cache", "cache", "--offline", "--read-lock", "closure.xml").result().termination());

        var mixed = run("check", "--repository", "second [index].xml", "--repositories", "first.xml", "--root", "Dep");
        assertEquals("2", mixed.result().resolutionLock().orElseThrow().models().get("Dep").getFirst().modelVersion());
        assertEquals(CliTermination.USAGE_ERROR, run("check", "--repositories", "first.xml;;second [index].xml",
                "--root", "Dep").result().termination());
    }

    @Test void defaultLookupPrefersNeighboursThenUsesTheVerifiedHttpsCache() throws Exception {
        Files.writeString(directory.resolve("root.ili"),
                "INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = IMPORTS Dep; END Root.");
        Path neighbour = directory.resolve("different-filename.ili");
        Files.writeString(neighbour, model("1"));
        Path cache = directory.resolve("cache");
        cache(cache, "https://models.interlis.ch/ilimodels.xml", "<TRANSFER>" + entry("2", "remote.ili") + "</TRANSFER>");
        cache(cache, "https://models.interlis.ch/remote.ili", model("2"));

        var local = run("check", "root.ili", "--offline", "--cache", "cache", "--write-lock", "local.xml", "--verbose");
        assertEquals(CliTermination.SUCCESS, local.result().termination(), local.result().output());
        assertTrue(local.result().repositorySteps().isEmpty());
        String scan = "INFO Searching for models in local directory: <" + directory.toAbsolutePath().normalize() + "> [ILI-RES-0015]";
        assertEquals(List.of(scan), local.stderr().lines().filter(line -> line.contains(" [ILI-RES-")).toList());
        assertTrue(local.result().diagnostics().isEmpty());
        assertFalse(Files.readString(directory.resolve("local.xml")).contains("ILI-RES-0015"));
        assertEquals(neighbour.toUri().toString(),
                local.result().resolutionLock().orElseThrow().models().get("Dep").getFirst().source().value());
        assertEquals(CliTermination.SUCCESS, run("check", "root.ili", "--offline", "--no-cache",
                "--read-lock", "local.xml").result().termination());

        Path nested = Files.createDirectory(directory.resolve("nested"));
        Files.move(neighbour, nested.resolve(neighbour.getFileName()));
        var remote = run("check", "root.ili", "--offline", "--cache", "cache", "--verbose");
        assertEquals(CliTermination.SUCCESS, remote.result().termination(), remote.result().output());
        assertEquals("https://models.interlis.ch/remote.ili",
                remote.result().resolutionLock().orElseThrow().models().get("Dep").getFirst().source().value());
        assertEquals("https://models.interlis.ch/", remote.result().repositorySteps().getFirst().repository().toString());
        var remoteLogs = remote.stderr().lines().filter(line -> line.contains(" [ILI-RES-")).toList();
        assertEquals(scan, remoteLogs.getFirst());
        assertTrue(remoteLogs.get(1).endsWith(" [ILI-RES-0011]"));
        var explicit = run("check", "root.ili", "--repository", repository(false).toString(), "--verbose");
        assertEquals(0, explicit.result().exitCode(), explicit.stderr());
        assertFalse(explicit.stderr().contains("ILI-RES-0015"));
        var complete = run("check", "root.ili", "nested/different-filename.ili", "--verbose");
        assertEquals(0, complete.result().exitCode(), complete.stderr());
        assertEquals(2, complete.stderr().lines().count());
        assertFalse(complete.stderr().contains("ILI-RES-"));
        var json = run("check", "root.ili", "--offline", "--cache", "cache", "--json", "--verbose");
        assertEquals(0, json.result().exitCode(), json.output());
        assertTrue(json.stderr().isEmpty());
        assertFalse(json.output().contains("ILI-RES-0015"));
    }

    static void cache(Path root, String uri, String content) throws IOException {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        var digest = ContentDigest.sha256(bytes);
        var source = new guru.interlis.source.SourceId(uri);
        new DirectoryContentStore(root).put(new VerifiedContent(source, digest, bytes));
        var metadata = Files.createDirectories(root.resolve("metadata-v2"));
        String name = ContentDigest.sha256(uri.getBytes(StandardCharsets.UTF_8)).value() + ".xml";
        Files.writeString(metadata.resolve(name), """
                <interlis-cache version="2" source="%s" response-source="%s" sha256="%s" downloaded-at="2026-01-01T00:00:00Z"/>
                """.formatted(uri, uri, digest.value()));
    }

    @Test void malformedRepositoryOptionsAndLockAreUsageErrors() throws Exception {
        assertEquals(CliTermination.USAGE_ERROR,run("check","--model-pin","Dep").result().termination());
        assertEquals(CliTermination.OPERATIONAL_FAILURE,
                run("check","--offline","--no-cache","--root","Dep").result().termination());
        assertEquals(CliTermination.USAGE_ERROR,run("format","--repository",directory.toString()).result().termination());
        var repo=repository(false);Files.writeString(directory.resolve("invalid.xml"),"<invalid/>");
        assertEquals(CliTermination.USAGE_ERROR,run("check","--repository",repo.toString(),"--root","Dep","--read-lock","invalid.xml").result().termination());
    }
}
