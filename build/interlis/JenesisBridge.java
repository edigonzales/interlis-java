package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import build.jenesis.*;
import build.jenesis.project.InferredMultiProjectAssembler;
import java.nio.file.*;
import java.util.*;

/** The only version-sensitive engine binding; never reads Gradle or local Maven products. */
public final class JenesisBridge {
    public record BuildOutcome(Path inventory, Map<String, Path> engineResults) {
        public BuildOutcome { inventory=inventory.toAbsolutePath().normalize(); engineResults=Map.copyOf(engineResults); }
        public Path root() { return inventory.getParent().getParent().getParent().getParent(); }
    }

    /** Actual product files, with optional sources when this invocation did not select that phase. */
    public record ModuleResult(String projectId, String engineLocator, Path inventoryDirectory,
            String inventoryPrefix, Path jar, Path pom, Optional<Path> sources,
            Optional<String> moduleName, boolean explicitDescriptor, List<Path> runtime) {
        public ModuleResult { sources=Objects.requireNonNull(sources);moduleName=Objects.requireNonNull(moduleName);
            runtime=List.copyOf(runtime); }
    }

    /** Resolves identities from inventory contents; engine path spelling is not a consumer contract. */
    public static ModuleResult moduleResult(BuildOutcome outcome,String projectId) throws Exception {
        InventoryResult product=inventoryResult(outcome,projectId,false);
        Path jar=product.file("artifacts.0");
        if(product.data().containsKey(product.prefix()+".artifacts.1"))throw new IllegalStateException("Ambiguous product JAR: "+projectId);
        Path pom=product.file("pom");
        var sources=new ArrayList<Path>();
        for(var entry:outcome.engineResults().entrySet()) {
            Path inventory=checkedOutput(outcome,entry.getValue()).resolve("inventory.properties");
            if(!Files.isRegularFile(inventory))continue;
            Properties data=readProperties(inventory);
            String value=data.getProperty(product.prefix()+".sources.0");
            if(value!=null)sources.add(requireArtifact(outcome.root().resolve("target/jenesis-jvm/engine"),inventory.getParent(),value));
        }
        if(sources.size()>1)throw new IllegalStateException("Ambiguous sources result: "+projectId);
        Optional<String> moduleName=Optional.empty();boolean explicit=false;
        try(var archive=new java.util.jar.JarFile(jar.toFile(),true,java.util.zip.ZipFile.OPEN_READ,Runtime.version())) {
            if(archive.getJarEntry("module-info.class")!=null) {
                var descriptor=java.lang.module.ModuleFinder.of(jar).findAll();
                if(descriptor.size()!=1)throw new IllegalStateException("Missing/ambiguous module identity: "+jar);
                var module=descriptor.iterator().next().descriptor();
                moduleName=Optional.of(module.name());explicit=!module.isAutomatic();
            }
        }
        return new ModuleResult(projectId,product.locator(),product.directory(),product.prefix(),jar,pom,
                sources.stream().findFirst(),moduleName,explicit,product.paths("runtime"));
    }

    public static List<Path> runtimeModulePath(String projectId,BuildOutcome outcome) throws Exception {
        ModuleResult result=moduleResult(outcome,projectId);
        if(!result.explicitDescriptor())throw new IllegalStateException("Product is not an explicit module: "+projectId);
        return result.runtime();
    }

    public static Path stagedMavenRepository(BuildOutcome outcome) throws Exception {
        Path path=outcome.engineResults().get("stage/maven");
        if(path==null)throw new IllegalStateException("Maven staging was not selected");
        return checkedOutput(outcome,path);
    }

