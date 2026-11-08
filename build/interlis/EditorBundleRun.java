package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Fixed JVM snapshot -> Node renderer -> pure bundle assembly, without invoking Gradle or an extension build. */
public final class EditorBundleRun {
    private record Inputs(ConsumerSnapshot.Snapshot snapshot,
            Map<String,String> sources,Map<String,Object> metadata,String requestId) {
        public Inputs {sources=Map.copyOf(sources);metadata=Map.copyOf(metadata);}
    }

    /** Called under the JVM producer lock. No Node/consumer process is launched here. */
    public static void execute(BuildRequest request) throws Exception {
        Path root=request.root(),lock=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/editor-run.lock");
        try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lease=channel.tryLock()) {
            if(lease==null)throw new IllegalStateException("Another editor bundle run is active");
            Inputs input;
            try(var jvm=java.nio.channels.FileChannel.open(JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/project.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var jvmLease=jvm.tryLock()) {
                if(jvmLease==null)throw new IllegalStateException("Another JVM producer is active");input=prepare(request);
            }
            run(request,input);
        }
    }

    private static Inputs prepare(BuildRequest request) throws Exception {
        Path root=request.root();String commit=git(root,"rev-parse","HEAD"),tree=git(root,"rev-parse","HEAD^{tree}");
        var outcome=JenesisBridge.run(new BuildRequest("artifacts",null,null,root));
        var snapshot=ConsumerSnapshot.create(root,outcome);var metadata=new TreeMap<String,Object>();
        metadata.put("coreCommit",commit);metadata.put("coreTree",tree);metadata.put("dirty",!git(root,"status","--porcelain").isBlank());
        metadata.put("editorProtocolVersion",1);metadata.put("diagramDocumentVersion","1.0");
        metadata.put("serverVersion",snapshot.data().get("projectVersion"));
        var lsp=JenesisBridge.moduleResult(outcome,"interlis-lsp");
        if(!lsp.explicitDescriptor()||!lsp.moduleName().orElseThrow().equals("guru.interlis.lsp"))throw new IllegalStateException("Editor server must be explicit JPMS");
        metadata.put("launch",Map.of("kind","module","moduleName",lsp.moduleName().orElseThrow(),"mainClass","guru.interlis.lsp.Main"));
        var moduleInventory=new TreeMap<String,Object>();var moduleNames=new HashSet<String>();
        for(Path jar:ConsumerSnapshot.closure(snapshot,"lsp")) {
            var found=java.lang.module.ModuleFinder.of(jar).findAll();if(found.size()!=1)throw new IllegalStateException("Unknown editor runtime module");
            var descriptor=found.iterator().next().descriptor();
            if(!moduleNames.add(descriptor.name())||(jar.getFileName().toString().startsWith("interlis-")&&descriptor.isAutomatic()))throw new IllegalStateException("Duplicate/automatic own editor module");
            moduleInventory.put("lib/"+jar.getFileName(),Map.of("name",descriptor.name(),"explicit",!descriptor.isAutomatic(),"sha256",JvmBuildInputs.sha256(jar)));
        }
        metadata.put("moduleInventory",moduleInventory);
        metadata.put("snapshotDigest",snapshot.digest());metadata.put("snapshotManifestSha256",snapshot.manifestSha256());
        var sources=sources(root);metadata.put("editorSourceDigest",digest(sources));
        return new Inputs(snapshot,sources,metadata,
                request.options().getOrDefault("--request-id",UUID.randomUUID().toString()));
    }

