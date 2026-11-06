package build.interlis.tests;

import build.interlis.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Small executable negative contracts for the build boundary; never part of a product module. */
public final class ArtifactContracts {
    private static int checks;
    @FunctionalInterface interface Action { void run() throws Exception; }
    private static void rejects(Action action,String reason) throws Exception {
        try { action.run();throw new AssertionError("Accepted invalid "+reason); }
        catch(IllegalStateException|IllegalArgumentException|java.io.IOException expected) { checks++; }
    }
    private static void check(boolean value) { if(!value)throw new AssertionError();checks++; }
    private static void store(Path file,Properties data) throws Exception {
        try(var writer=Files.newBufferedWriter(file)){data.store(writer,"Fixture inventory");}
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1||!Path.of(args[0]).isAbsolute())throw new IllegalArgumentException("Expected private fixture root");
        Path root=Files.createDirectory(Path.of(args[0]));
        Path engine=Files.createDirectories(root.resolve("target/jenesis-jvm/engine"));
        Path index=Files.createDirectories(root.resolve("target/jenesis-jvm/reports")).resolve("engine-results.properties");
        Files.writeString(root.resolve("pom.xml"),"<project><version>1</version></project>");
        Path inventory=Files.createDirectories(engine.resolve("unrelated-engine-layout/receipt"));
        Path jar=engine.resolve("product.jar"),pom=engine.resolve("pom.xml"),sources=engine.resolve("sources.jar");
        try(var archive=new JarOutputStream(Files.newOutputStream(jar))) { }
        Files.copy(jar,sources);Files.writeString(pom,"<project/>");
        var data=new Properties();String prefix="actual-engine-identity";
        data.setProperty(prefix+".path","interlis-model");data.setProperty(prefix+".artifact","interlis-model");
        data.setProperty(prefix+".identity.0","maven/guru.interlis/interlis-model/1");
        data.setProperty(prefix+".artifacts.0","../../product.jar");data.setProperty(prefix+".pom","../../pom.xml");
        data.setProperty(prefix+".runtime.0","../../product.jar");
        Path file=inventory.resolve("inventory.properties");store(file,data);
        var paths=Map.of("build/arbitrary/product/inventory",inventory);
        var outcome=new JenesisBridge.BuildOutcome(index,paths);
        var result=JenesisBridge.moduleResult(outcome,"interlis-model");
        check(result.jar().equals(jar));check(result.pom().equals(pom));check(result.sources().isEmpty());
        check(result.runtime().equals(List.of(jar)));check(result.moduleName().isEmpty());
        check(ModuleArtifacts.runtime(root,"interlis-model",outcome).getFirst().sha256().equals(JvmBuildInputs.sha256(jar)));
        rejects(()->JenesisBridge.runtimeModulePath("interlis-model",outcome),"decorative module mode");
        rejects(()->ModuleArtifacts.sources(outcome,"interlis-model"),"unselected sources");
        rejects(()->JenesisBridge.moduleResult(new JenesisBridge.BuildOutcome(index,Map.of()),"interlis-model"),"missing result");
        rejects(()->JenesisBridge.moduleResult(new JenesisBridge.BuildOutcome(index,Map.of("build/a/inventory",inventory,"build/b/inventory",inventory)),"interlis-model"),"ambiguous result");
        rejects(()->JenesisBridge.moduleResult(outcome,"foreign"),"unknown project");
        data.remove(prefix+".pom");store(file,data);
        rejects(()->JenesisBridge.moduleResult(outcome,"interlis-model"),"missing POM");
        data.setProperty(prefix+".pom","../../pom.xml");data.setProperty(prefix+".runtime.2","../../product.jar");store(file,data);
        rejects(()->JenesisBridge.runtimeClasspath("interlis-model",outcome),"runtime gap");
        data.remove(prefix+".runtime.2");data.setProperty(prefix+".runtime.1","../../product.jar");store(file,data);
        rejects(()->JenesisBridge.runtimeClasspath("interlis-model",outcome),"duplicate runtime entry");
        data.remove(prefix+".runtime.1");data.setProperty(prefix+".artifacts.1","../../product.jar");store(file,data);
        rejects(()->JenesisBridge.moduleResult(outcome,"interlis-model"),"ambiguous artifact");
        data.remove(prefix+".artifacts.1");data.setProperty(prefix+".identity.0","maven/guru.interlis/interlis-model/2");store(file,data);
        rejects(()->ModuleArtifacts.runtime(root,"interlis-model",outcome),"foreign own version");
        data.setProperty(prefix+".identity.0","maven/guru.interlis/interlis-model/1");store(file,data);
        Path foreign=engine.resolve("foreign.jar");Files.copy(jar,foreign);Files.write(foreign,new byte[]{1},StandardOpenOption.APPEND);
        data.setProperty(prefix+".runtime.0","../../foreign.jar");
        data.setProperty(prefix+".dependency.0","maven/guru.interlis/interlis-model/1 ../../foreign.jar SHA-256/"+JvmBuildInputs.sha256(foreign));
        store(file,data);
        rejects(()->ModuleArtifacts.runtime(root,"interlis-model",outcome),"changed own JAR with refreshed local hash");
        data.setProperty(prefix+".runtime.0","../../product.jar");data.remove(prefix+".dependency.0");store(file,data);
        Path outside=Files.createDirectory(root.resolve("outside"));Files.copy(jar,outside.resolve("jar"));
        Files.delete(jar);Files.createSymbolicLink(jar,outside.resolve("jar"));
        rejects(()->JenesisBridge.moduleResult(outcome,"interlis-model"),"escaped output symlink");
        Files.delete(jar);Files.copy(outside.resolve("jar"),jar);
        Files.createSymbolicLink(engine.resolve("escaped"),outside);
        rejects(()->JenesisBridge.moduleResult(new JenesisBridge.BuildOutcome(index,Map.of("build/a/inventory",engine.resolve("escaped"))),"interlis-model"),"escaped inventory symlink");
        Files.createDirectories(root.resolve("build/jenesis"));Files.writeString(root.resolve("build/jenesis/sentinel"),"source");
        Files.createDirectories(root.resolve("build/interlis"));Files.writeString(root.resolve("build/interlis/sentinel"),"source");
        Files.createDirectories(root.resolve("docs/contracts"));Files.writeString(root.resolve("docs/contracts/sentinel"),"contract");
        Path consumer=Files.createDirectories(root.resolve("target/jenesis-jvm/consumers"));Files.writeString(consumer.resolve("sentinel"),"consumer");
        BuildOutputs.clean(root,"jvm");check(!Files.exists(engine));check(Files.exists(consumer.resolve("sentinel")));
        check(Files.readString(root.resolve("build/jenesis/sentinel")).equals("source"));
        check(Files.readString(root.resolve("build/interlis/sentinel")).equals("source"));
        check(Files.readString(root.resolve("docs/contracts/sentinel")).equals("contract"));
        BuildOutputs.clean(root,"consumer");check(!Files.exists(consumer));check(Files.exists(outside.resolve("jar")));
        rejects(()->BuildOutputs.clean(root,"all"),"unbounded clean");
        Files.move(root.resolve("target"),root.resolve("saved-target"));Files.createSymbolicLink(root.resolve("target"),outside);
        rejects(()->JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm"),"output-root symlink");
        check(Files.isRegularFile(outside.resolve("jar")));
        Files.delete(root.resolve("target"));Files.move(root.resolve("saved-target"),root.resolve("target"));
        Path active=Files.createDirectories(root.resolve("target/jenesis-jvm/consumers/active"));
        try(var channel=java.nio.channels.FileChannel.open(root.resolve("target/jenesis-jvm/wasm-run.lock"),
                StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            rejects(()->BuildOutputs.clean(root,"consumer"),"active consumer clean");check(Files.isDirectory(active));
        }
        Path project=Path.of("").toAbsolutePath();
        try(var channel=java.nio.channels.FileChannel.open(project.resolve("target/jenesis-jvm/project.lock"),
                StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            long started=System.nanoTime();
            rejects(()->build.InterlisJvmBuild.main(new String[]{"artifacts","--module","interlis-model"}),"lock reentry");
            check(java.time.Duration.ofNanos(System.nanoTime()-started).toSeconds()<5);
        }
        System.out.println("BUILD_BOUNDARY_CHECKS="+checks);
    }
}
