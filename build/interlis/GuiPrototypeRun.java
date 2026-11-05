package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import build.jenesis.Environment;
import build.jenesis.Make;
import build.jenesis.Project;
import java.nio.file.*;
import java.util.*;

/** Optional Jenesis project: keeps the normal ten-module producer and its consumers unchanged. */
public final class GuiPrototypeRun {
    public static void run(BuildRequest request) throws Exception {
        if (!System.getProperty("os.name").equals("Mac OS X") || !System.getProperty("os.arch").equals("aarch64"))
            throw new IllegalStateException("Phase 1 currently pins macOS ARM64 JavaFX only");
        Path root = request.root(), output = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-gui");
        Files.createDirectories(output);
        try (var channel = java.nio.channels.FileChannel.open(output.resolve("project.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lease = channel.tryLock()) {
            if (lease == null) throw new IllegalStateException("Another GUI build is active");
            String node = System.getenv("INTERLIS_NODE");
            if (node == null || !Path.of(node).isAbsolute()) throw new IllegalArgumentException("Set INTERLIS_NODE to the absolute pinned Node 24.22.0 executable");
            var version = new ProcessBuilder(node, "--version").start();
            String actual = new String(version.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (version.waitFor() != 0 || !actual.equals("v24.22.0")) throw new IllegalStateException("Expected Node v24.22.0; got " + actual);
            prepareBundle(root, node);
            Path projectRoot = root.resolve("interlis-compiler-gui");
            var settings = new LinkedHashMap<>(Make.settings(root).keys());
            settings.put("maven.local", ""); settings.put("module.local", "");
            settings.put("dependency.pin", "strict");
            var environment = new Environment(settings).root(projectRoot);
            List<Path> paths;
            try (var sdkLease = JvmBuildLock.acquire(root)) {
                var sdk = GuiSdk.prepare(root, environment);
                var project = Project.ofEnvironment(environment, projectRoot)
                        .repositories(Map.of("maven", sdk.repository()))
                        .target(JvmBuildInputs.requireOutputRoot(root, "target/jenesis-gui/engine/sdk-" + sdk.digest()))
                        .tests(false).sources(false);
                System.out.println("Building optional JavaFX module with Jenesis 0.18.1");
                paths = runtimePaths(project.build("build"));
                sdk.verify(paths);
            }
            var modules = java.lang.module.ModuleFinder.of(paths.toArray(Path[]::new)).findAll();
            if (!modules.stream().map(m -> m.descriptor().name()).collect(java.util.stream.Collectors.toSet()).equals(Set.of(
                    "guru.interlis.compiler.gui", "guru.interlis.model", "guru.interlis.compiler", "guru.interlis.projection", "guru.interlis.workspace",
                    "org.antlr.antlr4.runtime", "javafx.base", "javafx.graphics", "javafx.controls", "javafx.media", "javafx.web"))
                    || modules.stream().anyMatch(m -> m.descriptor().isAutomatic() && !m.descriptor().name().equals("org.antlr.antlr4.runtime")))
                throw new IllegalStateException("Unexpected GUI module path: " + paths);
            if (request.profile().equals("gui-package")) {
                GuiPackaging.run(root, output, Path.of(request.options().get("--javafx-jmods")), paths, environment);
                return;
            }
            String modulePath = String.join(java.io.File.pathSeparator, paths.stream().map(Path::toString).toList());
            Files.writeString(output.resolve("module-path.txt"), modulePath);
            var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                    "--enable-native-access=javafx.graphics,javafx.web", "--module-path", modulePath,
                    "--module", "guru.interlis.compiler.gui/guru.interlis.compiler.gui.CompilerGui"));
            if (request.profile().equals("gui-check")) {
                prepareProbeModels(root, output);
                command.addAll(List.of("--probe", output.resolve("webview-probe.txt").toString()));
            }
            process(root, command);
        }
    }

    private static void prepareBundle(Path root, String node) throws Exception {
        Path lock = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/consumer.lock");
        try (var channel = java.nio.channels.FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lease = channel.tryLock()) {
            if (lease == null) throw new IllegalStateException("Another renderer consumer is active");
            for (String folder : List.of("dist", "generated", "node_modules", "build"))
                JvmBuildInputs.requireOutputRoot(root, "frontend/diagram-ui/" + folder);
            JvmBuildInputs.requireOutputRoot(root, "interlis-compiler-gui/src/main/resources/guru/interlis/compiler/gui/web");
            process(root, List.of(node, root.resolve("tools/gui/bundle.cjs").toString()));
        }
    }

    private static List<Path> runtimePaths(Map<String, Path> results) throws Exception {
        var paths = new LinkedHashSet<Path>();
        for (Path result : results.values()) {
            Path inventory = result.resolve("inventory.properties");
            if (!Files.isRegularFile(inventory)) continue;
            var data = new Properties(); try (var reader = Files.newBufferedReader(inventory)) { data.load(reader); }
            for (String key : data.stringPropertyNames()) if (key.matches(".*\\.runtime\\.[0-9]+")) {
                Path jar = result.resolve(data.getProperty(key)).normalize();
                if (!Files.isRegularFile(jar)) throw new IllegalStateException("Missing GUI runtime artifact: " + jar);
                paths.add(jar);
            }
        }
        return List.copyOf(paths);
    }
    static void prepareProbeModels(Path root, Path output) throws Exception {
        Path models = Files.createDirectories(output.resolve("probe-models"));
        Files.copy(root.resolve("frontend/diagram-ui/test/fixtures/interactive.ili"), models.resolve("Interactive.ili"), StandardCopyOption.REPLACE_EXISTING);
        for (String file : List.of("GuiExample.ili", "Broken.ili"))
            Files.copy(root.resolve("tools/gui/examples").resolve(file), models.resolve(file), StandardCopyOption.REPLACE_EXISTING);
    }

    static void process(Path root, List<String> command) throws Exception {
        int exit = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start().waitFor();
        if (exit != 0) throw new IllegalStateException("GUI step exited " + exit + ": " + command.getFirst());
    }
}
