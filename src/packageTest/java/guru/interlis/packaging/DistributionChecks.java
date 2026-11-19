package guru.interlis.packaging;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

final class DistributionChecks {
    private static final Gson JSON = new Gson();
    private static final Path ROOT = Path.of(System.getProperty("repo.root"));

    @Test
    void compilerDistribution() throws Exception {
        checkArchive(ROOT, Path.of(System.getProperty("compiler.install")),
                Path.of(System.getProperty("compiler.zip")), false);
    }

    @Test
    void sdkDistribution() throws Exception {
        checkArchive(ROOT, Path.of(System.getProperty("compiler.install")),
                Path.of(System.getProperty("sdk.zip")), true);
    }

    @Test
    void bundledCompiler() throws Exception {
        Path folder = Files.createTempDirectory("interlis compiler ä ");
        try {
            extractArchive(Path.of(System.getProperty("compiler.app.zip")), folder);
            Path app = folder.resolve("interlis-compiler");
            verifyBundledRuntime(app);
            String launcher = launcher(app, "interlis-compiler").toString();
            Map<String, String> environment = withoutJava(folder);
            Files.copy(ROOT.resolve("testdata/models/base24.ili"), folder.resolve("-Gültig & [x]!.ili"));
            runBundled(folder, List.of(launcher, "check", "--offline", "--", "-Gültig & [x]!.ili"),
                    environment).requireExit(0);
            Files.copy(ROOT.resolve("testdata/models/broken24.ili"), folder.resolve("Ungültig.ili"));
            Result broken = runBundled(folder, List.of(launcher, "check", "--json", "Ungültig.ili"), environment);
            broken.requireExit(1);
            require(broken.stdout().contains("SOURCE_ERRORS"), "Expected compiler source errors");
            checkHttps(folder, Path.of(launcher), environment);
        } finally {
            deleteTree(folder);
        }
    }

    @Test
    void installedLspLifecycle() throws Exception {
        Path folder = Files.createTempDirectory("interlis LSP ä ");
        try {
            extractArchive(Path.of(System.getProperty("lsp.zip")), folder);
            Path lsp = folder.resolve("interlis-lsp");
            require(Files.isRegularFile(lsp.resolve("LICENSE"))
                    && Files.isRegularFile(lsp.resolve("licenses/NOTICE")), "Missing LSP licenses");
            require(Files.readString(lsp.resolve("types/editor-protocol.ts")).contains("'./diagram-document'")
                    && Files.isRegularFile(lsp.resolve("types/diagram-document.ts"))
                    && Files.isRegularFile(lsp.resolve("schemas/diagram-document-1.0.schema.json"))
                    && Files.isRegularFile(lsp.resolve("schemas/diagram-options-1.0.schema.json")), "Missing editor contracts");
            try (var jars = Files.list(lsp.resolve("lib"))) {
                for (Path jar : jars.toList()) {
                    String name = jar.getFileName().toString();
                    require(!name.startsWith("junit-") && !name.matches("antlr4-[0-9].*") && !name.endsWith("-tests.jar"),
                            "Unexpected LSP dependency: " + name);
                }
            }
            require(!Files.exists(lsp.resolve("runtime")), "LSP ZIP must be platform-independent");
            run(folder, List.of(System.getProperty("node.executable"),
                    ROOT.resolve("tools/verification/lsp-lifecycle.cjs").toString(), lsp.toString())).requireExit(0);
        } finally {
            deleteTree(folder);
        }
    }

    @Test
    void validatorDistribution() throws Exception {
        checkValidatorArchive(Path.of(System.getProperty("validator.zip")));
    }

    private static void verifyBundledRuntime(Path app) {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path java = app.resolve("runtime/bin/" + (windows ? "java.exe" : "java"));
        require(Files.isRegularFile(java) && (windows || Files.isExecutable(java)), "Missing bundled Java launcher");
        require(Files.isRegularFile(app.resolve("LICENSE"))
                && Files.isRegularFile(app.resolve("licenses/NOTICE"))
                && Files.isRegularFile(app.resolve("runtime/legal/java.base/LICENSE")), "Missing application licenses");
    }

