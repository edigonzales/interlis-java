package build.interlis;

import java.lang.module.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Standard-JDK boundary gate. javac compiles only the separate SDK consumers, never product sources. */
public final class ModulePathContract {
    public static String name(String project) {
        if(!project.matches("interlis-[a-z0-9]+(?:-[a-z0-9]+)*"))throw new IllegalArgumentException("Unknown own module");
        return "guru.interlis."+project.substring(9).replace('-','.');
    }
    /** Own products always have the exact explicit, encapsulated descriptor. */
    public static void requireOwnDescriptor(String project, ModuleDescriptor descriptor) {
        if(descriptor.isAutomatic()||descriptor.isOpen()||!descriptor.name().equals(name(project)))
            throw new IllegalStateException("Own runtime module must be explicit and encapsulated: "+project);
        if(isXtfAdapter(project)||project.equals("interlis-validation")) {
            var required=descriptor.requires().stream().map(ModuleDescriptor.Requires::name).collect(java.util.stream.Collectors.toSet());
            var expected=isXtfAdapter(project)?Set.of("java.base","java.xml","guru.interlis.model","guru.interlis.transfer")
                    :Set.of("java.base","guru.interlis.transfer");
            if(!required.equals(expected)
                    ||descriptor.exports().size()!=1||descriptor.exports().stream().anyMatch(export->
                        export.isQualified()||!export.source().equals(name(project)))||!descriptor.opens().isEmpty())
                throw new IllegalStateException("Unexpected transfer or validation module boundary: "+descriptor);
        }
    }
    private static boolean isXtfAdapter(String project) {
        return project.equals("interlis-xtf23") || project.equals("interlis-xtf24");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Expected no arguments");
        Path root = Path.of("").toAbsolutePath().normalize();
        try (var lease = JvmBuildLock.acquire(root)) {
            run(root);
        }
    }

