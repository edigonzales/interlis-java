package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
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
    private static final String ANTLR = "org.antlr.antlr4.runtime";
    private static final String ANTLR_SHA = "dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7";
    private static final Set<String> GUI_MODULES = Set.of(MODULE, "guru.interlis.model", "guru.interlis.compiler",
            "guru.interlis.workspace", "guru.interlis.repository", "com.formdev.flatlaf", ANTLR);
    private static final String LIGHT_DEFAULTS = "com/formdev/flatlaf/FlatLightLaf.properties";
    private static final String MAC_NATIVE = "com/formdev/flatlaf/natives/libflatlaf-macos-arm64.dylib";

    private GuiRuntime() {
    }

    public static void run(BuildRequest request) throws Exception {
        Path root = request.root();
        var outcome = JenesisBridge.run(new BuildRequest("artifacts", PROJECT, null, root));
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
        if (request.profile().equals("gui-run")) {
            var process = new ProcessBuilder(tool(jdk, "java"), "--module-path", jars, "--module", ENTRY)
                    .directory(root.toFile()).inheritIO().start();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("GUI launch failed");
            }
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
        if (request.profile().equals("gui-check")) {
            checkShell(root, run, jdk, image, jars);
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
                    "-Xlog:library=info", "--patch-module", MODULE + "=" + classes));
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
        var start = Instant.now();
        var builder = new ProcessBuilder(command).directory(run.toFile())
                .redirectOutput(run.resolve(role + ".stdout").toFile())
                .redirectError(run.resolve(role + ".stderr").toFile());
        for (String key : List.of("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) {
            builder.environment().remove(key);
        }
        var child = builder.start();
        boolean finished = child.waitFor(90, TimeUnit.SECONDS);
        if (!finished) {
            child.destroyForcibly().waitFor();
        }
        Files.writeString(run.resolve(role + ".json"), JsonOutput.write(Map.of("argv", command, "start", start.toString(),
                "end", Instant.now().toString(), "cwd", run.toString(), "exitCode", child.exitValue(), "timeout", !finished)));
        if (!finished || child.exitValue() != 0) {
            throw new IllegalStateException(role + " failed. See " + run);
        }
    }
}
