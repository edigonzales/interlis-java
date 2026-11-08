package build.interlis;

import build.jenesis.Json;
import java.nio.file.*;
import java.util.*;

/** Existing CLI/LSP/SDK forms; products always come from the current Jenesis invocation. */
public final class ExistingJvmDistribution {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected sdk, check or compiler");
        Path root = Path.of("").toAbsolutePath().normalize();
        try (var lease = JvmBuildLock.acquire(root)) {
            switch (args[0]) {
                case "sdk" -> generate(root);
                case "check" -> run(root);
                case "compiler" -> packageCompiler(root);
                default -> throw new IllegalArgumentException("Expected sdk, check or compiler");
            }
        }
    }

    public static void run(Path root) throws Exception {
        Path delivery = generate(root);
        DistributionChecks.check(root, delivery);
    }

    public static Path generate(Path root) throws Exception {
        var outcome = JenesisBridge.artifacts(root);
        Path delivery = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/delivery");
        if (Files.exists(delivery)) {
            Path previous = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/delivery-history/" + UUID.randomUUID());
            Files.createDirectories(previous.getParent());
            Files.move(delivery, previous);
        }
        generateAt(root, outcome, delivery);
        return delivery;
    }

    public static void packageCompiler(Path root) throws Exception {
        Path delivery = generate(root);
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
        var provenance=new TreeMap<String,Object>();
        for(var item:Map.of("compiler","interlis-compiler-cli","lsp","interlis-lsp").entrySet()) {
            Path distribution=delivery.resolve(item.getKey());Files.createDirectories(distribution.resolve("lib"));Files.createDirectories(distribution.resolve("bin"));
            var artifacts=ModuleArtifacts.runtime(root,item.getValue(),outcome);var facts=new ArrayList<Map<String,String>>();
            for(var artifact:artifacts) {
                Path copy=distribution.resolve("lib").resolve(artifact.fileName());Files.copy(artifact.file(),copy);
                if(!JvmBuildInputs.sha256(copy).equals(artifact.sha256()))throw new IllegalStateException("Copied artifact changed");
                facts.add(Map.of("coordinates",artifact.coordinates(),"source",JvmBuildInputs.portableRelative(root,artifact.file()),"file",JvmBuildInputs.portableRelative(root,copy),"sha256",artifact.sha256()));
            }
            String name=item.getKey().equals("compiler")?"interlis-compiler":"interlis-lsp";
            String module=ModulePathContract.name(item.getValue());
            var result=JenesisBridge.moduleResult(outcome,item.getValue());
            if(!result.explicitDescriptor()||!result.moduleName().orElseThrow().equals(module))throw new IllegalStateException("Distribution requires its explicit product module");
            String main=item.getKey().equals("compiler")?"guru.interlis.cli.Main":"guru.interlis.lsp.Main";
            for(String suffix:List.of("",".bat")) {
                String template=Files.readString(root.resolve("build/interlis/templates/"+name+suffix));
                if(!template.contains("--module "+module+"/"+main)||!template.contains("--module-path")||template.contains("@CLASSPATH@")||template.contains("ALL-MODULE-PATH")||template.contains("ALL-DEFAULT")||template.contains("--patch-module")||java.util.regex.Pattern.compile("(?m)^[ \t]*set[ \t]*$").matcher(template).find())throw new IllegalStateException("Unexpected modular launcher template");
                Path launcher=distribution.resolve("bin/"+name+suffix);Files.writeString(launcher,template);
                if(!launcher.toFile().setExecutable(true,false))throw new IllegalStateException("Cannot set launcher permission");
            }
            provenance.put(item.getValue(),facts);
        }
        Files.writeString(delivery.resolve("runtime-inventory.json"),JsonOutput.write(provenance));
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

    static Path launcher(Path distribution, String name) {
        return distribution.resolve("bin").resolve(name + (System.getProperty("os.name").startsWith("Windows") ? ".bat" : ""));
    }

    static void archive(Path root, Path staging, Path zip) throws Exception {
        DistributionChecks.javaMain(root, JenesisBridge.buildArchiveTool(root, false),
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
