package build.interlis;

import build.jenesis.Jpx;
import build.jenesis.PathPlacement;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Builds and runs the original swisstopo JUnit tests against the JVM products. */
public final class ValidatorConformanceRun {
    public static void main(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 4) || !args[0].equals("--suite-dir")
                || args[1].isBlank() || args.length == 4 && (!args[2].equals("--filter") || args[3].isBlank())) {
            throw new IllegalArgumentException("Expected --suite-dir PATH [--filter REGEX]");
        }
        String filter = args.length == 4 ? Pattern.compile(args[3]).pattern() : ".*";
        Path root = Path.of("").toAbsolutePath();
        Path suite = Path.of(args[1]).toRealPath();
        Path routine = suite.resolve("routine");
        Path originals = routine.resolve("src/test/java/ch/interlis/testsuite");
        Path models = suite.resolve("data/models");
        if (!Files.isDirectory(originals) || !Files.isDirectory(models) || !Files.isReadable(models)) {
            throw new IllegalArgumentException("Expected suite test sources and model directory in " + suite);
        }
        try (var lease = JvmBuildLock.acquire(root)) {
            run(root, routine, originals, filter);
        }
    }

    private static void run(Path root, Path routine, Path originals, String filter) throws Exception {
        var sources = new ArrayList<String>();
        try (var files = Files.walk(originals)) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(sources::add);
        }
        if (sources.isEmpty()) throw new IllegalArgumentException("No original JUnit sources in " + originals);
        try (var files = Files.walk(root.resolve("build/interlis/validator-conformance/src"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(sources::add);
        }
        var products = List.of("interlis-workspace", "interlis-xtf", "interlis-validation-jts");
        var build = JenesisBridge.artifacts(root, products.stream().map(p -> "+" + p).toArray(String[]::new));
        // Multiple product closures can contain different copies of the same artifact.
        var jars = new LinkedHashMap<String, Path>();
        for (var product : products) {
            for (var artifact : ModuleArtifacts.runtime(root, product, build)) {
                jars.putIfAbsent(artifact.coordinates(), artifact.file());
            }
        }
        var resolver = new Jpx(PathPlacement.CLASS_PATH);
        var testJars = new LinkedHashSet<Path>();
        for (String coordinate : List.of("org.junit.platform:junit-platform-console-standalone@1.11.4",
                "org.slf4j:slf4j-simple@2.0.18")) {
            var installation = resolver.install(coordinate);
            for (String entry : installation.properties().getProperty("classpath").split(",")) {
                testJars.add(installation.folder().resolve(entry));
            }
        }
        var paths = new LinkedHashSet<>(jars.values());
        paths.addAll(testJars);
        var classpath = paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        Path output = Files.createTempDirectory(Files.createDirectories(JvmBuildInputs.requireOutputRoot(
                root, "target/jenesis-jvm/validator-conformance")), "run-");
        System.out.println("Validator conformance reports: " + output);
        Path classes = Files.createDirectory(output.resolve("classes"));
        var javac = new ArrayList<>(List.of(DistributionChecks.javaTool("javac"), "-encoding", "UTF-8",
                "-cp", classpath, "-d", classes.toString()));
        javac.addAll(sources);
        var compilation = DistributionChecks.run(root, javac);
        Files.writeString(output.resolve("javac.stdout"), compilation.stdout());
        Files.writeString(output.resolve("javac.stderr"), compilation.stderr());
        compilation.requireExit(0);

        var result = DistributionChecks.run(routine, List.of(DistributionChecks.javaTool("java"),
                "-Dvendor=interlis-java", "-cp", classpath + File.pathSeparator + classes,
                "guru.interlis.conformance.SuiteJUnit", classes.toString(), filter, output.toString()),
                Map.of(), Duration.ofMinutes(10));
        Files.writeString(output.resolve("runner.stdout"), result.stdout());
        Files.writeString(output.resolve("runner.stderr"), result.stderr());
        if (result.exit() != 0) {
            throw new IllegalStateException("Validator conformance failed (exit " + result.exit() + "). See " + output);
        }
    }
}
