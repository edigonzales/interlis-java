package guru.interlis.cli.evidence;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import static guru.interlis.cli.evidence.EvidenceFiles.*;

/** Preserved report-format failure paths; this helper neither runs nor certifies platforms. */
final class RuntimeReports {
    static final List<String> COUNTS = List.of("PASS","XFAIL","FAIL","XPASS","ERROR");
    private RuntimeReports() { }
    static void report(String key, JsonElement value, JsonObject profile) {
        try { check(key, value, profile); }
        catch (NullPointerException | ClassCastException | IllegalStateException e) { throw new IllegalArgumentException("Incomplete report: " + key, e); }
    }
    private static void check(String key, JsonElement value, JsonObject profile) {
        if (key.equals("reference")) { reference(value.getAsJsonObject(), profile); return; }
        if (key.equals("project")) { project(value.getAsJsonObject(), profile); return; }
        if (Set.of("teaVmBrowsers","webImageBrowsers").contains(key)) {
            JsonArray array = value.getAsJsonArray(); require(array.size()==3,"Incomplete browser matrix"); Set<String> names=new HashSet<>();
            for (var item : array) {
                var row=item.getAsJsonObject(); require(names.add(s(row,"browser")) && s(row,"status").equals("passed") && !s(row,"browserVersion").isBlank()
                        && !s(row,"playwright").isBlank() && row.get("node").equals(profile.get("nodeVersion")),"Incomplete browser result/version");
                var result=row.getAsJsonObject("result"); require(s(result,"status").equals("ok"),"Failed browser result"); portable(result,profile,key.equals("teaVmBrowsers"));
            }
            require(names.equals(Set.of("chromium","firefox","webkit")),"Incomplete browser matrix"); return;
        }
        var data=value.getAsJsonObject();
        require(Set.of("ok","passed").contains(s(data,"status")),"Failed runtime");
        if(key.equals("sdkProcesses")) { sdk(data); return; }
        if(key.equals("safari")) {
            require(s(data,"status").equals("passed") && s(data,"browser").equals("Safari") && !s(data,"driverVersion").isBlank() && data.get("node").equals(profile.get("nodeVersion")),"Missing Safari identity/version");
            var result=data.getAsJsonObject("result"); require(s(result,"status").equals("ok"),"Failed Safari result"); portable(result,profile,true); return;
        }
        var runtimes=Map.of("teaVmJavaScript","teavm-js","teaVmWasmGC","teavm-wasm-gc","webImageNode","graalvm-web-image");
        require(s(data,"runtime").equals(runtimes.get(key)) && data.get("node").equals(profile.get("nodeVersion")),"Missing runtime version"); portable(data,profile,false);
    }
    static void portable(JsonObject data, JsonObject profile, boolean browser) {
        require(data.get("stabilizationCases").toString().matches("[1-9][0-9]*") && data.get("stabilizationCases").equals(profile.get("portableCases")),"Incomplete portable cases");
        require(data.get(data.has("capabilities")?"capabilities":"tool").equals(profile.get("projectVersion")),"Changed compiler version");
        if(!browser) { require(s(data,"outcome").equals("succeeded"),"Incomplete portable outcome"); return; }
        require(s(data,"analyzeRevision").equals("1") && s(data,"compiledOutcome").equals("succeeded") && s(data,"rejectedOutcome").equals("rejected")
                && s(data,"workerRestartOutcome").equals("succeeded") && data.get("exactDecimal").equals(new JsonPrimitive(true))
                && n(data,"diagnostics")==0 && n(data,"diagramNodes")==2 && n(data,"diagramEdges")==0 && n(data,"diagramAssociations")==0,"Incomplete browser contracts");
    }
    static void reference(JsonObject data, JsonObject profile) {
        var selection=profile.getAsJsonObject("conformance"); var compilers=rows(data,"compilers");
        require(s(data,"schemaVersion").equals("1.0") && compilers.size()==1,"Incomplete reference profile");
        var compiler=compilers.getFirst(); var tests=rows(compiler,"tests");
        require(s(compiler,"id").equals("interlis-java") && tests.size()==n(selection,"selectedCases") && !tests.isEmpty(),"Incomplete reference cases");
        var ids=ids(tests,"testId").stream().sorted().toList();
        require(hash(new Gson().toJson(ids)).equals(s(selection,"caseIdsSha256")),"Changed reference selection");
        Map<String,Integer> counts=new LinkedHashMap<>(); COUNTS.forEach(k->counts.put(k,0));
        for(var test:tests) {
            require(strings(selection.get("languages")).contains(s(test,"languageVersion")) && test.get("timedOut").equals(new JsonPrimitive(false)) && Set.of(0,1).contains(n(test,"exitCode"))
                    && !strings(test.get("command")).isEmpty() && Set.of("accepted","rejected").contains(s(test,"actualOutcome")),"Invalid reference process");
            String status=s(test,"classification"); require(counts.containsKey(status),"Unknown reference classification"); counts.compute(status,(_,n)->n+1);
        }
        counts.put("TOTAL",tests.size()); var expected=new Gson().toJsonTree(counts);
        require(expected.equals(data.get("summary")) && expected.equals(compiler.get("summary")),"Reference summary disagrees with details");
        require(counts.get("ERROR")==0 && counts.get("XPASS")==0,"Unresolved reference result");
    }
    static void project(JsonObject data, JsonObject profile) {
        var summary=data.getAsJsonObject("summary"); var conformance=profile.getAsJsonObject("conformance");
        require(s(data,"overallResult").equals("PASS") && n(summary,"FAIL")==0 && n(summary,"ERROR")==0 && n(summary,"XPASS")==0,"Unresolved project result");
        require(n(summary,"TOTAL")>0 && n(summary,"TOTAL")==n(conformance,"selectedCases") && COUNTS.stream().mapToInt(k->n(summary,k)).sum()==n(summary,"TOTAL"),"Incomplete project cases");
        var selection=data.getAsJsonObject("selection");
        require(selection.get("languageVersions").equals(conformance.get("languages")) && n(selection,"suiteCaseCount")==n(conformance,"suiteCases")
                && n(selection,"selectedCaseCount")==n(summary,"TOTAL") && n(selection,"excludedCaseCount")==n(conformance,"excludedCases")
                && data.getAsJsonObject("suite").get("commit").equals(conformance.get("suiteCommit")),"Changed conformance selection");
        for(var a:rows(data,"normativeAssessments")) {
            var original=a.getAsJsonObject("original"); var control=a.getAsJsonObject("positiveControl");
            require(s(a,"result").equals("PASS") && (s(a,"referenceClassification").equals("FAIL") || s(a,"referenceClassification").equals("PASS") && approvedCompatibility(a)) && Set.of("accepted","rejected").contains(s(a,"ownExpectation"))
                    && a.get("actualOutcome").equals(a.get("ownExpectation")) && !strings(original.get("command")).isEmpty() && !strings(control.get("command")).isEmpty()
                    && n(original,"exitCode")== (s(a,"ownExpectation").equals("accepted")?0:1) && n(control,"exitCode")==0,"Incomplete normative assessment");
        }
    }
    static void pair(JsonObject raw, JsonObject project) {
        require(project.get("referenceSummary").equals(raw.get("summary")) && project.get("referenceResult").equals(raw.get("overallResult")),"Raw/project summaries disagree");
        Set<String> failed=new HashSet<>(); for(var t:rows(rows(raw,"compilers").getFirst(),"tests")) if(s(t,"classification").equals("FAIL")) failed.add(s(t,"testId"));
        var assessments=rows(project,"normativeAssessments");Set<String> required=new HashSet<>(failed);
        if(project.has("assessmentCaseIds")) require(ids(assessments,"testId").equals(new HashSet<>(strings(project.get("assessmentCaseIds")))),"Missing declared project assessment");
        for(var a:assessments)if(approvedCompatibility(a)) {
            require(rows(rows(raw,"compilers").getFirst(),"tests").stream().anyMatch(t->t.get("testId").equals(a.get("testId")) && s(t,"classification").equals("PASS")),"Compatibility assessment lacks raw PASS");required.add(s(a,"testId"));
        }
        require(ids(assessments,"testId").equals(required) && assessments.size()==required.size(),"Missing/duplicate normative assessment");
        var summary=raw.getAsJsonObject("summary").deepCopy(); summary.addProperty("FAIL",n(summary,"FAIL")-failed.size()); summary.addProperty("XFAIL",n(summary,"XFAIL")+failed.size());
        require(summary.equals(project.get("summary")),"Unexplained classification change");
    }
    static boolean approvedCompatibility(JsonObject assessment) {
        if(!assessment.has("compatibilityDecision"))return false;
        var decision=assessment.getAsJsonObject("compatibilityDecision");
        return s(decision,"id").equals("LEGACY_OID_23_WITHOUT_CONTRACTED") && s(decision,"userDecision").equals("APPROVED_USER");
    }
    static void sdk(JsonObject data) {
        List<List<String>> args=List.of(List.of("version"),List.of("version","--json"),List.of("capabilities"),
                List.of("check","{ROOT}/testdata/models/base24.ili","{ROOT}/testdata/models/survey24.ili"),List.of("check","{ROOT}/testdata/models/broken24.ili"),
                List.of("format","--check","{TEMP_MODEL}"),List.of("format","--indent","4","--line-ending","lf","{TEMP_MODEL}"),
                List.of("format","--write","--indent","4","--line-ending","lf","{TEMP_MODEL}"),List.of("check","--no-such-option","{ROOT}/testdata/models/base24.ili"));
        var commands=rows(data,"commands"); var exits=List.of(0,0,0,0,1,1,0,0,2);
        require(s(data,"schemaVersion").equals("1.0") && commands.size()==args.size() && s(data,"cli").equals("interlis-cli/build/install/interlis-cli/bin/interlis-cli"),"Incomplete SDK profile");
        String suffix="/testdata/models/base24.ili", argument=strings(commands.get(3).get("arguments")).get(1);
        require(argument.endsWith(suffix),"Missing SDK source root"); String root=argument.substring(0,argument.length()-suffix.length()); require(Path.of(root).isAbsolute(),"SDK root not absolute");
        String temporary=null;
        for(int i=0;i<commands.size();i++) {
            var row=commands.get(i); List<String> normalized=new ArrayList<>();
            for(String arg:strings(row.get("arguments"))) {
                if(arg.startsWith(root+"/")) normalized.add("{ROOT}/"+arg.substring(root.length()+1));
                else if(Path.of(arg).isAbsolute() && Path.of(arg).getFileName().toString().equals("model.ili")) {
                    require(temporary==null || temporary.equals(arg),"Different SDK temporary model"); temporary=arg; normalized.add("{TEMP_MODEL}");
                } else normalized.add(arg);
            }
            require(normalized.equals(args.get(i)) && n(row,"exitCode")==exits.get(i),"Changed SDK command/exit");
            require(s(row,"stdoutSha256").matches("[0-9a-f]{64}") && n(row,"stdoutLines")>=0 && n(row,"stderrLines")>=0,"Incomplete SDK stream evidence");
        }
    }
}
