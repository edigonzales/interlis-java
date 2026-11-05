import build.interlis.ape.*;
import build.interlis.*;
import build.jenesis.Json;
import java.nio.file.*;
import java.util.*;

/** Scratch-only mutation checks; original source and snapshot bytes are never modified. */
public class InputChecks {
    static void rejects(String name, RunnableWithException action) throws Exception {
        try { action.run(); } catch (Exception expected) { System.out.println("REJECTED " + name + ": " + expected.getMessage()); return; }
        throw new AssertionError("Accepted mutation: " + name);
    }
    interface RunnableWithException { void run() throws Exception; }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), original = Path.of(args[1]), scratch = Path.of(args[2]);
        var cp = CompilerRuntimeInputs.readVerified(root, original);
        rejects("empty operation", () -> CompilerApe.parse(new String[]{}));
        rejects("unknown", () -> CompilerApe.parse(new String[]{"build"}));
        rejects("extra", () -> CompilerApe.parse(new String[]{"inspect", "--lock", "x"}));
        rejects("relative", () -> CompilerApe.parse(new String[]{"verify", "--inputs", "pom.xml"}));
        rejects("existing parser", () -> build.InterlisJvmBuild.main(new String[]{"build", "--module", "foreign"}));
        Files.createDirectories(scratch.resolve("runtime"));
        for (Path jar : cp) Files.copy(jar, scratch.resolve("runtime").resolve(jar.getFileName()));
        Path manifest = Files.copy(original, scratch.resolve("compiler-inputs.json"));
        CompilerRuntimeInputs.readVerified(root, manifest);
        var parsed = (Map<String,Object>)Json.parse(Files.readString(original));
        for (String mutation : List.of("missing", "foreign", "reorder", "stale", "digest")) {
            var data = (Map<String,Object>)Json.parse(Files.readString(original));
            var rows = (List<Map<String,Object>>)data.get("orderedRuntime");
            switch (mutation) {
                case "missing" -> rows.removeLast();
                case "foreign" -> rows.getFirst().put("coordinates", "maven/guru.interlis/interlis-lsp/0.2.0-SNAPSHOT");
                case "reorder" -> Collections.swap(rows,0,1);
                case "stale" -> ((Map<String,Object>)data.get("buildInputs")).put("pom.xml", "0".repeat(64));
                case "digest" -> data.put("productInputDigest", "0".repeat(64));
            }
            Files.writeString(manifest, CompilerRuntimeInputs.json(data) + "\n");
            rejects(mutation, () -> CompilerRuntimeInputs.readVerified(root, manifest));
        }
        Files.copy(original,manifest,StandardCopyOption.REPLACE_EXISTING);
        Path extra = Files.copy(cp.getFirst(),scratch.resolve("runtime/extra.jar"));
        rejects("extra jar", () -> CompilerRuntimeInputs.readVerified(root,manifest)); Files.delete(extra);
        Path copy = scratch.resolve("runtime").resolve(cp.getFirst().getFileName());
        Files.writeString(copy,"changed");
        rejects("changed jar", () -> CompilerRuntimeInputs.readVerified(root,manifest));
        Files.copy(cp.getFirst(),copy,StandardCopyOption.REPLACE_EXISTING);
        CompilerRuntimeInputs.readVerified(root,manifest);
        var closure = new ArrayList<ModuleArtifacts.Artifact>();
        for (var row : (List<Map<String,Object>>)parsed.get("orderedRuntime")) {
            closure.add(new ModuleArtifacts.Artifact((String)row.get("coordinates"),original.getParent().resolve((String)row.get("path")),(String)row.get("sha256")));
        }
        CompilerRuntimeInputs.verifyClosure(closure);
        closure.set(0,new ModuleArtifacts.Artifact("maven/guru.interlis/interlis-verification/0.2.0-SNAPSHOT",cp.getFirst(),JvmBuildInputs.sha256(cp.getFirst())));
        rejects("tool leak", () -> CompilerRuntimeInputs.verifyClosure(closure));
        System.out.println("INPUT_CHECKS_PASS");
    }
}
