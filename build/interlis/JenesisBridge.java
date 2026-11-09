package build.interlis;

import build.jenesis.*;
import java.nio.file.*;
import java.util.*;

/** The only version-sensitive engine binding; never reads Gradle or local Maven products. */
public final class JenesisBridge {
    public record BuildOutcome(Path root, Map<String, Path> engineResults) {
        public BuildOutcome {
            root = root.toAbsolutePath().normalize();
            engineResults = Map.copyOf(engineResults);
        }
    }

    /** Product artifact and descriptor from the native inventory. */
    public record ModuleResult(Path jar,
            Optional<String> moduleName, boolean explicitDescriptor) {
        public ModuleResult {
            moduleName = Objects.requireNonNull(moduleName);
        }
    }

    /** Resolves identities from inventory contents; engine path spelling is not a consumer contract. */
    public static ModuleResult moduleResult(BuildOutcome outcome,String projectId) throws Exception {
        var product = productInventory(outcome, projectId);
        Path jar = product.jar();
        product.file("pom");
        Optional<String> moduleName=Optional.empty();boolean explicit=false;
        try(var archive=new java.util.jar.JarFile(jar.toFile(),true,java.util.zip.ZipFile.OPEN_READ,Runtime.version())) {
            if(archive.getJarEntry("module-info.class")!=null) {
                var descriptor=java.lang.module.ModuleFinder.of(jar).findAll();
                if(descriptor.size()!=1)throw new IllegalStateException("Missing/ambiguous module identity: "+jar);
                var module=descriptor.iterator().next().descriptor();
                moduleName=Optional.of(module.name());explicit=!module.isAutomatic();
            }
        }
        return new ModuleResult(jar, moduleName, explicit);
    }

    static InventoryResult productInventory(BuildOutcome outcome, String projectId) throws Exception {
        return inventoryResult(outcome, projectId, false);
    }

    record InventoryResult(BuildOutcome outcome,Path directory,String prefix,Properties data) {
        Path jar() throws Exception {
            if (data.containsKey(prefix + ".artifacts.1")) {
                throw new IllegalStateException("Ambiguous product JAR: " + prefix);
            }
            return file("artifacts.0");
        }

        Path file(String role) throws Exception {
            String value=data.getProperty(prefix+"."+role);
            if(value==null)throw new IllegalStateException("Missing inventory role: "+prefix+"."+role);
            return resolve(value);
        }

        Path resolve(String relative) throws Exception {
            return requireArtifact(outcome.root().resolve("target/jenesis-jvm/engine"), directory, relative);
        }

        List<Path> paths(String role) throws Exception {
            var paths=new ArrayList<Path>();
            for(int i=0;data.containsKey(prefix+"."+role+"."+i);i++)paths.add(file(role+"."+i));
            long actual=data.stringPropertyNames().stream().filter(k->k.matches(java.util.regex.Pattern.quote(prefix+"."+role+".")+"[0-9]+")).count();
            if(paths.isEmpty()||actual!=paths.size()||new HashSet<>(paths).size()!=paths.size())throw new IllegalStateException("Empty/gapped/duplicate "+role+": "+prefix);
            return List.copyOf(paths);
        }
    }

    private static InventoryResult inventoryResult(BuildOutcome outcome,String projectId,boolean test) throws Exception {
        var candidates=findInventories(outcome,projectId,test);
        if(candidates.size()!=1)throw new IllegalStateException("Expected one "+(test?"test":"product")+" inventory for "+projectId+", found "+candidates.size());
        return candidates.getFirst();
    }

