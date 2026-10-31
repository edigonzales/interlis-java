package guru.interlis.cli.verification;

import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipFile;
import static guru.interlis.cli.verification.HostChecks.*;

/** Executes a separate consumer using only the extracted delivered SDK. */
public final class SdkArchiveCheck {
    private SdkArchiveCheck() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),out=root.resolve("build/reports/sdk/archive-consumer.json");Files.deleteIfExists(out);
        var properties=new Properties();try(var reader=Files.newBufferedReader(root.resolve("gradle.properties"))){properties.load(reader);}
        Path archive=root.resolve("build/releases/interlis-java-sdk-"+properties.getProperty("projectVersion")+".zip");String archiveSha=hash(archive);
        Path temporary=Files.createTempDirectory("interlis SDK consumer ");var entries=new TreeMap<String,String>();
        try {
            try(var zip=new ZipFile(archive.toFile())) {
                for(var entry:zip.stream().toList()) {
                    String name=entry.getName();Path target=temporary.resolve(name).normalize();
                    require(!name.startsWith("/")&&!name.contains("\\")&&target.startsWith(temporary)&&!entries.containsKey(name),"Unsafe/duplicate SDK entry");
                    if(entry.isDirectory()){Files.createDirectories(target);continue;}
                    Files.createDirectories(target.getParent());try(var input=zip.getInputStream(entry)){Files.copy(input,target);}
                    entries.put(name,hash(target));
                }
            }
            var source=temporary.resolve("examples/sdk/SdkExample.java");require(Files.isRegularFile(source),"SDK example absent");
            Path lib=temporary.resolve("cli/lib");List<Path> jars;try(var files=Files.list(lib)){jars=files.filter(p->p.toString().endsWith(".jar")).sorted().toList();}
            require(jars.size()>=4&&jars.stream().noneMatch(p->p.getFileName().toString().matches(".*(junit|gson|test|teavm).*")),"Missing jars or verification/runtime dependency leak");
            String classpath=String.join(File.pathSeparator,jars.stream().map(Path::toString).toList());Path classes=temporary.resolve("consumer-classes");Files.createDirectories(classes);
            Path javac=Path.of(System.getProperty("java.home"),"bin","javac");
            var compilation=run(temporary,List.of(javac.toString(),"--release","25","-encoding","UTF-8","-cp",classpath,"-d",classes.toString(),source.toString()),Duration.ofSeconds(30));
            require(compilation.exitCode()==0,"Extracted SDK does not compile separate consumer: "+compilation);
            var executed=run(temporary,List.of(java().toString(),"-cp",classes+File.pathSeparator+classpath,"SdkExample"),Duration.ofSeconds(30));
            require(executed.exitCode()==0&&executed.stdout().startsWith("SDK PASS ")&&executed.stderr().isEmpty(),"Extracted SDK consumer failed: "+executed);
            boolean windows=System.getProperty("os.name").startsWith("Windows");Path launcher=temporary.resolve("cli/bin/interlis-cli"+(windows?".bat":""));
            require(windows||launcher.toFile().setExecutable(true,false),"Cannot restore executable permission after Java ZIP extraction");
            var version=run(temporary,List.of(launcher.toString(),"version","--json"),Duration.ofSeconds(30));require(version.exitCode()==0&&version.stderr().isEmpty(),"Extracted launcher failed");
            var shipped=read(temporary.resolve("version/version-manifest.json"));require(JsonParser.parseString(version.stdout()).equals(shipped),"Extracted SDK version manifest differs from actual launcher");SdkProcesses.version(shipped);
            require(hash(archive).equals(archiveSha),"SDK changed during separate consumer run");
            write(out,Map.of("status","passed","archiveSha256",archiveSha,"entries",entries,"consumerClasspath","Only extracted cli/lib jars; no repository classes/test dependencies","compile",compilation,"execute",executed,"launcher",version,"versions",shipped,"os",System.getProperty("os.name"),"arch",System.getProperty("os.arch")));
            System.out.println("SDK_ARCHIVE_CONSUMER_PASS "+archiveSha);
        }finally{try(var paths=Files.walk(temporary)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
