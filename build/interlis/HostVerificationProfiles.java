package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Closed host plans. Prepared outputs and original process receipts belong to one explicit run. */
public final class HostVerificationProfiles {
    private record Host(String main,String report,List<String> args) {
        Host(String main,String report,String... args){this(main,report,List.of(args));}
    }
    private static final Map<String,Host> HOSTS=Map.ofEntries(
        Map.entry("python-free",new Host("PythonFreeCheck","python-free/source-check.json")),
        Map.entry("cli",new Host("CompilerCliProcesses","p3/compiler-cli/processes.json")),
        Map.entry("platform",new Host("PlatformProcesses","p3/platform/result.json")),
        Map.entry("conformance-evidence",new Host("ConformanceEvidence","conformance/candidate.json")),
        Map.entry("geo-corpus",new Host("GeoCorpus","performance/corpus-verification.json")),
        Map.entry("full-corpus",new Host("FullCorpus","performance/full-corpus/prepared.json")),
        Map.entry("full-corpus-jvm",new Host("FullCorpusRun","performance/full-corpus/jvm.json")),
        Map.entry("jvm-benchmark",new Host("JvmBenchmark","performance/performance-jvm.json")),
        Map.entry("observation-pressure",new Host("ObservationPressure","performance/full-corpus/observation-pressure.json")),
        Map.entry("editor-pressure",new Host("EditorPressure","performance/full-corpus/editor-jvm.json")),
        Map.entry("reference",new Host("ReferenceProcesses","reference/reference-results.json","profile")),
        Map.entry("reference-originals",new Host("ReferenceProcesses","stabilization/original-view-reference-comparison.json","originals")),
        Map.entry("reference-views",new Host("ReferenceProcesses","stabilization/views-reference-comparison.json","views","@node")),
        Map.entry("reference-units",new Host("ReferenceProcesses","stabilization/units-reference-comparison.json","units","@node")),
        Map.entry("repository-reference",new Host("RepositoryReferenceCheck","reference/repository/results.json")),
        Map.entry("semantic-comparison",new Host("SemanticComparison","g2/semantic-comparison.json","@node")));
    public static final Set<String> PROFILES;
    static {var profiles=new TreeSet<>(HOSTS.keySet());profiles.addAll(List.of("conformance","sdk","performance","performance-checker-test","release-evidence-checker-test","wasm-benchmark","browser-benchmark","full-corpus-wasm","full-corpus-browsers","full-corpus-safari","full-corpus-editor-wasm","full-corpus-artifacts","portable","browser","reproducibility"));PROFILES=Set.copyOf(profiles);}

