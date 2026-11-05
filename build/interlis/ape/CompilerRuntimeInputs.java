package build.interlis.ape;

import build.InterlisJvmBuild.BuildRequest;
import build.interlis.*;
import build.jenesis.Json;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Compiler-only, ordered immutable snapshot; no wildcard classpath or discovery of arbitrary JARs. */
public final class CompilerRuntimeInputs {
    public static final String MAIN = "guru.interlis.cli.Main";
    private static final List<String> MODULES = List.of("interlis-compiler-cli", "interlis-compiler",
            "interlis-model", "interlis-workspace", "interlis-repository", "interlis-projection");

    /** Caller holds JVM lock across generation, inventory selection and copying. Existing snapshots are never overwritten. */
    public static Path prepare(Path root, Path destination) throws Exception {
        var before = inputs(root);
        var outcome = JenesisBridge.run(new BuildRequest("artifacts", null, null, root));
        var artifacts = ModuleArtifacts.runtime(root, "interlis-compiler-cli", outcome);
        verifyClosure(artifacts);
        var rows = rows(artifacts);
        if (!before.equals(inputs(root))) throw new IllegalStateException("Input drift during generation");
        Map<String,Object> identity = Map.of("orderedRuntime", rows, "buildInputs", before);
        String digest = digest(identity);
        Map<String,Object> manifest = Map.of("schemaVersion", "1.0", "kind", "interlis-compiler-ape-inputs",
                "source", source(root), "productInputDigest", digest, "mainClass", MAIN,
                "builder", Map.of("jenesis", "0.18.1", "jdk", Runtime.version().toString()),
                "orderedRuntime", rows, "buildInputs", before);
        Files.createDirectories(destination);
        Path finalDirectory = destination.resolve(digest);
        if (Files.exists(finalDirectory)) {
            readVerified(root, finalDirectory.resolve("compiler-inputs.json"));
            return finalDirectory.resolve("compiler-inputs.json");
        }
        Path work = Files.createTempDirectory(destination, ".preparing-");
        Path runtime = Files.createDirectory(work.resolve("runtime"));
        for (var artifact : artifacts) {
            if (!JvmBuildInputs.sha256(artifact.file()).equals(artifact.sha256())) throw new IllegalStateException("Original changed");
            Path copy = Files.copy(artifact.file(), runtime.resolve(artifact.fileName()));
            if (!JvmBuildInputs.sha256(copy).equals(artifact.sha256()) || !JvmBuildInputs.sha256(artifact.file()).equals(artifact.sha256()))
                throw new IllegalStateException("Copy/input drift");
        }
        if (!before.equals(inputs(root))) throw new IllegalStateException("Input drift during snapshot");
        Files.writeString(work.resolve("compiler-inputs.json"), json(manifest) + "\n", StandardOpenOption.CREATE_NEW);
        readVerified(root, work.resolve("compiler-inputs.json"));
        Files.move(work, finalDirectory, StandardCopyOption.ATOMIC_MOVE);
        return finalDirectory.resolve("compiler-inputs.json");
    }

    /** Requires exactly the current CLI closure; rejects all tool/test/LSP/bridge and foreign dependencies. */
    public static void verifyClosure(List<ModuleArtifacts.Artifact> artifacts) throws Exception {
        var own = new HashSet<String>(); var names = new HashSet<String>(); var coordinates = new HashSet<String>();
        boolean antlr = false;
        for (var artifact : artifacts) {
            String id = artifact.coordinates();
            if (!names.add(artifact.fileName()) || !coordinates.add(id)) throw new IllegalStateException("Duplicate closure entry");
            if (!Files.isRegularFile(artifact.file()) || !JvmBuildInputs.sha256(artifact.file()).equals(artifact.sha256()))
                throw new IllegalStateException("Missing/changed closure file");
            if (id.startsWith("maven/guru.interlis/")) {
                String[] parts = id.split("/");
                if (parts.length != 4 || !MODULES.contains(parts[2]) || !parts[3].equals("0.2.0-SNAPSHOT"))
                    throw new IllegalStateException("Forbidden own runtime: " + id);
                own.add(parts[2]);
            } else if (id.equals("maven/org.antlr/antlr4-runtime/4.13.2")) antlr = true;
            else throw new IllegalStateException("Unexpected compiler runtime: " + id);
        }
        if (!own.equals(Set.copyOf(MODULES)) || !antlr || artifacts.size() != MODULES.size() + 1)
            throw new IllegalStateException("Incomplete compiler closure");
    }

