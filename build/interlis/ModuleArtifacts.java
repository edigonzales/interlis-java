package build.interlis;

import java.nio.file.*;
import java.util.*;

/** Immutable files resolved from this invocation's native inventory, with coordinates for host distribution names. */
public final class ModuleArtifacts {
    public record Artifact(String coordinates,Path file) {
        public String fileName() {
            String[] fields=coordinates.split("/");
            if(fields.length!=4||!fields[0].equals("maven"))throw new IllegalArgumentException("Unexpected artifact coordinates: "+coordinates);
            return fields[2]+"-"+fields[3]+".jar";
        }
    }
    public static List<Artifact> runtime(Path root,String module,JenesisBridge.BuildOutcome outcome)throws Exception {
        var inventory = JenesisBridge.productInventory(outcome, module);
        var data = inventory.data();
        String prefix = inventory.prefix();
        var identities = new HashMap<Path, String>();
        Path own = inventory.jar();
        identities.put(own, data.getProperty(prefix + ".identity.0"));
        for (int i = 0; data.containsKey(prefix + ".dependency." + i); i++) {
            String[] parts = data.getProperty(prefix + ".dependency." + i).split(" ");
            identities.put(inventory.resolve(parts[1]), parts[0]);
        }
        var artifacts=new ArrayList<Artifact>();var names=new HashSet<String>();
        String version = JvmBuildInputs.projectVersion(root);
        for (Path path : inventory.paths("runtime")) {
            String coordinates=identities.get(path);if(coordinates==null)throw new IllegalStateException("Unidentified runtime artifact: "+path);
            var artifact=new Artifact(coordinates,path);
            if(!names.add(artifact.fileName()))throw new IllegalStateException("Duplicate runtime name");
            if(coordinates.matches(".*(junit|platform-console|antlr4/|commons-compress|archive-tool).*"))throw new IllegalStateException("Tool/runtime leak: "+coordinates);
            if(coordinates.startsWith("maven/guru.interlis/")) {
                String[] id=coordinates.split("/");
                if(id.length!=4||!id[3].equals(version))throw new IllegalStateException("Foreign own coordinates: "+coordinates);
                Path producer = module.equals(id[2]) ? own : JenesisBridge.productInventory(outcome, id[2]).jar();
                if (!path.equals(producer) && Files.mismatch(path, producer) != -1) {
                    throw new IllegalStateException("Foreign/stale own runtime bytes: " + coordinates);
                }
            }
            artifacts.add(artifact);
        }
        return List.copyOf(artifacts);
    }
    public static Path jar(JenesisBridge.BuildOutcome outcome,String projectId) throws Exception {
        return JenesisBridge.productInventory(outcome, projectId).jar();
    }
}
