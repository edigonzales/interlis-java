package guru.interlis.cli;

import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SemanticRepositoryCommandsTest {
    private static final List<String> COMMANDS = List.of("check", "compile", "analyze", "diagram");
    @TempDir Path directory;
    record Run(CliExecutionResult result, String out, String err) { }
    Run run(String command, String... options) {
        var args = new ArrayList<String>(); args.add(command); args.addAll(List.of(options));
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        var result = new DefaultCompilerCli().run(args, new CliEnvironment(directory, InputStream.nullInputStream(),
                new PrintStream(out), new PrintStream(err), Map.of()), CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    static String model(String name, String content) {
        return "INTERLIS 2.4; MODEL " + name + " AT \"x\" VERSION \"1\" = " + content + " END " + name + ".";
    }
    static String entry(String name, String file) {
        return "<ModelMetadata><Name>"+name+"</Name><SchemaLanguage>ili2_4</SchemaLanguage><File>"+file+"</File><Version>1</Version></ModelMetadata>";
    }
    Path repository(String name) throws Exception {
        Path repo = Files.createDirectory(directory.resolve(name));
        Files.writeString(repo.resolve("Base.ili"), model("Base", "DOMAIN Value=TEXT*20;"));
        Files.writeString(repo.resolve("Mid.ili"), model("Mid", "IMPORTS Base; DOMAIN Value EXTENDS Base.Value=TEXT*10;"));
        Files.writeString(repo.resolve("Root.ili"), model("Root", "IMPORTS Mid; TOPIC T=CLASS C=value:Mid.Value;END C;END T;"));
        Files.writeString(repo.resolve("ilimodels.xml"), "<TRANSFER>"+entry("Base","Base.ili")+entry("Mid","Mid.ili")+entry("Root","Root.ili")+"</TRANSFER>");
        return repo;
    }
    void success(String command, Run run) {
        assertEquals(CliTermination.SUCCESS, run.result.termination(), command+": "+run.err+run.result.output());
        assertTrue(run.err.isEmpty(), run.err);
        assertEquals(command.equals("diagram"), !run.out.isEmpty(), command);
    }
    @Test void transitiveNamedRootsLockAndOfflineReplayForEverySemanticCommand() throws Exception {
        for (String command : COMMANDS) {
            Path repo = repository(command);
            success(command, run(command, "--repository", repo.toString(), "--root", "Root", "--cache", command+"-cache", "--write-lock", command+".lock"));
            var lock = new ResolutionLockCodec().decode(Files.readString(directory.resolve(command+".lock")));
            assertEquals(Set.of("Root", "Mid", "Base"), lock.models().keySet());
            try (var files = Files.list(repo)) { for (var file : files.toList()) Files.delete(file); }
            success(command, run(command, "--repository", repo.toString(), "--root", "Root", "--cache", command+"-cache", "--read-lock", command+".lock", "--offline"));
            Files.writeString(directory.resolve(command+"-cache").resolve(lock.models().get("Base").getFirst().digest().value()), "corrupt");
            var corrupt = run(command, "--repository", repo.toString(), "--root", "Root", "--cache", command+"-cache", "--read-lock", command+".lock", "--offline");
            assertEquals(CliTermination.OPERATIONAL_FAILURE, corrupt.result.termination()); assertTrue(corrupt.out.isEmpty());
        }
    }
    @Test void explicitSourcesAndMultipleRepositoriesAndConfigurationPrecedence() throws Exception {
        Path repo = repository("repo"), second = Files.createDirectory(directory.resolve("second"));
        Files.move(repo.resolve("Base.ili"), second.resolve("Base.ili"));
        Files.writeString(repo.resolve("ilimodels.xml"), "<TRANSFER>"+entry("Mid","Mid.ili")+"</TRANSFER>");
        Files.writeString(second.resolve("ilimodels.xml"), "<TRANSFER>"+entry("Base","Base.ili")+"</TRANSFER>");
        Files.writeString(directory.resolve("settings.properties"), "repository=missing\nroot=Missing\nwarnings=reject\n");
        for (String command : COMMANDS) {
            success(command, run(command, "--config", "settings.properties", "--repository", repo.toString(), "--repository", second.toUri().toString(), "--root", "Root", repo.resolve("Root.ili").toString()));
            var explain = run(command, "--config", "settings.properties", "--repository", repo.toString(), "--explain-config");
            assertEquals(0, explain.result.exitCode()); assertTrue(explain.out.contains("CONFIGURATION_FILE")); assertTrue(explain.out.contains("CLI"));
        }
        Files.writeString(directory.resolve("settings.properties"), "repository="+repo.toUri()+"\nroot=Mid\n");
        // Configured named roots work without positional source files as well.
        Files.move(second.resolve("Base.ili"), repo.resolve("Base.ili"));
        Files.writeString(repo.resolve("ilimodels.xml"), "<TRANSFER>"+entry("Base","Base.ili")+entry("Mid","Mid.ili")+"</TRANSFER>");
        for (String command : COMMANDS) success(command, run(command,"--config","settings.properties"));
    }
    @Test void httpDiscoveryAndOfflineReplayDoesNotContactServer() throws Exception {
        Path repo = repository("http"); var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); byte[] bytes = Files.readAllBytes(repo.resolve(exchange.getRequestURI().getPath().substring(1)));
            exchange.sendResponseHeaders(200, bytes.length); try (var out = exchange.getResponseBody()) { out.write(bytes); }
        }); server.start();
        try {
            String base = "http://127.0.0.1:"+server.getAddress().getPort()+"/";
            for (String command : COMMANDS) {
                success(command, run(command, "--repository", base, "--root", "Root", "--cache", command+"-cache", "--write-lock", command+".lock"));
                int before = requests.get();
                success(command, run(command, "--repository", base, "--root", "Root", "--cache", command+"-cache", "--read-lock", command+".lock", "--offline"));
                assertEquals(before, requests.get());
            }
            assertTrue(requests.get() >= 16);
        } finally { server.stop(0); }
    }
    @Test void pinsAmbiguityMissingModelsIndicesAndLockCollisionsUseSameFailures() throws Exception {
        Path repo = repository("repo");
        String source = repo.resolve("Base.ili").toUri().toString();
        String original = Files.readString(repo.resolve("Base.ili"));
        String hash = ContentDigest.sha256(Files.readAllBytes(repo.resolve("Base.ili"))).value();
        for (String command : COMMANDS) {
            success(command, run(command, "--repository", repo.toString(), "--root", "Root", "--model-pin", "Base", "1", source, hash));
            assertEquals(CliTermination.INPUT_UNRESOLVED, run(command,"--repository",repo.toString(),"--root","Root","--model-pin","Base","1",source,"0".repeat(64)).result.termination());
            assertEquals(CliTermination.INPUT_UNRESOLVED, run(command,"--repository",repo.toString(),"--root","Missing").result.termination());
            for (String file : List.of("Base.ili", "ilimodels.xml"))
                assertEquals(CliTermination.USAGE_ERROR, run(command,"--repository",repo.toString(),"--root","Root","--write-lock",repo.resolve(file).toString()).result.termination());
        }
        assertEquals(original, Files.readString(repo.resolve("Base.ili")));
        Files.writeString(repo.resolve("Other.ili"), original.replace("VERSION \"1\"", "VERSION \"2\""));
        String index = Files.readString(repo.resolve("ilimodels.xml"));
        Files.writeString(repo.resolve("ilimodels.xml"), index.replace("</TRANSFER>", entry("Base","Other.ili").replace("<Version>1", "<Version>2")+"</TRANSFER>"));
        for (String command : COMMANDS) {
            var ambiguous = run(command,"--repository",repo.toString(),"--root","Root");
            assertEquals(CliTermination.INPUT_UNRESOLVED, ambiguous.result.termination()); assertTrue(ambiguous.result.output().contains("AMBIGUOUS")); assertTrue(ambiguous.out.isEmpty());
        }
        Files.writeString(repo.resolve("ilimodels.xml"), "<broken");
        for (String command : COMMANDS) {
            var broken = run(command,"--repository",repo.toString(),"--root","Root");
            assertEquals(CliTermination.OPERATIONAL_FAILURE, broken.result.termination()); assertTrue(broken.out.isEmpty());
        }
    }
    @Test void malformedLocksSourcePinsAndCancellationDoNotPublishArtifacts() throws Exception {
        Path repo = repository("repo");
        Files.writeString(directory.resolve("invalid.lock"), "<invalid/>");
        for (String command : COMMANDS) {
            assertEquals(2, run(command,"--repository",repo.toString(),"--root","Root","--read-lock","invalid.lock").result.exitCode());
            assertEquals(1, run(command,"--repository",repo.toString(),"--root","Root","--source-pin",repo.resolve("ilimodels.xml").toUri().toString(),"0".repeat(64)).result.exitCode());
            var args = List.of(command,"--repository",repo.toString(),"--root","Root","--write-lock",command+".lock");
            var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
            var result = new DefaultCompilerCli().run(args, new CliEnvironment(directory, InputStream.nullInputStream(),
                    new PrintStream(out), new PrintStream(err), Map.of()), CancellationToken.requested(guru.interlis.execution.CancellationReason.USER_REQUESTED));
            assertEquals(CliTermination.CANCELLED, result.termination()); assertTrue(result.artifacts().isEmpty());
            assertEquals("", out.toString()); assertFalse(Files.exists(directory.resolve(command+".lock")));
        }
    }
    @Test void warningPolicyAndLockWriteFailuresAreAppliedToNewCommands() throws Exception {
        Path repo = repository("repo");
        Files.writeString(repo.resolve("Base.ili"), model("Base", "DOMAIN Value=TEXT*20; TOPIC T(ABSTRACT,ABSTRACT)=END T;"));
        assertEquals(CliTermination.WARNINGS_REJECTED, run("diagram","--repository",repo.toString(),"--root","Root","--warnings","reject").result.termination());
        assertEquals(0, run("analyze","--repository",repo.toString(),"--root","Root","--warnings","reject").result.exitCode());
        for (String command : COMMANDS) {
            var failed = run(command,"--repository",repo.toString(),"--root","Root","--write-lock","missing/target.lock");
            assertEquals(CliTermination.OPERATIONAL_FAILURE, failed.result.termination()); assertTrue(failed.out.isEmpty()); assertTrue(failed.result.artifacts().isEmpty());
        }
    }
    @Test void analyzeStillReturnsDiagnosticsAndLockWithoutCertifyingSchema() throws Exception {
        Path repo = repository("repo");
        Files.writeString(repo.resolve("Base.ili"), model("Base", "DOMAIN Value=TEXT*20; TOPIC Office=CLASS Office=END Office;END Office;"));
        var analysis = run("analyze", "--repository", repo.toString(), "--root", "Root", "--verbose", "--write-lock", "analysis.lock");
        assertEquals(0, analysis.result.exitCode(), analysis.err+analysis.result.output()); assertFalse(analysis.result.diagnostics().isEmpty());
        assertTrue(analysis.err.contains(" | "), analysis.err); assertFalse(analysis.err.contains("@1 (UTF-16"));
        assertTrue(Files.exists(directory.resolve("analysis.lock")));
        var diagram = run("diagram", "--repository", repo.toString(), "--root", "Root", "--write-lock", "diagram.lock");
        assertEquals(1, diagram.result.exitCode()); assertTrue(diagram.out.isEmpty()); assertFalse(Files.exists(directory.resolve("diagram.lock")));
    }
}
