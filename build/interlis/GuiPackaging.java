package build.interlis;

import build.jenesis.BuildExecutor;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.JLink;
import build.jenesis.step.JPackage;
import build.jenesis.step.ProcessHandler;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Native Jenesis jlink and jpackage steps, plus a relocated loopback-only app launch. */
final class GuiPackaging {
    private static final String JMOD_HASH = "4cd258001c75af7047005c5c891e2400ed11d24fbb09412324c0cbaf8b503c5a";
    private static final String ZIP_PREFIX = "javafx-jmods-25.0.2/";
    private static final String APP = "INTERLIS Compiler.app";
    private static final List<String> FX_MODULES = List.of("javafx.base", "javafx.graphics", "javafx.controls", "javafx.media", "javafx.web");

    static void run(Path root, Path output, Path archive, List<Path> paths, Environment environment) throws Exception {
        if (!JvmBuildInputs.sha256(archive).equals(JMOD_HASH)) throw new IllegalArgumentException("Expected pinned Gluon JavaFX 25.0.2 macOS ARM64 JMOD archive");
        Path input = Files.createDirectories(JvmBuildInputs.requireOutputRoot(root, "target/jenesis-gui/jmod-input"));
        Path jmods = Files.createDirectories(input.resolve("jmods"));
        try (var zip = new ZipFile(archive.toFile())) {
            for (String module : FX_MODULES) {
                String name = module + ".jmod";
                var entry = Objects.requireNonNull(zip.getEntry(ZIP_PREFIX + name), name);
                try (var bytes = zip.getInputStream(entry)) { Files.copy(bytes, jmods.resolve(name), StandardCopyOption.REPLACE_EXISTING); }
            }
        }
        // The pinned ANTLR JAR is an automatic module; keep all application modules on the
        // app's module path and link only the platform runtime. No foreign JAR is rewritten.
        Path modules = Files.createDirectories(JvmBuildInputs.requireOutputRoot(root, "target/jenesis-gui/application-modules"));
        for (Path jar : paths) {
            var descriptor = java.lang.module.ModuleFinder.of(jar).findAll().iterator().next().descriptor();
            if (!FX_MODULES.contains(descriptor.name()))
                Files.copy(jar, modules.resolve(descriptor.name() + ".jar"), StandardCopyOption.REPLACE_EXISTING);
        }
        Path configuration = Files.createDirectories(JvmBuildInputs.requireOutputRoot(root, "target/jenesis-gui/package-config"));
        Path checked = root.resolve("tools/gui/packaging/process");
        ProcessBuildStep.store(configuration, ProcessBuildStep.PROCESS, "jlink", SequencedProperties.ofFiles(checked.resolve("jlink.properties")));
        var packaging = SequencedProperties.ofFiles(checked.resolve("jpackage.properties"));
        packaging.setProperty("--module-path", modules.toString());
        ProcessBuildStep.store(configuration, ProcessBuildStep.PROCESS, "jpackage", packaging);
        var executor = BuildExecutor.Configuration.ofEnvironment(environment).of(JvmBuildInputs.requireOutputRoot(root, "target/jenesis-gui/packaging"));
        var factories = ProcessHandler.Factory.ofEnvironment(environment);
        executor.addSource("javafx", input);
        executor.addSource("configuration", configuration);
        executor.addSource("application-modules", modules);
        executor.addStep("link", JLink.ofEnvironment(environment, factories), "javafx", "configuration");
        executor.addStep("package", JPackage.ofEnvironment(environment, factories).type("app-image"), "link", "configuration", "application-modules");
        var products = executor.execute("package");
        Path app = products.get("package").resolve(JPackage.PACKAGES).resolve(APP);
        if (!Files.isExecutable(app.resolve("Contents/MacOS/INTERLIS Compiler"))) throw new IllegalStateException("Missing native GUI launcher: " + app);
        Files.writeString(output.resolve("app-path.txt"), app.toString() + "\n");
        System.out.println("Packaged application: " + app);
        checkRelocated(root, output, app);
    }

    private static void checkRelocated(Path root, Path output, Path app) throws Exception {
        Path scratch = Files.createTempDirectory("interlis-gui-offline-");
        try {
            Path relocated = scratch.resolve(APP), probe = scratch.resolve("probe.txt");
            GuiPrototypeRun.process(root, List.of("/usr/bin/ditto", app.toString(), relocated.toString()));
            GuiPrototypeRun.prepareProbeModels(root, scratch);
            Path home = Files.createDirectory(scratch.resolve("home"));
            var launch = new ProcessBuilder("/usr/bin/sandbox-exec", "-f", root.resolve("tools/gui/offline.sb").toString(),
                    relocated.resolve("Contents/MacOS/INTERLIS Compiler").toString(), "--probe", probe.toString())
                    .directory(scratch.toFile()).inheritIO();
            launch.environment().keySet().removeAll(Set.of("JAVA_HOME", "JDK_HOME", "CLASSPATH", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"));
            launch.environment().put("PATH", "/usr/bin:/bin");
            launch.environment().put("HOME", home.toString());
            int exit = launch.start().waitFor();
            if (Files.isRegularFile(probe)) Files.copy(probe, output.resolve("packaged-probe.txt"), StandardCopyOption.REPLACE_EXISTING);
            if (exit != 0 || !Files.isRegularFile(probe) || !Files.readString(probe).contains("result=PASS\n"))
                throw new IllegalStateException("Relocated offline application probe failed; exit " + exit);
            for (String file : List.of("diagram.svg", "diagram.png"))
                Files.copy(scratch.resolve(file), output.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Relocated offline app probe: PASS");
        } finally {
            try (var tree = Files.walk(scratch)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
