package guru.interlis.packaging;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

final class GuiImageChecks {
    private static final String MODULE = "guru.interlis.compiler.gui";
    private static final String ENTRY = MODULE + "/guru.interlis.gui.GuiMain";
    private static final String APP_NAME = "INTERLIS Compiler";
    private static final Path JDK = Path.of(System.getProperty("java.home"));

    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path output;

    @Test
    @Tag("gui-shell")
    void modularAndLinkedGuiOpenRealWindows() throws Exception {
        checkShell(output, JDK, Path.of(System.getProperty("gui.runtime")), System.getProperty("gui.libs"));
    }

    @Test
    @Tag("gui-image")
    void linkedGuiRetainsFlatLafResources() throws Exception {
        var image = Path.of(System.getProperty("gui.runtime"));
        process(output, "modules", List.of(tool(image, "java"), "--list-modules"));
        if (!Files.readString(output.resolve("modules.stdout")).contains(MODULE)) {
            throw new IllegalStateException("Linked GUI module missing");
        }
        process(output, "resources", List.of(tool(JDK, "jimage"), "list", image.resolve("lib/modules").toString()));
        var resources = Files.readString(output.resolve("resources.stdout"));
        var libraries = Path.of(System.getProperty("gui.libs"));
        var flatlafLocation = java.lang.module.ModuleFinder.of(libraries).find("com.formdev.flatlaf")
                .orElseThrow().location().orElseThrow();
        try (var flatlaf = new JarFile(Path.of(flatlafLocation).toFile())) {
            var natives = flatlaf.stream().map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith("com/formdev/flatlaf/natives/") && !name.endsWith("/")).toList();
            if (!natives.contains("com/formdev/flatlaf/natives/libflatlaf-macos-arm64.dylib")
                    || !resources.contains("com/formdev/flatlaf/FlatLightLaf.properties")
                    || !natives.stream().allMatch(resources::contains)) {
                throw new IllegalStateException("FlatLaf resources are incomplete in the linked image");
            }
        }
    }

    @Test
    @Tag("gui-app")
    void macAppRetainsTheEmbeddedImageAndOpensRealWindows() throws Exception {
        var app = Path.of(System.getProperty("gui.app")).resolve(APP_NAME + ".app");
        var image = Path.of(System.getProperty("gui.runtime"));
        var launcher = app.resolve("Contents/MacOS").resolve(APP_NAME);
        var bundledJava = app.resolve("Contents/runtime/Contents/Home");
        process(output, "mac-launcher", List.of("/usr/bin/file", launcher.toString()));
        process(output, "mac-signature", List.of("/usr/bin/codesign", "-dv", launcher.toString()));
        process(output, "modules", List.of(tool(image, "java"), "--list-modules"));
        process(output, "mac-modules", List.of(tool(bundledJava, "java"), "--list-modules"));
        if (!Files.readString(output.resolve("mac-launcher.stdout")).strip().endsWith(": Mach-O 64-bit executable arm64")
                || Files.mismatch(image.resolve("lib/modules"), bundledJava.resolve("lib/modules")) != -1
                || Files.mismatch(output.resolve("modules.stdout"), output.resolve("mac-modules.stdout")) != -1) {
            throw new IllegalStateException("The app does not contain the expected ARM64 launcher and runtime");
        }
        checkPackage(output, JDK, app);
    }

    private static void checkPackage(Path run, Path jdk, Path app) throws Exception {
        Path outside = Files.createTempDirectory("INTERLIS GUI Smoke ").toRealPath();
        if (outside.startsWith(Path.of(System.getProperty("repo.root")).toRealPath())) {
            throw new IllegalStateException("The packaged smoke needs a temporary directory outside the checkout");
        }
        try {
            Path copiedApp = outside.resolve(app.getFileName());
            process(run, "copy-app", outside, List.of("/usr/bin/ditto", app.toString(), copiedApp.toString()), Map.of());
            Path launcher = copiedApp.resolve("Contents/MacOS").resolve(APP_NAME);
            Path configuration = copiedApp.resolve("Contents/app").resolve(APP_NAME + ".cfg");
            Path javaHome = copiedApp.resolve("Contents/runtime/Contents/Home").toRealPath();
            checkNativeWindow(run, jdk, outside, launcher, javaHome);

            Path classes = Path.of(System.getProperty("gui.test.classes"));
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

    private static void checkNativeWindow(Path run, Path jdk, Path outside, Path launcher, Path javaHome)
            throws Exception {
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
            process(run, "native-window", outside, List.of(System.getProperty("gui.window.probe"),
                    Long.toString(child.pid()), MODULE), Map.of());
            finished = child.waitFor(15, TimeUnit.SECONDS);
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly().waitFor();
            }
        }
        if (!finished || child.exitValue() != 0 || !Files.readString(run.resolve("native-app.stdout")).lines()
                .anyMatch(line -> line.contains("Loaded library") && line.contains("libflatlaf-macos-arm64"))) {
            throw new IllegalStateException("Native app did not load the ARM64 FlatLaf library and quit normally");
        }
    }

    private static void checkShell(Path run, Path jdk, Path image, String jars) throws Exception {
        Path classes = Path.of(System.getProperty("gui.test.classes"));
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
        var child = processBuilder(run, role, cwd, command, environment).start();
        boolean finished = child.waitFor(90, TimeUnit.SECONDS);
        if (!finished) {
            child.descendants().forEach(ProcessHandle::destroyForcibly);
            child.destroyForcibly().waitFor();
        }
        if (!finished || child.exitValue() != 0) {
            throw new IllegalStateException(role + (finished ? " exited " + child.exitValue() : " timed out")
                    + ": " + Files.readString(run.resolve(role + ".stderr")) + "\nSee " + run);
        }
    }

}
