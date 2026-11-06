package guru.interlis.verification;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.verification.TestReportInventory.*;

class TestReportInventoryTest {
    @TempDir Path temporary;
    private final TestReportInventory reader=new TestReportInventory();
    private String testcase(int index,String outcome) {
        return "<testcase classname=\"example.ProbeTest\" name=\"parameter(String)["+index+"]\">"+outcome+"<system-out><![CDATA[\nunique-id: [engine:junit-jupiter]/[class:example.ProbeTest]/[test-template:parameter(java.lang.String)]/[test-template-invocation:#"+index+"]\ndisplay-name: parameter(String) ["+index+"] value\n]]></system-out></testcase>";
    }
    private String suite(String cases,int count,int failures,int errors,int skipped) {
        return "<testsuite name=\"JUnit Jupiter\" tests=\""+count+"\" failures=\""+failures+"\" errors=\""+errors+"\" skipped=\""+skipped+"\">"+cases+"</testsuite>";
    }
    private Path report(String xml)throws Exception {Path dir=Files.createTempDirectory(temporary,"raw-");Files.writeString(dir.resolve("TEST-jupiter.xml"),xml);return dir;}
    private ExecutionReceipt receipt(Path dir)throws Exception {
        Map<String,String> hashes=new TreeMap<>();try(var files=Files.list(dir)){for(Path p:files.toList())hashes.put(p.getFileName().toString(),HostChecks.hash(p));}
        return new ExecutionReceipt("interlis-compiler","jenesis","fixture-run",Instant.parse("2026-11-04T12:00:00Z"),0,"EXECUTED",hashes,Map.of());
    }
    @Test void nativeParametersRemainDistinctAndProjectionIsBoundToBothByteSets()throws Exception {
        Path raw=report(suite(testcase(1,"")+testcase(2,""),2,0,0,0));var actual=reader.readJenesis(raw,receipt(raw));reader.requireSuccessful(actual);
        var selection=new TestSelection(new HashSet<>(actual.cases().stream().map(TestCase::identity).toList()));reader.requireCompleteSelection(actual,selection);
        assertEquals(Set.of("1","2"),actual.cases().stream().map(c->c.identity().invocation()).collect(java.util.stream.Collectors.toSet()));
        var projected=reader.projectPerSuite(actual,temporary.resolve("projected"));reader.verifyRawBindings(projected);
        assertTrue(Files.readString(projected.projectedHashes().keySet().iterator().next()).contains("parameter(String) [2] value"));
        Files.writeString(raw.resolve("TEST-jupiter.xml"),"changed");assertThrows(IllegalArgumentException.class,()->reader.verifyRawBindings(projected));
    }
    @Test void losingAnInvocationCannotBeHiddenByCorrectingTotals()throws Exception {
        Path raw=report(suite(testcase(1,"")+testcase(2,""),2,0,0,0));var original=reader.readJenesis(raw,receipt(raw));
        var expected=new TestSelection(new HashSet<>(original.cases().stream().map(TestCase::identity).toList()));reader.requireCompleteSelection(original,expected);
        Files.writeString(raw.resolve("TEST-jupiter.xml"),suite(testcase(1,""),1,0,0,0));var lost=reader.readJenesis(raw,receipt(raw));
        assertThrows(IllegalArgumentException.class,()->reader.requireCompleteSelection(lost,expected));
    }
    @Test void failureSkipAndCounterContradictionsAreNeverNormalizedToSuccess()throws Exception {
        for(String outcome:List.of("<failure message=\"broken\">trace</failure>","<error/>","<skipped/>")) {
            int failure=outcome.contains("failure")?1:0,error=outcome.contains("error")?1:0,skip=outcome.contains("skipped")?1:0;
            Path raw=report(suite(testcase(1,outcome),1,failure,error,skip));var actual=reader.readJenesis(raw,receipt(raw));
            assertThrows(IllegalArgumentException.class,()->reader.requireSuccessful(actual));
            var projection=reader.projectPerSuite(actual,Files.createTempDirectory(temporary,"projected-"));reader.verifyRawBindings(projection);
            assertFalse(actual.cases().getFirst().outcome().equals("PASSED"));
            Files.writeString(raw.resolve("TEST-jupiter.xml"),suite(testcase(1,outcome),1,0,0,0));assertThrows(IllegalArgumentException.class,()->reader.readJenesis(raw,receipt(raw)));
        }
    }
    @Test void duplicateEmptyUnboundDtdAndConflictingIdentitiesAreRejected()throws Exception {
        Path control=report(suite(testcase(1,""),1,0,0,0));reader.requireSuccessful(reader.readJenesis(control,receipt(control)));
        for(String body:List.of(suite(testcase(1,"")+testcase(1,""),2,0,0,0),suite("",0,0,0,0),
                "<!DOCTYPE testsuite [<!ENTITY x SYSTEM 'file:///not-read'>]>"+suite(testcase(1,""),1,0,0,0),
                suite(testcase(1,"").replace("parameter(String)[1]","other(String)[1]"),1,0,0,0))) {
            Path raw=report(body);assertThrows(Exception.class,()->reader.readJenesis(raw,receipt(raw)));
        }
        var bound=receipt(control);Files.writeString(control.resolve("TEST-extra.xml"),suite(testcase(2,""),1,0,0,0));
        assertThrows(IllegalArgumentException.class,()->reader.readJenesis(control,bound));
    }
    @Test void modifiedProjectionAndUnsuccessfulProcessAreRejected()throws Exception {
        Path raw=report(suite(testcase(1,""),1,0,0,0));var actual=reader.readJenesis(raw,receipt(raw));var projected=reader.projectPerSuite(actual,temporary.resolve("derived"));reader.verifyRawBindings(projected);
        Files.writeString(projected.projectedHashes().keySet().iterator().next(),"changed");assertThrows(IllegalArgumentException.class,()->reader.verifyRawBindings(projected));
        var r=actual.receipt();var failed=new ExecutionReceipt(r.module(),r.builder(),r.runId(),r.started(),1,r.execution(),r.reportHashes(),r.requiredProperties());
        assertThrows(IllegalArgumentException.class,()->reader.requireSuccessful(new TestInventory(failed,actual.cases(),actual.rawBindings())));
    }
}
