package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed backend steps consuming an already verified snapshot; never builds shared JVM sources. */
public final class WasmBuildRun {
    public static final Set<String> PROFILES=Set.of("teavm-smoke","webimage-smoke","teavm-contracts","webimage-contracts","browser-prepare","browser-contracts");

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
        Path run=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/wasm-runs/"+UUID.randomUUID());Files.createDirectories(run);
        Path lock=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/wasm-run.lock");
        try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lease=channel.tryLock()) {
            if(lease==null)throw new IllegalStateException("Another backend orchestration is active");
            execute(root,List.of(selectedNode,"--version"),run,"node-version",Duration.ofSeconds(10));
            var pins=new Properties();try(var reader=Files.newBufferedReader(root.resolve("gradle.properties"))){pins.load(reader);}
            if(!Files.readString(run.resolve("node-version.stdout")).trim().equals("v"+pins.getProperty("nodeVersion")))throw new IllegalStateException("Wrong Node runtime");
            var command=new ArrayList<String>();command.add(root.resolve(System.getProperty("os.name").startsWith("Windows")?"gradlew.bat":"gradlew").toString());
            boolean webimage=profile.startsWith("webimage");
            if(webimage)command.add(":interlis-wasm-webimage:webImageBuild");
            else command.addAll(List.of(":interlis-wasm-teavm:generateJavaScript",":interlis-wasm-teavm:buildWasmGC"));
            switch(profile) {
                case "teavm-contracts" -> command.addAll(List.of(":interlis-wasm-teavm:check",":interlis-wasm-teavm:jsContractTest",":interlis-wasm-teavm:wasmContractTest",":interlis-wasm-teavm:diagramAdapterTest",":interlis-wasm-teavm:browserClientTest"));
                case "webimage-contracts" -> command.addAll(List.of(":interlis-wasm-webimage:check",":interlis-wasm-webimage:webImageProbe"));
                case "browser-prepare" -> command.add(":interlis-wasm-teavm:prepareBrowserHarness");
                case "browser-contracts" -> command.addAll(List.of(":interlis-wasm-teavm:browserContractTest",":interlis-wasm-teavm:uiProjectionTest",":interlis-wasm-teavm:safariContractTest",":interlis-wasm-webimage:webImageBrowserTest"));
                default -> { }
            }
            command.addAll(List.of("--no-daemon","-PjvmInputs="+snapshot.manifest(),"-PjvmInputsSha256="+snapshot.manifestSha256(),"-PnodeExecutable="+selectedNode));
            execute(root,command,run,"gradle",Duration.ofMinutes(20));
            ConsumerSnapshot.read(snapshot.manifest(),snapshot.manifestSha256());
            verifyCodegen(root,snapshot,profile);
            var reports=new TreeMap<String,String>();
            for(String backend:!webimage?List.of("teavm-js","teavm-wasm"):List.of("webimage")) {
                Path report=JvmBuildInputs.requireOutputRoot(root,(backend.equals("webimage")?"interlis-wasm-webimage":"interlis-wasm-teavm")+"/build/consumer-smoke-"+backend+".json");
                Files.deleteIfExists(report);
                var smoke=new ArrayList<String>();smoke.add(selectedNode);
                if(backend.equals("teavm-wasm"))smoke.add("--experimental-wasm-exnref");
                smoke.addAll(List.of(root.resolve("tools/verification/backend-smoke.cjs").toString(),backend,snapshot.manifest().toString(),snapshot.manifestSha256()));
                execute(root,smoke,run,backend,Duration.ofSeconds(60));
                var actual=object(build.jenesis.Json.parse(Files.readString(report)));
                if(!"passed".equals(actual.get("status"))||!"consumer-smoke-only".equals(actual.get("scope"))
                        ||!backend.equals(actual.get("backend"))||!snapshot.manifestSha256().equals(actual.get("manifestSha256"))
                        ||!snapshot.digest().equals(actual.get("snapshotDigest"))||!(actual.get("cases") instanceof List<?> cases)||cases.size()!=2
                        ||!"succeeded".equals(object(cases.get(0)).get("outcome"))||!"rejected".equals(object(cases.get(1)).get("outcome")))
                    throw new IllegalStateException("Incomplete/foreign runtime smoke report");
                Files.copy(report,run.resolve(backend+".report.json"));reports.put(backend,JvmBuildInputs.sha256(report));
            }
            ConsumerSnapshot.read(snapshot.manifest(),snapshot.manifestSha256());
            verifyCodegen(root,snapshot,profile);
            Files.writeString(run.resolve("result.json"),JsonOutput.write(Map.of("scope","consumer-smoke-only","status","passed",
                    "profile",profile,"snapshotDigest",snapshot.digest(),"manifestSha256",snapshot.manifestSha256(),"reports",reports))+"\n");
            System.out.println("Backend consumer smoke completed: "+run);
        }
    }

    private static void verifyCodegen(Path root,ConsumerSnapshot.Snapshot snapshot,String profile) throws Exception {
        String project=!profile.startsWith("webimage")?"interlis-wasm-teavm":"interlis-wasm-webimage";
        for(String task:!profile.startsWith("webimage")?List.of("generateJavaScript","generateWasmGC","buildWasmGC"):List.of("webImageBuild")) {
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

    private static void execute(Path root,List<String> command,Path run,String name,Duration timeout) throws Exception {
        Files.writeString(run.resolve(name+".argv.json"),JsonOutput.write(command));
        var builder=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(run.resolve(name+".stdout").toFile()).redirectError(run.resolve(name+".stderr").toFile());
        builder.environment().put("JAVA_HOME",System.getProperty("java.home"));
        String node=System.getenv("INTERLIS_NODE"),separator=java.io.File.pathSeparator;
        builder.environment().put("PATH",Path.of(System.getProperty("java.home"),"bin")+separator+(node==null?"":Path.of(node).getParent()+separator)+builder.environment().getOrDefault("PATH",""));
        var process=builder.start();
        try {
            boolean finished=process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS);
            var receipt=new TreeMap<String,Object>();receipt.put("timedOut",!finished);receipt.put("exitCode",finished?process.exitValue():null);
            Files.writeString(run.resolve(name+".process.json"),JsonOutput.write(receipt)+"\n");
            if(!finished||process.exitValue()!=0)throw new IllegalStateException("Backend process failed: "+name+"; "+run);
        } finally {if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
}
