package build.interlis;

import build.jenesis.Jpx;
import build.jenesis.PathPlacement;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Builds and runs the original swisstopo JUnit tests against the JVM products. */
public final class ValidatorConformanceRun {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 6 || args.length % 2 != 0 || !args[0].equals("--suite-dir")) {
            throw new IllegalArgumentException("Expected --suite-dir PATH [--validator-zip ZIP] [--filter REGEX]");
        }
        String filter = ".*";
        Path suppliedZip = null;
        var seen = new LinkedHashSet<String>();
        for (int i = 2; i < args.length; i += 2) {
            if (!seen.add(args[i]) || args[i + 1].isBlank()) throw new IllegalArgumentException("Invalid option " + args[i]);
            switch (args[i]) {
                case "--filter" -> filter = Pattern.compile(args[i + 1]).pattern();
                case "--validator-zip" -> suppliedZip = Path.of(args[i + 1]).toRealPath();
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        Path root = Path.of("").toAbsolutePath();
        Path suite = Path.of(args[1]).toRealPath();
        Path routine = suite.resolve("routine");
        Path originals = routine.resolve("src/test/java/ch/interlis/testsuite");
        Path models = suite.resolve("data/models");
        if (!Files.isDirectory(originals) || !Files.isDirectory(models) || !Files.isReadable(models)) {
            throw new IllegalArgumentException("Expected suite test sources and model directory in " + suite);
        }
        try (var lease = JvmBuildLock.acquire(root)) {
            Path zip = suppliedZip == null ? ExistingJvmDistribution.packageValidator(root) : suppliedZip;
            Path installation = Files.createTempDirectory("interlis suite validator ");
            try {
                DistributionChecks.extractArchive(zip, installation);
                Path launcher = ExistingJvmDistribution.launcher(installation.resolve("interlis-validator"), "interlis-validator");
                run(root, routine, originals, filter, launcher);
            } finally {
                BuildOutputs.deleteTree(installation, java.util.Set.of());
            }
        }
    }

    private static void run(Path root, Path routine, Path originals, String filter, Path launcher) throws Exception {
        var sources = new ArrayList<String>();
        try (var files = Files.walk(originals)) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(sources::add);
        }
        if (sources.isEmpty()) throw new IllegalArgumentException("No original JUnit sources in " + originals);
        try (var files = Files.walk(root.resolve("build/interlis/validator-conformance/src"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(sources::add);
        }
        var resolver = new Jpx(PathPlacement.CLASS_PATH);
        var testJars = new LinkedHashSet<Path>();
        for (String coordinate : List.of("org.junit.platform:junit-platform-console-standalone@1.11.4",
                "org.slf4j:slf4j-simple@2.0.18", "com.google.code.gson:gson@2.14.0")) {
            var installation = resolver.install(coordinate);
            for (String entry : installation.properties().getProperty("classpath").split(",")) {
                testJars.add(installation.folder().resolve(entry));
            }
        }
        var classpath = testJars.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
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

        Path adapterTests = Files.createDirectory(output.resolve("adapter-tests"));
        Path adapterTest = root.resolve("build/interlis/validator-conformance/test/guru/interlis/conformance/SuiteValidationTest.java");
        String suiteClasspath = classpath + File.pathSeparator + classes;
        DistributionChecks.run(root, List.of(DistributionChecks.javaTool("javac"), "-encoding", "UTF-8",
                "-cp", suiteClasspath, "-d", adapterTests.toString(), adapterTest.toString())).requireExit(0);
        var probes = DistributionChecks.run(root, List.of(DistributionChecks.javaTool("java"),
                "-Dsuite.validator=" + launcher, "-Dsuite.directory=" + routine.getParent(), "-Dsuite.classes=" + classes, "-cp", suiteClasspath + File.pathSeparator + adapterTests,
                "org.junit.platform.console.ConsoleLauncher", "execute", "--disable-ansi-colors",
                "--fail-if-no-tests", "--reports-dir", output.resolve("adapter-reports").toString(),
                "--select-class", "guru.interlis.conformance.SuiteValidationTest"));
        Files.writeString(output.resolve("adapter.stdout"), probes.stdout());
        Files.writeString(output.resolve("adapter.stderr"), probes.stderr());
        probes.requireExit(0);

        var result = DistributionChecks.run(routine, List.of(DistributionChecks.javaTool("java"),
                "-Dvendor=interlis-java", "-Dsuite.validator=" + launcher, "-cp", suiteClasspath,
                "guru.interlis.conformance.SuiteJUnit", classes.toString(), filter, output.toString(),
                root.resolve("build/interlis/validator-conformance/deviations.tsv").toString()),
                Map.of(), Duration.ofMinutes(20));
        Files.writeString(output.resolve("runner.stdout"), result.stdout());
        Files.writeString(output.resolve("runner.stderr"), result.stderr());
        if (result.exit() != 0) {
            throw new IllegalStateException("Validator conformance failed (exit " + result.exit() + "). See " + output);
        }
    }
}
