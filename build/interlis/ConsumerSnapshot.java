package build.interlis;

import build.jenesis.Json;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable, content-addressed consumer files. Expected manifest hashes are supplied by the producer receipt. */
public final class ConsumerSnapshot {
    private static final List<String> PAYLOAD=List.of("schemaVersion","kind","projectVersion",
            "productInputDigest","enginePinDigest","artifacts","closures","files");
    public record Snapshot(Path manifest,String manifestSha256,String digest,Map<String,Object> data) {
        public Snapshot { manifest=manifest.toAbsolutePath().normalize();data=object(freeze(data)); }
        public Path root() { return manifest.getParent(); }
    }

    /** Read-only developer/fixture entrypoint using the same consumer verifier. */
    public static void main(String[] args) throws Exception {
        if(args.length!=3||!args[0].equals("verify")||!Path.of(args[1]).isAbsolute())throw new IllegalArgumentException("Expected verify ABSOLUTE_MANIFEST EXPECTED_SHA256");
        System.out.println(read(Path.of(args[1]),args[2]).digest());
    }

    /** Creates one verified snapshot from one actual engine outcome; no test success is inferred. */
    public static Snapshot create(Path root,JenesisBridge.BuildOutcome outcome) throws Exception {
        var artifacts=new TreeMap<String,ModuleArtifacts.Artifact>();var closures=new TreeMap<String,Object>();
        for(var selection:Map.of("teavm","interlis-wasm-bridge","webimage","interlis-wasm-bridge","lsp","interlis-lsp").entrySet()) {
            var closure=ModuleArtifacts.runtime(root,selection.getValue(),outcome);
            closures.put(selection.getKey(),closure.stream().map(ModuleArtifacts.Artifact::coordinates).toList());
            for(var artifact:closure) {
                var previous=artifacts.putIfAbsent(artifact.coordinates(),artifact);
                if(previous!=null&&!previous.sha256().equals(artifact.sha256()))throw new IllegalStateException("Conflicting coordinate: "+artifact.coordinates());
            }
        }
        Path outputs=root.resolve("target/jenesis-jvm"),consumers=outputs.resolve("consumers");
        for(String directory:List.of("consumers","consumer-references","handoffs","reports"))
            JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/"+directory);
        Files.createDirectories(consumers);
        Path staging=Files.createDirectory(consumers.resolve(".staging-"+UUID.randomUUID()));
        var files=new TreeMap<String,Object>();var rows=new ArrayList<Object>();var inputs=new TreeMap<String,Object>();
        for(var artifact:artifacts.values()) {
            String coordinate=artifact.coordinates();String[] id=coordinate.split("/");
            if(id.length!=4||!id[0].equals("maven"))throw new IllegalStateException("Unexpected runtime coordinate: "+coordinate);
            String base="maven/"+id[1].replace('.','/')+"/"+id[2]+"/"+id[3]+"/"+id[2]+"-"+id[3];
            var row=new TreeMap<String,Object>();row.put("coordinate",coordinate);
            row.put("jar",copy(artifact.file(),staging,base+".jar",files));
            Path jar=staging.resolve(base+".jar");
            var modules=java.lang.module.ModuleFinder.of(jar).findAll();
            if(modules.size()!=1)throw new IllegalStateException("Unidentified module: "+coordinate);
            var descriptor=modules.iterator().next().descriptor();
            if(coordinate.startsWith("maven/guru.interlis/"))ModulePathContract.requireOwnDescriptor(coordinate.split("/")[2],descriptor);row.put("moduleName",descriptor.name());
            row.put("descriptorKind",descriptor.isAutomatic()?"automatic":"explicit");
            if(id[1].equals("guru.interlis")) {
                if(!JvmBuildInputs.MODULES.contains(id[2]))throw new IllegalStateException("Nonproduct own artifact");
                row.put("projectId",id[2]);
                row.put("pom",copy(ModuleArtifacts.pom(outcome,id[2]),staging,base+".pom",files));
                row.put("sources",copy(ModuleArtifacts.sources(outcome,id[2]),staging,base+"-sources.jar",files));
                inputs.put(id[2],JvmBuildInputs.fingerprint(root,id[2]));
            }
            rows.add(row);
        }
        // Preserve the actual resolved POMs, including required parent/BOM metadata.
        // Own generated POMs are flattened by the engine and were copied above.
        for(String project:inputs.keySet()) {
            var module=JenesisBridge.moduleResult(outcome,project);var data=new Properties();
            try(var reader=Files.newBufferedReader(module.inventoryDirectory().resolve("inventory.properties"))){data.load(reader);}
            for(int i=0;data.containsKey(module.inventoryPrefix()+".pom."+i);i++) {
                String[] fields=data.getProperty(module.inventoryPrefix()+".pom."+i).split(" ");
                if(fields.length!=3||!fields[2].startsWith("SHA-256/"))throw new IllegalStateException("Unbound POM metadata");
                String[] id=fields[0].split("/");
                if(id.length!=5||!id[0].equals("maven")||!id[3].equals("pom"))throw new IllegalStateException("Unexpected POM identity");
                if(id[1].equals("guru.interlis"))continue;
                Path source=JenesisBridge.requireArtifact(root.resolve("target/jenesis-jvm/engine"),module.inventoryDirectory(),fields[1]);
                if(!JvmBuildInputs.sha256(source).equals(fields[2].substring(8)))throw new IllegalStateException("Changed resolved POM");
                String path="maven/"+id[1].replace('.','/')+"/"+id[2]+"/"+id[4]+"/"+id[2]+"-"+id[4]+".pom";
                copy(source,staging,path,files);
                for(Object value:rows) {
                    var row=object(value);
                    if(row.get("coordinate").equals("maven/"+id[1]+"/"+id[2]+"/"+id[4]))row.put("pom",reference(path,files));
                }
            }
        }
        for(Object value:rows)if(!object(value).containsKey("pom"))throw new IllegalStateException("Runtime artifact without resolved POM");
        Path versionRun=outputs.resolve("handoffs/version-"+UUID.randomUUID());Files.createDirectories(versionRun);
        var versionRuntime=ModuleArtifacts.runtime(root,"interlis-compiler-cli",outcome);
        var command=List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-cp",
                String.join(java.io.File.pathSeparator,versionRuntime.stream().map(a->a.file().toString()).toList()),
                "guru.interlis.cli.Main","version","--json");
        Files.writeString(versionRun.resolve("argv.json"),JsonOutput.write(command));
        Path versions=versionRun.resolve("version-manifest.json"),error=versionRun.resolve("stderr");
        var process=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(versions.toFile()).redirectError(error.toFile()).start();
        try {
            if(!process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Version metadata timeout");
            Files.writeString(versionRun.resolve("exit"),Integer.toString(process.exitValue()));
            if(process.exitValue()!=0||Files.size(error)!=0||!object(Json.parse(Files.readString(versions))).get("version").equals(JvmBuildInputs.projectVersion(root)))
                throw new IllegalStateException("Invalid actual CLI version metadata");
        } finally { if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();} }
        copy(versions,staging,"resources/version-manifest.json",files);
        inputs.put("interlis-compiler-cli",JvmBuildInputs.fingerprint(root,"interlis-compiler-cli"));
        var payload=new TreeMap<String,Object>();payload.put("schemaVersion",1);payload.put("kind","interlis-jvm-consumer-snapshot");
        payload.put("projectVersion",JvmBuildInputs.projectVersion(root));payload.put("productInputDigest",digest(JsonOutput.write(inputs)));
        payload.put("enginePinDigest",JvmBuildInputs.sha256(root.resolve("build/JENESIS-UPSTREAM.json")));
        payload.put("artifacts",rows);payload.put("closures",closures);payload.put("files",files);
        String digest=digest(JsonOutput.write(payload));
        var producer=new TreeMap<String,Object>();producer.put("builder","jenesis");producer.put("coreCommit",git(root,"rev-parse","HEAD"));
        producer.put("coreTree",git(root,"rev-parse","HEAD^{tree}"));producer.put("dirty",!git(root,"status","--porcelain").isBlank());
        producer.put("resultInventorySha256",JvmBuildInputs.sha256(outcome.inventory()));
        var manifest=new TreeMap<>(payload);manifest.put("snapshotDigest",digest);manifest.put("producer",producer);
        Path staged=staging.resolve("consumer-manifest.json");Files.writeString(staged,JsonOutput.write(manifest)+"\n");
        Snapshot candidate=read(staged,JvmBuildInputs.sha256(staged));verifyProducer(candidate,artifacts);
        Path destination=consumers.resolve(digest),references=outputs.resolve("consumer-references");Files.createDirectories(references);
        Path reference=references.resolve(digest+".sha256");
        Snapshot result;
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) {
            result=read(destination.resolve("consumer-manifest.json"),Files.readString(reference).trim());
            if(!result.digest().equals(digest))throw new IllegalStateException("Foreign snapshot reference");
            verifyProducer(result,artifacts);
            removePrivateStaging(staging);
        } else {
            Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(reference,candidate.manifestSha256()+"\n",StandardOpenOption.CREATE_NEW);
            result=read(destination.resolve("consumer-manifest.json"),candidate.manifestSha256());
        }
        Path receipts=outputs.resolve("handoffs");Files.createDirectories(receipts);
        Path receipt=receipts.resolve("handoff-"+UUID.randomUUID()+".json");
        Files.writeString(receipt,JsonOutput.write(Map.of("manifest",result.manifest().toString(),"manifestSha256",result.manifestSha256(),
                "snapshotDigest",result.digest(),"currentProducer",producer,"productInputs",inputs))+"\n");
        Path reports=outputs.resolve("reports");Files.createDirectories(reports);
        Files.writeString(reports.resolve("wasm-export.json"),JsonOutput.write(Map.of("manifest",result.manifest().toString(),
                "manifestSha256",result.manifestSha256(),"snapshotDigest",result.digest(),"receipt",receipt.toString()))+"\n");
        return result;
    }

    /** Reads a complete immutable inventory against the independent producer-provided hash. */
    public static Snapshot read(Path manifest,String expectedSha256) throws Exception {
        manifest=manifest.toAbsolutePath().normalize();
        if(expectedSha256==null||!expectedSha256.matches("[0-9a-f]{64}")||!JvmBuildInputs.sha256(manifest).equals(expectedSha256))
            throw new IllegalStateException("Missing/changed expected consumer manifest");
        if(Files.isSymbolicLink(manifest)||Files.isSymbolicLink(manifest.getParent()))throw new IllegalStateException("Symlink snapshot root");
        String encoded=Files.readString(manifest);
        Map<String,Object> data=object(integralNumbers(Json.parse(encoded)));
        if(!encoded.equals(JsonOutput.write(data)+"\n"))throw new IllegalStateException("Noncanonical/ambiguous consumer manifest");
        var keys=new HashSet<>(PAYLOAD);keys.addAll(List.of("snapshotDigest","producer"));
        if(!keys.equals(data.keySet())||!(data.get("schemaVersion") instanceof Number number)||number.intValue()!=1
                ||!data.get("kind").equals("interlis-jvm-consumer-snapshot"))throw new IllegalStateException("Unknown consumer schema");
        for(String key:List.of("productInputDigest","enginePinDigest"))
            if(!string(data.get(key)).matches("[0-9a-f]{64}"))throw new IllegalStateException("Missing producer input identity");
        var producer=object(data.get("producer"));
        if(!producer.keySet().equals(Set.of("builder","coreCommit","coreTree","dirty","resultInventorySha256"))
                ||!producer.get("builder").equals("jenesis")||!(producer.get("dirty") instanceof Boolean)
                ||!string(producer.get("coreCommit")).matches("[0-9a-f]{40}")
                ||!string(producer.get("coreTree")).matches("[0-9a-f]{40}")
                ||!string(producer.get("resultInventorySha256")).matches("[0-9a-f]{64}"))throw new IllegalStateException("Invalid producer identity");
        var payload=new TreeMap<String,Object>();for(String key:PAYLOAD)payload.put(key,data.get(key));
        String digest=digest(JsonOutput.write(payload));
        if(!digest.equals(data.get("snapshotDigest")))throw new IllegalStateException("Changed snapshot payload");
        var snapshot=new Snapshot(manifest,expectedSha256,digest,data);verifyFiles(snapshot);
        verifyPomParents(snapshot);
        var artifacts=list(data.get("artifacts"));var coordinates=new HashSet<String>();
        if(artifacts.isEmpty())throw new IllegalStateException("Empty runtime inventory");
        for(Object item:artifacts) {
            var artifact=object(item);String coordinate=string(artifact.get("coordinate"));
            if(!coordinate.matches("maven/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                    ||coordinate.matches(".*(junit|archive-tool|antlr4/|commons-compress).*"))throw new IllegalStateException("Nonproduct runtime coordinate");
            if(!coordinates.add(coordinate))throw new IllegalStateException("Duplicate coordinate");
            String[] id=coordinate.split("/");
            String base="maven/"+id[1].replace('.','/')+"/"+id[2]+"/"+id[3]+"/"+id[2]+"-"+id[3];
            if(!base.concat(".jar").equals(object(artifact.get("jar")).get("path"))
                    ||!base.concat(".pom").equals(object(artifact.get("pom")).get("path")))throw new IllegalStateException("Foreign coordinate path");
            for(String role:List.of("jar","pom"))verifyReference(data,object(artifact.get(role)));
            var pom=JvmBuildInputs.xml(contained(snapshot.root(),base+".pom")).getDocumentElement();
            var parent=pom.getElementsByTagName("parent");
            String group=child(pom,"groupId"),version=child(pom,"version");
            if(parent.getLength()>0) {
                if(group.isEmpty())group=child((org.w3c.dom.Element)parent.item(0),"groupId");
                if(version.isEmpty())version=child((org.w3c.dom.Element)parent.item(0),"version");
            }
            if(!id[1].equals(group)||!id[2].equals(child(pom,"artifactId"))||!id[3].equals(version))throw new IllegalStateException("POM GAV differs from producer coordinate");
            if(coordinate.startsWith("maven/guru.interlis/")) {
                String project=coordinate.split("/")[2];
                if(!JvmBuildInputs.MODULES.contains(project)||!project.equals(artifact.get("projectId"))
                        ||!coordinate.endsWith("/"+data.get("projectVersion")))throw new IllegalStateException("Foreign own product");
                verifyReference(data,object(artifact.get("sources")));
                if(!base.concat("-sources.jar").equals(object(artifact.get("sources")).get("path")))throw new IllegalStateException("Foreign sources coordinate");
            }
            Path jar=contained(snapshot.root(),string(object(artifact.get("jar")).get("path")));
            var modules=java.lang.module.ModuleFinder.of(jar).findAll();
            if(modules.size()!=1)throw new IllegalStateException("Ambiguous module");
            var descriptor=modules.iterator().next().descriptor();
            if(coordinate.startsWith("maven/guru.interlis/"))ModulePathContract.requireOwnDescriptor(coordinate.split("/")[2],descriptor);
            if(!descriptor.name().equals(artifact.get("moduleName"))||!(descriptor.isAutomatic()?"automatic":"explicit").equals(artifact.get("descriptorKind")))
                throw new IllegalStateException("Foreign descriptor identity");
        }
        var closures=object(data.get("closures"));
        if(!closures.keySet().equals(Set.of("teavm","webimage","lsp")))throw new IllegalStateException("Missing consumer closure");
        var used=new HashSet<String>();
        for(Object closure:closures.values()) {
            var entries=list(closure);var unique=new HashSet<>(entries);
            if(entries.isEmpty()||entries.size()!=unique.size()||!coordinates.containsAll(entries))throw new IllegalStateException("Incomplete/duplicate closure");
            for(Object entry:entries)used.add(string(entry));
        }
        if(!used.equals(coordinates))throw new IllegalStateException("Unused injected runtime artifact");
        return snapshot;
    }

    public static List<Path> closure(Snapshot snapshot,String consumer) throws Exception {
        if(!Set.of("teavm","webimage","lsp").contains(consumer))throw new IllegalArgumentException("Unknown consumer");
        var byCoordinate=new HashMap<String,Path>();
        for(Object item:list(snapshot.data().get("artifacts"))) {
            var artifact=object(item);byCoordinate.put(string(artifact.get("coordinate")),contained(snapshot.root(),string(object(artifact.get("jar")).get("path"))));
        }
        var paths=new ArrayList<Path>();for(Object coordinate:list(object(snapshot.data().get("closures")).get(consumer)))paths.add(byCoordinate.get(coordinate));
        return List.copyOf(paths);
    }

    private static void verifyProducer(Snapshot snapshot,Map<String,ModuleArtifacts.Artifact> current) throws Exception {
        var expected=new TreeMap<String,String>();current.forEach((key,value)->expected.put(key,value.sha256()));
        var actual=new TreeMap<String,String>();
        for(Object row:list(snapshot.data().get("artifacts"))) {
            var artifact=object(row);actual.put(string(artifact.get("coordinate")),string(object(artifact.get("jar")).get("sha256")));
        }
        if(!actual.equals(expected))throw new IllegalStateException("Snapshot differs from independent producer artifacts");
    }

    private static Map<String,Object> copy(Path source,Path staging,String relative,Map<String,Object> files) throws Exception {
        String hash=JvmBuildInputs.sha256(source);long bytes=Files.size(source);
        if(files.containsKey(relative)) {
            if(!files.get(relative).equals(Map.of("sha256",hash,"bytes",bytes)))throw new IllegalStateException("Coordinate/path conflict");
        } else {
            Path destination=staging.resolve(relative);Files.createDirectories(destination.getParent());Files.copy(source,destination);
            if(!JvmBuildInputs.sha256(destination).equals(hash)||!JvmBuildInputs.sha256(source).equals(hash))throw new IllegalStateException("Mutated copy input");
            files.put(relative,Map.of("sha256",hash,"bytes",bytes));
        }
        return reference(relative,files);
    }
    private static Map<String,Object> reference(String path,Map<String,Object> files) {
        var ref=new TreeMap<>(object(files.get(path)));ref.put("path",path);return ref;
    }
    private static void verifyReference(Map<String,Object> data,Map<String,Object> ref) {
        if(!ref.equals(reference(string(ref.get("path")),object(data.get("files")))))throw new IllegalStateException("Inconsistent file reference");
    }
    private static void verifyFiles(Snapshot snapshot) throws Exception {
        var files=object(snapshot.data().get("files"));if(files.isEmpty())throw new IllegalStateException("Empty snapshot inventory");
        var actual=new TreeSet<String>();
        try(var walk=Files.walk(snapshot.root())) {
            for(Path file:walk.toList()) {
                if(Files.isSymbolicLink(file))throw new IllegalStateException("Symlink in snapshot");
                if(Files.isRegularFile(file)&&!file.equals(snapshot.manifest()))actual.add(snapshot.root().relativize(file).toString().replace('\\','/'));
            }
        }
        if(!actual.equals(files.keySet()))throw new IllegalStateException("Missing/extra snapshot files");
        for(var entry:files.entrySet()) {
            Path file=contained(snapshot.root(),entry.getKey());var expected=object(entry.getValue());
            if(!expected.keySet().equals(Set.of("sha256","bytes"))||!JvmBuildInputs.sha256(file).equals(expected.get("sha256"))
                    ||Files.size(file)!=((Number)expected.get("bytes")).longValue())throw new IllegalStateException("Changed snapshot file: "+entry.getKey());
        }
    }
    private static Path contained(Path root,String relative) throws Exception {
        if(relative.isBlank()||relative.contains("\\")||relative.contains(":")||Path.of(relative).isAbsolute()
                ||List.of(relative.split("/")).contains(".."))throw new IllegalStateException("Unsafe snapshot path");
        Path file=root.resolve(relative).normalize();
        if(!file.startsWith(root)||!file.toRealPath().startsWith(root.toRealPath())||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Escaped snapshot file");
        return file;
    }
    private static String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    private static String git(Path root,String... arguments) throws Exception {
        var command=new ArrayList<String>();command.add("git");command.addAll(List.of(arguments));
        var process=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String value=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0)throw new IllegalStateException("Cannot read producer identity");return value;
    }
    private static void removePrivateStaging(Path staging) throws Exception {
        try(var paths=Files.walk(staging)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) {
        if(!(value instanceof Map<?,?>))throw new IllegalStateException("Expected JSON object");return (Map<String,Object>)value;
    }
    private static List<?> list(Object value) { if(!(value instanceof List<?> values))throw new IllegalStateException("Expected JSON array");return values; }
    private static String string(Object value) { if(!(value instanceof String text))throw new IllegalStateException("Expected JSON string");return text; }
    private static Object freeze(Object value) {
        if(value instanceof Map<?,?> map) {var result=new TreeMap<String,Object>();map.forEach((key,item)->result.put((String)key,freeze(item)));return Collections.unmodifiableMap(result);}
        if(value instanceof List<?> list)return list.stream().map(ConsumerSnapshot::freeze).toList();return value;
    }
    private static Object integralNumbers(Object value) {
        if(value instanceof Number number) {
            double actual=number.doubleValue();long integer=number.longValue();
            if(!Double.isFinite(actual)||actual<0||actual!=integer)throw new IllegalStateException("Nonintegral inventory number");
            return integer;
        }
        if(value instanceof Map<?,?> map) {var result=new TreeMap<String,Object>();map.forEach((key,item)->result.put((String)key,integralNumbers(item)));return result;}
        if(value instanceof List<?> list)return list.stream().map(ConsumerSnapshot::integralNumbers).toList();return value;
    }
    private static String child(org.w3c.dom.Element parent,String name) {
        for(var node=parent.getFirstChild();node!=null;node=node.getNextSibling())
            if(node instanceof org.w3c.dom.Element element && element.getTagName().equals(name))return element.getTextContent().trim();
        return "";
    }
    private static void verifyPomParents(Snapshot snapshot) throws Exception {
        var files=object(snapshot.data().get("files"));
        for(String relative:files.keySet())if(relative.endsWith(".pom")) {
            var document=JvmBuildInputs.xml(contained(snapshot.root(),relative)).getDocumentElement();
            for(var node=document.getFirstChild();node!=null;node=node.getNextSibling())
                if(node instanceof org.w3c.dom.Element parent&&parent.getTagName().equals("parent")) {
                    String group=child(parent,"groupId"),artifact=child(parent,"artifactId"),version=child(parent,"version");
                    if(!group.matches("[A-Za-z0-9_.-]+")||!artifact.matches("[A-Za-z0-9_.-]+")||!version.matches("[A-Za-z0-9_.-]+"))
                        throw new IllegalStateException("Unresolved published parent identity");
                    String path="maven/"+group.replace('.','/')+"/"+artifact+"/"+version+"/"+artifact+"-"+version+".pom";
                    if(!files.containsKey(path))throw new IllegalStateException("Missing resolved parent POM: "+path);
                }
        }
    }
}