    /** Validates source freshness, authoritative Jenesis ordering, confined copies and the complete runtime file set. */
    @SuppressWarnings("unchecked")
    public static List<Path> readVerified(Path root, Path manifestFile) throws Exception {
        if (!manifestFile.isAbsolute()) throw new IllegalArgumentException("Absolute manifest required");
        String raw = Files.readString(manifestFile);
        var manifest = (Map<String,Object>) Json.parse(raw);
        if (!(json(manifest) + "\n").equals(raw)) throw new IllegalStateException("Noncanonical/ambiguous manifest");
        if (!"1.0".equals(manifest.get("schemaVersion")) || !"interlis-compiler-ape-inputs".equals(manifest.get("kind")) || !MAIN.equals(manifest.get("mainClass")))
            throw new IllegalStateException("Wrong compiler input schema/entry point");
        var bound = manifest.get("buildInputs");
        if (!inputs(root).equals(bound)) throw new IllegalStateException("Stale source fingerprint");
        var authoritative = ModuleArtifacts.runtime(root, "interlis-compiler-cli", JenesisBridge.readExistingOutcome(root));
        verifyClosure(authoritative);
        if (!json(rows(authoritative)).equals(json(manifest.get("orderedRuntime")))) throw new IllegalStateException("Foreign/incomplete/reordered runtime");
        if (!digest(Map.of("orderedRuntime", manifest.get("orderedRuntime"), "buildInputs", bound)).equals(manifest.get("productInputDigest")))
            throw new IllegalStateException("Wrong input digest");
        Path base = manifestFile.getParent().toRealPath();
        var expected = new TreeSet<Path>(); var classpath = new ArrayList<Path>();
        for (var row : (List<Map<String,Object>>)manifest.get("orderedRuntime")) {
            Path copy = JvmBuildInputs.confined(base, (String)row.get("path"));
            if (!JvmBuildInputs.sha256(copy).equals(row.get("sha256")) || Files.size(copy) != ((Number)row.get("bytes")).longValue())
                throw new IllegalStateException("Changed runtime copy");
            classpath.add(copy); expected.add(copy);
        }
        try (var files = Files.walk(base.resolve("runtime"))) {
            if (!expected.equals(new TreeSet<>(files.filter(p -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList())))
                throw new IllegalStateException("Runtime file set drift");
        }
        return List.copyOf(classpath);
    }

    private static List<Map<String,Object>> rows(List<ModuleArtifacts.Artifact> artifacts) throws Exception {
        var result = new ArrayList<Map<String,Object>>();
        for (var a : artifacts) result.add(Map.of("coordinates", a.coordinates(), "path", "runtime/" + a.fileName(),
                "sha256", a.sha256(), "bytes", Files.size(a.file())));
        return List.copyOf(result);
    }

    private static Map<String,String> inputs(Path root) throws Exception {
        var result = new TreeMap<String,String>();
        for (String module : MODULES) result.putAll(JvmBuildInputs.snapshot(root, module).files());
        return Collections.unmodifiableMap(result);
    }

    public static Map<String,Object> source(Path root) throws Exception {
        return Map.of("commit", git(root, "rev-parse", "HEAD"), "tree", git(root, "rev-parse", "HEAD^{tree}"),
                "dirty", !git(root, "status", "--porcelain").isBlank());
    }
    private static String git(Path root, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", root.toString())); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.waitFor() != 0) throw new IllegalStateException("Git identity unavailable"); return text;
    }
    private static String digest(Object value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8)));
    }
    public static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + Json.escaped(s) + "\"";
        if (value instanceof Number) return new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map<?,?> m) return m.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(e -> json(e.getKey().toString()) + ":" + json(e.getValue())).collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Collection<?> c) return c.stream().map(CompilerRuntimeInputs::json).collect(Collectors.joining(",", "[", "]"));
        throw new IllegalArgumentException("Unsupported receipt value");
    }
}
