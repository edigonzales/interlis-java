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
            Optional<String> moduleName, boolean explicitDescriptor, List<Path> runtime,
            Optional<Path> testReports) {
        public ModuleResult { sources=Objects.requireNonNull(sources);moduleName=Objects.requireNonNull(moduleName);
            runtime=List.copyOf(runtime);testReports=Objects.requireNonNull(testReports); }
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
        Optional<Path> reports=Optional.empty();
        var test=findInventories(outcome,projectId,true);
        if(test.size()>1)throw new IllegalStateException("Ambiguous tests: "+projectId);
        if(!test.isEmpty()&&test.getFirst().data().containsKey(test.getFirst().prefix()+".report.tests"))
            reports=Optional.of(test.getFirst().directory("report.tests"));
        return new ModuleResult(projectId,product.locator(),product.directory(),product.prefix(),jar,pom,
                sources.stream().findFirst(),moduleName,explicit,product.paths("runtime"),reports);
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

    public static Map<String,String> inputFingerprints(BuildOutcome outcome) throws Exception {
        var data=readProperties(outcome.inventory());var inputs=new TreeMap<String,String>();
        for(String module:JvmBuildInputs.MODULES) {
            String value=data.getProperty("input."+module);
            if(value==null||!value.matches("[0-9a-f]{64}"))throw new IllegalStateException("Missing actual input fingerprint: "+module);
            inputs.put(module,value);
        }
        return Map.copyOf(inputs);
    }

    private record InventoryResult(BuildOutcome outcome,String locator,Path directory,String prefix,Properties data) {
        Path file(String role) throws Exception {
            String value=data.getProperty(prefix+"."+role);
            if(value==null)throw new IllegalStateException("Missing inventory role: "+prefix+"."+role);
            return requireArtifact(outcome.root().resolve("target/jenesis-jvm/engine"),directory,value);
        }
        Path directory(String role) throws Exception {
            String value=data.getProperty(prefix+"."+role);
            if(value==null)throw new IllegalStateException("Missing directory role: "+role);
            Path result=checkedOutput(outcome,directory.resolve(value));
            if(!Files.isDirectory(result))throw new IllegalStateException("Not a report directory: "+result);
            return result;
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

    /** Additional source files read by the current source-binding validators. */
    @SuppressWarnings("unchecked")
    public static Set<String> currentBindingInputs(Path root) throws Exception {
        var original=(Map<String,Object>)Json.parse(Files.readString(root.resolve("testdata/g2/completion-links.json")));
        var overlay=(Map<String,Object>)Json.parse(Files.readString(root.resolve("testdata/verification/current-bindings.json")));
        var bindings=new LinkedHashMap<>((Map<String,Object>)original.get("bindings"));
        bindings.putAll((Map<String,Object>)overlay.get("overrides"));bindings.putAll((Map<String,Object>)overlay.get("extras"));
        Set<String> inputs=new TreeSet<>();
        for(Object value:bindings.values())inputs.add((String)((Map<String,Object>)value).get("source"));
        var overrides=(Map<String,Object>)overlay.get("processOverrides");
        for(Object requirement:(List<Object>)original.get("requirements")) {
            Object evidence=((Map<String,Object>)requirement).get("processEvidence");
            if(evidence==null)continue;
            for(Object row:(List<Object>)evidence) {
                var process=(Map<String,Object>)row;
                if(overrides.containsKey(process.get("source")))process=(Map<String,Object>)overrides.get(process.get("source"));
                inputs.add((String)process.get("source"));
            }
        }
        return Set.copyOf(inputs);
    }

    public static BuildOutcome run(BuildRequest request) throws Exception {
        return run(request, Map.of());
    }

    public static BuildOutcome run(BuildRequest request, Map<String,String> overrides) throws Exception {
        Path root = request.root();
        var settings = new LinkedHashMap<>(Make.settings(root).keys());
        settings.put("maven.local", "");
        settings.put("module.local", "");
        settings.put("variable.repoRoot", root.toString());
        settings.put("variable.testdataRoot", root.resolve("testdata").toString());
        if (request.tests() != null) settings.put("test.filter", JpmsTestRun.selections(request.tests()).stream()
                .map(test -> request.module() + "/" + test).collect(java.util.stream.Collectors.joining(",")));
        settings.putAll(overrides);
        var env = new Environment(settings).root(root);
        Map<String, String> inputs = new HashMap<>();
        for (String module : JvmBuildInputs.MODULES) inputs.put(module, JvmBuildInputs.fingerprint(root, module));
        Set<String> discovered = java.util.concurrent.ConcurrentHashMap.newKeySet();
        var project = Project.ofEnvironment(env, root).assembler((descriptor, repositories, resolvers) -> {
            String module = descriptor.location().getFileName().toString();
            if (!inputs.containsKey(module)) throw new IllegalStateException("Foreign project: " + descriptor.location());
            discovered.add(module);
            var local = new LinkedHashMap<>(settings);
            local.put("variable.inputFingerprint", inputs.get(module));
            // There are no test grammars. The module-level generator config must
            // not regenerate the main parser into the packaged white-box tests.
            if (descriptor.name().startsWith("testmodule-")) local.put("generate.antlr", "false");
            // Keep existing relative file accesses and Gradle's module working directory.
            local.put("process.directory", root.resolve(module).toString());
            var assembler = InferredMultiProjectAssembler.ofEnvironment(new Environment(local).root(root));
            if (module.equals("interlis-compiler") && !descriptor.name().startsWith("test")) {
                var resources = new LinkedHashMap<Path, Path>();
                resources.put(Path.of("META-INF/licenses/UNICODE-LICENSE.txt"),
                        root.resolve("docs/evidence/stabilization/registries/UNICODE-LICENSE.txt"));
                assembler = assembler.resources(resources);
            }
            if(descriptor.test()&&Files.isRegularFile(root.resolve(module+"/src/main/java/module-info.java"))) {
                boolean selected=!request.profile().equals("artifacts")&&(request.tests()==null||module.equals(request.module()));
                Map<String,String> testSettings=Map.copyOf(local);
                assembler=assembler.observe(observation->observation.test(nativeTests->{
                    try{return JpmsTestRun.observe(nativeTests,root,module,testSettings,selected,"true".equals(testSettings.get("test.force")));}
                    catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                }));
            }
            var assembly=assembler.apply(descriptor, repositories, resolvers);
            if(descriptor.name().equals("module-"+module)) {
                var sourceEnvironment=new Environment(local).root(root);
                // Native Jar step, with source-only grammar bindings and an
                // internal source archive that retains stage=false.
                assembly=assembly.nest("source","",stock->(executor,inherited)->{
                    var wanted=new TreeSet<String>();
                    // Bind only Java compilation sources, matching Gradle's Java source set.
                    // Finder metadata such as .DS_Store is not a maintained source.
                    for(String name:descriptor.resources())wanted.add(localIdentity(name));
                    for(String name:descriptor.manifests())wanted.add(localIdentity(name));
                    wanted.addAll(List.of("prepare","binary","include"));
                    var required=new ArrayList<String>();
                    for(String key:inherited.sequencedKeySet()) {
                        String name=localIdentity(key);if(name.startsWith("build/"))name=name.substring(6);
                        String identity=name;
                        if(wanted.stream().anyMatch(w->identity.equals(w)||identity.startsWith(w+"/")))required.add(key);
                    }
                    executor.addSource("maintained-java",build.jenesis.step.Bind.asSources().extensions(Set.of(".java")),root.resolve(module+"/src/main/java"));required.add("maintained-java");
                    if(module.equals("interlis-compiler")) {
                        executor.addSource("grammars",build.jenesis.step.Bind.asResources(),root.resolve(module+"/src/main/antlr"));required.add("grammars");
                    }
                    if(required.isEmpty())throw new IllegalStateException("Missing native source bindings: "+inherited.keySet()+" expected "+wanted);
                    executor.addStep("archive",build.jenesis.step.Jar.ofEnvironment(sourceEnvironment,
                            build.jenesis.step.ProcessHandler.Factory.ofEnvironment(sourceEnvironment),build.jenesis.step.Jar.Sort.SOURCES),required.stream());
                    var inventoryInputs=new ArrayList<>(required);inventoryInputs.add("archive");
                    executor.addStep("inventory",new build.jenesis.step.Inventory().supplement(true),inventoryInputs.stream());
                });
            }
            return assembly;
        });
        if(request.profile().equals("artifacts"))project=project.tests(false);
        String[] selectors = request.profile().equals("artifacts") && request.module()==null ? new String[]{"stage"} : request.module() == null ? new String[]{"build"}
                : new String[]{"+" + request.module(), "build/maven/compose/module/source-module-" + request.module()};
        Files.createDirectories(root.resolve("target/jenesis-jvm"));
        var invocationStarted=java.time.Instant.now();
        var results = project.build(selectors);
        if (results.isEmpty()) throw new IllegalStateException("Empty engine result");
        if (!discovered.equals(Set.copyOf(JvmBuildInputs.MODULES)))
            throw new IllegalStateException("Discovery differs from the registered module scope: " + discovered);
        var index = new Properties();
        index.setProperty("scope", request.module() == null ? "all" : request.module());
        index.setProperty("tests", Objects.toString(request.tests(), "normal"));
        int reports = 0;Set<String> tested=new TreeSet<>();
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
                for(String property:inventory.stringPropertyNames())
                    if(property.endsWith(".test"))tested.add(inventory.getProperty(property));
                reports++;
            }
        }
        if (reports == 0 && !request.profile().equals("artifacts")) throw new IllegalStateException("No required test reports");
        for (var entry : inputs.entrySet()) {
            if (!entry.getValue().equals(JvmBuildInputs.fingerprint(root, entry.getKey())))
                throw new IllegalStateException("Inputs changed during build: " + entry.getKey());
            index.setProperty("input." + entry.getKey(), entry.getValue());
        }
        Path destination = root.resolve("target/jenesis-jvm/reports/engine-results.properties");
        Files.createDirectories(destination.getParent());
        try (var out = Files.newBufferedWriter(destination)) { index.store(out, "Actual engine result paths"); }
        var outcome=new BuildOutcome(destination, Map.copyOf(results));
        if(request.profile().equals("build")) {
            Path evidence=root.resolve("target/jenesis-jvm/evidence/normal").resolve(engineRunId(root));Files.createDirectories(evidence);
            index.setProperty("evidence.normal",root.relativize(evidence).toString());
            try(var writer=Files.newBufferedWriter(destination)){index.store(writer,"Actual engine and evidence results");}
            for(String module:tested) {
                var facts=snapshotTests(root,module,outcome,evidence.resolve(module),false);
                facts.setProperty("builder","jenesis");facts.setProperty("module",module);
                facts.setProperty("invocationStarted",invocationStarted.toString());facts.setProperty("invocationEnded",java.time.Instant.now().toString());
                facts.setProperty("selection",Objects.toString(request.tests(),"normal"));
                facts.setProperty("jdk",Runtime.version().toString());facts.setProperty("jenesis","0.18.1");
                facts.setProperty("gitCommit",git(root,"rev-parse","HEAD"));facts.setProperty("treeSha",git(root,"rev-parse","HEAD^{tree}"));
                facts.setProperty("dirty",Boolean.toString(!git(root,"status","--porcelain").isBlank()));
                try(var writer=Files.newBufferedWriter(evidence.resolve(module).resolve("receipt.properties"))){facts.store(writer,"Actual test execution/reuse; run identity comes from the engine");}
            }
        }
        return outcome;
    }

    /** Reads only the explicit last result index, with no discovery/fallback to unrelated products. */
    public static BuildOutcome readExistingOutcome(Path root)throws Exception {
        Path index=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/reports/engine-results.properties");var properties=new Properties();
        try(var reader=Files.newBufferedReader(index)){properties.load(reader);}
        for(String module:JvmBuildInputs.MODULES)
            if(!JvmBuildInputs.fingerprint(root,module).equals(properties.getProperty("input."+module)))
                throw new IllegalStateException("Stale recorded product inputs: "+module);
        var paths=new TreeMap<String,Path>();
        for(String key:properties.stringPropertyNames())if(key.startsWith("build/")||key.startsWith("stage/")) {
            Path path=root.resolve(properties.getProperty(key)).toAbsolutePath().normalize();
            if(!path.startsWith(root.resolve("target/jenesis-jvm/engine"))||!Files.exists(path)||!path.toRealPath().startsWith(root.resolve("target/jenesis-jvm/engine").toRealPath()))throw new IllegalStateException("Foreign/missing recorded output: "+path);
            paths.put(key,path);
        }
        if(paths.isEmpty())throw new IllegalStateException("No actual engine outputs");return new BuildOutcome(index,Map.copyOf(paths));
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

    @SuppressWarnings("unchecked")
    static Properties snapshotFreshTests(Path root,String module,BuildOutcome outcome,Path destination) throws Exception {
        return snapshotTests(root,module,outcome,destination,true);
    }

    @SuppressWarnings("unchecked")
    private static Properties snapshotTests(Path root,String module,BuildOutcome outcome,Path destination,boolean requireFresh) throws Exception {
        var test=inventoryResult(outcome,module,true);
        Path output=test.directory("report.tests").getParent().getParent();
        output=output.toAbsolutePath().normalize();
        Path events=root.resolve("target/jenesis-jvm/engine/.jenesis.events.jsonl");
        String run=null,origin=null;boolean executed=false,found=false,completed=false;
        for(String line:Files.readAllLines(events)) {
            var event=(Map<String,Object>)Json.parse(line);
            if(event.get("status").equals("started"))run=(String)event.get("run");
            if(event.get("status").equals("completed"))completed=true;
            if(Objects.equals(event.get("folder"),output.getParent().toString())) {
                found=true;executed=event.get("status").equals("executed");origin=(String)event.get("run");
            }
        }
        if(!found||(!executed&&requireFresh)||!completed||run==null)throw new IllegalStateException("Missing/freshness-invalid test execution: "+module);
        Files.createDirectory(destination);
        Files.copy(events,destination.resolve("engine-events.jsonl"));
        Path reports=output.resolve("reports/tests"),raw=Files.createDirectory(destination.resolve("native"));
        var facts=new Properties();facts.setProperty("engineRunId",run);facts.setProperty("originalTestRunId",executed?run:Objects.requireNonNull(origin));
        facts.setProperty("execution",executed?"EXECUTED":"REUSED");facts.setProperty("exitCode","0");
        try(var files=Files.list(reports)) {
            for(Path file:files.filter(p->p.toString().endsWith(".xml")).toList()) {
                String hash=JvmBuildInputs.sha256(file);Files.copy(file,raw.resolve(file.getFileName()));
                if(!JvmBuildInputs.sha256(raw.resolve(file.getFileName())).equals(hash)||!JvmBuildInputs.sha256(file).equals(hash))throw new IllegalStateException("Report changed while snapshotting");
                facts.setProperty("report."+file.getFileName(),hash);
            }
        }
        for(String name:List.of("command","java.args","output","error","compile.args","compile.argv.json","compile.stdout","compile.stderr","compile.process.json","test.args","test.argv.json","test.stdout","test.stderr","test.process.json")) {
            Path file=output.getParent().resolve("supplement").resolve(name);
            if(Files.isRegularFile(file)) {
                Path copy=destination.resolve("process-"+name);Files.copy(file,copy);
                facts.setProperty("evidence.process-"+name,JvmBuildInputs.sha256(copy));
            }
        }
        Path plan=output.resolve("test-plan.json");
        if(Files.isRegularFile(plan)) {
            Files.copy(plan,destination.resolve("test-plan.json"));facts.setProperty("evidence.test-plan.json",JvmBuildInputs.sha256(plan));
            for(String kind:List.of("classes","modules","test-tools"))try(var walk=Files.walk(output.resolve(kind))) {
                for(Path file:walk.filter(Files::isRegularFile).toList()) {
                    String hash=JvmBuildInputs.sha256(file);
                    facts.setProperty("patch."+kind+"."+output.resolve(kind).relativize(file),hash);
                    facts.setProperty("artifact."+root.relativize(file),hash);
                }
            }
        }
        var index=new Properties();try(var reader=Files.newBufferedReader(outcome.inventory())){index.load(reader);}
        facts.setProperty("inputFingerprint",index.getProperty("input."+module));
        var inputs=JvmBuildInputs.snapshot(root,module);
        if(!JvmBuildInputs.fingerprint(inputs).equals(facts.getProperty("inputFingerprint")))throw new IllegalStateException("Changed snapshot inputs");
        inputs.files().forEach((file,hash)->facts.setProperty("source."+file,hash));
        facts.setProperty("inputPolicy","interlis-jvm-inputs-v1");
        var artifacts=new TreeSet<Path>();artifacts.addAll(runtimeClasspath(module,outcome));artifacts.addAll(testClasspath(module,outcome));
        Path classes=outcome.engineResults().get(test.locator()+"/binary/classes");
        if(classes==null)throw new IllegalStateException("Missing actual class-directory test input");
        try(var files=Files.walk(classes)) {artifacts.addAll(files.filter(Files::isRegularFile).map(p->p.toAbsolutePath().normalize()).toList());}
        for(Path artifact:artifacts)facts.setProperty("artifact."+root.relativize(artifact),JvmBuildInputs.sha256(artifact));
        return facts;
    }

    @SuppressWarnings("unchecked")
    private static String engineRunId(Path root) throws Exception {
        String first=Files.readAllLines(root.resolve("target/jenesis-jvm/engine/.jenesis.events.jsonl")).getFirst();
        return (String)((Map<String,Object>)Json.parse(first)).get("run");
    }

    private static String localIdentity(String value) {
        while(value.startsWith("../"))value=value.substring(3);return value;
    }

    private static String git(Path root,String... args) throws Exception {
        var command=new ArrayList<String>();command.add("git");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0)throw new IllegalStateException("Cannot inspect Git provenance");return output;
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
