package build.interlis;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Assembles the editor bundle from the current LSP runtime and wire contracts. */
public final class EditorBundleRun {
    private static final String EDITOR_TYPES = "interlis-lsp/src/main/typescript/editor-protocol.ts";
    private static final String DIAGRAM_TYPES = "interlis-projection/src/main/typescript/diagram-document.ts";
    private static final Map<String, String> SCHEMAS = Map.of(
            "interlis-projection", "projection/diagram-document-1.0.schema.json",
            "interlis-wasm-bridge", "bridge/diagram-options-1.0.schema.json");

    private record BundleFile(Path source, String path, String sha256, long bytes) {
        Map<String, Object> inventory() {
            return Map.of("path", path, "sha256", sha256, "bytes", bytes);
        }

        void verify(Path file) throws Exception {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) != bytes || !JvmBuildInputs.sha256(file).equals(sha256)) {
                throw new IllegalStateException("Changed editor bundle file: " + file);
            }
        }
    }

    private record Inputs(Map<String, BundleFile> runtime, Map<String, Path> schemaJars,
            Map<String,String> sources, Map<String,Object> metadata, String requestId) {
        private Inputs {
            runtime = Map.copyOf(runtime);
            schemaJars = Map.copyOf(schemaJars);
            sources = Map.copyOf(sources);
            metadata = Map.copyOf(metadata);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0 && (args.length != 2 || !args[0].equals("--request-id")
                || !UUID.fromString(args[1]).toString().equals(args[1]))) {
            throw new IllegalArgumentException("Expected optional --request-id UUID");
        }
        Path root = Path.of("").toAbsolutePath().normalize();
        Path lock = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/editor-run.lock");
        Files.createDirectories(lock.getParent());
        try (var channel = java.nio.channels.FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lease = channel.tryLock(); var jvm = JvmBuildLock.acquire(root)) {
            if (lease == null) throw new IllegalStateException("Another editor bundle run is active");
            String requestId = args.length == 0 ? UUID.randomUUID().toString() : args[1];
            run(root, prepare(root, requestId));
        }
    }

    private static Inputs prepare(Path root, String requestId) throws Exception {
        String commit = git(root, "rev-parse", "HEAD"), tree = git(root, "rev-parse", "HEAD^{tree}");
        var outcome = JenesisBridge.artifacts(root);
        var runtime = ModuleArtifacts.runtime(root, "interlis-lsp", outcome);
        var metadata = new TreeMap<String, Object>();
        metadata.put("coreCommit",commit);metadata.put("coreTree",tree);metadata.put("dirty",!git(root,"status","--porcelain").isBlank());
        metadata.put("editorProtocolVersion",1);metadata.put("diagramDocumentVersion","1.0");
        metadata.put("serverVersion",JvmBuildInputs.projectVersion(root));
        var lsp=JenesisBridge.moduleResult(outcome,"interlis-lsp");
        if(!lsp.explicitDescriptor()||!lsp.moduleName().orElseThrow().equals("guru.interlis.lsp"))throw new IllegalStateException("Editor server must be explicit JPMS");
        metadata.put("launch",Map.of("kind","module","moduleName",lsp.moduleName().orElseThrow(),"mainClass","guru.interlis.lsp.Main"));
        var runtimeFiles = new TreeMap<String, BundleFile>();
        var moduleInventory=new TreeMap<String,Object>();var moduleNames=new HashSet<String>();
        for (var artifact : runtime) {
            Path jar = artifact.file();
            var found=java.lang.module.ModuleFinder.of(jar).findAll();if(found.size()!=1)throw new IllegalStateException("Unknown editor runtime module");
            var descriptor=found.iterator().next().descriptor();
            if(!moduleNames.add(descriptor.name())||(artifact.coordinates().startsWith("maven/guru.interlis/")&&descriptor.isAutomatic()))throw new IllegalStateException("Duplicate/automatic own editor module");
            String digest = JvmBuildInputs.sha256(jar);
            add(runtimeFiles, jar, "server/lib/" + artifact.fileName(), digest);
            moduleInventory.put("lib/"+artifact.fileName(),Map.of("name",descriptor.name(),"explicit",!descriptor.isAutomatic(),"sha256",digest));
        }
        metadata.put("moduleInventory",moduleInventory);
        var sources=sources(root);metadata.put("editorSourceDigest",digest(sources));
        var schemaJars = Map.of(
                "interlis-projection", ModuleArtifacts.jar(outcome, "interlis-projection"),
                "interlis-wasm-bridge", ModuleArtifacts.jar(outcome, "interlis-wasm-bridge"));
        return new Inputs(runtimeFiles, schemaJars, sources, metadata, requestId);
    }

    private static void run(Path root, Inputs prepared) throws Exception {
        verify(root, prepared);
        Path run = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/editor-runs/" + prepared.requestId());
        Files.createDirectories(run.getParent());
        Files.createDirectory(run);
        var files = new TreeMap<>(prepared.runtime());
        Path protocol = run.resolve("editor-protocol.ts");
        Files.writeString(protocol, Files.readString(root.resolve(EDITOR_TYPES))
                .replace("'../../../../" + DIAGRAM_TYPES.replace(".ts", "") + "'", "'./diagram-document'"));
        add(files, protocol, "types/editor-protocol.ts", JvmBuildInputs.sha256(protocol));
        addSource(root, prepared, files, DIAGRAM_TYPES, "types/diagram-document.ts");
        for (var schema : SCHEMAS.entrySet()) {
            String relative = schemaSource(schema);
            Path source = root.resolve(relative);
            try (var archive = new JarFile(prepared.schemaJars().get(schema.getKey()).toFile())) {
                var entry = archive.getJarEntry("guru/interlis/" + schema.getValue());
                if (entry == null) throw new IllegalStateException("Missing schema in producing JAR: " + relative);
                try (var input = archive.getInputStream(entry)) {
                    if (!Arrays.equals(Files.readAllBytes(source), input.readAllBytes())) {
                        throw new IllegalStateException("Schema differs from producing JAR: " + relative);
                    }
                }
            }
            addSource(root, prepared, files, relative, "schemas/" + source.getFileName());
        }
        addSource(root, prepared, files, "LICENSE", "licenses/interlis-java.txt");

        Path output = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/editor-bundles");
        Files.createDirectories(output);
        Path staging = Files.createDirectory(output.resolve(".staging-" + prepared.requestId()));
        try {
            for (var file : files.values()) {
                Path destination = staging.resolve(file.path());
                Files.createDirectories(destination.getParent());
                file.verify(file.source());
                Files.copy(file.source(), destination);
                file.verify(destination);
            }
            var manifest = new TreeMap<>(prepared.metadata());
            manifest.put("schemaVersion", 4);
            manifest.put("files", files.values().stream().map(BundleFile::inventory).toList());
            Path manifestFile = staging.resolve("manifest.json");
            Files.writeString(manifestFile, JsonOutput.write(manifest) + "\n");
            String manifestHash = JvmBuildInputs.sha256(manifestFile);
            Path directory = output.resolve(manifestHash);
            verify(root, prepared);
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                verifyBundle(directory, files, manifestHash);
            } else {
                Files.move(staging, directory, StandardCopyOption.ATOMIC_MOVE);
            }
            Path result = run.resolve("result.json");
            Files.writeString(result, JsonOutput.write(Map.of("schemaVersion", 1,
                    "directory", directory.toString(), "manifestSha256", manifestHash,
                    "coreCommit", manifest.get("coreCommit"), "dirty", manifest.get("dirty"),
                    "requestId", prepared.requestId())) + "\n");
            Path report = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/reports/editor-bundle.json");
            Files.createDirectories(report.getParent());
            Files.copy(result, report, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Editor bundle result: " + report);
        } finally {
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                try (var paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }

    private static void verifyBundle(Path directory, Map<String, BundleFile> files, String manifestHash)
            throws Exception {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Invalid editor bundle directory: " + directory);
        }
        var remaining = new HashSet<>(files.keySet());
        remaining.add("manifest.json");
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = directory.relativize(path).toString().replace('\\', '/');
                if (!remaining.remove(name) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("Unexpected editor bundle entry: " + path);
                }
                if (name.equals("manifest.json")) {
                    if (!JvmBuildInputs.sha256(path).equals(manifestHash)) {
                        throw new IllegalStateException("Changed editor bundle manifest");
                    }
                } else {
                    files.get(name).verify(path);
                }
            }
        }
        if (!remaining.isEmpty()) throw new IllegalStateException("Missing editor bundle entries: " + remaining);
    }

    private static String schemaSource(Map.Entry<String, String> schema) {
        return schema.getKey() + "/src/main/resources/guru/interlis/" + schema.getValue();
    }

    private static void verify(Path root,Inputs input) throws Exception {
        if(!git(root,"rev-parse","HEAD").equals(input.metadata().get("coreCommit"))||!sources(root).equals(input.sources()))throw new IllegalStateException("Editor sources changed during build");
    }
    private static Map<String,String> sources(Path root) throws Exception {
        var files = new TreeMap<String, String>();
        var inputs = new ArrayList<>(List.of("LICENSE", EDITOR_TYPES, DIAGRAM_TYPES));
        SCHEMAS.entrySet().stream().map(EditorBundleRun::schemaSource).forEach(inputs::add);
        for (String relative : inputs) {
            Path file = JvmBuildInputs.confined(root, relative);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Invalid editor source: " + file);
            }
            files.put(relative, JvmBuildInputs.sha256(file));
        }
        return Map.copyOf(files);
    }
    private static String digest(Map<String,String> sources) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JsonOutput.write(sources).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static void addSource(Path root, Inputs input, Map<String, BundleFile> files,
            String source, String destination) throws Exception {
        add(files, root.resolve(source), destination, input.sources().get(source));
    }

    private static void add(Map<String, BundleFile> files, Path source, String destination, String hash)
            throws Exception {
        Path relative = Path.of(destination);
        if (relative.isAbsolute() || destination.contains("\\") || destination.contains(":")
                || !relative.normalize().equals(relative) || relative.startsWith("..")) {
            throw new IllegalStateException("Unsafe bundle path: " + destination);
        }
        var file = new BundleFile(source, destination, hash, Files.size(source));
        file.verify(source);
        if (files.putIfAbsent(destination, file) != null) {
            throw new IllegalStateException("Duplicate bundle path: " + destination);
        }
    }

    private static String git(Path root,String... args) throws Exception {
        var command=new ArrayList<String>();command.add("git");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).directory(root.toFile()).start();String value=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0)throw new IllegalStateException("Cannot inspect Core provenance");return value;
    }
}