    /** Called after releasing the JVM lock. The consumer lease protects immutable inputs and generated bundle outputs. */
    private static void run(BuildRequest request,Inputs prepared) throws Exception {
        Path root=request.root();String node=System.getenv("INTERLIS_NODE");
        if(node==null||!Path.of(node).isAbsolute())throw new IllegalArgumentException("Select the pinned absolute INTERLIS_NODE executable");
        Path lock=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/consumer.lock");
        try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lease=channel.tryLock()) {
            if(lease==null)throw new IllegalStateException("Another consumer is active");
            Path run=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/editor-runs/"+prepared.requestId());Files.createDirectories(run.getParent());Files.createDirectory(run);
            process(root,List.of(node,"--version"),run.resolve("node-version"));
            var pins=new Properties();try(var reader=Files.newBufferedReader(root.resolve("gradle.properties"))){pins.load(reader);}
            if(!Files.readString(run.resolve("node-version.stdout")).trim().equals("v"+pins.getProperty("nodeVersion")))throw new IllegalStateException("Wrong Node runtime");
            verify(root,prepared);
            for(String directory:List.of("dist","build/package","node_modules"))JvmBuildInputs.requireOutputRoot(root,"frontend/diagram-ui/"+directory);
            process(root,List.of(node,root.resolve("frontend/diagram-ui/prepare.cjs").toString()),run.resolve("renderer"));
            process(root,List.of(node,root.resolve("frontend/diagram-ui/package-renderer.cjs").toString()),run.resolve("renderer-package"));
            verify(root,prepared);
            var files=new ArrayList<Map<String,Object>>();
            for(Path jar:ConsumerSnapshot.closure(prepared.snapshot(),"lsp"))add(files,jar,"server/lib/"+jar.getFileName());
            try(var walk=Files.walk(root.resolve("frontend/diagram-ui/dist"))) {
                for(Path file:walk.filter(Files::isRegularFile).sorted().toList())add(files,file,"diagram-ui/"+root.resolve("frontend/diagram-ui/dist").relativize(file).toString().replace('\\','/'));
            }
            List<Path> packages;try(var list=Files.list(root.resolve("frontend/diagram-ui/build/package"))){packages=list.filter(p->p.toString().endsWith(".tgz")).toList();}
            if(packages.size()!=1)throw new IllegalStateException("Ambiguous renderer package");add(files,packages.getFirst(),"diagram-ui.tgz");
            Path protocol=run.resolve("editor-protocol.ts");
            Files.writeString(protocol,Files.readString(root.resolve("interlis-lsp/src/main/typescript/editor-protocol.ts"))
                    .replace("'../../../../frontend/diagram-ui/src/types'","'./diagram-document'"));
            add(files,protocol,"types/editor-protocol.ts");add(files,root.resolve("frontend/diagram-ui/src/types.ts"),"types/diagram-document.ts");
            for(var schema:Map.of("interlis-projection","projection/diagram-document-1.0.schema.json","interlis-wasm-bridge","bridge/diagram-options-1.0.schema.json").entrySet()) {
                Path source=root.resolve(schema.getKey()+"/src/main/resources/guru/interlis/"+schema.getValue());
                Path jar=ConsumerSnapshot.closure(prepared.snapshot(),schema.getKey().equals("interlis-projection")?"lsp":"teavm").stream().filter(p->p.getFileName().toString().startsWith(schema.getKey()+"-")).findFirst().orElseThrow();
                try(var archive=new JarFile(jar.toFile())) {
                    var entry=archive.getJarEntry("guru/interlis/"+schema.getValue());
                    if(entry==null||!Arrays.equals(Files.readAllBytes(source),archive.getInputStream(entry).readAllBytes()))throw new IllegalStateException("Schema differs from producing JAR");
                }
                add(files,source,"schemas/"+source.getFileName());
            }
            add(files,root.resolve("frontend/diagram-ui/provenance.json"),"renderer-provenance.json");
            add(files,root.resolve("LICENSE"),"licenses/interlis-java.txt");
            Path output=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/editor-bundles");
            Path input=run.resolve("inputs.json");Files.writeString(input,JsonOutput.write(Map.of("schemaVersion",1,"outputRoot",output.toString(),
                    "metadata",prepared.metadata(),"files",files,"requestId",prepared.requestId()))+"\n");
            Path result=run.resolve("result.json");
            process(root,List.of(node,root.resolve("tools/editor/bundle.cjs").toString(),input.toString(),JvmBuildInputs.sha256(input),result.toString()),run.resolve("assembly"));
            verify(root,prepared);
            Files.copy(result,root.resolve("target/jenesis-jvm/reports/editor-bundle.json"),StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Editor bundle result: "+root.resolve("target/jenesis-jvm/reports/editor-bundle.json"));
        }
    }
    private static void verify(Path root,Inputs input) throws Exception {
        if(!git(root,"rev-parse","HEAD").equals(input.metadata().get("coreCommit"))||!sources(root).equals(input.sources()))throw new IllegalStateException("Editor sources changed during build");
        ConsumerSnapshot.read(input.snapshot().manifest(),input.snapshot().manifestSha256());
    }
    private static Map<String,String> sources(Path root) throws Exception {
        var files=new TreeMap<String,String>();
        try(var walk=Files.walk(root.resolve("frontend/diagram-ui"))) {
            for(Path file:walk.toList()) {
                String relative=root.relativize(file).toString().replace('\\','/');
                if(Arrays.stream(relative.split("/")).anyMatch(p->Set.of("node_modules","dist","build","generated","test",".DS_Store").contains(p)))continue;
                if(Files.isSymbolicLink(file))throw new IllegalStateException("Symlink editor source");
                if(Files.isRegularFile(file))files.put(relative,JvmBuildInputs.sha256(file));
            }
        }
        for(String relative:List.of("LICENSE","interlis-lsp/src/main/typescript/editor-protocol.ts",
                "interlis-projection/src/main/resources/guru/interlis/projection/diagram-document-1.0.schema.json",
                "interlis-wasm-bridge/src/main/resources/guru/interlis/bridge/diagram-options-1.0.schema.json"))files.put(relative,JvmBuildInputs.sha256(JvmBuildInputs.confined(root,relative)));
        return Map.copyOf(files);
    }
    private static String digest(Map<String,String> sources) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JsonOutput.write(sources).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static void add(List<Map<String,Object>> files,Path source,String destination) throws Exception {
        if(Files.isSymbolicLink(source))throw new IllegalStateException("Symlink bundle source");
        files.add(Map.of("source",source.toAbsolutePath().toString(),"path",destination,"sha256",JvmBuildInputs.sha256(source)));
    }
    private static String git(Path root,String... args) throws Exception {
        var command=new ArrayList<String>();command.add("git");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).directory(root.toFile()).start();String value=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0)throw new IllegalStateException("Cannot inspect Core provenance");return value;
    }
    private static void process(Path root,List<String> command,Path output) throws Exception {
        Files.writeString(output.resolveSibling(output.getFileName()+".argv.json"),JsonOutput.write(command));
        var builder=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(output.resolveSibling(output.getFileName()+".stdout").toFile()).redirectError(output.resolveSibling(output.getFileName()+".stderr").toFile());
        builder.environment().put("PATH",Path.of(command.getFirst()).getParent()+java.io.File.pathSeparator+builder.environment().getOrDefault("PATH",""));
        var process=builder.start();try {
            if(!process.waitFor(180,TimeUnit.SECONDS))throw new IllegalStateException("Editor process timeout: "+output);
            Files.writeString(output.resolveSibling(output.getFileName()+".exit"),Integer.toString(process.exitValue()));
            if(process.exitValue()!=0)throw new IllegalStateException("Editor process failed: "+output);
        }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
}
