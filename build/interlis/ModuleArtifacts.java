package build.interlis;

import java.nio.file.*;
import java.util.*;

/** Immutable files resolved from this invocation's native inventory, including coordinates and actual hashes. */
public final class ModuleArtifacts {
    public record Artifact(String coordinates,Path file,String sha256) {
        public String fileName() {
            String[] fields=coordinates.split("/");
            if(fields.length!=4||!fields[0].equals("maven"))throw new IllegalArgumentException("Unexpected artifact coordinates: "+coordinates);
            return fields[2]+"-"+fields[3]+".jar";
        }
    }
    public static List<Artifact> runtime(Path root,String module,JenesisBridge.BuildOutcome outcome)throws Exception {
        var result=JenesisBridge.moduleResult(outcome,module);
        Path directory=result.inventoryDirectory();
        Properties data=new Properties();try(var reader=Files.newBufferedReader(directory.resolve("inventory.properties"))){data.load(reader);}
        String prefix=result.inventoryPrefix();var identities=new HashMap<Path,String>();
        Path own=file(root,directory,data.getProperty(prefix+".artifacts.0"));identities.put(own,data.getProperty(prefix+".identity.0"));
        for(int i=0;data.containsKey(prefix+".dependency."+i);i++) {
            String[] parts=data.getProperty(prefix+".dependency."+i).split(" ");
            identities.put(file(root,directory,parts[1]),parts[0]);
        }
        var artifacts=new ArrayList<Artifact>();var names=new HashSet<String>();
        for(Path path:JenesisBridge.runtimeClasspath(module,outcome)) {
            String coordinates=identities.get(path);if(coordinates==null)throw new IllegalStateException("Unidentified runtime artifact: "+path);
            var artifact=new Artifact(coordinates,path,JvmBuildInputs.sha256(path));
            if(!names.add(artifact.fileName()))throw new IllegalStateException("Duplicate runtime name");
            if(coordinates.matches(".*(junit|platform-console|antlr4/|commons-compress|archive-tool).*"))throw new IllegalStateException("Tool/runtime leak: "+coordinates);
            if(coordinates.startsWith("maven/guru.interlis/")) {
                String[] id=coordinates.split("/");
                if(id.length!=4||!id[3].equals(JvmBuildInputs.projectVersion(root))||!JvmBuildInputs.MODULES.contains(id[2]))throw new IllegalStateException("Foreign own coordinates: "+coordinates);
                Path producer=JenesisBridge.moduleResult(outcome,id[2]).jar();
                if(!artifact.sha256().equals(JvmBuildInputs.sha256(producer)))throw new IllegalStateException("Foreign/stale own runtime bytes: "+coordinates);
            }
            artifacts.add(artifact);
        }
        return List.copyOf(artifacts);
    }
    public static Path jar(JenesisBridge.BuildOutcome outcome,String projectId) throws Exception {
        return JenesisBridge.moduleResult(outcome,projectId).jar();
    }
    public static Path pom(JenesisBridge.BuildOutcome outcome,String projectId) throws Exception {
        return JenesisBridge.moduleResult(outcome,projectId).pom();
    }
    public static Path sources(JenesisBridge.BuildOutcome outcome,String projectId) throws Exception {
        return JenesisBridge.moduleResult(outcome,projectId).sources().orElseThrow(()->new IllegalStateException("Sources not selected: "+projectId));
    }
    private static Path file(Path root,Path directory,String value)throws Exception {
        if(value==null)throw new IllegalStateException("Missing artifact");
        Path file=directory.resolve(value).normalize().toAbsolutePath();Path boundary=root.resolve("target/jenesis-jvm/engine");
        if(!file.startsWith(boundary)||!Files.isRegularFile(file)||!file.toRealPath().startsWith(boundary.toRealPath()))throw new IllegalStateException("Foreign/missing artifact: "+file);
        return file;
    }
}
