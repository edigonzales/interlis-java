package build.interlis;

import java.io.File;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs the public validation example against the built SDK artifacts. */
public final class ModulePathContract {
    public static String name(String project) {
        if (!project.matches("interlis-[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Unknown own module");
        }
        return "guru.interlis." + project.substring(9).replace('-', '.');
    }

    public static void requireOwnDescriptor(String project, ModuleDescriptor descriptor) {
        if (descriptor.isAutomatic() || descriptor.isOpen() || !descriptor.name().equals(name(project))) {
            throw new IllegalStateException("Own runtime module must be explicit and encapsulated: " + project);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("Expected no arguments");
        }
        var root = Path.of("").toAbsolutePath().normalize();
        try (var lease = JvmBuildLock.acquire(root)) {
            run(root);
        }
    }

    public static void run(Path root) throws Exception {
        var products = List.of("interlis-projection", "interlis-xtf", "interlis-validation");
        var outcome = JenesisBridge.artifacts(root, products.stream().map(id -> "+" + id).toArray(String[]::new));
        var jars = new LinkedHashSet<Path>();
        for (var product : products) {
            ModuleArtifacts.runtime(root, product, outcome).forEach(artifact -> jars.add(artifact.file()));
        }
        var path = String.join(File.pathSeparator, jars.stream().map(Path::toString).toList());
        var sources = root.resolve("examples/sdk/validation");
        var output = Files.createTempDirectory(JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm"), "sdk-example-");
        var named = Files.createDirectory(output.resolve("named"));
        var unnamed = Files.createDirectory(output.resolve("classpath"));
        var source = sources.resolve("ValidationExample.java").toString();
        process(root, "javac", "--release", "25", "--module-path", path, "-d", named.toString(),
                sources.resolve("module-info.java").toString(), source);
        process(root, "java", "--module-path", path + File.pathSeparator + named, "--module",
                "interlis.validation.example/example.validation.ValidationExample");
        process(root, "javac", "--release", "25", "-cp", path, "-d", unnamed.toString(), source);
        process(root, "java", "-cp", path + File.pathSeparator + unnamed,
                "example.validation.ValidationExample", "classpath");
    }

    private static void process(Path root, String executable, String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("SDK consumer process failed: " + executable);
            }
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
