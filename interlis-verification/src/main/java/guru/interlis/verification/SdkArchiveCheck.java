package guru.interlis.verification;

import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipFile;
import static guru.interlis.verification.HostChecks.*;

/** Executes a separate consumer using only the extracted delivered SDK. */
public final class SdkArchiveCheck {
    private SdkArchiveCheck() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),out=VerificationLayout.forInvocation(root).rootReport("sdk/archive-consumer.json");Files.deleteIfExists(out);
        String productVersion=projectVersion(root);
        Path archive=VerificationLayout.forInvocation(root).sdkArchive(productVersion);String archiveSha=hash(archive);
        boolean windows=System.getProperty("os.name").startsWith("Windows");
        Path temporary=Files.createTempDirectory("interlis SDK consumer ");var entries=new TreeMap<String,String>();
        try {
            try(var zip=new ZipFile(archive.toFile())) {
                for(var entry:zip.stream().toList()) {
                    String name=entry.getName();
                    require(!Set.of("cli/bin/interlis-cli", "cli/bin/interlis-cli.bat", "cli/bin/ili2c", "cli/bin/ili2c.bat", "cli/compat/ili2c.jar").contains(name), "Forbidden legacy SDK entry: " + name);
                    Path target=temporary.resolve(name).normalize();
                    require(!name.startsWith("/")&&!name.contains("\\")&&target.startsWith(temporary)&&!entries.containsKey(name),"Unsafe/duplicate SDK entry");
                    if(entry.isDirectory()){Files.createDirectories(target);continue;}
                    Files.createDirectories(target.getParent());try(var input=zip.getInputStream(entry)){if(windows) Files.copy(input,target); else entries.put(name,hash(input.readAllBytes()));}
                    if(windows) entries.put(name,hash(target));
                }
            }
            if (!windows) {
                // Native ZIP extraction preserves the delivered Unix modes. Never repair them.
                var extraction=run(temporary,List.of("/usr/bin/unzip","-q",archive.toString(),"-d",temporary.toString()),Duration.ofSeconds(30));
                require(extraction.exitCode()==0,"Native SDK extraction failed: "+extraction);
                for(var entry:entries.entrySet()) {
                    Path file=temporary.resolve(entry.getKey());
                    require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && hash(file).equals(entry.getValue()),"Extracted SDK entry differs: "+entry.getKey());
                }
            }
            for (String name : List.of("cli/bin/interlis-compiler", "cli/bin/interlis-compiler.bat",
                    "cli/lib/interlis-compiler-cli-" + productVersion + ".jar",
                    "cli/lib/interlis-compiler-" + productVersion + ".jar"))
                require(entries.containsKey(name), "Missing compiler SDK entry: " + name);
            var source=temporary.resolve("examples/sdk/SdkExample.java");require(Files.isRegularFile(source),"SDK example absent");
            Path lib=temporary.resolve("cli/lib");List<Path> jars;try(var files=Files.list(lib)){jars=files.filter(p->p.toString().endsWith(".jar")).sorted().toList();}
            require(jars.size()>=4&&jars.stream().noneMatch(p->p.getFileName().toString().matches(".*(junit|gson|test|teavm|antlr4-[0-9]|antlr-runtime|ST4|treelayout|icu4j).*")),"Missing jars or verification/runtime dependency leak");
            String classpath=String.join(File.pathSeparator,jars.stream().map(Path::toString).toList());Path classes=temporary.resolve("consumer-classes");Files.createDirectories(classes);
            Path javac=Path.of(System.getProperty("java.home"),"bin","javac");
            var compilation=run(temporary,List.of(javac.toString(),"--release","25","-encoding","UTF-8","-cp",classpath,"-d",classes.toString(),source.toString()),Duration.ofSeconds(30));
            require(compilation.exitCode()==0,"Extracted SDK does not compile separate consumer: "+compilation);
            var executed=run(temporary,List.of(java().toString(),"-cp",classes+File.pathSeparator+classpath,"SdkExample"),Duration.ofSeconds(30));
            require(executed.exitCode()==0&&executed.stdout().startsWith("SDK PASS ")&&executed.stderr().isEmpty(),"Extracted SDK consumer failed: "+executed);
            Path launcher=temporary.resolve("cli/bin/interlis-compiler"+(windows?".bat":""));
            require(windows||Files.isExecutable(launcher),"Delivered SDK Unix launcher is not executable");
            var version=run(temporary,List.of(launcher.toString(),"version","--json"),Duration.ofSeconds(30));require(version.exitCode()==0&&version.stderr().isEmpty(),"Extracted launcher failed");
            var shipped=read(temporary.resolve("version/version-manifest.json"));require(JsonParser.parseString(version.stdout()).equals(shipped),"Extracted SDK version manifest differs from actual launcher");SdkProcesses.version(shipped);
            require(hash(archive).equals(archiveSha),"SDK changed during separate consumer run");
            write(out,Map.of("status","passed","archiveSha256",archiveSha,"entries",entries,"consumerClasspath","Only extracted cli/lib jars; no repository classes/test dependencies","compile",compilation,"execute",executed,"launcher",version,"versions",shipped,"os",System.getProperty("os.name"),"arch",System.getProperty("os.arch")));
            System.out.println("SDK_ARCHIVE_CONSUMER_PASS "+archiveSha);
        }finally{try(var paths=Files.walk(temporary)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
