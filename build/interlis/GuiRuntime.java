package build.interlis;

import build.jenesis.BuildExecutor;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.project.ModularizeModule;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessHandler;

import java.io.File;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** GUI delivery uses the current Jenesis inventory and a separately linked runtime. */
public final class GuiRuntime {
    private static final String PROJECT = "interlis-compiler-gui";
    private static final String MODULE = "guru.interlis.compiler.gui";
    private static final String ENTRY = MODULE + "/guru.interlis.gui.GuiMain";
    private static final String APP_NAME = "INTERLIS Compiler";
    private static final String NATIVE_ACCESS = "--enable-native-access=com.formdev.flatlaf";
    private static final String ANTLR = "org.antlr.antlr4.runtime";
    private static final String ANTLR_SHA = "dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7";
    private static final Set<String> GUI_MODULES = Set.of(MODULE, "guru.interlis.model", "guru.interlis.compiler",
            "guru.interlis.workspace", "guru.interlis.repository", "com.formdev.flatlaf", ANTLR);
    private static final String LIGHT_DEFAULTS = "com/formdev/flatlaf/FlatLightLaf.properties";
    private static final String MAC_NATIVE = "com/formdev/flatlaf/natives/libflatlaf-macos-arm64.dylib";

    private GuiRuntime() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !(args.length == 1 && List.of("run", "runtime", "package").contains(args[0])
                || args.length == 2 && args[0].equals("check")
                && List.of("shell", "compile", "diagnostics", "dependencies", "repositories", "lifecycle", "packaged").contains(args[1]))) {
            throw new IllegalArgumentException("Expected run, runtime, package, or check PROFILE");
        }
        Path root = Path.of("").toAbsolutePath().normalize();
        try (var lease = JvmBuildLock.acquire(root)) {
            run(root, args[0], args.length == 2 ? args[1] : null);
        }
    }

    private static void run(Path root, String action, String check) throws Exception {
        boolean packageApp = action.equals("package") || "packaged".equals(check);
        if (packageApp && !(System.getProperty("os.name").startsWith("Mac")
                && System.getProperty("os.arch").equals("aarch64"))) {
            throw new IllegalArgumentException("The GUI app package requires macOS on ARM64");
        }
        var outcome = JenesisBridge.artifacts(root, "+" + PROJECT);
        var artifacts = ModuleArtifacts.runtime(root, PROJECT, outcome);
        var modules = new TreeMap<String, ModuleArtifacts.Artifact>();
        for (var artifact : artifacts) {
            var descriptor = ModuleFinder.of(artifact.file()).findAll().iterator().next().descriptor();
            String name = descriptor.name();
            if (modules.put(name, artifact) != null || (descriptor.isAutomatic() && !name.equals(ANTLR))) {
                throw new IllegalStateException("Unexpected runtime module: " + descriptor);
            }
            if (name.startsWith("guru.interlis.")) {
                ModulePathContract.requireOwnDescriptor(artifact.coordinates().split("/")[2], descriptor);
            }
        }
        if (!modules.keySet().equals(GUI_MODULES)) {
            throw new IllegalStateException("Unexpected GUI closure: " + modules.keySet());
        }
        Path run = Files.createDirectories(JvmBuildInputs.requireOutputRoot(root,
                "target/jenesis-jvm/gui/" + UUID.randomUUID()));
        System.out.println("GUI artifacts and process records: " + run);
        var closure = new TreeMap<String, Object>();
        for (var entry : modules.entrySet()) {
            var artifact = entry.getValue();
            closure.put(entry.getKey(), Map.of("file", artifact.file().toString(), "sha256", artifact.sha256()));
        }
        Files.writeString(run.resolve("closure.json"), JsonOutput.write(closure));
        String jars = String.join(File.pathSeparator, artifacts.stream().map(a -> a.file().toString()).toList());
        Path jdk = Path.of(System.getProperty("java.home"));
        if (action.equals("run")) {
            var process = new ProcessBuilder(tool(jdk, "java"), NATIVE_ACCESS, "--module-path", jars, "--module", ENTRY)
                    .directory(root.toFile()).inheritIO().start();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("GUI launch failed");
            }
            return;
        }

        if (check != null && !check.equals("shell") && !check.equals("packaged")) {
            checkInteraction(root, run, jdk, jars, check);
            return;
        }

        var linkPath = new ArrayList<String>();
        linkPath.add(jdk.resolve("jmods").toString());
        for (var entry : modules.entrySet()) {
            Path jar = entry.getKey().equals(ANTLR) ? modularAntlr(run, entry.getValue()) : entry.getValue().file();
            linkPath.add(jar.toString());
        }
        Path image = run.resolve("runtime");
        process(run, "jlink", List.of(tool(jdk, "jlink"), "--module-path", String.join(File.pathSeparator, linkPath),
                "--add-modules", MODULE + ",jdk.crypto.ec", "--output", image.toString()));
        process(run, "modules", List.of(tool(image, "java"), "--list-modules"));
        process(run, "resources", List.of(tool(jdk, "jimage"), "list", image.resolve("lib/modules").toString()));
        String resources = Files.readString(run.resolve("resources.stdout"));
        try (var flatlaf = new JarFile(modules.get("com.formdev.flatlaf").file().toFile())) {
            var natives = flatlaf.stream().map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith("com/formdev/flatlaf/natives/") && !name.endsWith("/")).toList();
            if (natives.isEmpty() || !natives.contains(MAC_NATIVE) || !resources.contains(LIGHT_DEFAULTS)
                    || !natives.stream().allMatch(resources::contains)) {
                throw new IllegalStateException("FlatLaf resources are incomplete in the linked image");
            }
        }
        if (packageApp) {
            Path app = packageApp(root, run, jdk, image, modules.get("com.formdev.flatlaf").file());
            if ("packaged".equals(check)) {
                checkPackage(root, run, jdk, jars, app);
            }
        } else if ("shell".equals(check)) {
            checkShell(root, run, jdk, image, jars);
        }
    }

    private static Path packageApp(Path root, Path run, Path jdk, Path image, Path flatlafJar) throws Exception {
        Path notices = Files.createDirectory(run.resolve("Licenses"));
        Files.copy(root.resolve("LICENSE"), notices.resolve("INTERLIS.txt"));
        Files.copy(root.resolve("interlis-compiler/src/main/antlr/LICENSE"), notices.resolve("ANTLR.txt"));
        Files.copy(root.resolve("interlis-compiler/src/main/antlr/NOTICE"), notices.resolve("ANTLR-NOTICE.txt"));
        try (var flatlaf = new JarFile(flatlafJar.toFile());
             var license = flatlaf.getInputStream(flatlaf.getJarEntry("META-INF/LICENSE"))) {
            Files.copy(license, notices.resolve("FlatLaf.txt"));
        }
        Path destination = Files.createDirectory(run.resolve("macos-arm64"));
        process(run, "jpackage", List.of(tool(jdk, "jpackage"), "--type", "app-image", "--name", APP_NAME,
                "--mac-package-name", "INTERLIS", "--mac-package-identifier", MODULE, "--app-version", "1",
                "--module", ENTRY, "--runtime-image", image.toString(), "--dest", destination.toString(),
                "--java-options", NATIVE_ACCESS, "--app-content", notices.toString()));
        Path app = destination.resolve(APP_NAME + ".app");
        Path launcher = app.resolve("Contents/MacOS").resolve(APP_NAME);
        Path bundledJava = app.resolve("Contents/runtime/Contents/Home");
        process(run, "mac-launcher", List.of("/usr/bin/file", launcher.toString()));
        process(run, "mac-modules", List.of(tool(bundledJava, "java"), "--list-modules"));
        process(run, "mac-signature", List.of("/usr/bin/codesign", "-dv", launcher.toString()));
        if (!Files.readString(run.resolve("mac-launcher.stdout")).strip().endsWith(": Mach-O 64-bit executable arm64")
                || Files.mismatch(image.resolve("lib/modules"), bundledJava.resolve("lib/modules")) != -1
                || Files.mismatch(run.resolve("modules.stdout"), run.resolve("mac-modules.stdout")) != -1) {
            throw new IllegalStateException("The app does not contain the expected ARM64 launcher and runtime");
        }
        Files.writeString(run.resolve("packaged-artifacts.json"), JsonOutput.write(Map.of(
                "app", app.toString(), "launcherSha256", JvmBuildInputs.sha256(launcher),
                "modulesSha256", JvmBuildInputs.sha256(bundledJava.resolve("lib/modules")))));
        System.out.println("macOS ARM64 app: " + app);
        return app;
    }

    private static void checkPackage(Path root, Path run, Path jdk, String jars, Path app) throws Exception {
        Path outside = Files.createTempDirectory("INTERLIS GUI Smoke ").toRealPath();
        if (outside.startsWith(root.toRealPath())) {
            throw new IllegalStateException("The packaged smoke needs a temporary directory outside the checkout");
        }
        try {
            Path copiedApp = outside.resolve(app.getFileName());
            process(run, "copy-app", outside, List.of("/usr/bin/ditto", app.toString(), copiedApp.toString()), Map.of());
            Path launcher = copiedApp.resolve("Contents/MacOS").resolve(APP_NAME);
            Path configuration = copiedApp.resolve("Contents/app").resolve(APP_NAME + ".cfg");
            Path javaHome = copiedApp.resolve("Contents/runtime/Contents/Home").toRealPath();
            checkNativeWindow(root, run, jdk, outside, launcher, javaHome);

            Path probes = root.resolve("build/interlis/tests");
            Path classes = Files.createDirectory(outside.resolve("Smoke Classes"));
            process(run, "packaged-probe-compile", List.of(tool(jdk, "javac"), "--release", "25", "--module-path", jars,
                    "--patch-module", MODULE + "=" + probes, "-d", classes.toString(),
                    probes.resolve("GuiCompileProbe.java").toString(), probes.resolve("GuiPackagedProbe.java").toString()));
            String production = Files.readString(configuration);
            if (!production.contains("app.mainmodule=" + ENTRY + "\n")) {
                throw new IllegalStateException("Native launcher does not name the GUI entry point");
            }
            Files.writeString(run.resolve("packaged-production.cfg"), production);
            String driver = production.replace("app.mainmodule=" + ENTRY + "\n",
                    "app.mainmodule=" + MODULE + "/guru.interlis.gui.GuiPackagedProbe\n")
                    + "java-options=--patch-module=" + MODULE + "=" + classes + "\n"
                    + "java-options=-Dinterlis.smoke.directory=" + outside + "\n"
                    + "java-options=-Dinterlis.gui.settingsDir=" + outside.resolve("Test Settings") + "\n";
            Files.writeString(configuration, driver);
            Files.writeString(run.resolve("packaged-probe.cfg"), driver);
            if (Files.mismatch(app.resolve("Contents/MacOS").resolve(APP_NAME), launcher) != -1
                    || Files.mismatch(app.resolve("Contents/runtime/Contents/Home/lib/modules"), javaHome.resolve("lib/modules")) != -1) {
                throw new IllegalStateException("Packaged driver must use the original native launcher and linked modules");
            }
            process(run, "interactive-packaged", outside, List.of(launcher.toString()), Map.of("PATH", "/usr/bin:/bin"));
            if (!Files.readString(run.resolve("interactive-packaged.stdout")).contains("GUI_PACKAGED_PASS")) {
                throw new IllegalStateException("The packaged interaction produced no assertions");
            }
            for (String area : List.of("check", "dependencies", "repositories")) {
                Files.copy(outside.resolve(area + ".png"), run.resolve("packaged-" + area + ".png"));
            }
        } finally {
            try (var paths = Files.walk(outside)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void checkNativeWindow(Path root, Path run, Path jdk, Path outside, Path launcher, Path javaHome)
            throws Exception {
        var start = Instant.now();
        var command = List.of(launcher.toString());
        var builder = processBuilder(run, "native-app", outside, command, Map.of("PATH", "/usr/bin:/bin",
                "JAVA_TOOL_OPTIONS", "-Dinterlis.gui.settingsDir=\"" + outside.resolve("Launch Settings") + "\" -Xlog:library=info"));
        var child = builder.start();
        boolean finished = false;
        try {
            Path properties = outside.resolve("vm-properties.txt");
            var attach = new ProcessBuilder(tool(jdk, "jcmd"), Long.toString(child.pid()), "VM.system_properties")
                    .directory(outside.toFile()).redirectOutput(properties.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!attach.waitFor(20, TimeUnit.SECONDS)) {
                attach.destroyForcibly().waitFor();
                throw new IllegalStateException("Could not read the app's embedded runtime properties");
            }
            List<String> observed = Files.readAllLines(properties).stream().filter(line -> line.startsWith("java.home=")
                    || line.startsWith("os.arch=") || line.startsWith("user.dir=")).toList();
            Files.delete(properties);
            Files.write(run.resolve("native-app-runtime.txt"), observed);
            if (attach.exitValue() != 0 || !observed.contains("java.home=" + javaHome)
                    || !observed.contains("os.arch=aarch64") || !observed.contains("user.dir=" + outside)) {
                throw new IllegalStateException("Native app did not use the expected embedded runtime and working directory: " + observed);
            }
            process(run, "native-window", outside, List.of("/usr/bin/swift", "-module-cache-path",
                    outside.resolve("SwiftCache").toString(), root.resolve("build/interlis/tests/quit-macos-gui.swift").toString(),
                    Long.toString(child.pid()), MODULE), Map.of());
            finished = child.waitFor(15, TimeUnit.SECONDS);
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly().waitFor();
            }
            recordProcess(run, "native-app", outside, command, start, child.exitValue(), finished);
        }
        if (!finished || child.exitValue() != 0 || !Files.readString(run.resolve("native-app.stdout")).lines()
                .anyMatch(line -> line.contains("Loaded library") && line.contains("libflatlaf-macos-arm64"))) {
            throw new IllegalStateException("Native app did not load the ARM64 FlatLaf library and quit normally");
        }
    }

    private static Path modularAntlr(Path run, ModuleArtifacts.Artifact artifact) throws Exception {
        if (!artifact.coordinates().equals("maven/org.antlr/antlr4-runtime/4.13.2")
                || !artifact.sha256().equals(ANTLR_SHA)) {
            throw new IllegalStateException("Unexpected ANTLR input to modularization");
        }
        Path inputs = Files.createDirectory(run.resolve("antlr-input"));
        Path jar = inputs.resolve(artifact.fileName());
        Files.copy(artifact.file(), jar);
        var dependencies = new SequencedProperties();
        dependencies.setProperty("runtime/" + artifact.coordinates(), jar.getFileName().toString());
        dependencies.store(inputs.resolve(BuildStep.DEPENDENCIES));

        var env = new Environment(Map.of()).root(run);
        var executor = BuildExecutor.of(run.resolve("antlr-modularization"));
        executor.addSource("runtime", inputs);
        executor.addModule("modules", ModularizeModule.ofEnvironment(env, ProcessHandler.Factory.ofEnvironment(env)),
                "runtime");
        var results = executor.execute("modules/modularize");
        Path output = results.get("modules");
        if (output == null) {
            throw new IllegalStateException("Jenesis did not produce the modular ANTLR runtime: " + results.keySet());
        }
        var index = SequencedProperties.ofFiles(output.resolve(Dependencies.MODULAR));
        Path linked = output.resolve(index.getProperty("runtime/" + artifact.coordinates()));
        var descriptor = ModuleFinder.of(linked).find(ANTLR).orElseThrow().descriptor();
        if (descriptor.isAutomatic()) {
            throw new IllegalStateException("ANTLR runtime still has an automatic module");
        }
        Files.writeString(run.resolve("antlr-modularization.json"), JsonOutput.write(Map.of(
                "inputSha256", artifact.sha256(), "outputSha256", JvmBuildInputs.sha256(linked),
                "descriptor", descriptor.toString(), "output", linked.toString())));
        return linked;
    }

    private static void checkShell(Path root, Path run, Path jdk, Path image, String jars) throws Exception {
        Path probe = root.resolve("build/interlis/tests/GuiShellProbe.java");
        Path classes = Files.createDirectory(run.resolve("probe-classes"));
        process(run, "probe-compile", List.of(tool(jdk, "javac"), "--release", "25", "--module-path", jars,
                "--patch-module", MODULE + "=" + probe.getParent(), "-d", classes.toString(), probe.toString()));
        for (String variant : List.of("module-path", "linked-image")) {
            Path executable = variant.equals("module-path") ? jdk : image;
            var args = new ArrayList<>(List.of(tool(executable, "java"), "-Djava.awt.headless=false",
                    "-Dinterlis.gui.settingsDir=" + run.resolve("settings-" + variant), "-Xlog:library=info", "--patch-module", MODULE + "=" + classes));
            if (variant.equals("module-path")) {
                args.addAll(List.of("--module-path", jars));
            }
            args.addAll(List.of("--module", MODULE + "/guru.interlis.gui.GuiShellProbe", run.resolve(variant + ".png").toString()));
            process(run, variant, args);
            String stdout = Files.readString(run.resolve(variant + ".stdout"));
            if (!stdout.contains("GUI_SHELL_PASS") || !Files.isRegularFile(run.resolve(variant + ".png"))) {
                throw new IllegalStateException("Missing shell assertions or screenshot: " + variant);
            }
            if (System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch").equals("aarch64")
                    && !stdout.lines().anyMatch(line -> line.contains("Loaded library") && line.contains("flatlaf"))) {
                throw new IllegalStateException("FlatLaf native load is missing: " + variant);
            }
        }
    }

    private static void checkInteraction(Path root, Path run, Path jdk, String jars, String profile) throws Exception {
        Path probes = root.resolve("build/interlis/tests");
        Path classes = Files.createDirectory(run.resolve("probe-classes"));
        var compile = new ArrayList<>(List.of(tool(jdk, "javac"), "--release", "25", "--module-path", jars,
                "--patch-module", MODULE + "=" + probes, "-d", classes.toString(),
                probes.resolve("GuiCompileProbe.java").toString()));
        String probe = switch (profile) {
            case "compile" -> "GuiCompileProbe";
            case "diagnostics" -> {
                compile.add(probes.resolve("GuiDiagnosticsProbe.java").toString());
                yield "GuiDiagnosticsProbe";
            }
            case "dependencies" -> {
                compile.add(probes.resolve("GuiDependenciesProbe.java").toString());
                yield "GuiDependenciesProbe";
            }
            case "repositories" -> {
                compile.add(probes.resolve("GuiRepositoriesProbe.java").toString());
                yield "GuiRepositoriesProbe";
            }
            case "lifecycle" -> {
                compile.add(probes.resolve("GuiLifecycleProbe.java").toString());
                yield "GuiLifecycleProbe";
            }
            default -> throw new IllegalArgumentException("Unknown GUI interaction profile: " + profile);
        };
        process(run, "probe-compile", compile);
        String role = "interactive-" + profile;
        process(run, role, List.of(tool(jdk, "java"), "-Djava.awt.headless=false",
                "--module-path", jars, "--patch-module", MODULE + "=" + classes,
                "--module", MODULE + "/guru.interlis.gui." + probe));
        if (!Files.readString(run.resolve(role + ".stdout"))
                .contains("GUI_" + profile.toUpperCase(java.util.Locale.ROOT) + "_PASS")) {
            throw new IllegalStateException("The interactive probe produced no assertions: " + profile);
        }
    }

    private static String tool(Path jdk, String name) {
        return jdk.resolve("bin").resolve(System.getProperty("os.name").startsWith("Windows") ? name + ".exe" : name).toString();
    }

    private static void process(Path run, String role, List<String> command) throws Exception {
        process(run, role, run, command, Map.of());
    }

    private static ProcessBuilder processBuilder(Path run, String role, Path cwd, List<String> command,
                                                  Map<String, String> environment) {
        var builder = new ProcessBuilder(command).directory(cwd.toFile())
                .redirectOutput(run.resolve(role + ".stdout").toFile())
                .redirectError(run.resolve(role + ".stderr").toFile());
        for (String key : List.of("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH", "JAVA_HOME")) {
            builder.environment().remove(key);
        }
        builder.environment().putAll(environment);
        return builder;
    }

    private static void process(Path run, String role, Path cwd, List<String> command,
                                Map<String, String> environment) throws Exception {
        var start = Instant.now();
        var child = processBuilder(run, role, cwd, command, environment).start();
        boolean finished = child.waitFor(90, TimeUnit.SECONDS);
        if (!finished) {
            child.destroyForcibly().waitFor();
        }
        recordProcess(run, role, cwd, command, start, child.exitValue(), finished);
        if (!finished || child.exitValue() != 0) {
            throw new IllegalStateException(role + " failed. See " + run);
        }
    }

    private static void recordProcess(Path run, String role, Path cwd, List<String> command, Instant start,
                                      int exitCode, boolean finished) throws Exception {
        Files.writeString(run.resolve(role + ".json"), JsonOutput.write(Map.of("argv", command, "start", start.toString(),
                "end", Instant.now().toString(), "cwd", cwd.toString(), "exitCode", exitCode, "timeout", !finished)));
    }
}
