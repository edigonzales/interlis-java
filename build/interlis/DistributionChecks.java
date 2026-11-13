package build.interlis;

import build.jenesis.Json;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

final class DistributionChecks {
    record Result(int exit, String stdout, String stderr) {
        void requireExit(int expected) {
            require(exit == expected, "Expected exit " + expected + ", got " + exit + "\n" + stdout + stderr);
        }
    }

    static void check(Path root, Path delivery) throws Exception {
        checkArchive(root, delivery, delivery.resolve("sdk.zip"), true);
        String node = System.getenv().getOrDefault("INTERLIS_NODE", "node");
        run(root, List.of(node, root.resolve("tools/verification/lsp-lifecycle.cjs").toString(),
                delivery.resolve("lsp").toString())).requireExit(0);
    }

    static void checkArchive(Path root, Path delivery, Path archive, boolean sdk) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path folder = Files.createTempDirectory("interlis SDK ä ");
        try {
            try (var zip = new ZipFile(archive.toFile())) {
                var paths = new HashSet<Path>();
                for (var entry : zip.stream().toList()) {
                    Path target = folder.resolve(entry.getName()).normalize();
                    require(!entry.getName().contains("\\") && target.startsWith(folder)
                            && !target.equals(folder) && paths.add(target), "Unsafe or duplicate ZIP entry " + entry.getName());
                    if (!windows) continue;
                    if (entry.isDirectory()) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        try (var input = zip.getInputStream(entry)) { Files.copy(input, target); }
                    }
                }
            }
            if (!windows) run(folder, List.of("unzip", "-q", archive.toString())).requireExit(0);

            Path cli = folder.resolve("cli");
            verifyInstallation(delivery.resolve("compiler"), cli);
            Path launcher = ExistingJvmDistribution.launcher(cli, "interlis-compiler");
            require(Files.isRegularFile(cli.resolve("bin/interlis-compiler"))
                    && Files.isRegularFile(cli.resolve("bin/interlis-compiler.bat"))
                    && (windows || Files.isExecutable(launcher)), "Missing or non-executable compiler launcher");
            var version = run(folder, List.of(launcher.toString(), "version", "--json"));
            version.requireExit(0);
            var info = (Map<?, ?>) Json.parse(version.stdout());
            require(version.stderr().isEmpty() && "interlis-compiler".equals(info.get("tool"))
                    && info.equals(Json.parse(Files.readString(delivery.resolve("version.json")))), "Wrong compiler version");
            var revision = run(root, List.of("git", "rev-parse", "--verify", "HEAD"));
            revision.requireExit(0);
            require(revision.stdout().strip().equals(info.get("gitCommit")), "Compiler version does not identify the built commit");
            Files.copy(root.resolve("testdata/models/base24.ili"), folder.resolve("-Gültig & [x]!.ili"));
            run(folder, List.of(launcher.toString(), "check", "--offline", "--", "-Gültig & [x]!.ili")).requireExit(0);
            if (!sdk) return;