    private record InventoryResult(BuildOutcome outcome,String locator,Path directory,String prefix,Properties data) {
        Path file(String role) throws Exception {
            String value=data.getProperty(prefix+"."+role);
            if(value==null)throw new IllegalStateException("Missing inventory role: "+prefix+"."+role);
            return requireArtifact(outcome.root().resolve("target/jenesis-jvm/engine"),directory,value);
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
        if(!JvmBuildInputs.MODULES.contains(projectId))throw new IllegalArgumentException("Unknown project: "+projectId);
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
                matches.add(new InventoryResult(outcome,locator.substring(0,locator.length()-10),directory,prefix,data));
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
    public static List<Path> buildArchiveTool(Path root,boolean refreshPins) throws Exception {
        Path tool=root.resolve("build/interlis/archive-tool");
        var keys=new LinkedHashMap<>(Make.settings(tool).keys());keys.put("maven.local","");keys.put("module.local","");
        keys.put("dependency.pin",refreshPins?"versions":"strict");
        Path target=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/tools/archive");Files.createDirectories(target.getParent());
        var results=Project.ofEnvironment(new Environment(keys).root(tool),tool).target(target).artifacts(root.resolve(".jenesis/archive-artifacts")).tests(false).sources(false).version("1")
                .build(refreshPins?"pin":"build");
        Path inventory=results.get("build/maven/build-module-/inventory");
        if(inventory==null)throw new IllegalStateException("Missing archive-tool inventory: "+results.keySet());
        var data=new Properties();try(var reader=Files.newBufferedReader(inventory.resolve("inventory.properties"))){data.load(reader);}
        var classpath=new ArrayList<Path>();
        for(int i=0;data.containsKey("module.runtime."+i);i++) {
            Path file=inventory.resolve(data.getProperty("module.runtime."+i)).normalize();
            if(!Files.isRegularFile(file)||!file.startsWith(target))throw new IllegalStateException("Foreign/missing archive tool");classpath.add(file);
        }
        if(classpath.isEmpty())throw new IllegalStateException("Empty archive-tool closure");return List.copyOf(classpath);
    }


    public static BuildOutcome run(BuildRequest request) throws Exception {
        Path root = request.root();
        var settings = new LinkedHashMap<>(Make.settings(root).keys());
        settings.put("maven.local", "");
        settings.put("module.local", "");
        if (!request.profile().equals("build")) settings.put("test.skip", "true");
        if (request.tests() != null) settings.put("test.filter", request.module() + "/" + request.tests());
        var project = Project.ofEnvironment(new Environment(settings).root(root), root);
        if(request.profile().equals("artifacts"))project=project.tests(false);
        String[] selectors = request.profile().equals("artifacts") && request.module()==null ? new String[]{"stage"} : request.module() == null ? new String[]{"build"}
                : new String[]{"+" + request.module(), "build/maven/compose/module/source-module-" + request.module()};
        Files.createDirectories(root.resolve("target/jenesis-jvm"));
        var results = new LinkedHashMap<>(project.build(selectors));
        results.keySet().removeIf(key -> !key.endsWith("/inventory") && !key.equals("stage/maven"));
        if (results.isEmpty()) throw new IllegalStateException("Empty engine result");
        var index = new Properties();
        index.setProperty("scope", request.module() == null ? "all" : request.module());
        index.setProperty("tests", Objects.toString(request.tests(), "normal"));
        int reports = 0;
        for (var entry : results.entrySet()) {
            Path output = entry.getValue().toAbsolutePath().normalize();
            if (!output.startsWith(root.resolve("target/jenesis-jvm/engine")))
                throw new IllegalStateException("Foreign engine output: " + output);
            index.setProperty(entry.getKey(), root.relativize(output).toString());
            if (!entry.getKey().endsWith("/inventory")) continue;
            Path file = output.resolve("inventory.properties");
            if (!Files.exists(file)) continue;
            var inventory = new Properties();
            try (var in = Files.newBufferedReader(file)) { inventory.load(in); }
            for (String key : inventory.stringPropertyNames()) {
                if (key.matches(".*\\.(artifacts|runtime|sources)\\.[0-9]+")) {
                    requireArtifact(root.resolve("target/jenesis-jvm/engine"), output, inventory.getProperty(key));
                }
                if (!key.endsWith(".report.tests")) continue;
                Path directory = output.resolve(inventory.getProperty(key)).normalize();
                requireSuccessfulReports(directory);
                reports++;
            }
        }
        if (reports == 0 && request.profile().equals("build")) throw new IllegalStateException("No required test reports");
        Path destination = root.resolve("target/jenesis-jvm/reports/engine-results.properties");
        Files.createDirectories(destination.getParent());
        try (var out = Files.newBufferedWriter(destination)) { index.store(out, "Actual engine result paths"); }
        return new BuildOutcome(destination, Map.copyOf(results));
    }

    public static List<Path> runtimeClasspath(String module, BuildOutcome outcome) throws Exception {
        return runtimeClasspath(module,outcome,false);
    }

    public static List<Path> testClasspath(String module, BuildOutcome outcome) throws Exception {
        return runtimeClasspath(module,outcome,true);
    }

    private static List<Path> runtimeClasspath(String module,BuildOutcome outcome,boolean test) throws Exception {
        return inventoryResult(outcome,module,test).paths("runtime");
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

    static void requireSuccessfulReports(Path directory) throws Exception {
        int count = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("TEST-")
                    && p.toString().endsWith(".xml")).toList()) {
                var xml = JvmBuildInputs.xml(file);
                var suite = xml.getDocumentElement();
                for (String attr : List.of("failures", "errors", "skipped"))
                    if (!suite.getAttribute(attr).equals("0")) throw new IllegalStateException("Unsuccessful report: " + file);
                int actual = xml.getElementsByTagName("testcase").getLength();
                if (!suite.getAttribute("tests").equals(Integer.toString(actual)))
                    throw new IllegalStateException("Inconsistent report: " + file);
                count += actual;
            }
        }
        if (count == 0) throw new IllegalStateException("Empty required test selection: " + directory);
    }
}