    private static Map<String, String> withoutJava(Path folder) throws IOException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return Map.of("PATH", Path.of(System.getenv("SystemRoot"), "System32").toString());
        }
        Path tools = Files.createDirectory(folder.resolve("system tools"));
        // Gradle launchers need these POSIX tools; exclude the system's Java launcher.
        for (String tool : List.of("ls", "uname", "sed", "tr", "xargs")) {
            Path source = Path.of("/usr/bin", tool);
            Files.createSymbolicLink(tools.resolve(tool), Files.exists(source) ? source : Path.of("/bin", tool));
        }
        return Map.of("PATH", tools.toString());
    }

    private static void deleteTree(Path folder) throws IOException {
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    static Path launcher(Path distribution, String name) {
        return distribution.resolve("bin").resolve(name + (System.getProperty("os.name").startsWith("Windows") ? ".bat" : ""));
    }

    record Result(int exit, String stdout, String stderr) {
        void requireExit(int expected) {
            require(exit == expected, "Expected exit " + expected + ", got " + exit + "\n" + stdout + stderr);
        }
    }

    static void checkArchive(Path root, Path installation, Path archive, boolean sdk) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path folder = Files.createTempDirectory("interlis SDK ä ");
        try {
            extractArchive(archive, folder);
            Path cli = folder.resolve("cli");
            verifyInstallation(installation, cli);
            Path launcher = launcher(cli, "interlis-compiler");
            require(Files.isRegularFile(cli.resolve("bin/interlis-compiler"))
                    && Files.isRegularFile(cli.resolve("bin/interlis-compiler.bat"))
                    && (windows || Files.isExecutable(launcher)), "Missing or non-executable compiler launcher");
            var version = run(folder, List.of(launcher.toString(), "version", "--json"));
            version.requireExit(0);
            var info = (Map<?, ?>) JSON.fromJson(version.stdout(), Map.class);
            require(version.stderr().isEmpty() && "interlis-compiler".equals(info.get("tool"))
                    && System.getProperty("product.version").equals(info.get("version")), "Wrong compiler version");
            var revision = run(root, List.of("git", "rev-parse", "--verify", "HEAD"));
            revision.requireExit(0);
            require(revision.stdout().strip().equals(info.get("gitCommit")), "Compiler version does not identify the built commit");
            Files.copy(root.resolve("testdata/models/base24.ili"), folder.resolve("-Gültig & [x]!.ili"));
            run(folder, List.of(launcher.toString(), "check", "--offline", "--", "-Gültig & [x]!.ili")).requireExit(0);
            if (!sdk) return;

            require(info.equals(JSON.fromJson(Files.readString(folder.resolve("version/version-manifest.json")), Map.class)),
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
            deleteTree(folder);
        }
    }

    private static void checkValidatorArchive(Path archive) throws Exception {
        Path folder = Files.createTempDirectory("interlis validator ä ");
        try {
            DistributionChecks.extractArchive(archive, folder);
            Path installed = folder.resolve("interlis-validator");
            Path cwd = Files.createDirectory(folder.resolve("data files"));
            verifyBundledRuntime(installed);
            Map<String, String> environment = withoutJava(folder);
            var launcher = launcher(installed, "interlis-validator").toString();
            var version = runBundled(cwd, List.of(launcher, "--version", "--json"), environment);
            version.requireExit(0);
            var info = (Map<?, ?>) JSON.fromJson(version.stdout(), Map.class);
            if (!version.stderr().isEmpty() || !"interlis-validator".equals(info.get("tool"))
                    || !System.getProperty("product.version").equals(info.get("version"))) {
                throw new IllegalStateException("Wrong installed validator version");
            }
            Files.writeString(cwd.resolve("Smoke.ili"), """
                    INTERLIS 2.3;
                    MODEL Smoke AT "https://example.org" VERSION "1" =
                      TOPIC Data = CLASS Item = label : MANDATORY TEXT*3; END Item; END Data;
                    END Smoke.
                    """);
            String xtf = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                      <HEADERSECTION VERSION="2.3" SENDER="smoke"><MODELS>
                        <MODEL NAME="Smoke" VERSION="1" URI="https://example.org"/>
                      </MODELS></HEADERSECTION>
                      <DATASECTION><Smoke.Data BID="b"><Smoke.Data.Item TID="i">
                        <label>%s</label>
                      </Smoke.Data.Item></Smoke.Data></DATASECTION>
                    </TRANSFER>
                    """;
            Files.writeString(cwd.resolve("reference data.xtf"), xtf.formatted("reference"));
            for (String value : List.of("ok", "long")) {
                String file = "-Daten ä " + value + ".xtf";
                Files.writeString(cwd.resolve(file), xtf.formatted(value));
                var result = runBundled(cwd, List.of(launcher, "--offline", "--json", "--repository", ".",
                        "--refdata", "reference data.xtf", "--", file), environment);
                int expected = value.equals("ok") ? 0 : 1;
                result.requireExit(expected);
                var report = (Map<?, ?>) JSON.fromJson(result.stdout(), Map.class);
                if (!Boolean.TRUE.equals(report.get("completed"))
                        || !Double.valueOf(expected).equals(report.get("exitCode"))
                        || !Double.valueOf(1).equals(report.get("objects"))
                        || expected == 1 && !result.stdout().contains("TEXT_TOO_LONG")) {
                    throw new IllegalStateException("Unexpected installed validator report: " + result.stdout());
                }
            }
        } finally {
            deleteTree(folder);
        }
    }

    static void extractArchive(Path archive, Path folder) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
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
    }

    private static void checkCli(Path root, Path folder, Path launcher) throws Exception {
        String cli = launcher.toString();
        var capabilities = run(folder, List.of(cli, "capabilities"));
        capabilities.requireExit(0);
        var info = (Map<?, ?>) JSON.fromJson(capabilities.stdout(), Map.class);
        require("0.10".equals(info.get("apiVersion")) && "interlis.compiler/1.0".equals(info.get("protocol")),
                "Unexpected SDK capabilities");
        Files.copy(root.resolve("testdata/models/broken24.ili"), folder.resolve("Ungültig.ili"));
        var diagnostic = run(folder, List.of(cli, "check", "--json", "--color=always", "Ungültig.ili"));
        diagnostic.requireExit(1);
        var payload = (Map<?, ?>) JSON.fromJson(diagnostic.stdout(), Map.class);
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

        checkRepository(folder, launcher, HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0),
                "http", Map.of(), false);
    }

    private static void checkHttps(Path folder, Path launcher, Map<String, String> environment) throws Exception {
        // A private fixture exercises the shipped runtime with an explicit HTTPS trust store.
        Path store = folder.resolve("localhost.p12");
        String password = "interlis-distribution-test";
        run(folder, List.of(javaTool("keytool"), "-genkeypair", "-alias", "localhost",
                "-keyalg", "EC", "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1",
                "-validity", "2", "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", password, "-noprompt")).requireExit(0);
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(store)) {
            keys.load(input, password.toCharArray());
        }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, password.toCharArray());
        var context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, null);
        var https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(context));
        var options = new HashMap<>(environment);
        options.put("JAVA_OPTS", "\"-Djavax.net.ssl.trustStore=" + store + "\" -Djavax.net.ssl.trustStorePassword=" + password);
        checkRepository(folder, launcher, https, "https", options, true);
    }

    private static void checkRepository(Path folder, Path launcher, HttpServer server,
                                        String scheme, Map<String, String> environment, boolean bundled) throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
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
            var repository = run(folder, List.of(launcher.toString(), "check", "--repository",
                    scheme + "://127.0.0.1:" + server.getAddress().getPort() + "/", "--root", "Remote"),
                    environment, Duration.ofSeconds(90), bundled);
            repository.requireExit(0);
            require(repository.stderr().isEmpty() && requests.get() >= 2, "Expected real repository resolution over " + scheme);
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

    static Result run(Path cwd, List<String> command) throws Exception {
        return run(cwd, command, Map.of());
    }

    private static Result run(Path cwd, List<String> command, Map<String, String> environment) throws Exception {
        return run(cwd, command, environment, Duration.ofSeconds(90));
    }

    static Result run(Path cwd, List<String> command, Map<String, String> environment, Duration timeout) throws Exception {
        return run(cwd, command, environment, timeout, false);
    }

    private static Result runBundled(Path cwd, List<String> command, Map<String, String> environment) throws Exception {
        return run(cwd, command, environment, Duration.ofSeconds(90), true);
    }

    private static Result run(Path cwd, List<String> command, Map<String, String> environment,
                              Duration timeout, boolean bundledJava) throws Exception {
        Path logs = Files.createTempDirectory("interlis-process-");
        Path stdout = logs.resolve("stdout"), stderr = logs.resolve("stderr");
        Process process = null;
        try {
            var builder = new ProcessBuilder(command).directory(cwd.toFile())
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            if (bundledJava) builder.environment().remove("JAVA_HOME");
            else builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
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
