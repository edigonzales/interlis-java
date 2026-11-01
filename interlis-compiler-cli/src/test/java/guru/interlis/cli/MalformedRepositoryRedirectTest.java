package guru.interlis.cli;

import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class MalformedRepositoryRedirectTest {
    @TempDir Path directory;
    HttpServer server;
    @AfterEach void close() { if (server != null) server.stop(0); }

    @Test void malformedRedirectsRemainOperationalFailuresForAllRepositoryCommands() throws Exception {
        var location = new AtomicReference<>("http://user:marked-secret@[");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Location", location.get());
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        for (String target : List.of("http://user:marked-secret@[", "http:/missing-host")) {
            location.set(target);
            for (String command : List.of("check", "compile", "analyze", "diagram")) {
                var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
                var args = new ArrayList<>(List.of(command, "--root", "Missing", "--repository",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/", "--no-cache", "--no-proxy"));
                if (command.equals("diagram")) args.addAll(List.of("--format", "json")); else args.add("--json");
                var env = new CliEnvironment(directory, InputStream.nullInputStream(), new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
                var result = new DefaultCompilerCli().run(args, env, CancellationToken.none());
                assertEquals(1, result.exitCode(), command);
                assertEquals(CliTermination.OPERATIONAL_FAILURE, result.termination(), command);
                String output = out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
                assertTrue(result.diagnostics().stream().flatMap(d -> d.arguments().stream()).anyMatch(a ->
                        a instanceof guru.interlis.diagnostics.DiagnosticArgument.TextArgument text
                                && text.name().equals("reason") && text.value().equals("IO_FAILURE")), output);
                assertFalse(output.contains("marked-secret"), output);
                assertFalse(output.contains("internal failure"), output);
                assertFalse(output.contains("IllegalArgumentException"), output);
            }
        }
    }
}