            require(info.equals(Json.parse(Files.readString(folder.resolve("version/version-manifest.json")))),
                    "Wrong version manifest in SDK archive");
            checkCli(root, folder, launcher);
            Path classes = Files.createDirectory(folder.resolve("classes"));
            var moduleOptions = List.of("--module-path", cli.resolve("lib").toString(), "--add-modules", "guru.interlis.compiler");
            var javac = new ArrayList<>(List.of(javaTool("javac"), "--release", "25", "-encoding", "UTF-8"));
            javac.addAll(moduleOptions);
            javac.addAll(List.of("-d", classes.toString(), "examples/sdk/SdkExample.java"));
            run(folder, javac).requireExit(0);
            var java = new ArrayList<>(List.of(javaTool("java")));
            java.addAll(moduleOptions);
            java.addAll(List.of("-cp", classes.toString(), "SdkExample"));
            var consumer = run(folder, java);
            consumer.requireExit(0);
            require(consumer.stdout().startsWith("SDK PASS ") && consumer.stderr().isEmpty(), "SDK consumer failed");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void checkCli(Path root, Path folder, Path launcher) throws Exception {
        String cli = launcher.toString();
        var capabilities = run(folder, List.of(cli, "capabilities"));
        capabilities.requireExit(0);
        var info = (Map<?, ?>) Json.parse(capabilities.stdout());
        require("0.9".equals(info.get("apiVersion")) && "interlis.compiler/1.0".equals(info.get("protocol")),
                "Unexpected SDK capabilities");
        Files.copy(root.resolve("testdata/models/broken24.ili"), folder.resolve("Ungültig.ili"));
        var diagnostic = run(folder, List.of(cli, "check", "--json", "--color=always", "Ungültig.ili"));
        diagnostic.requireExit(1);
        var payload = (Map<?, ?>) Json.parse(diagnostic.stdout());
        require(diagnostic.stderr().isEmpty() && !diagnostic.stdout().contains("\u001b")
                && ((String) payload.get("status")).contains("SOURCE_ERRORS")
                && !((List<?>) payload.get("diagnostics")).isEmpty(), "Expected clean JSON source diagnostics");
        checkTerminal(folder, launcher);
        var usage = run(folder, List.of(cli, "check", "--no-such-option", "Ungültig.ili"));
        usage.requireExit(2);
        require(usage.stdout().isEmpty() && !usage.stderr().isEmpty(), "Usage output must use stderr");
        Path encoding = folder.resolve("Encoding.ili");
        Files.writeString(encoding, Files.readString(folder.resolve("-Gültig & [x]!.ili")) + "\n!! Grüezi 😀\n");
        var format = run(folder, List.of(cli, "format", encoding.toString()),
                Map.of("JAVA_OPTS", "-Dstdout.encoding=windows-1252 -Dstderr.encoding=windows-1252"));
        format.requireExit(0);
        require(format.stderr().isEmpty() && format.stdout().contains("Grüezi 😀"), "CLI output must remain UTF-8");

        var requests = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String text = exchange.getRequestURI().getPath().equals("/ilimodels.xml")
                    ? "<TRANSFER><ModelMetadata><Name>Remote</Name><SchemaLanguage>ili2_4</SchemaLanguage>"
                        + "<File>Remote.ili</File><Version>1</Version></ModelMetadata></TRANSFER>"
                    : "INTERLIS 2.4; MODEL Remote AT \"x\" VERSION \"1\" = END Remote.";
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var repository = run(folder, List.of(cli, "check", "--repository",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", "--root", "Remote"));
            repository.requireExit(0);
            require(repository.stderr().isEmpty() && requests.get() >= 2, "Expected real HTTP repository resolution");
        } finally { server.stop(0); }
    }

    private static void verifyInstallation(Path source, Path extracted) throws Exception {
        try (var paths = Files.walk(source); var archivePaths = Files.walk(extracted)) {
            var expected = paths.map(source::relativize).collect(java.util.stream.Collectors.toSet());
            var actual = archivePaths.map(extracted::relativize).collect(java.util.stream.Collectors.toSet());
            require(expected.equals(actual), "Compiler ZIP differs from the staged installation");
            for (Path path : expected) {
                Path original = source.resolve(path);
                Path copy = extracted.resolve(path);
                require(Files.isDirectory(original)
                        ? Files.isDirectory(copy, LinkOption.NOFOLLOW_LINKS)
                        : Files.isRegularFile(copy, LinkOption.NOFOLLOW_LINKS) && Files.mismatch(original, copy) == -1,
                        "Compiler ZIP entry differs: " + path);
            }
        }
    }

    private static void checkTerminal(Path folder, Path launcher) throws Exception {
        if (!System.getProperty("os.name").equals("Mac OS X")) return;
        Path script = folder.resolve("terminal-check.sh");
        Path error = folder.resolve("redirect.err");
        for (boolean redirect : List.of(false, true)) {
            Files.writeString(script, """
                    test -t 1 && test -t 2 || exit 80
                    "$1" check --color=auto "$2"%s
                    test "$?" = 1 || exit 81
                    """.formatted(redirect ? " 2>\"$3\"" : ""));
            Path capture = folder.resolve("terminal-" + redirect + ".txt");
            var result = run(folder, List.of("/usr/bin/script", "-q", capture.toString(), "/bin/sh",
                    script.toString(), launcher.toString(), folder.resolve("Ungültig.ili").toString(), error.toString()),
                    Map.of("NO_COLOR", "", "TERM", "xterm"));
            result.requireExit(0);
            String text = Files.readString(redirect ? error : capture);
            require(text.contains("ILI-") && text.contains("\u001b[31mERROR\u001b[0m") != redirect,
                    "Wrong diagnostic or auto color for stderr terminal");
        }
    }

    static String javaTool(String name) {
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

    static Result javaMain(Path cwd, List<Path> classpath, String main, List<String> args) throws Exception {
        String cp = String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList());
        var command = new ArrayList<>(List.of(javaTool("java"), "-cp", cp, main));
        command.addAll(args);
        return run(cwd, command);
    }

    static Result run(Path cwd, List<String> command) throws Exception {
        return run(cwd, command, Map.of());
    }

    private static Result run(Path cwd, List<String> command, Map<String, String> environment) throws Exception {
        return run(cwd, command, environment, Duration.ofSeconds(90));
    }

    static Result run(Path cwd, List<String> command, Map<String, String> environment, Duration timeout) throws Exception {
        Path logs = Files.createTempDirectory("interlis-process-");
        Path stdout = logs.resolve("stdout"), stderr = logs.resolve("stderr");
        Process process = null;
        try {
            var builder = new ProcessBuilder(command).directory(cwd.toFile())
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
            builder.environment().putAll(environment);
            if (command.getFirst().equals("/usr/bin/script")) builder.redirectInput(Path.of("/dev/null").toFile());
            process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Process timed out: " + command);
            return new Result(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
            Files.delete(logs);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
