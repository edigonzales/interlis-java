package guru.interlis.cli.evidence;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.cli.evidence.EvidenceFiles.*;

class RuntimeReportsTest {
    @ParameterizedTest @ValueSource(strings={"reference","project","teaVmJavaScript","teaVmWasmGC","webImageNode","safari","webImageBrowsers","sdkProcesses"})
    void eightEmptyGreenReportVariantsFail(String key) throws Exception {
        JsonElement value=JsonParser.parseString(key.equals("webImageBrowsers")?"[{\"browser\":\"chromium\",\"status\":\"passed\",\"result\":{\"status\":\"ok\"}},{\"browser\":\"firefox\",\"status\":\"passed\",\"result\":{\"status\":\"ok\"}},{\"browser\":\"webkit\",\"status\":\"passed\",\"result\":{\"status\":\"ok\"}}]":"{\"status\":\"passed\",\"overallResult\":\"PASS\",\"schemaVersion\":\"1.0\",\"commands\":[]}");
        assertThrows(Exception.class,()->RuntimeReports.report(key,value,json("testdata/g2/a1-evidence-profile.json")));
    }
    private JsonObject browser(JsonObject profile) {
        var row=JsonParser.parseString("{\"status\":\"ok\",\"analyzeRevision\":\"1\",\"compiledOutcome\":\"succeeded\",\"rejectedOutcome\":\"rejected\",\"workerRestartOutcome\":\"succeeded\",\"exactDecimal\":true,\"diagnostics\":0,\"diagramNodes\":2,\"diagramEdges\":0,\"diagramAssociations\":0}").getAsJsonObject();
        row.add("stabilizationCases",profile.get("portableCases"));row.add("capabilities",profile.get("projectVersion"));row.addProperty("outcome","succeeded");return row;
    }
    @Test void completePortableAndBrowserFormatPassesWithExplicitHistoricalProfile() throws Exception {
        var profile=json("testdata/g2/a1-evidence-profile.json");
        var runtime=JsonParser.parseString("{\"status\":\"passed\",\"runtime\":\"teavm-wasm-gc\",\"outcome\":\"succeeded\"}").getAsJsonObject();
        runtime.add("node",profile.get("nodeVersion"));runtime.add("tool",profile.get("projectVersion"));runtime.add("stabilizationCases",profile.get("portableCases"));
        RuntimeReports.report("teaVmWasmGC",runtime,profile); RuntimeReports.portable(browser(profile),profile,true);
        for(int count:new int[]{0,n(profile,"portableCases")-1}) {runtime.addProperty("stabilizationCases",count);assertThrows(Exception.class,()->RuntimeReports.report("teaVmWasmGC",runtime,profile));}
    }
    @ParameterizedTest @ValueSource(strings={"exactDecimal","diagnostics","diagramNodes","diagramEdges","diagramAssociations","analyzeRevision","compiledOutcome","rejectedOutcome","workerRestartOutcome","stabilizationCases"})
    void missingOrWrongBrowserPayloadFails(String key) throws Exception {
        var profile=json("testdata/g2/a1-evidence-profile.json");var row=browser(profile);
        if(key.equals("exactDecimal"))row.addProperty(key,false);else if(row.get(key).getAsJsonPrimitive().isNumber())row.addProperty(key,999);else row.addProperty(key,"broken");
        assertThrows(Exception.class,()->RuntimeReports.portable(row,profile,true));
    }
    @ParameterizedTest @ValueSource(strings={"[]","[{\"browser\":\"chromium\"}]","[{\"browser\":\"chromium\"},{\"browser\":\"chromium\"},{\"browser\":\"chromium\"}]"})
    void partialOrDuplicateBrowserMatrixFails(String value) throws Exception {assertThrows(Exception.class,()->RuntimeReports.report("teaVmBrowsers",JsonParser.parseString(value),json("testdata/g2/a1-evidence-profile.json")));}
    @Test void completeBrowserAndSafariReportsPassAndFailedPayloadsDoNot() throws Exception {
        var profile=json("testdata/g2/a1-evidence-profile.json"); JsonArray matrix=new JsonArray();
        for(String name:java.util.List.of("chromium","firefox","webkit")) {
            JsonObject row=new JsonObject();row.addProperty("browser",name);row.addProperty("status","passed");
            row.addProperty("browserVersion","fixture-version");row.addProperty("playwright","fixture-version");row.add("node",profile.get("nodeVersion"));row.add("result",browser(profile));matrix.add(row);
        }
        RuntimeReports.report("teaVmBrowsers",matrix,profile);RuntimeReports.report("webImageBrowsers",matrix,profile);
        JsonObject safari=new JsonObject();safari.addProperty("status","passed");safari.addProperty("browser","Safari");safari.addProperty("driverVersion","fixture-version");
        safari.add("node",profile.get("nodeVersion"));safari.add("result",browser(profile));RuntimeReports.report("safari",safari,profile);
        safari.getAsJsonObject("result").addProperty("compiledOutcome","failed");assertThrows(Exception.class,()->RuntimeReports.report("safari",safari,profile));
        matrix.get(0).getAsJsonObject().addProperty("node","wrong-version");assertThrows(Exception.class,()->RuntimeReports.report("teaVmBrowsers",matrix,profile));
    }
    @Test void sdkCommandAndStreamProfileCannotBeChanged() {
        var arguments=java.util.List.of(java.util.List.of("version"),java.util.List.of("version","--json"),java.util.List.of("capabilities"),
                java.util.List.of("check","/fixture/testdata/models/base24.ili","/fixture/testdata/models/survey24.ili"),java.util.List.of("check","/fixture/testdata/models/broken24.ili"),
                java.util.List.of("format","--check","/temporary/model.ili"),java.util.List.of("format","--indent","4","--line-ending","lf","/temporary/model.ili"),
                java.util.List.of("format","--write","--indent","4","--line-ending","lf","/temporary/model.ili"),java.util.List.of("check","--no-such-option","/fixture/testdata/models/base24.ili"));
        var exits=java.util.List.of(0,0,0,0,1,1,0,0,2);JsonObject data=new JsonObject();data.addProperty("schemaVersion","1.0");data.addProperty("cli","interlis-cli/build/install/interlis-cli/bin/interlis-cli");
        JsonArray commands=new JsonArray();data.add("commands",commands);
        for(int i=0;i<arguments.size();i++) {JsonObject row=new JsonObject();row.add("arguments",new Gson().toJsonTree(arguments.get(i)));row.addProperty("exitCode",exits.get(i));
            row.addProperty("stdoutSha256","0".repeat(64));row.addProperty("stdoutLines",0);row.addProperty("stderrLines",0);commands.add(row);}
        RuntimeReports.sdk(data);
        var changed=data.deepCopy();rows(changed,"commands").get(4).addProperty("exitCode",0);assertThrows(Exception.class,()->RuntimeReports.sdk(changed));
        var broken=data.deepCopy();rows(broken,"commands").get(0).addProperty("stdoutSha256","broken");assertThrows(Exception.class,()->RuntimeReports.sdk(broken));
        var missing=data.deepCopy();missing.getAsJsonArray("commands").remove(0);assertThrows(Exception.class,()->RuntimeReports.sdk(missing));
    }
    @Test void detailedReferenceSummaryAndProjectReclassificationMustAgree() throws Exception {
        var profile=json("testdata/g2/a1-evidence-profile.json");profile.getAsJsonObject("conformance").addProperty("selectedCases",2);profile.getAsJsonObject("conformance").addProperty("caseIdsSha256",hash("[\"one\",\"two\"]"));
        var raw=JsonParser.parseString("""
                {"schemaVersion":"1.0","overallResult":"FAIL","summary":{"PASS":1,"XFAIL":0,"FAIL":1,"XPASS":0,"ERROR":0,"TOTAL":2},"compilers":[{"id":"interlis-java","tests":[{"testId":"one","classification":"PASS","languageVersion":"2.4","timedOut":false,"exitCode":0,"command":["compiler","one"],"actualOutcome":"accepted"},{"testId":"two","classification":"FAIL","languageVersion":"2.4","timedOut":false,"exitCode":1,"command":["compiler","two"],"actualOutcome":"rejected"}],"summary":{"PASS":1,"XFAIL":0,"FAIL":1,"XPASS":0,"ERROR":0,"TOTAL":2}}]}
                """).getAsJsonObject();
        RuntimeReports.report("reference",raw,profile);
        var project=JsonParser.parseString("{\"referenceResult\":\"FAIL\",\"normativeAssessments\":[{\"testId\":\"two\"}],\"summary\":{\"PASS\":1,\"XFAIL\":1,\"FAIL\":0,\"XPASS\":0,\"ERROR\":0,\"TOTAL\":2}}").getAsJsonObject();
        project.add("referenceSummary",raw.get("summary").deepCopy());RuntimeReports.pair(raw,project);
        project.add("normativeAssessments",new JsonArray());assertThrows(Exception.class,()->RuntimeReports.pair(raw,project));
        raw.getAsJsonObject("summary").addProperty("PASS",2);assertThrows(Exception.class,()->RuntimeReports.report("reference",raw,profile));
    }
}
