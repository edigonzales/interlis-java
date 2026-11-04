package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Serial, fresh producer-to-consumer coordination using the shared current selection. */
public final class CompilerContractRun {
    public static void run(Path root) throws Exception {
        String runId="contract-"+UUID.randomUUID();Path run=root.resolve("target/jenesis-jvm/evidence").resolve(runId);
        Files.createDirectories(run);
        var verification=JenesisBridge.run(new BuildRequest("build","interlis-verification",null,root));
        var classpath=JenesisBridge.runtimeClasspath("interlis-verification",verification);
        host(root,classpath,"guru.interlis.verification.CurrentContractSelection",List.of(root.toString(),run.resolve("plan").toString()),run.resolve("plan-process"));
        host(root,classpath,"guru.interlis.verification.CurrentContractSelection",List.of("norms",root.toString(),run.resolve("norms").toString()),run.resolve("norm-process"));
        var selection=new Properties();try(var reader=Files.newBufferedReader(run.resolve("plan/selection.properties"))){selection.load(reader);}
        var layout=new Properties();layout.setProperty("schemaVersion","1");layout.setProperty("builder","jenesis");layout.setProperty("root",root.toString());
        for(var entry:Map.of("compiler","target/jenesis-jvm/delivery/compiler","lsp","target/jenesis-jvm/delivery/lsp","sdk","target/jenesis-jvm/delivery/sdk.zip").entrySet())layout.setProperty(entry.getKey(),entry.getValue());
        layout.setProperty("reports",root.relativize(run.resolve("reports")).toString());layout.setProperty("norms",root.relativize(run.resolve("norms")).toString());
        var modules=new TreeSet<>(selection.stringPropertyNames());if(modules.isEmpty())throw new IllegalStateException("Empty contract plan");
        for(String module:modules) {
            if(!JvmBuildInputs.MODULES.contains(module))throw new IllegalStateException("Foreign producer: "+module);
            Path moduleRun=run.resolve(module);
            layout.setProperty("junit."+module+".contractAssertionsTest",root.relativize(moduleRun.resolve("projected")).toString());
            layout.setProperty("manifest."+module,root.relativize(moduleRun.resolve("manifest.json")).toString());
        }
        layout.setProperty("junit.interlis-verification.contractTest",root.relativize(run.resolve("consumer/native")).toString());
        Path layoutFile=run.resolve("layout.properties");store(layoutFile,layout);
        String commit=git(root,"HEAD"),tree=git(root,"HEAD^{tree}");boolean dirty=dirty(root);
        for(String module:modules) {
            Instant started=Instant.now();
            var result=JenesisBridge.run(new BuildRequest("contracts",module,selection.getProperty(module),root),
                    Map.of("test.force","true","variable.contractRunId",runId));
            Path moduleRun=run.resolve(module);
            var receipt=JenesisBridge.snapshotFreshTests(root,module,result,moduleRun);
            bindReceipt(receipt,module,runId,started,commit,tree,dirty);store(moduleRun.resolve("receipt.properties"),receipt);
            host(root,classpath,"guru.interlis.verification.ReportProjectionMain",List.of(root.toString(),moduleRun.resolve("native").toString(),moduleRun.resolve("projected").toString(),moduleRun.resolve("receipt.properties").toString(),moduleRun.resolve("manifest.json").toString()),moduleRun.resolve("projection-process"));
        }
        Instant started=Instant.now();
        var consumer=JenesisBridge.run(new BuildRequest("contracts","interlis-verification",".*",root),
                Map.of("test.force","true","test.tag","compiler-contract","variable.contractRunId",runId,
                        "variable.layoutFile",layoutFile.toString(),"variable.layoutHash",JvmBuildInputs.sha256(layoutFile)));
        var receipt=JenesisBridge.snapshotFreshTests(root,"interlis-verification",consumer,run.resolve("consumer"));
        bindReceipt(receipt,"interlis-verification",runId,started,commit,tree,dirty);store(run.resolve("consumer/receipt.properties"),receipt);
        System.out.println("Fresh compiler contracts completed: "+run);
    }
    private static void bindReceipt(Properties receipt,String module,String runId,Instant started,String commit,String tree,boolean dirty) {
        receipt.setProperty("module",module);receipt.setProperty("builder","jenesis");receipt.setProperty("runId",runId);receipt.setProperty("started",started.toString());
        receipt.setProperty("gitCommit",commit);receipt.setProperty("treeSha",tree);receipt.setProperty("dirty",Boolean.toString(dirty));
        receipt.setProperty("execution","EXECUTED");receipt.setProperty("exitCode","0");receipt.setProperty("jdk",Runtime.version().toString());receipt.setProperty("jenesis","0.18.1");
    }
    private static void store(Path file,Properties data) throws Exception {try(var writer=Files.newBufferedWriter(file)){data.store(writer,"Actual run binding");}}
    public static void host(Path root,List<Path> classpath,String main,List<String> args,Path log) throws Exception {
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin/java").toString());command.add("-cp");
        command.add(String.join(java.io.File.pathSeparator,classpath.stream().map(Path::toString).toList()));command.add(main);command.addAll(args);
        var arguments=new Properties();for(int i=0;i<command.size();i++)arguments.setProperty("argument."+i,command.get(i));arguments.setProperty("workingDirectory",root.toString());store(log.resolveSibling(log.getFileName()+".arguments.properties"),arguments);
        Process process=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if(!process.waitFor(90,TimeUnit.SECONDS))throw new IllegalStateException("Host process timeout: "+main);
            Files.writeString(log.resolveSibling(log.getFileName()+".exit"),Integer.toString(process.exitValue()));
            if(process.exitValue()!=0)throw new IllegalStateException("Host process failed: "+main+" ("+log+")");
        } finally {if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
    private static boolean dirty(Path root)throws Exception {
        var process=new ProcessBuilder("git","status","--porcelain").directory(root.toFile()).redirectErrorStream(true).start();String value=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(process.waitFor()!=0)throw new IllegalStateException("Cannot read Git status");return !value.isBlank();
    }
    private static String git(Path root,String revision)throws Exception {
        var process=new ProcessBuilder("git","rev-parse",revision).directory(root.toFile()).redirectErrorStream(true).start();String value=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0||!value.matches("[0-9a-f]{40}"))throw new IllegalStateException("Missing Git identity");return value;
    }
}
