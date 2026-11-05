package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import build.jenesis.Json;
import java.nio.file.*;
import java.util.*;

/** Existing CLI/LSP/SDK forms; products always come from the current Jenesis invocation. */
public final class ExistingJvmDistribution {
    public static void run(Path root)throws Exception { run(root,true); }
    public static void generate(Path root)throws Exception { run(root,false); }
    private static void run(Path root,boolean checks)throws Exception {
        var outcome=JenesisBridge.run(new BuildRequest("artifacts",null,null,root));
        Path delivery=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/delivery");
        if(Files.exists(delivery)) {
            Path history=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/delivery-history/"+UUID.randomUUID());
            Files.createDirectories(history.getParent());Files.move(delivery,history);
        }
        generateAt(root,outcome,delivery,delivery.resolve("reports"),checks);
    }
    static void generateAt(Path root,JenesisBridge.BuildOutcome outcome,Path delivery,Path reports,boolean checks)throws Exception {
        JvmBuildInputs.requireOutputRoot(root,root.relativize(delivery).toString());
        JvmBuildInputs.requireOutputRoot(root,JvmBuildInputs.portableRelative(root,reports));
        Files.createDirectories(delivery);
        var provenance=new TreeMap<String,Object>();
        for(var item:Map.of("compiler","interlis-compiler-cli","lsp","interlis-lsp").entrySet()) {
            Path distribution=delivery.resolve(item.getKey());Files.createDirectories(distribution.resolve("lib"));Files.createDirectories(distribution.resolve("bin"));
            var artifacts=ModuleArtifacts.runtime(root,item.getValue(),outcome);var facts=new ArrayList<Map<String,String>>();
            for(var artifact:artifacts) {
                Path copy=distribution.resolve("lib").resolve(artifact.fileName());Files.copy(artifact.file(),copy);
                if(!JvmBuildInputs.sha256(copy).equals(artifact.sha256()))throw new IllegalStateException("Copied artifact changed");
                facts.add(Map.of("coordinates",artifact.coordinates(),"source",JvmBuildInputs.portableRelative(root,artifact.file()),"file",JvmBuildInputs.portableRelative(root,copy),"sha256",artifact.sha256()));
            }
            String name=item.getKey().equals("compiler")?"interlis-compiler":"interlis-lsp";
            String module=ModulePathContract.name(item.getValue());
            var result=JenesisBridge.moduleResult(outcome,item.getValue());
            if(!result.explicitDescriptor()||!result.moduleName().orElseThrow().equals(module))throw new IllegalStateException("Distribution requires its explicit product module");
            String main=item.getKey().equals("compiler")?"guru.interlis.cli.Main":"guru.interlis.lsp.Main";
            for(String suffix:List.of("",".bat")) {
                String template=Files.readString(root.resolve("build/interlis/templates/"+name+suffix));
                if(!template.contains("--module "+module+"/"+main)||!template.contains("--module-path")||template.contains("@CLASSPATH@")||template.contains("ALL-MODULE-PATH")||template.contains("ALL-DEFAULT")||template.contains("--patch-module")||java.util.regex.Pattern.compile("(?m)^[ \t]*set[ \t]*$").matcher(template).find())throw new IllegalStateException("Unexpected modular launcher template");
                Path launcher=distribution.resolve("bin/"+name+suffix);Files.writeString(launcher,template);
                if(!launcher.toFile().setExecutable(true,false))throw new IllegalStateException("Cannot set launcher permission");
            }
            provenance.put(item.getValue(),facts);
        }
        Files.writeString(delivery.resolve("runtime-inventory.json"),JsonOutput.write(provenance));
        Properties layout=new Properties();layout.setProperty("schemaVersion","1");layout.setProperty("builder","jenesis");layout.setProperty("root",root.toString());
        for(var pair:Map.of("compiler","compiler","lsp","lsp","sdk","sdk.zip","reports","reports","norms","norms").entrySet())layout.setProperty(pair.getKey(),JvmBuildInputs.portableRelative(root,delivery.resolve(pair.getValue())));
        layout.setProperty("reports",JvmBuildInputs.portableRelative(root,reports));
        Path file=delivery.resolve("layout.properties");try(var writer=Files.newBufferedWriter(file)){layout.store(writer,"Explicit Jenesis delivery check layout");}
        var classpath=JenesisBridge.runtimeClasspath("interlis-verification",outcome);
        if(checks) {
            host(root,classpath,file,"SdkProcesses",delivery.resolve("sdk-process"));
            host(root,classpath,file,"CompilerCliProcesses",delivery.resolve("cli-process"));
        } else {
            Path manifest=reports.resolve("sdk/version-manifest.json");Files.createDirectories(manifest.getParent());
            String launcher="interlis-compiler"+(System.getProperty("os.name").startsWith("Windows")?".bat":"");
            var command=List.of(delivery.resolve("compiler/bin/"+launcher).toString(),"version","--json");
            Files.writeString(delivery.resolve("version-command.json"),JsonOutput.write(command));
            var builder=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(manifest.toFile()).redirectError(delivery.resolve("version.stderr").toFile());builder.environment().put("JAVA_HOME",System.getProperty("java.home"));
            var process=builder.start();try {
                if(!process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Version process timeout");
                Files.writeString(delivery.resolve("version.exit"),Integer.toString(process.exitValue()));
                if(process.exitValue()!=0||Files.size(delivery.resolve("version.stderr"))!=0)throw new IllegalStateException("Cannot produce SDK version manifest");
                if(!(Json.parse(Files.readString(manifest)) instanceof Map))throw new IllegalStateException("Malformed SDK version manifest");
            }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        }
        Path staging=delivery.resolve("sdk-staging");copyTree(delivery.resolve("compiler"),staging.resolve("cli"));copyTree(root.resolve("examples/sdk"),staging.resolve("examples/sdk"));
        Files.createDirectories(staging.resolve("version"));Files.copy(reports.resolve("sdk/version-manifest.json"),staging.resolve("version/version-manifest.json"));
        CompilerContractRun.host(root,JenesisBridge.buildArchiveTool(root,false),"build.interlis.archive.ArchiveWriter",List.of(staging.toString(),delivery.resolve("sdk.zip").toString()),delivery.resolve("archive-process"));
        if(checks)host(root,classpath,file,"SdkArchiveCheck",delivery.resolve("sdk-archive-check"));
        System.out.println((checks?"SDK_CHECK_PASS":"SDK_GENERATED")+"; tests NOT_RUN (artifact generation only); "+delivery);
    }
    static void host(Path root,List<Path> cp,Path layout,String main,Path log)throws Exception {
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin/java").toString());
        command.add("-Dverification.layout="+layout);command.add("-Dverification.layout.sha256="+JvmBuildInputs.sha256(layout));
        command.add("-cp");command.add(String.join(java.io.File.pathSeparator,cp.stream().map(Path::toString).toList()));command.add("guru.interlis.verification."+main);command.add(root.toString());
        Files.writeString(log.resolveSibling(log.getFileName()+".argv.json"),JsonOutput.write(command));
        var builder=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("JAVA_HOME",System.getProperty("java.home"));
        var process=builder.start();
        try {
            if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Delivery check timeout: "+main);
            Files.writeString(log.resolveSibling(log.getFileName()+".exit"),Integer.toString(process.exitValue()));
            if(process.exitValue()!=0)throw new IllegalStateException("Delivery check failed: "+log);
        }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
    private static void copyTree(Path source,Path destination)throws Exception {
        try(var files=Files.walk(source)) {
            for(Path path:files.sorted().toList()) {
                if(Files.isSymbolicLink(path))throw new IllegalStateException("Symlink in delivered tree");Path target=destination.resolve(source.relativize(path));
                if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target,StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }
}
