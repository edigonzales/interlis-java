package build.interlis;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit, private HotSpot capability gate; no installer or native compiler is invoked. */
public final class CompilerRuntimeCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Expected no arguments");
        run(Path.of("").toAbsolutePath().normalize());
    }

    public static void run(Path root)throws Exception {
        var outcome=JenesisBridge.artifacts(root);
        Path run=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/runtime-checks/"+UUID.randomUUID());
        Files.createDirectories(run);
        Path delivery=run.resolve("delivery"),image=run.resolve("runtime");
        ExistingJvmDistribution.generateAt(root,outcome,delivery);
        Path lib=delivery.resolve("compiler/lib");
        Path jdk=Path.of(System.getProperty("java.home"));
        var gate=new Gate(run);
        gate.process("jdeps",List.of(jdk.resolve("bin/jdeps").toString(),"--multi-release","25","--print-module-deps","--module-path",lib.toString(),"--module","guru.interlis.compiler.cli"),Map.of(),0);
        String detected=Files.readString(run.resolve("jdeps.stdout")).strip().lines().reduce((a,b)->b).orElseThrow();
        var system=java.lang.module.ModuleFinder.ofSystem().findAll().stream().map(r->r.descriptor().name()).collect(java.util.stream.Collectors.toSet());
        var modules=new TreeSet<String>(List.of("jdk.compiler","jdk.crypto.ec"));
        for(String module:detected.split(","))if(system.contains(module))modules.add(module);
        for(var reference:java.lang.module.ModuleFinder.of(lib).findAll())for(var dependency:reference.descriptor().requires())if(system.contains(dependency.name()))modules.add(dependency.name());
        gate.process("jlink",List.of(jdk.resolve("bin/jlink").toString(),"--module-path",jdk.resolve("jmods").toString(),"--add-modules",String.join(",",modules),"--no-header-files","--no-man-pages","--output",image.toString()),Map.of(),0);
        Path commands=Files.createDirectory(run.resolve("no-java-on-path"));
        if(!System.getProperty("os.name").startsWith("Windows"))for(String tool:List.of("uname","xargs","sed","tr","dirname")){Path executable=Path.of("/usr/bin",tool);if(!Files.isExecutable(executable))executable=Path.of("/bin",tool);Files.createSymbolicLink(commands.resolve(tool),executable);}
        var env=Map.of("JAVA_HOME",image.toString(),"PATH",commands.toString());
        String launcher=delivery.resolve("compiler/bin/interlis-compiler"+(System.getProperty("os.name").startsWith("Windows")?".bat":"")).toString();
        var cliJava=List.of(image.resolve("bin/java").toString(),"--module-path",lib.toString(),"--module","guru.interlis.compiler.cli/guru.interlis.cli.Main");
        gate.process("runtime-version",List.of(image.resolve("bin/java").toString(),"-version"),env,0);
        gate.process("runtime-modules",List.of(image.resolve("bin/java").toString(),"--list-modules"),env,0);
        gate.process("version",List.of(launcher,"version","--json"),env,0);
        if(!Files.readString(run.resolve("version.stdout")).strip().startsWith("{"))throw new IllegalStateException("Missing JSON version");
        Path valid=run.resolve("Grüezi Modell.ili"),invalid=run.resolve("Ungültig.ili");
        Files.writeString(valid,"INTERLIS 2.4; MODEL Gruezi AT \"x\" VERSION \"1\"=DOMAIN Name=TEXT*20;END Gruezi.");
        Files.writeString(invalid,"INTERLIS 2.4; MODEL Broken AT \"x\" VERSION \"1\"=DOMAIN Name=;END Broken.");
        gate.process("valid",List.of(launcher,"check",valid.toString()),env,0);
        gate.process("invalid",List.of(launcher,"check",invalid.toString()),env,1);
        gate.process("format",List.of(launcher,"format",valid.toString()),env,0);
        if(!Files.readString(run.resolve("format.stdout")).contains("Gruezi"))throw new IllegalStateException("Formatting lost Unicode");
        gate.process("missing-module",List.of(image.resolve("bin/java").toString(),"--module-path",lib.toString(),"--module","guru.interlis.missing/guru.interlis.cli.Main"),env,1);
        Path incomplete=Files.createDirectory(run.resolve("incomplete-lib"));
        try(var jars=Files.list(lib)){for(Path jar:jars.toList())if(!jar.getFileName().toString().startsWith("interlis-model-"))Files.copy(jar,incomplete.resolve(jar.getFileName()));}
        gate.process("missing-dependency",List.of(image.resolve("bin/java").toString(),"--module-path",incomplete.toString(),"--module","guru.interlis.compiler.cli/guru.interlis.cli.Main","version"),env,1);
        if(!Files.readString(run.resolve("missing-dependency.stdout")).contains("guru.interlis.model"))throw new IllegalStateException("Unexpected graph failure");
        Path classes=Files.createDirectory(run.resolve("sdk-classes"));
        gate.process("sdk-compile",List.of(image.resolve("bin/javac").toString(),"--module-path",lib.toString(),"--add-modules","guru.interlis.compiler","-d",classes.toString(),root.resolve("examples/sdk/SdkExample.java").toString()),env,0);
        gate.process("sdk-run",List.of(image.resolve("bin/java").toString(),"--module-path",lib.toString(),"--add-modules","guru.interlis.compiler","-cp",classes.toString(),"SdkExample"),env,0);
        // Trust is scoped to this private fixture. No global store or insecure TLS option.
        Path store=run.resolve("localhost.p12");String password="interlis-runtime-test";
        gate.process("certificate",List.of(jdk.resolve("bin/keytool").toString(),"-genkeypair","-alias","localhost","-keyalg","RSA","-dname","CN=localhost","-ext","SAN=ip:127.0.0.1,dns:localhost","-validity","2","-storetype","PKCS12","-keystore",store.toString(),"-storepass",password,"-keypass",password,"-noprompt"),Map.of(),0);
        var keys=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(store)){keys.load(input,password.toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,password.toCharArray());
        var ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),null,null);
        for(boolean tls:List.of(false,true)) {
            var requests=new AtomicInteger();
            HttpServer server=tls?HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0):HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            if(tls)((HttpsServer)server).setHttpsConfigurator(new HttpsConfigurator(ssl));
            server.createContext("/",exchange->{requests.incrementAndGet();String body=exchange.getRequestURI().getPath().equals("/ilimodels.xml")?"<TRANSFER><ModelMetadata><Name>Remote</Name><SchemaLanguage>ili2_4</SchemaLanguage><File>Remote.ili</File><Version>1</Version></ModelMetadata></TRANSFER>":"INTERLIS 2.4; MODEL Remote AT \"x\" VERSION \"1\"=DOMAIN Name=TEXT*20;END Remote.";byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var stream=exchange.getResponseBody()){stream.write(bytes);}});
            server.start();try {
                var command=new ArrayList<String>();command.add(cliJava.getFirst());
                if(tls){command.add("-Djavax.net.ssl.trustStore="+store);command.add("-Djavax.net.ssl.trustStorePassword="+password);}
                command.addAll(cliJava.subList(1,cliJava.size()));command.addAll(List.of("check","--repository",(tls?"https":"http")+"://127.0.0.1:"+server.getAddress().getPort()+"/","--root","Remote"));
                gate.process(tls?"https":"http",command,env,0);
                if(requests.get()<2)throw new IllegalStateException("Repository was not fetched");
                Files.writeString(run.resolve((tls?"https":"http")+".requests"),""+requests.get());
            }finally{server.stop(0);}
        }
        // Standard-JDK capability probe only; never installed, signed or embedded in VSIX.
        Path app=run.resolve("app-image");
        gate.process("jpackage",List.of(jdk.resolve("bin/jpackage").toString(),"--type","app-image","--name","InterlisCompiler","--app-version","1.0","--dest",app.toString(),"--runtime-image",image.toString(),"--module-path",lib.toString(),"--module","guru.interlis.compiler.cli/guru.interlis.cli.Main"),Map.of(),0);
        String appLauncher=System.getProperty("os.name").startsWith("Mac")?"InterlisCompiler.app/Contents/MacOS/InterlisCompiler":System.getProperty("os.name").startsWith("Windows")?"InterlisCompiler/InterlisCompiler.exe":"InterlisCompiler/bin/InterlisCompiler";
        gate.process("app-image-version",List.of(app.resolve(appLauncher).toString(),"version","--json"),Map.of("JAVA_HOME",run.resolve("foreign-home").toString(),"PATH",env.get("PATH")),0);
        if(!Files.isRegularFile(image.resolve("legal/java.base/LICENSE")))throw new IllegalStateException("Missing runtime license");
        var inventory=new TreeMap<String,String>();try(var paths=Files.walk(image)){for(Path file:paths.filter(Files::isRegularFile).sorted().toList())inventory.put(image.relativize(file).toString(),JvmBuildInputs.sha256(file));}
        Files.writeString(run.resolve("result.json"),JsonOutput.write(Map.of("status","passed","jdkHome",jdk.toString(),"jdkReleaseSha256",JvmBuildInputs.sha256(jdk.resolve("release")),"jdkModules",modules,"dynamicModuleReasons",Map.of("jdk.crypto.ec","HTTPS cryptographic provider","jdk.compiler","SDK javac consumer"),"bindServices",false,"runtimeFiles",inventory,"appImageCapability",true,"productionFormatSelected",false,"engineInventorySha256",JvmBuildInputs.sha256(JenesisBridge.moduleResult(outcome, "interlis-compiler-cli").inventoryDirectory().resolve("inventory.properties")))));
        System.out.println("COMPILER_RUNTIME_CHECK_PASS "+run);
    }
    private record Gate(Path run) {
        void process(String role,List<String> command,Map<String,String> env,int expected)throws Exception {
            Files.writeString(run.resolve(role+".argv.json"),JsonOutput.write(command));Files.writeString(run.resolve(role+".env.json"),JsonOutput.write(env));
            var builder=new ProcessBuilder(command).directory(run.toFile()).redirectOutput(run.resolve(role+".stdout").toFile()).redirectError(run.resolve(role+".stderr").toFile());builder.environment().putAll(env);
            // Ambient JVM injection must not invalidate the runtime provenance.
            for(String key:List.of("JDK_JAVA_OPTIONS","JAVA_TOOL_OPTIONS","_JAVA_OPTIONS"))builder.environment().remove(key);
            var process=builder.start();try{boolean done=process.waitFor(180,TimeUnit.SECONDS);Files.writeString(run.resolve(role+".process.json"),JsonOutput.write(Map.of("timedOut",!done,"exitCode",done?process.exitValue():-1,"expected",expected)));if(!done||process.exitValue()!=expected)throw new IllegalStateException("Runtime gate failed: "+run.resolve(role));}finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        }
    }
}
