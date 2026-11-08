package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed backend steps consuming an already verified snapshot; never builds shared JVM sources. */
public final class WasmBuildRun {
    public static final Set<String> PROFILES=Set.of("teavm-smoke", "wasm-prepare", "webimage-smoke", "browser-prepare", "browser-smoke", "browser-check");

    public static void run(BuildRequest request) throws Exception {
        Path root=request.root(),manifest=Path.of(request.options().get("--inputs"));
        String expected=request.options().get("--inputs-sha256");
        if(expected==null) {
            String digest=manifest.getParent().getFileName().toString();
            if(!manifest.startsWith(root.resolve("target/jenesis-jvm/consumers"))||!digest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("External inputs require --inputs-sha256 from their producer");
            expected=Files.readString(JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/consumer-references/"+digest+".sha256")).trim();
        }
        var snapshot=ConsumerSnapshot.read(manifest,expected);
        String profile=request.options().get("--profile");
        if(!PROFILES.contains(profile))throw new IllegalArgumentException("Unknown backend profile");
        String selectedNode=System.getenv("INTERLIS_NODE");
        if(selectedNode==null||!Path.of(selectedNode).isAbsolute()||!Files.isRegularFile(Path.of(selectedNode)))
            throw new IllegalArgumentException("INTERLIS_NODE must select the pinned absolute Node executable");
        Path lock=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/wasm-run.lock");
        try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lease=channel.tryLock()) {
            if(lease==null)throw new IllegalStateException("Another backend orchestration is active");
            var command = new ArrayList<String>();
            command.add(root.resolve(System.getProperty("os.name").startsWith("Windows") ? "gradlew.bat" : "gradlew").toString());
            command.add(":checkNodeRuntime");
            switch (profile) {
                case "teavm-smoke" -> command.addAll(List.of(":interlis-wasm-teavm:teavmJsTest",
                        ":interlis-wasm-teavm:teavmWasmTest", ":interlis-wasm-teavm:browserClientTest",
                        ":interlis-wasm-teavm:diagramAdapterTest"));
                case "wasm-prepare" -> command.add(":interlis-wasm-teavm:buildWasmGC");
                case "webimage-smoke" -> command.add(":interlis-wasm-webimage:webImageSmoke");
                case "browser-prepare" -> command.add(":interlis-wasm-teavm:prepareBrowserHarness");
                case "browser-smoke" -> command.add(":interlis-wasm-teavm:browserSmoke");
                case "browser-check" -> command.add(":interlis-wasm-teavm:browserCheck");
                default -> throw new IllegalArgumentException("Unknown backend mode: " + profile);
            }
            command.addAll(List.of("--no-daemon", "-PjvmInputs=" + snapshot.manifest(),
                    "-PjvmInputsSha256=" + snapshot.manifestSha256(), "-PnodeExecutable=" + selectedNode));
            execute(root, command, Duration.ofMinutes(20));
            ConsumerSnapshot.read(snapshot.manifest(), snapshot.manifestSha256());
            verifyCodegen(root, snapshot, profile);

        }
    }

    private static void verifyCodegen(Path root,ConsumerSnapshot.Snapshot snapshot,String profile) throws Exception {
        String project=!profile.startsWith("webimage")?"interlis-wasm-teavm":"interlis-wasm-webimage";
        var tasks=profile.startsWith("webimage")?List.of("webImageBuild"):profile.equals("teavm-smoke")
                ?List.of("generateJavaScript","generateWasmGC","buildWasmGC"):List.of("generateWasmGC","buildWasmGC");
        for(String task:tasks) {
            Path file=root.resolve(project+"/build/consumer-inputs/"+task+".json");
            var receipt=object(build.jenesis.Json.parse(Files.readString(file)));
            if(!snapshot.manifestSha256().equals(receipt.get("manifestSha256"))||!snapshot.digest().equals(receipt.get("snapshotDigest")))
                throw new IllegalStateException("Foreign backend receipt");
            var outputs=object(receipt.get("outputs"));if(outputs.isEmpty())throw new IllegalStateException("Empty backend output inventory");
            for(var entry:outputs.entrySet()) {
                Path output=JvmBuildInputs.confined(root.resolve(project+"/build"),entry.getKey());
                if(!JvmBuildInputs.sha256(output).equals(object(entry.getValue()).get("sha256")))throw new IllegalStateException("Backend output changed after codegen");
            }
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) {
        if(!(value instanceof Map<?,?>))throw new IllegalStateException("Missing report object");return (Map<String,Object>)value;
    }

    private static void execute(Path root, List<String> command, Duration timeout) throws Exception {
        var builder = new ProcessBuilder(command).directory(root.toFile()).inheritIO();
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        String node = System.getenv("INTERLIS_NODE");
        String separator = java.io.File.pathSeparator;
        builder.environment().put("PATH", Path.of(System.getProperty("java.home"), "bin") + separator
                + Path.of(node).getParent() + separator + builder.environment().getOrDefault("PATH", ""));
        var process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IllegalStateException("Backend build timed out");
            if (process.exitValue() != 0) throw new IllegalStateException("Backend build failed with exit " + process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly().waitFor();
            }
        }
    }
}