    public static void verifyPythonFree(Path root,JenesisBridge.BuildOutcome outcome)throws Exception {
        Path run=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/host/python-free-"+UUID.randomUUID());Files.createDirectories(run);
        var context=new Context(root,run,outcome);context.perform("python-free");context.finish("python-free");
    }
    public static void release(Path root,JvmBuildLock lease)throws Exception {
        Path run=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/host/P3-"+UUID.randomUUID());Files.createDirectories(run);
        var context=new Context(root,run,null,lease);
        // External platform and active OS guard evidence must already exist for this candidate.
        Path external=root.resolve("target/jenesis-jvm/reports");
        for(String directory:List.of("p3/platform-evidence","python-free")) {
            Path source=JvmBuildInputs.requireOutputRoot(root,root.relativize(external.resolve(directory)).toString());
            if(!Files.isDirectory(source))throw new IllegalStateException("Missing P3 preflight evidence: "+source);
            try(var walk=Files.walk(source)) {for(Path file:walk.sorted().toList()) {
                if(Files.isSymbolicLink(file))throw new IllegalStateException("Symlink preflight evidence");
                Path destination=context.reports.resolve(directory).resolve(source.relativize(file));
                if(Files.isDirectory(file))Files.createDirectories(destination);else Files.copy(file,destination);
            }}
        }
        context.node("tools/release/evidence.cjs","--preflight");
        context.outcome=JenesisBridge.run(new BuildRequest("artifacts",null,null,root));
        context.initialInputs=Map.copyOf(JenesisBridge.inputFingerprints(context.outcome));
        context.producerInventorySha256=JvmBuildInputs.sha256(context.outcome.inventory());
        Files.copy(context.outcome.inventory(),run.resolve("producer-index.properties"));
        for(String profile:List.of("platform","conformance-evidence","portable","browser","reproducibility","release-evidence-checker-test"))context.perform(profile);
        context.node("tools/release/reproducibility.cjs");context.perform("performance");
        context.node("tools/release/evidence.cjs");context.finish("P3");
    }
    public static void run(BuildRequest request,JvmBuildLock lease) throws Exception {
        String profile=request.options().get("--profile");
        if(!PROFILES.contains(profile))throw new IllegalArgumentException("Unknown host profile: "+profile);
        Path root=request.root(),run=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/host/"+profile+"-"+UUID.randomUUID());
        Files.createDirectories(run);
        String githubOutput=System.getenv("GITHUB_OUTPUT");
        if(githubOutput!=null)Files.writeString(Path.of(githubOutput),"evidence="+run+"\n",StandardOpenOption.APPEND);
        var outcome=JenesisBridge.run(new BuildRequest("artifacts",null,null,root));
        var context=new Context(root,run,outcome,lease);context.perform(profile);
        context.finish(profile);
    }
    private static final class Context {
        final Path root,run,reports,layoutFile;JenesisBridge.BuildOutcome outcome;
        final Properties layout=new Properties();final Set<String> done=new HashSet<>();
        final JvmBuildLock lease;
        Map<String,String> initialInputs;
        String producerInventorySha256;
        final List<Map<String,Object>> processes=new ArrayList<>();int step;ConsumerSnapshot.Snapshot snapshot;
        Context(Path root,Path run,JenesisBridge.BuildOutcome outcome)throws Exception {this(root,run,outcome,null);}
        Context(Path root,Path run,JenesisBridge.BuildOutcome outcome,JvmBuildLock lease)throws Exception {
            this.lease=lease;initialInputs=outcome==null?null:Map.copyOf(JenesisBridge.inputFingerprints(outcome));
            this.root=root;this.run=run;this.outcome=outcome;reports=run.resolve("reports");layoutFile=run.resolve("layout.properties");
            layout.setProperty("schemaVersion","1");layout.setProperty("builder","jenesis");layout.setProperty("root",root.toString());
            for(var pair:Map.of("reports","reports","compiler","delivery/compiler","lsp","delivery/lsp","sdk","delivery/sdk.zip","norms","norms","conformance","conformance","reference","reference/ili2c.jar").entrySet())layout.setProperty(pair.getKey(),JvmBuildInputs.portableRelative(root,run.resolve(pair.getValue())));
            producerInventorySha256=outcome==null?"PREFLIGHT":JvmBuildInputs.sha256(outcome.inventory());
            if(outcome!=null)Files.copy(outcome.inventory(),run.resolve("producer-index.properties"));
            store();
        }
        void store()throws Exception{try(var writer=Files.newBufferedWriter(layoutFile)){layout.store(writer,"Explicit current host inputs");}}
        void perform(String profile)throws Exception {
            if(done.contains(profile))return;
            switch(profile) {
                case "sdk" -> ExistingJvmDistribution.generateAt(root,outcome,run.resolve("delivery"),reports,true);
                case "cli","jvm-benchmark","reference-originals","reference-views","reference-units" -> {perform("sdk");if(profile.equals("jvm-benchmark"))perform("geo-corpus");host(profile);}
                case "full-corpus-jvm" -> {perform("full-corpus");host(profile);}
                case "jvm" -> {perform("python-free");JenesisBridge.run(new BuildRequest("build",null,null,root));}
                case "platform" -> {
                    perform("python-free");
                    var normal=JenesisBridge.run(new BuildRequest("build",null,null,root));done.add("jvm");
                    for(String module:JvmBuildInputs.MODULES) {
                        var result=JenesisBridge.moduleResult(normal,module);
                        if(result.testReports().isPresent()) {
                            Path copy=reports.resolve("jvm/"+module+"/test");copyXml(result.testReports().get(),copy);
                            layout.setProperty("junit."+module+".test",JvmBuildInputs.portableRelative(root,copy));
                        }
                    }
                    Path contracts=CompilerContractRun.run(root);
                    var contractLayout=new Properties();try(var reader=Files.newBufferedReader(contracts.resolve("layout.properties"))){contractLayout.load(reader);}
                    for(String key:contractLayout.stringPropertyNames())if(key.startsWith("junit.")||key.startsWith("manifest."))layout.setProperty(key,contractLayout.getProperty(key));
                    for(String key:layout.stringPropertyNames())if(key.startsWith("junit.")&&!key.endsWith(".test")) {
                        String[] parts=key.split("\\.");Path source=root.resolve(layout.getProperty(key));
                        Path destination=reports.resolve("jvm/"+parts[1]+"/"+parts[2]);copyXml(source,destination);
                        layout.setProperty(key,JvmBuildInputs.portableRelative(root,destination));
                    }
                    store();perform("sdk");host(profile);
                    Files.writeString(reports.resolve("p3/platform/commit.txt"),git("rev-parse","HEAD")+"\n");
                }
                case "conformance" -> conformance();
                case "conformance-evidence" -> {perform("conformance");host(profile);}
                case "semantic-comparison" -> {backend("teavm-smoke");host(profile);}
                case "reproducibility" -> node("tools/release/reproducibility.cjs");
                case "portable" -> {perform("jvm");backend("teavm-contracts");backend("webimage-contracts");}
                case "browser" -> {backend("browser-contracts");}
                case "wasm-benchmark","browser-benchmark" -> {perform("geo-corpus");backend(profile.equals("wasm-benchmark")?"teavm-smoke":"browser-prepare");node(profile.equals("wasm-benchmark")?"interlis-wasm-teavm/wasm-benchmark.cjs":"interlis-wasm-teavm/browser-benchmark.cjs");}
                case "full-corpus-wasm" -> {perform("full-corpus-jvm");backend("teavm-smoke");node("tools/performance/full-corpus-node.cjs");}
                case "full-corpus-browsers" -> {perform("full-corpus-wasm");backend("browser-prepare");node("tools/performance/full-corpus-browser.cjs");}
                case "full-corpus-safari" -> {perform("full-corpus-browsers");node("tools/performance/full-corpus-browser.cjs","safari");}
                case "full-corpus-artifacts" -> {backend("browser-prepare");node("tools/performance/artifacts.cjs");}
                case "full-corpus-editor-wasm" -> {perform("full-corpus-safari");perform("full-corpus-artifacts");node("tools/performance/editor-node.cjs");}
                case "performance" -> {perform("full-corpus-editor-wasm");perform("editor-pressure");perform("observation-pressure");perform("performance-checker-test");node("tools/performance/check.cjs");}
                case "performance-checker-test" -> node("--test","tools/performance/check.test.cjs");
                case "release-evidence-checker-test" -> node("--test","tools/release/artifact-comparison.test.cjs","tools/release/evidence.test.cjs","tools/release/distribution-files.test.cjs","tools/verification/node-junit.test.cjs");
                default -> host(profile);
            }
            done.add(profile);
        }
        void host(String profile)throws Exception {
            Host host=HOSTS.get(profile);if(host==null)throw new IllegalArgumentException("Unknown fixed host");
            var command=javaCommand(JenesisBridge.runtimeClasspath("interlis-verification",outcome),"guru.interlis.verification."+host.main());
            command.add(root.toString());for(String arg:host.args())command.add(arg.equals("@node")?node():arg);
            Path report=reports.resolve(host.report());Files.deleteIfExists(report);
            execute(command,Duration.ofHours(2),Set.of(0));
            if(!Files.isRegularFile(report)||Files.size(report)==0)throw new IllegalStateException("Missing actual host report: "+report);
        }
        ArrayList<String> javaCommand(List<Path> classpath,String main)throws Exception {
            var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),
                "-Dverification.layout="+layoutFile,"-Dverification.layout.sha256="+JvmBuildInputs.sha256(layoutFile),"-cp",
                String.join(java.io.File.pathSeparator,classpath.stream().map(Path::toString).toList()),main));return command;
        }
        String node()throws Exception {
            String node=System.getenv("INTERLIS_NODE");if(node==null||!Path.of(node).isAbsolute())throw new IllegalArgumentException("Select pinned absolute INTERLIS_NODE");
            if(!done.contains("node-pin")) {
                var result=execute(List.of(node,"--version"),Duration.ofSeconds(10),Set.of(0));
                var pins=new Properties();try(var reader=Files.newBufferedReader(root.resolve("gradle.properties"))){pins.load(reader);}
                if(!Files.readString(result.resolveSibling(result.getFileName()+".stdout")).trim().equals("v"+pins.getProperty("nodeVersion")))throw new IllegalStateException("Wrong Node pin");done.add("node-pin");
            }
            return node;
        }
        void node(String... args)throws Exception{var command=new ArrayList<String>();command.add(node());command.addAll(List.of(args));consume(()->{execute(command,Duration.ofHours(2),Set.of(0));return null;});}
        void backend(String profile)throws Exception {
            if(snapshot==null)snapshot=ConsumerSnapshot.create(root,outcome);
            consume(()->{WasmBuildRun.run(new BuildRequest("wasm",null,null,root,Map.of("--profile",profile,"--inputs",snapshot.manifest().toString(),"--inputs-sha256",snapshot.manifestSha256())));return null;});
        }
        void requireOriginalInputs()throws Exception {
            if(initialInputs!=null)for(var entry:initialInputs.entrySet())if(!JvmBuildInputs.fingerprint(root,entry.getKey()).equals(entry.getValue()))throw new IllegalStateException("Host inputs changed");
        }
        <T> T consume(java.util.concurrent.Callable<T> consumer)throws Exception {
            if(lease==null)throw new IllegalStateException("Explicit host consumer requires its invocation lease");
            requireOriginalInputs();
            T result=lease.withoutLock(consumer);
            requireOriginalInputs();
            // Another producer may have replaced the last selected result index. Resolve all
            // current artifacts again under the lease; original input/provenance facts stay fixed.
            if(outcome!=null)outcome=JenesisBridge.run(new BuildRequest("artifacts",null,null,root));
            return result;
        }
        Path execute(List<String> command,Duration timeout,Set<Integer> allowed)throws Exception {
            Path output=run.resolve("process-"+(step++));Files.writeString(output.resolveSibling(output.getFileName()+".argv.json"),JsonOutput.write(command));
            var builder=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(output.resolveSibling(output.getFileName()+".stdout").toFile()).redirectError(output.resolveSibling(output.getFileName()+".stderr").toFile());
            builder.environment().put("JAVA_HOME",System.getProperty("java.home"));builder.environment().put("VERIFICATION_LAYOUT",layoutFile.toString());builder.environment().put("VERIFICATION_LAYOUT_SHA256",JvmBuildInputs.sha256(layoutFile));
            var process=builder.start();boolean ended=false;try {
                ended=process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS);
                var receipt=Map.<String,Object>of("argv",command,"timedOut",!ended,"exitCode",ended?process.exitValue():-1);
                Files.writeString(output.resolveSibling(output.getFileName()+".receipt.json"),JsonOutput.write(receipt));processes.add(receipt);
                if(!ended||!allowed.contains(process.exitValue()))throw new IllegalStateException("Host process failed: "+output);
            }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
            return output;
        }
        void conformance()throws Exception {
            perform("sdk");var pin=new Properties();try(var reader=Files.newBufferedReader(root.resolve("gradle/conformance-suite.properties"))){pin.load(reader);}
            String override=System.getenv("CONFORMANCE_CACHE");Path cache=override==null?Path.of(System.getProperty("user.home"),".cache/interlis-java/conformance"):Path.of(override).toAbsolutePath();
            Files.createDirectories(cache);Path archive=cache.resolve("suite-"+pin.getProperty("commit")+".tar.gz");
            if(!Files.exists(archive)) {
                Path temporary=Files.createTempFile(cache,"suite-",".part");try {
                    var connection=java.net.URI.create(pin.getProperty("url")).toURL().openConnection();connection.setConnectTimeout(180000);connection.setReadTimeout(180000);
                    try(var input=connection.getInputStream()){Files.copy(input,temporary,StandardCopyOption.REPLACE_EXISTING);}
                    if(!JvmBuildInputs.sha256(temporary).equals(pin.getProperty("sha256")))throw new IllegalStateException("Suite pin mismatch");Files.move(temporary,archive);
                }finally{Files.deleteIfExists(temporary);}
            }
            if(!JvmBuildInputs.sha256(archive).equals(pin.getProperty("sha256")))throw new IllegalStateException("Suite pin mismatch");
            Path suite=run.resolve("suite"),output=run.resolve("conformance");Files.createDirectories(output);
            CompilerContractRun.host(root,JenesisBridge.buildArchiveTool(root,false),"build.interlis.archive.ExtractTar",List.of(archive.toString(),suite.toString()),run.resolve("suite-extract"));
            String launcher=System.getProperty("os.name").startsWith("Windows")?"interlis-compiler.bat":"interlis-compiler";
            String filter=System.getenv().getOrDefault("CONFORMANCE_FILTER","^(ili23|ili24)\\.");
            var config=Map.of("suite",suite.resolve("suite/test-cases.json").toString(),"outputDirectory",output.toString(),"cacheDirectory",cache.resolve("fixtures").toString(),"timeoutSeconds",60,"parallelism",4,"xpassPolicy","fail","filter",filter,"compilers",List.of(Map.of("id","interlis-java","source",run.resolve("delivery/compiler/bin/"+launcher).toString(),"command",List.of("{compiler}","check","{files}"),"acceptedExitCodes",List.of(0),"rejectedExitCodes",List.of(1),"internalErrorPatterns",List.of("internal failure:","Exception in thread"),"baseline",root.resolve("testdata/conformance/baseline.json").toString())));
            Path configFile=output.resolve("run-config.json");Files.writeString(configFile,JsonOutput.write(config));Files.writeString(output.resolve("run-start.txt"),Long.toString(System.currentTimeMillis()));
            Path log=execute(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),suite.resolve("InterlisConformance.java").toString(),"run","--config",configFile.toString()),Duration.ofMinutes(30),Set.of(0,1));
            var receipt=(Map<?,?>)build.jenesis.Json.parse(Files.readString(log.resolveSibling(log.getFileName()+".receipt.json")));
            Files.writeString(output.resolve("runner-exit.txt"),Integer.toString(((Number)receipt.get("exitCode")).intValue()));Files.copy(log.resolveSibling(log.getFileName()+".stdout"),output.resolve("conformance.log"));
            var tested=JenesisBridge.run(new BuildRequest("build","interlis-compiler-cli","guru.interlis.cli.conformance.NormativeConformanceContractTest",root));
            var command=javaCommand(JenesisBridge.testClasspath("interlis-compiler-cli",tested),"guru.interlis.cli.conformance.NormativeConformance");command.addAll(List.of(root.toString(),output.toString(),reports.resolve("conformance").toString()));
            execute(command,Duration.ofMinutes(5),Set.of(0));
        }
        String git(String... args)throws Exception {
            var command=new ArrayList<String>();command.add("git");command.addAll(List.of(args));
            var process=new ProcessBuilder(command).directory(root.toFile()).start();
            String text=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
            if(process.waitFor()!=0)throw new IllegalStateException("Cannot read candidate identity");return text;
        }
        void copyXml(Path source,Path destination)throws Exception {
            Files.createDirectories(destination);try(var walk=Files.list(source)) {
                var xml=walk.filter(p->p.getFileName().toString().endsWith(".xml")).toList();
                if(xml.isEmpty())throw new IllegalStateException("Empty required test output");
                for(Path file:xml)Files.copy(file,destination.resolve(file.getFileName()));
            }
        }
        void finish(String profile)throws Exception {
            requireOriginalInputs();
            var files=new TreeMap<String,String>();if(Files.isDirectory(reports))try(var walk=Files.walk(reports)){for(Path file:walk.filter(Files::isRegularFile).toList())files.put(JvmBuildInputs.portableRelative(run,file),JvmBuildInputs.sha256(file));}
            Files.writeString(run.resolve("receipt.json"),JsonOutput.write(Map.of("schemaVersion",1,"profile",profile,"exitCode",0,"engineInventorySha256",producerInventorySha256,"completedSteps",new TreeSet<>(done),"processes",processes,"reports",files)));
            System.out.println("Host verification completed: "+run);
        }
    }
}
