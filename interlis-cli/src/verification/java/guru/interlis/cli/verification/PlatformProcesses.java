package guru.interlis.cli.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static guru.interlis.cli.verification.HostChecks.*;

/** Binds actual local JVM/distribution process reports to one exact candidate. */
public final class PlatformProcesses {
    private PlatformProcesses() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),out=root.resolve("build/reports/p3/platform/result.json");Files.deleteIfExists(out);
        var directories=new ArrayList<String>();
        for(String module:List.of("interlis-model","interlis-compiler","interlis-cli","interlis-lsp","interlis-projection","interlis-repository","interlis-workspace","interlis-wasm-bridge"))directories.add(module+"/build/test-results/test");
        directories.addAll(List.of("interlis-compiler/build/test-results/g2AssertionTest","interlis-cli/build/test-results/evidenceTest","interlis-cli/build/test-results/verificationTest"));
        var parser=DocumentBuilderFactory.newInstance();parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);parser.setXIncludeAware(false);parser.setExpandEntityReferences(false);
        long tests=0;int suites=0;var xmlHashes=new TreeMap<String,String>();
        for(String directory:directories) {
            Path folder=root.resolve(directory);require(Files.isDirectory(folder),"Missing required JVM suite: "+directory);List<Path> xmls;
            try(var files=Files.list(folder)){xmls=files.filter(p->p.toString().endsWith(".xml")).sorted().toList();}
            require(!xmls.isEmpty(),"No tests executed in "+directory);
            for(var file:xmls) {
                var suite=parser.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                require(suite.getTagName().equals("testsuite"),"Unexpected JUnit report");
                int count=Integer.parseInt(suite.getAttribute("tests"));require(count>0,"Empty required test suite");
                for(String field:List.of("failures","errors","skipped"))require(Integer.parseInt(suite.getAttribute(field))==0,"Unsuccessful/skipped JVM suite: "+file);
                tests+=count;suites++;xmlHashes.put(root.relativize(file).toString().replace('\\','/'),hash(file));
            }
        }
        require(tests>=1186,"Incomplete current JVM profile");
        var sdk=read(root.resolve("build/reports/sdk/sdk-process-comparison.json"));var archive=read(root.resolve("build/reports/sdk/archive-consumer.json"));var legacy=read(root.resolve("build/reports/p3/legacy/cli-comparison.json"));
        require(s(sdk,"status").equals("passed")&&rows(sdk,"commands").size()==9,"Missing SDK processes");
        require(s(archive,"status").equals("passed")&&s(legacy,"status").equals("passed")&&rows(legacy,"cases").size()>=50,"Missing installed consumer/legacy CLI processes");
        var versions=read(root.resolve("build/reports/sdk/version-manifest.json"));SdkProcesses.version(versions);
        var report=new JsonObject();report.addProperty("schemaVersion","1.0");report.addProperty("profile","P3-jvm-distribution-v1");report.addProperty("status","passed");report.addProperty("gitCommit",ProfileFingerprint.commit(root));report.addProperty("dirty",ProfileFingerprint.dirty(root));report.addProperty("sourceFingerprint",ProfileFingerprint.current(root));
        report.addProperty("os",System.getProperty("os.name"));report.addProperty("arch",System.getProperty("os.arch"));report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("tests",tests);report.addProperty("suites",suites);report.addProperty("failures",0);report.addProperty("errors",0);report.addProperty("skipped",0);report.add("xmlHashes",JSON.toJsonTree(xmlHashes));
        report.add("versions",versions);report.addProperty("sdkCommands",rows(sdk,"commands").size());report.addProperty("legacyCases",rows(legacy,"cases").size());report.addProperty("sdkArchiveSha256",s(archive,"archiveSha256"));report.add("installedJars",JSON.toJsonTree(artifacts(root)));
        write(out,report);System.out.println("PLATFORM_PROCESSES_PASS "+System.getProperty("os.name")+"/"+System.getProperty("os.arch")+" tests="+tests+" sdkArchive="+s(archive,"archiveSha256"));
    }
}
