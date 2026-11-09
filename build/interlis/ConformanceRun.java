package build.interlis;

import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class ConformanceRun {
    public static void main(String[] args) throws Exception {
        if (args.length != 0 && (args.length != 2 || !args[0].equals("--filter") || args[1].isBlank())) {
            throw new IllegalArgumentException("Expected optional --filter REGEX");
        }
        String filter = args.length == 0 ? null : args[1];
        if (filter != null) java.util.regex.Pattern.compile(filter);
        Path root = Path.of("").toAbsolutePath().normalize();
        try (var lease = JvmBuildLock.acquire(root)) {
            run(root, filter);
        }
    }

    public static void run(Path root, String filter) throws Exception {
        var pin = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("testdata/conformance/suite.properties"))) {
            pin.load(reader);
        }
        String override = System.getenv("CONFORMANCE_CACHE");
        Path cache = override == null ? Path.of(System.getProperty("user.home"), ".cache/interlis-java/conformance")
                : Path.of(override).toAbsolutePath();
        Files.createDirectories(cache);
        Path archive = cache.resolve("suite-" + pin.getProperty("commit") + ".tar.gz");
        if (Files.notExists(archive)) {
            Path temporary = Files.createTempFile(cache, "suite-", ".part");
            try {
                var connection = URI.create(pin.getProperty("url")).toURL().openConnection();
                connection.setConnectTimeout(180000);
                connection.setReadTimeout(180000);
                try (var input = connection.getInputStream()) {
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                requirePin(temporary, pin);
                try { Files.move(temporary, archive); }
                catch (FileAlreadyExistsException ignored) { /* Concurrent downloader; verify its bytes below. */ }
            } finally { Files.deleteIfExists(temporary); }
        }
        requirePin(archive, pin);

        Path output = Files.createTempDirectory(Files.createDirectories(JvmBuildInputs.requireOutputRoot(
                root, "target/jenesis-jvm/conformance")), "run-");
        Path suite = output.resolve("suite");
        DistributionChecks.javaMain(root, JenesisBridge.buildArchiveTool(root), "build.interlis.archive.ExtractTar",
                List.of(archive.toString(), suite.toString())).requireExit(0);
        Path delivery = ExistingJvmDistribution.generate(root);
        Path cli = ExistingJvmDistribution.launcher(delivery.resolve("compiler"), "interlis-compiler");
        var compiler = Map.of("id", "interlis-java", "source", cli.toString(),
                "command", List.of("{compiler}", "check", "{files}"), "acceptedExitCodes", List.of(0),
                "rejectedExitCodes", List.of(1), "internalErrorPatterns", List.of("internal failure:", "Exception in thread"),
                "baseline", root.resolve("testdata/conformance/baseline.json").toString());
        var config = Map.of("suite", suite.resolve("suite/test-cases.json").toString(), "outputDirectory", output.toString(),
                "cacheDirectory", cache.resolve("fixtures").toString(), "timeoutSeconds", 60, "parallelism", 4,
                "xpassPolicy", "fail", "filter", filter == null ? "^(ili23|ili24)\\." : filter, "compilers", List.of(compiler));
        Path configFile = output.resolve("run-config.json");
        Files.writeString(configFile, JsonOutput.write(config));
        System.out.println("Codeberg reports: " + output);
        var result = DistributionChecks.run(root, List.of(DistributionChecks.javaTool("java"),
                suite.resolve("InterlisConformance.java").toString(), "run", "--config", configFile.toString()),
                Map.of(), Duration.ofMinutes(30));
        Files.writeString(output.resolve("runner.stdout"), result.stdout());
        Files.writeString(output.resolve("runner.stderr"), result.stderr());
        if (result.exit() != 1) result.requireExit(0);

        var build = JenesisBridge.artifacts(root, "+interlis-compiler-cli");
        var assessment = DistributionChecks.javaMain(root, JenesisBridge.testClasspath("interlis-compiler-cli", build),
                "guru.interlis.cli.conformance.NormativeConformance",
                List.of(root.toString(), output.toString(), Integer.toString(result.exit())));
        System.out.print(assessment.stdout());
        assessment.requireExit(0);
    }

    private static void requirePin(Path archive, Properties pin) throws Exception {
        if (!JvmBuildInputs.sha256(archive).equals(pin.getProperty("sha256"))) {
            throw new IllegalStateException("Suite pin mismatch: " + archive);
        }
    }
}