    public static void run(Path root)throws Exception {
        List<String> projects = JvmBuildInputs.projectModules(root);
        for(String project:projects)if(!Files.isRegularFile(root.resolve(project+"/src/main/java/module-info.java")))throw new IllegalStateException("Missing own module descriptor: "+project);
        var outcome=JenesisBridge.artifacts(root);
        Path run=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/module-checks/"+UUID.randomUUID()),lib=run.resolve("lib");Files.createDirectories(lib);
        var own=new TreeSet<String>();var files=new TreeMap<String,Path>();
        for(String project:projects) {
            var result=JenesisBridge.moduleResult(outcome,project);
            if(!result.explicitDescriptor()||!result.moduleName().orElseThrow().equals(name(project)))throw new IllegalStateException("Wrong own module: "+project);
            own.add(name(project));
            var runtime=ModuleArtifacts.runtime(root,project,outcome);
            if(project.equals("interlis-transfer")||isXtfAdapter(project)||project.equals("interlis-validation")) {
                String prefix="maven/guru.interlis/", version="/"+JvmBuildInputs.projectVersion(root);
                var expected=new HashSet<>(Set.of(prefix+"interlis-transfer"+version,prefix+"interlis-model"+version));
                expected.add(prefix+project+version);
                var actual=runtime.stream().map(ModuleArtifacts.Artifact::coordinates).collect(java.util.stream.Collectors.toSet());
                if(!actual.equals(expected))throw new IllegalStateException("Unexpected runtime closure for "+project+": "+actual);
            }
            for(var artifact:runtime) {
                Path prior=files.putIfAbsent(artifact.fileName(),artifact.file());
                if(prior != null && !prior.equals(artifact.file()) && Files.mismatch(prior, artifact.file()) != -1)throw new IllegalStateException("Conflicting module-path artifact");
            }
        }
        var packages=new TreeMap<String,String>();var modules=new TreeMap<String,Object>();
        for(var file:files.entrySet()) {
            Path copy=lib.resolve(file.getKey());Files.copy(file.getValue(),copy);
            var descriptors=ModuleFinder.of(copy).findAll();if(descriptors.size()!=1)throw new IllegalStateException("Missing descriptor");
            var descriptor=descriptors.iterator().next().descriptor();
            if(modules.containsKey(descriptor.name()))throw new IllegalStateException("Duplicate module name");
            for(String pkg:descriptor.packages())if(packages.putIfAbsent(pkg,descriptor.name())!=null)throw new IllegalStateException("Split production package: "+pkg);
            if(own.contains(descriptor.name()))requireOwnDescriptor(projects.stream().filter(project->name(project).equals(descriptor.name())).findFirst().orElseThrow(),descriptor);
            modules.put(descriptor.name(),Map.of("file",copy.toString(),"sha256",JvmBuildInputs.sha256(copy),"automatic",descriptor.isAutomatic(),"descriptor",descriptor.toString()));
        }
        Configuration.resolve(ModuleFinder.of(lib),List.of(ModuleLayer.boot().configuration()),ModuleFinder.ofSystem(),own);
        Path source=Files.createDirectories(run.resolve("consumer/consumer"));
        String text="package consumer;\n"+Files.readString(root.resolve("examples/sdk/SdkExample.java")).replace("SdkExample", "NamedConsumer");
        String checks="""
                boolean named = args.length == 0;
                if (NamedConsumer.class.getModule().isNamed() != named || CompilerEngines.class.getModule().isNamed() != named)
                    throw new AssertionError("Classpath/module launch mismatch");
                if (named) {
                    if (!CompilerEngines.class.getModule().getName().equals("guru.interlis.compiler")
                        || !SourceId.class.getModule().getName().equals("guru.interlis.model")) throw new AssertionError("Wrong product modules");
                    try {
                        var implementation = Class.forName("guru.interlis.compiler.internal.DefaultCompilerEngine");
                        if (implementation.getDeclaredConstructors()[0].trySetAccessible()) throw new AssertionError("Internal package opened");
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                }
                SourceText source""";
        var entrypoints=Map.ofEntries(
            Map.entry("guru.interlis.compiler.cli","guru.interlis.cli.CliApplication"),
            Map.entry("guru.interlis.lsp","guru.interlis.lsp.InterlisLanguageServerApi"),
            Map.entry("guru.interlis.workspace","guru.interlis.workspace.Workspace"),
            Map.entry("guru.interlis.repository","guru.interlis.repository.RepositoryClient"),
            Map.entry("guru.interlis.projection","guru.interlis.projection.DiagramProjector"),
            Map.entry("guru.interlis.documentation","guru.interlis.documentation.DocumentationProjector"),
            Map.entry("guru.interlis.transfer","guru.interlis.transfer.TransferPlanner"),
            Map.entry("guru.interlis.xtf23","guru.interlis.xtf23.Xtf23Reader"),
            Map.entry("guru.interlis.xtf24","guru.interlis.xtf24.Xtf24Reader"),
            Map.entry("guru.interlis.wasm.bridge","guru.interlis.bridge.BridgeDispatcher"),
            Map.entry("guru.interlis.validation","guru.interlis.validation.LocalValidator"));
        String extra=String.join("\n",own.stream().filter(entrypoints::containsKey).map(name->
            "if (named && !"+entrypoints.get(name)+".class.getModule().getName().equals(\""+name+"\")) throw new AssertionError(\"Wrong public entrypoint module\");").toList());
        checks=checks.replace("SourceText source",extra+"\nSourceText source");
        text=text.replace("SourceText source",checks);
        Path consumer=source.resolve("NamedConsumer.java");Files.writeString(consumer,text);
        String requires=String.join("\n",own.stream().map(n->"requires "+n+";").toList());
        Path descriptor=run.resolve("consumer/module-info.java");Files.writeString(descriptor,"module interlis.boundary {"+requires+"}\n");
        Path classes=run.resolve("named-classes");
        process(run,"named-compile","javac",List.of("--release","25","--module-path",lib.toString(),"-d",classes.toString(),descriptor.toString(),consumer.toString()),0);
        process(run,"named-run","java",List.of("--module-path",lib+java.io.File.pathSeparator+classes,"--module","interlis.boundary/consumer.NamedConsumer"),0);
        if(!Files.readString(run.resolve("named-run.stdout")).startsWith("SDK PASS "))throw new IllegalStateException("Named SDK consumer produced no result");
        Path cpClasses=run.resolve("classpath-classes");String cp;
        try(var list=Files.list(lib)){cp=String.join(java.io.File.pathSeparator,list.sorted().map(Path::toString).toList());}
        process(run,"classpath-compile","javac",List.of("--release","25","-cp",cp,"-d",cpClasses.toString(),consumer.toString()),0);
        process(run,"classpath-run","java",List.of("-cp",cpClasses+java.io.File.pathSeparator+cp,"consumer.NamedConsumer","classpath"),0);
        Path negative=Files.createDirectories(run.resolve("negative/negative"));
        Path bad=negative.resolve("Probe.java");Files.writeString(bad,"package negative; import guru.interlis.compiler.internal.DefaultCompilerEngine; public class Probe { DefaultCompilerEngine engine; }\n");
        Path badDescriptor=negative.getParent().resolve("module-info.java");Files.writeString(badDescriptor,"module interlis.negative { requires guru.interlis.compiler; }\n");
        process(run,"negative-compile","javac",List.of("--release","25","--module-path",lib.toString(),"-d",run.resolve("negative-classes").toString(),badDescriptor.toString(),bad.toString()),1);
        if(!Files.readString(run.resolve("negative-compile.stderr")).contains("does not export"))throw new IllegalStateException("Negative probe failed for unrelated reason");
        Files.writeString(run.resolve("result.json"),JsonOutput.write(Map.of("status","passed","ownModules",own,"modules",modules,"namedConsumer",true,"classpathConsumer",true,"forbiddenAccessRejected",true)));
        System.out.println("MODULE_PATH_CONTRACT_PASS "+run);
    }
    private static void process(Path run,String role,String tool,List<String> args,int expected)throws Exception {
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin",tool).toString());command.addAll(args);
        Files.writeString(run.resolve(role+".argv.json"),JsonOutput.write(command));
        var process=new ProcessBuilder(command).directory(run.toFile()).redirectOutput(run.resolve(role+".stdout").toFile()).redirectError(run.resolve(role+".stderr").toFile()).start();
        try {
            boolean ended=process.waitFor(60,TimeUnit.SECONDS);Files.writeString(run.resolve(role+".process.json"),JsonOutput.write(Map.of("timedOut",!ended,"exitCode",ended?process.exitValue():-1,"expected",expected)));
            if(!ended||process.exitValue()!=expected)throw new IllegalStateException("Boundary process failed: "+run.resolve(role));
        }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
}
