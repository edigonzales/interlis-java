package guru.interlis.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static guru.interlis.verification.HostChecks.*;

/** Binds actual local JVM/distribution process reports to one exact candidate. */
public final class PlatformProcesses {
    private PlatformProcesses() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),out=VerificationLayout.forInvocation(root).rootReport("platform/result.json");Files.deleteIfExists(out);
        var profile=read(root.resolve("testdata/verification/jvm-profile.json"));
        Path reportRoot=VerificationLayout.forInvocation(root).rootReport("jvm").getParent();
        var directories=strings(profile.get("requiredDirectories")).stream().map(reportRoot::resolve).toList();
        var summary=inspectJUnitReports(reportRoot,directories);
        verifyRequiredProfile(summary,profile);
        long tests=summary.tests();int suites=summary.suites();var xmlHashes=summary.xmlHashes();
        var sdk=read(VerificationLayout.forInvocation(root).rootReport("sdk/sdk-process-comparison.json"));var archive=read(VerificationLayout.forInvocation(root).rootReport("sdk/archive-consumer.json"));var cli=read(VerificationLayout.forInvocation(root).rootReport("release/compiler-cli/processes.json"));
        require(s(sdk,"status").equals("passed")&&rows(sdk,"commands").size()==9,"Missing SDK processes");
        require(s(archive,"status").equals("passed")&&s(cli,"status").equals("passed")&&cli.getAsJsonObject("cases").keySet().equals(CompilerCliProcesses.REQUIRED),"Missing installed consumer/standard CLI processes");
        require(s(cli,"profile").equals("compiler-cli/v1") && s(cli,"gitCommit").equals(ProfileFingerprint.commit(root))
                && s(cli,"sourceFingerprint").equals(ProfileFingerprint.current(root)), "Stale/foreign standard CLI process report");
        var colorCases=new LinkedHashMap<String,Result>();
        cli.getAsJsonObject("cases").entrySet().forEach(e -> colorCases.put(e.getKey(),JSON.fromJson(e.getValue(),Result.class)));
        CompilerCliProcesses.verifyColorCases(colorCases);
        var versions=read(VerificationLayout.forInvocation(root).rootReport("sdk/version-manifest.json"));SdkProcesses.version(versions);
        var report=new JsonObject();report.addProperty("schemaVersion","2.0");report.addProperty("reportRoot",root.relativize(reportRoot).toString().replace('\\','/'));report.addProperty("profile",s(profile,"profile"));report.addProperty("status","passed");report.addProperty("gitCommit",ProfileFingerprint.commit(root));report.addProperty("dirty",ProfileFingerprint.dirty(root));report.addProperty("sourceFingerprint",ProfileFingerprint.current(root));
        report.addProperty("os",System.getProperty("os.name"));report.addProperty("arch",System.getProperty("os.arch"));report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("tests",tests);report.addProperty("suites",suites);report.addProperty("failures",0);report.addProperty("errors",0);report.addProperty("skipped",0);report.add("xmlHashes",JSON.toJsonTree(xmlHashes));report.addProperty("rawReportFiles",xmlHashes.size());report.add("logicalReports",JSON.toJsonTree(new TreeSet<>(summary.logicalReports())));
        report.addProperty("compilerCliColorVerified",true);report.add("versions",versions);report.addProperty("sdkCommands",rows(sdk,"commands").size());report.add("compilerCliCases",JSON.toJsonTree(new TreeSet<>(cli.getAsJsonObject("cases").keySet())));report.addProperty("sdkArchiveSha256",s(archive,"archiveSha256"));report.add("installedJars",JSON.toJsonTree(artifacts(root)));
        write(out,report);System.out.println("PLATFORM_PROCESSES_PASS "+System.getProperty("os.name")+"/"+System.getProperty("os.arch")+" tests="+tests+" sdkArchive="+s(archive,"archiveSha256"));
    }

    record JvmReportSummary(long tests,int suites,Map<String,String> xmlHashes,Set<String> logicalReports) {
        JvmReportSummary { xmlHashes=Map.copyOf(xmlHashes);logicalReports=Set.copyOf(logicalReports); }
    }
    /** Counts actual cases and logical classes independently of the native JUnit XML container shape. */
    static JvmReportSummary inspectJUnitReports(Path root,List<Path> requiredDirectories) throws Exception {
        root=root.toAbsolutePath().normalize();
        require(!requiredDirectories.isEmpty(),"No required JVM directories");
        var directories=new HashSet<Path>();var identities=new HashSet<String>();
        var logical=new TreeSet<String>();long tests=0;var hashes=new TreeMap<String,String>();
        var parser=DocumentBuilderFactory.newInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        parser.setFeature("http://xml.org/sax/features/external-general-entities",false);
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        parser.setXIncludeAware(false);parser.setExpandEntityReferences(false);
        Map<String,String> engines=Map.of("TEST-junit-jupiter.xml","JUnit Jupiter","TEST-junit-platform-suite.xml","JUnit Platform Suite","TEST-junit-vintage.xml","JUnit Vintage");
        for(Path input:requiredDirectories) {
            Path folder=input.toAbsolutePath().normalize();
            require(folder.startsWith(root) && Files.isDirectory(folder) && directories.add(folder.toRealPath()),"Missing/duplicate JVM directory: "+folder);
            require(folder.toRealPath().startsWith(root.toRealPath()),"JVM directory escapes root");
            List<Path> xmls;
            try(var files=Files.list(folder)){xmls=files.filter(f->f.toString().endsWith(".xml")).sorted().toList();}
            require(!xmls.isEmpty(),"No tests executed in "+folder);long folderTests=0;
            String directory=root.relativize(folder).toString().replace('\\','/');
            for(Path file:xmls) {
                require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Not a regular XML report");
                byte[] bytes=Files.readAllBytes(file);String sha=hash(bytes);
                var suite=parser.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes)).getDocumentElement();
                String name=suite.getAttribute("name"),filename=file.getFileName().toString();
                boolean nativeReport=engines.containsKey(filename);
                require(suite.getTagName().equals("testsuite")&&!name.isBlank() &&
                    (nativeReport?name.equals(engines.get(filename)):filename.equals("TEST-"+name+".xml")),"Unexpected JUnit suite identity");
                int count=Integer.parseInt(suite.getAttribute("tests"));
                var cases=suite.getElementsByTagName("testcase");require(count>=0 && (nativeReport||count>0) && count==cases.getLength(),"Incomplete JVM testcase details");
                for(String field:List.of("failures","errors","skipped"))require(suite.getAttribute(field).equals("0"),"Unsuccessful/skipped JVM suite");
                for(int i=0;i<cases.getLength();i++) {
                    var test=(org.w3c.dom.Element)cases.item(i);String clazz=test.getAttribute("classname"),display=test.getAttribute("name");
                    require(test.getParentNode()==suite && clazz.matches("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*") && !display.isBlank() && (nativeReport||name.equals(clazz)),"Wrong/missing JUnit testcase");
                    String identity=display;
                    if(nativeReport) {
                        String output="";for(int j=0;j<test.getElementsByTagName("system-out").getLength();j++)output+=test.getElementsByTagName("system-out").item(j).getTextContent();
                        var ids=output.lines().filter(line->line.startsWith("unique-id: ")).toList();
                        require(ids.size()==1 && ids.getFirst().contains("[class:"+clazz+"]"),"Missing/foreign native invocation identity");identity=ids.getFirst();
                    }
                    require(identities.add(directory+"\0"+clazz+"\0"+identity),"Duplicate JVM testcase");
                    for(String kind:List.of("failure","error","skipped"))require(test.getElementsByTagName(kind).getLength()==0,"Unsuccessful JVM testcase");
                    logical.add(directory+"/TEST-"+clazz+".xml");
                }
                require(hash(file).equals(sha),"JVM report changed during validation");
                tests+=count;folderTests+=count;hashes.put(directory+"/"+filename,sha);
            }
            require(folderTests>0,"Empty required JVM report directory");
        }
        return new JvmReportSummary(tests,logical.size(),hashes,logical);
    }
    static void verifyRequiredProfile(JvmReportSummary summary,JsonObject profile) {
        require(summary.tests()>=profile.get("minimumTests").getAsLong()&&summary.suites()>=profile.get("minimumSuites").getAsInt(),"Incomplete current JVM profile");
        var directories=new HashSet<String>();
        for(String file:summary.xmlHashes().keySet()) {
            int slash=file.lastIndexOf('/');require(slash>0,"Invalid JVM XML path");directories.add(file.substring(0,slash));
        }
        require(directories.equals(new HashSet<>(strings(profile.get("requiredDirectories")))),"Foreign/missing JVM report directory");
        require(summary.logicalReports().containsAll(strings(profile.get("requiredLogicalReports"))),"Missing current required logical suite");
    }
}
