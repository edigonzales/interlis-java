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
        Path delivery=root.resolve("target/jenesis-jvm/delivery");
        if(Files.exists(delivery))throw new IllegalStateException("Delivery already exists; select a fresh output checkout or archive/remove only this generated delivery directory");
        Files.createDirectories(delivery);
        var provenance=new TreeMap<String,Object>();
        for(var item:Map.of("compiler","interlis-compiler-cli","lsp","interlis-lsp").entrySet()) {
            Path distribution=delivery.resolve(item.getKey());Files.createDirectories(distribution.resolve("lib"));Files.createDirectories(distribution.resolve("bin"));
            var artifacts=ModuleArtifacts.runtime(root,item.getValue(),outcome);var facts=new ArrayList<Map<String,String>>();
            for(var artifact:artifacts) {
                Path copy=distribution.resolve("lib").resolve(artifact.fileName());Files.copy(artifact.file(),copy);
                if(!JvmBuildInputs.sha256(copy).equals(artifact.sha256()))throw new IllegalStateException("Copied artifact changed");
                facts.add(Map.of("coordinates",artifact.coordinates(),"source",root.relativize(artifact.file()).toString(),"file",root.relativize(copy).toString(),"sha256",artifact.sha256()));
            }
            String name=item.getKey().equals("compiler")?"interlis-compiler":"interlis-lsp";
            for(String suffix:List.of("",".bat")) {
                String separator=suffix.isEmpty()?":":";",home=suffix.isEmpty()?"$APP_HOME/lib/":"%APP_HOME%\\lib\\";
                String cp=String.join(separator,artifacts.stream().map(a->home+a.fileName()).toList());
                String template=Files.readString(root.resolve("build/interlis/templates/"+name+suffix));
                if(template.indexOf("@CLASSPATH@")!=template.lastIndexOf("@CLASSPATH@")||!template.contains("@CLASSPATH@"))throw new IllegalStateException("Unexpected launcher template");
                Path launcher=distribution.resolve("bin/"+name+suffix);Files.writeString(launcher,template.replace("@CLASSPATH@",cp));
                if(!launcher.toFile().setExecutable(true,false))throw new IllegalStateException("Cannot set launcher permission");
            }
            provenance.put(item.getValue(),facts);
        }
        Files.writeString(delivery.resolve("runtime-inventory.json"),JsonOutput.write(provenance));
        Properties layout=new Properties();layout.setProperty("schemaVersion","1");layout.setProperty("builder","jenesis");layout.setProperty("root",root.toString());
        for(var pair:Map.of("compiler","compiler","lsp","lsp","sdk","sdk.zip","reports","reports","norms","norms").entrySet())layout.setProperty(pair.getKey(),root.relativize(delivery.resolve(pair.getValue())).toString());
        Path file=delivery.resolve("layout.properties");try(var writer=Files.newBufferedWriter(file)){layout.store(writer,"Explicit Jenesis delivery check layout");}
        var classpath=JenesisBridge.runtimeClasspath("interlis-verification",outcome);
        if(checks) {
            host(root,classpath,file,"SdkProcesses",delivery.resolve("sdk-process"));
            host(root,classpath,file,"CompilerCliProcesses",delivery.resolve("cli-process"));
        } else {
            Path manifest=delivery.resolve("reports/sdk/version-manifest.json");Files.createDirectories(manifest.getParent());
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
        Files.createDirectories(staging.resolve("version"));Files.copy(delivery.resolve("reports/sdk/version-manifest.json"),staging.resolve("version/version-manifest.json"));
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
