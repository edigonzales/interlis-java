package guru.interlis.verification;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PlatformProcessesTest {
    @TempDir Path root;
    private Path report(String directory,String suite,String body) throws Exception {
        Path folder=Files.createDirectories(root.resolve(directory));
        return Files.writeString(folder.resolve("TEST-"+suite+".xml"),body);
    }
    static final String XML="<testsuite name=\"test.Example\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"test.Example\" name=\"sample()\"/></testsuite>";
    @Test void newDirectoriesAreRequiredAndCannotBeCountedTwice() throws Exception {
        var old=report("interlis-compiler-cli/build/test-results/verificationTest","test.Example",XML);
        var layout=new VerificationLayout(root);
        var directories=List.of(layout.verificationUnitReports(),layout.verificationContractReports());
        assertThrows(IllegalArgumentException.class,()->PlatformProcesses.inspectJUnitReports(root,directories));
        var unit=report("target/jenesis-jvm/reports/junit/interlis-verification/test","test.Example",XML);
        report("target/jenesis-jvm/reports/junit/interlis-verification/contractTest","test.Contract",XML.replace("test.Example","test.Contract"));
        var summary=PlatformProcesses.inspectJUnitReports(root,directories);
        assertEquals(2,summary.tests());assertEquals(2,summary.suites());assertEquals(2,summary.xmlHashes().size());
        assertFalse(summary.xmlHashes().containsKey(root.relativize(old).toString()));
        assertThrows(IllegalArgumentException.class,()->PlatformProcesses.inspectJUnitReports(root,List.of(unit.getParent(),unit.getParent())));
        var profile=new JsonObject();profile.addProperty("minimumTests",2);profile.addProperty("minimumSuites",2);
        profile.add("requiredDirectories",HostChecks.JSON.toJsonTree(directories.stream().map(root::relativize).map(Path::toString).map(s->s.replace('\\','/')).toList()));
        profile.add("requiredLogicalReports",HostChecks.JSON.toJsonTree(summary.logicalReports()));
        PlatformProcesses.verifyRequiredProfile(summary,profile);
        assertThrows(IllegalArgumentException.class,()->PlatformProcesses.verifyRequiredProfile(PlatformProcesses.inspectJUnitReports(root,List.of(unit.getParent(),old.getParent())),profile));
        Files.writeString(unit,XML.replace("sample()","changed()"));
        assertNotEquals(summary.xmlHashes(),PlatformProcesses.inspectJUnitReports(root,directories).xmlHashes());
    }
    @Test void malformedIncompleteAndManipulatedSuitesFail() throws Exception {
        Path dir=root.resolve("reports");
        var file=report("reports","test.Example",XML);
        var valid=PlatformProcesses.inspectJUnitReports(root,List.of(dir));assertEquals(1,valid.tests());
        for(String xml:List.of(XML.replace("tests=\"1\"","tests=\"2\""),XML.replace("failures=\"0\"","failures=\"1\""),
                XML.replace("skipped=\"0\"","skipped=\"1\""),XML.replace("classname=\"test.Example\"","classname=\"foreign\""),
                XML.replace("/>","><skipped/></testcase>"),XML.replace("<testcase classname=\"test.Example\" name=\"sample()\"/>",""),
                XML.replace("name=\"test.Example\"","name=\"foreign\""),"<!DOCTYPE testsuite SYSTEM 'file:///etc/passwd'>"+XML)) {
            Files.writeString(file,xml);assertThrows(Exception.class,()->PlatformProcesses.inspectJUnitReports(root,List.of(dir)));
        }
    }
    @Test void nativeContainersKeepAllLogicalCasesAndOriginalRawHashes() throws Exception {
        String first="<testcase classname=\"test.Example\" name=\"sample()\"><system-out>unique-id: [engine:junit-jupiter]/[class:test.Example]/[method:sample()]\ndisplay-name: sample()</system-out></testcase>";
        String second=first.replace("test.Example","test.Other");
        String nativeXml="<testsuite name=\"JUnit Jupiter\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\">"+first+second+"</testsuite>";
        Path file=report("native","junit-jupiter",nativeXml);var summary=PlatformProcesses.inspectJUnitReports(root,List.of(file.getParent()));
        assertEquals(2,summary.tests());assertEquals(2,summary.suites());assertEquals(1,summary.xmlHashes().size());
        assertEquals(Set.of("native/TEST-test.Example.xml","native/TEST-test.Other.xml"),summary.logicalReports());
        assertEquals(HostChecks.hash(file),summary.xmlHashes().get("native/TEST-junit-jupiter.xml"));
        for(String broken:List.of(nativeXml.replace("tests=\"2\"","tests=\"3\""),nativeXml.replace("unique-id: ","missing-id: "),nativeXml.replace(second,first),nativeXml.replace("[class:test.Other]","[class:foreign.Class]"))) {
            Files.writeString(file,broken);assertThrows(Exception.class,()->PlatformProcesses.inspectJUnitReports(root,List.of(file.getParent())));
        }
    }

}