    private static List<InventoryResult> findInventories(BuildOutcome outcome,String projectId,boolean test) throws Exception {
        var matches=new ArrayList<InventoryResult>();
        for(var entry:outcome.engineResults().entrySet()) {
            Path directory=checkedOutput(outcome,entry.getValue()),inventory=directory.resolve("inventory.properties");
            if(!Files.isRegularFile(inventory))continue;
            Properties data=readProperties(inventory);
            for(String key:data.stringPropertyNames())if(key.endsWith(".path")&&projectId.equals(data.getProperty(key))) {
                String prefix=key.substring(0,key.length()-5);
                if(!projectId.equals(data.getProperty(prefix+".artifact")))continue;
                if(test!=data.containsKey(prefix+".test"))continue;
                String locator=entry.getKey();
                if(!locator.endsWith("/inventory"))throw new IllegalStateException("Unidentified inventory locator: "+locator);
                matches.add(new InventoryResult(outcome,directory,prefix,data));
            }
        }
        return List.copyOf(matches);
    }

    private static Properties readProperties(Path file) throws Exception {
        var data=new Properties();try(var reader=Files.newBufferedReader(file)){data.load(reader);}return data;
    }

    private static Path checkedOutput(BuildOutcome outcome,Path path) throws Exception {
        Path engine=outcome.root().resolve("target/jenesis-jvm/engine"),normalized=path.toAbsolutePath().normalize();
        JvmBuildInputs.requireOutputRoot(outcome.root(),outcome.root().relativize(normalized).toString());
        if(!normalized.startsWith(engine)||!Files.isDirectory(normalized)||!normalized.toRealPath().startsWith(engine.toRealPath()))
            throw new IllegalStateException("Foreign/missing engine output: "+path);
        return normalized;
    }

    /** Isolated build-only library/ZIP helper; never part of the product projects or their staging. */
    public static List<Path> buildArchiveTool(Path root) throws Exception {
        Path tool=root.resolve("build/interlis/archive-tool");
        var keys=new LinkedHashMap<>(Make.settings(tool).keys());keys.put("maven.local","");keys.put("module.local","");
        keys.put("dependency.pin","strict");
        Path target=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/tools/archive");Files.createDirectories(target.getParent());
        var results=Project.ofEnvironment(new Environment(keys).root(tool),tool).target(target).artifacts(root.resolve(".jenesis/archive-artifacts")).tests(false).sources(false).version("1")
                .build("build");
        Path inventory=results.get("build/maven/build-module-/inventory");
        if(inventory==null)throw new IllegalStateException("Missing archive-tool inventory: "+results.keySet());
        var data = readProperties(inventory.resolve("inventory.properties"));
        var classpath=new ArrayList<Path>();
        for(int i=0;data.containsKey("module.runtime."+i);i++) {
            Path file=inventory.resolve(data.getProperty("module.runtime."+i)).normalize();
            if(!Files.isRegularFile(file)||!file.startsWith(target))throw new IllegalStateException("Foreign/missing archive tool");classpath.add(file);
        }
        if(classpath.isEmpty())throw new IllegalStateException("Empty archive-tool closure");return List.copyOf(classpath);
    }


    public static BuildOutcome artifacts(Path root, String... selectors) throws Exception {
        var settings = new LinkedHashMap<>(Make.settings(root).keys());
        settings.put("maven.local", "");
        settings.put("module.local", "");
        settings.put("test.skip", "true");
        var project = Project.ofEnvironment(new Environment(settings).root(root), root);
        var results = new LinkedHashMap<>(project.build(selectors.length == 0 ? new String[]{"stage"} : selectors));
        results.keySet().removeIf(key -> !key.endsWith("/inventory"));
        if (results.isEmpty()) throw new IllegalStateException("No Jenesis product inventory");
        return new BuildOutcome(root, results);
    }

    public static List<Path> testClasspath(String module, BuildOutcome outcome) throws Exception {
        return inventoryResult(outcome, module, true).paths("runtime");
    }

    static Path requireArtifact(Path engine, Path output, String relative) throws Exception {
        Path artifact = output.resolve(relative).normalize();
        Path root=engine.getParent().getParent().getParent();
        JvmBuildInputs.requireOutputRoot(root,root.relativize(artifact).toString());
        if (!Files.isRegularFile(artifact) || !artifact.startsWith(engine)
                || !artifact.toRealPath().startsWith(engine.toRealPath()))
            throw new IllegalStateException("Missing or foreign artifact: " + artifact);
        return artifact;
    }

}
