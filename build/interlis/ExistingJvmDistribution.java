package build.interlis;

import build.jenesis.Json;
import java.nio.file.*;
import java.util.*;

/** Existing CLI/LSP/SDK forms; products always come from the current Jenesis invocation. */
public final class ExistingJvmDistribution {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected sdk, check, compiler or validator");
        Path root = Path.of("").toAbsolutePath().normalize();
        try (var lease = JvmBuildLock.acquire(root)) {
            switch (args[0]) {
                case "sdk" -> generate(root);
                case "check" -> run(root);
                case "compiler" -> packageCompiler(root);
                case "validator" -> System.out.println(packageValidator(root));
                default -> throw new IllegalArgumentException("Expected sdk, check, compiler or validator");
            }
        }
    }

    private enum Delivery { SDK, CHECKED_SDK, COMPILER, VALIDATOR }

    public static void run(Path root) throws Exception {
        replace(root, Delivery.CHECKED_SDK);
        System.out.println("SDK, CLI and LSP checks passed");
    }

    public static Path generate(Path root) throws Exception {
        return replace(root, Delivery.SDK);
    }

    public static void packageCompiler(Path root) throws Exception {
        replace(root, Delivery.COMPILER);
    }

    private static Path replace(Path root, Delivery kind) throws Exception {
        return replace(root, kind, "target/jenesis-jvm/delivery");
    }

    private static Path replace(Path root, Delivery kind, String destination) throws Exception {
        var outcome = kind == Delivery.VALIDATOR
                ? JenesisBridge.artifacts(root, "+interlis-validator-cli")
                : JenesisBridge.artifacts(root, "+interlis-compiler-cli", "+interlis-lsp");
        Path delivery = JvmBuildInputs.requireOutputRoot(root, destination);
        Path work = Files.createTempDirectory(delivery.getParent(), "delivery-");
        Path next = work.resolve("next");
        Path previous = work.resolve("previous");
        boolean cleanup = true;
        try {
            if (kind == Delivery.VALIDATOR) packageValidatorAt(root, outcome, next);
            else generateAt(root, outcome, next);
            if (kind == Delivery.CHECKED_SDK) DistributionChecks.check(root, next);
            if (kind == Delivery.COMPILER) packageCompilerAt(root, next);
            if (Files.exists(delivery)) Files.move(delivery, previous);
            try {
                Files.move(next, delivery);
            } catch (Exception error) {
                try {
                    if (Files.exists(previous)) Files.move(previous, delivery);
                } catch (Exception restore) {
                    cleanup = false;
                    error.addSuppressed(restore);
                }
                throw error;
            }
            return delivery;
        } finally {
            // Keep the previous complete delivery available if restoring it failed.
            if (cleanup) {
                BuildOutputs.deleteTree(work, Set.of());
            }
        }
    }

    static Path packageValidator(Path root) throws Exception {
        return replace(root, Delivery.VALIDATOR, "target/jenesis-jvm/validator-delivery")
                .resolve("interlis-validator-" + JvmBuildInputs.projectVersion(root) + ".zip");
    }

    private static void packageValidatorAt(Path root, JenesisBridge.BuildOutcome outcome, Path delivery) throws Exception {
        Path staging = Files.createDirectories(delivery.resolve("staging"));
        Path installed = staging.resolve("interlis-validator");
        installProduct(root, outcome, installed, "interlis-validator-cli", "interlis-validator",
                "guru.interlis.validator.cli.Main");
        Files.copy(root.resolve("LICENSE"), installed.resolve("LICENSE"));
        copyTree(root.resolve("build/interlis/validator-licenses"), installed.resolve("licenses"));
        Files.writeString(installed.resolve("README.txt"), """
                INTERLIS Validator
                Requires Java 25. Set JAVA_HOME or make java available on PATH.

                macOS/Linux: ./bin/interlis-validator --help
                Windows:     bin\\interlis-validator.bat --help

                Supports XTF 2.3 and 2.4. Use --json for a machine-readable result.
                See https://github.com/edigonzales/interlis-java/blob/main/docs/usage/validator.md
                """);
        Path archive = delivery.resolve("interlis-validator-" + JvmBuildInputs.projectVersion(root) + ".zip");
        archive(root, staging, archive);
        BuildOutputs.deleteTree(staging, Set.of());
        checkValidatorArchive(root, archive);
    }

    private static void checkValidatorArchive(Path root, Path archive) throws Exception {
        Path folder = Files.createTempDirectory("interlis validator ä ");
        try {
            DistributionChecks.extractArchive(archive, folder);
            Path installed = folder.resolve("interlis-validator");
            Path cwd = Files.createDirectory(folder.resolve("data files"));
            var launcher = launcher(installed, "interlis-validator").toString();
            var version = DistributionChecks.run(cwd, List.of(launcher, "--version", "--json"));
            version.requireExit(0);
            var info = (Map<?, ?>) Json.parse(version.stdout());
            if (!version.stderr().isEmpty() || !"interlis-validator".equals(info.get("tool"))
                    || !JvmBuildInputs.projectVersion(root).equals(info.get("version"))) {
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
                var result = DistributionChecks.run(cwd, List.of(launcher, "--offline", "--json", "--repository", ".",
                        "--refdata", "reference data.xtf", "--", file));
                int expected = value.equals("ok") ? 0 : 1;
                result.requireExit(expected);
                var report = (Map<?, ?>) Json.parse(result.stdout());
                if (!Boolean.TRUE.equals(report.get("completed"))
                        || !Double.valueOf(expected).equals(report.get("exitCode"))
                        || !Double.valueOf(1).equals(report.get("objects"))
                        || expected == 1 && !result.stdout().contains("TEXT_TOO_LONG")) {
                    throw new IllegalStateException("Unexpected installed validator report: " + result.stdout());
                }
            }
        } finally {
            BuildOutputs.deleteTree(folder, Set.of());
        }
    }

    private static void packageCompilerAt(Path root, Path delivery) throws Exception {
        var manifest = (Map<?, ?>) Json.parse(Files.readString(delivery.resolve("version.json")));
        String version = Objects.toString(manifest.get("version"), "");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+-SNAPSHOT")) {
            throw new IllegalArgumentException("Expected a compiler snapshot version");
        }
        Path output = Files.createDirectory(delivery.resolve("bindist"));
        Path staging = Files.createDirectory(delivery.resolve("bindist-staging"));
        copyTree(delivery.resolve("compiler"), staging.resolve("cli"));
        Files.copy(root.resolve("LICENSE"), staging.resolve("LICENSE"));
        Files.writeString(staging.resolve("README.txt"), """
                INTERLIS Compiler CLI
                Requires Java 25. Set JAVA_HOME or make java available on PATH.

                macOS/Linux: ./cli/bin/interlis-compiler check model.ili
                Windows:     cli\\bin\\interlis-compiler.bat check model.ili
                """);
        Path zip = output.resolve("interlis-compiler-bindist-" + version + ".zip");
        archive(root, staging, zip);
        DistributionChecks.checkArchive(root, delivery, zip, false);
        Files.writeString(output.resolve("SHA256SUMS"), JvmBuildInputs.sha256(zip) + "  " + zip.getFileName() + "\n");
    }

    static void generateAt(Path root, JenesisBridge.BuildOutcome outcome, Path delivery) throws Exception {
        JvmBuildInputs.requireOutputRoot(root, root.relativize(delivery).toString());
        Files.createDirectories(delivery);
        for(var item:Map.of("compiler","interlis-compiler-cli","lsp","interlis-lsp").entrySet()) {
            boolean compiler = item.getKey().equals("compiler");
            installProduct(root, outcome, delivery.resolve(item.getKey()), item.getValue(),
                    compiler ? "interlis-compiler" : "interlis-lsp",
                    compiler ? "guru.interlis.cli.Main" : "guru.interlis.lsp.Main");
        }
        var version = DistributionChecks.run(root,
                List.of(launcher(delivery.resolve("compiler"), "interlis-compiler").toString(), "version", "--json"));
        version.requireExit(0);
        if (!version.stderr().isEmpty() || !(Json.parse(version.stdout()) instanceof Map<?, ?> info)
                || !"interlis-compiler".equals(info.get("tool"))
                || !JvmBuildInputs.projectVersion(root).equals(info.get("version"))) {
            throw new IllegalStateException("SDK version output differs from the built compiler");
        }
        Path manifest = delivery.resolve("version.json");
        Files.writeString(manifest, version.stdout());
        Path staging = delivery.resolve("sdk-staging");
        copyTree(delivery.resolve("compiler"), staging.resolve("cli"));
        copyTree(root.resolve("examples/sdk"), staging.resolve("examples/sdk"));
        Files.createDirectories(staging.resolve("version"));
        Files.copy(manifest, staging.resolve("version/version-manifest.json"));
        archive(root, staging, delivery.resolve("sdk.zip"));
    }

    private static void installProduct(Path root, JenesisBridge.BuildOutcome outcome, Path distribution,
                                       String project, String name, String main) throws Exception {
        Files.createDirectories(distribution.resolve("lib"));
        Files.createDirectories(distribution.resolve("bin"));
        for (var artifact : ModuleArtifacts.runtime(root, project, outcome)) {
            Files.copy(artifact.file(), distribution.resolve("lib").resolve(artifact.fileName()));
        }
        String module = ModulePathContract.name(project);
        var result = JenesisBridge.moduleResult(outcome, project);
        if (!result.explicitDescriptor() || !result.moduleName().orElseThrow().equals(module)) {
            throw new IllegalStateException("Distribution requires its explicit product module");
        }
        for (String suffix : List.of("", ".bat")) {
            String template = Files.readString(root.resolve("build/interlis/templates/" + name + suffix));
            if (!template.contains("--module " + module + "/" + main) || !template.contains("--module-path")
                    || template.contains("@CLASSPATH@") || template.contains("ALL-MODULE-PATH")
                    || template.contains("ALL-DEFAULT") || template.contains("--patch-module")
                    || java.util.regex.Pattern.compile("(?m)^[ \t]*set[ \t]*$").matcher(template).find()) {
                throw new IllegalStateException("Unexpected modular launcher template");
            }
            Path launcher = distribution.resolve("bin").resolve(name + suffix);
            Files.writeString(launcher, template);
            if (!launcher.toFile().setExecutable(true, false)) throw new IllegalStateException("Cannot set launcher permission");
        }
    }

    static Path launcher(Path distribution, String name) {
        return distribution.resolve("bin").resolve(name + (System.getProperty("os.name").startsWith("Windows") ? ".bat" : ""));
    }

    static void archive(Path root, Path staging, Path zip) throws Exception {
        DistributionChecks.javaMain(root, JenesisBridge.buildArchiveTool(root),
                "build.interlis.archive.ArchiveWriter", List.of(staging.toString(), zip.toString())).requireExit(0);
    }

    private static void copyTree(Path source,Path destination)throws Exception {
        try(var files=Files.walk(source)) {
            for(Path path:files.sorted().toList()) {
                if(Files.isSymbolicLink(path))throw new IllegalStateException("Symlink in delivered tree");Path target=destination.resolve(source.relativize(path));
                if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target,StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }
}
