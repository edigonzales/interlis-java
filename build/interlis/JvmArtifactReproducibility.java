package build.interlis;

import java.nio.file.*;
import java.util.*;

/** Hard byte gate for two independent generations; timing, absolute layouts and process receipts are not deliveries. */
public final class JvmArtifactReproducibility {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Expected two fresh generation roots and a receipt path");
        Path first=Path.of(args[0]).toRealPath(),second=Path.of(args[1]).toRealPath();
        if(first.equals(second))throw new IllegalArgumentException("Independent output roots required");
        Map<String,String> before=inventory(first),after=inventory(second);
        Path receipt=Path.of(args[2]);Files.createDirectories(receipt.toAbsolutePath().getParent());
        if(!before.equals(after)) {
            var differences=new TreeMap<String,Object>();var keys=new TreeSet<>(before.keySet());keys.addAll(after.keySet());
            for(String key:keys)if(!Objects.equals(before.get(key),after.get(key)))differences.put(key,Map.of("before",Objects.toString(before.get(key),"MISSING"),"after",Objects.toString(after.get(key),"MISSING")));
            Files.writeString(receipt,JsonOutput.write(Map.of("status","failed","differences",differences)));throw new IllegalStateException("Nonreproducible JVM artifacts: "+differences.keySet());
        }
        Files.writeString(receipt,JsonOutput.write(Map.of("status","passed","first",first.toString(),"second",second.toString(),"artifacts",before)));
        System.out.println("JVM_REPRODUCIBILITY_PASS "+before.size()+" artifacts");
    }
    static Map<String,String> inventory(Path root)throws Exception {
        Map<String,String> files=new TreeMap<>();
        var outcome=JenesisBridge.readExistingOutcome(root);
        for(String module:JvmBuildInputs.MODULES)for(String kind:List.of("build","source")) {
            Path file=kind.equals("build")?ModuleArtifacts.jar(outcome,module):ModuleArtifacts.sources(outcome,module);
            requireFile(root,file);files.put("modules/"+module+"/"+kind+".jar",JvmBuildInputs.sha256(file));
        }
        for(Path directory:List.of(JenesisBridge.stagedMavenRepository(outcome),root.resolve("target/jenesis-jvm/delivery/compiler"),root.resolve("target/jenesis-jvm/delivery/lsp"))) {
            String tree=root.relativize(directory).toString().replace('\\','/');
            if(!Files.isDirectory(directory))throw new IllegalStateException("Missing delivery tree: "+tree);
            try(var walk=Files.walk(directory)){for(Path file:walk.filter(p->!Files.isDirectory(p)).toList()){requireFile(root,file);files.put(tree+"/"+directory.relativize(file).toString().replace('\\','/'),JvmBuildInputs.sha256(file));}}
        }
        for(String name:List.of("sdk.zip","runtime-inventory.json")){Path file=root.resolve("target/jenesis-jvm/delivery/"+name);requireFile(root,file);files.put(name,JvmBuildInputs.sha256(file));}return Map.copyOf(files);
    }
    private static void requireFile(Path root,Path file)throws Exception {
        Path boundary=root.resolve("target/jenesis-jvm");if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!file.toRealPath().startsWith(boundary.toRealPath()))throw new IllegalStateException("Foreign/missing delivery file: "+file);
    }
}
