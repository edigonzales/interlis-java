package guru.interlis.cli.verification;

import com.google.gson.*;
import guru.interlis.bridge.BridgeDispatcher;
import java.nio.file.*;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Full-closure public protocol capture. Expectations and performance budgets are checked separately. */
public final class FullCorpusRun {
    private static final Gson RAW=new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static long counter;
    private FullCorpusRun() { }
    static JsonObject call(BridgeDispatcher bridge,String operation,String session,JsonObject params) {
        JsonObject request=new JsonObject();request.addProperty("protocol","interlis.compiler");request.addProperty("protocolVersion","1.0");request.addProperty("requestId","full-"+(++counter));request.addProperty("operation",operation);request.add("params",params);if(session!=null)request.addProperty("sessionId",session);
        var response=JsonParser.parseString(bridge.handle(RAW.toJson(request))).getAsJsonObject();
        require(s(response,"status").equals("ok"),"Unexpected public protocol error: "+response);return response.getAsJsonObject("result");
    }
    private static JsonObject params(String key,String value) {var object=new JsonObject();object.addProperty(key,value);return object;}
    static JsonObject capture(BridgeDispatcher bridge,JsonObject root,int measuredRuns) {
        var opening=new JsonObject();var sources=new JsonArray();for(var source:rows(root,"sources")){var input=new JsonObject();input.add("sourceId",source.get("sourceId"));input.add("text",source.get("text"));sources.add(input);}opening.add("sources",sources);opening.add("configuration",root.get("configuration"));
        double openStarted=System.nanoTime();var opened=call(bridge,"openSession",null,opening);double openMs=(System.nanoTime()-openStarted)/1_000_000.0;String session=s(opened,"sessionId");
        try {
            var request=params("expectedWorkspaceRevision",s(opened,"workspaceRevision"));request.add("roots",root.get("roots"));
            double firstStarted=System.nanoTime();var compiled=call(bridge,"compile",session,request);double firstMs=(System.nanoTime()-firstStarted)/1_000_000.0;
            String outcome=s(compiled,"outcome");require(Set.of("succeeded","rejected").contains(outcome),"Unexpected corpus outcome");
            require(compiled.has("schemaId")==outcome.equals("succeeded"),"Schema gate mismatch");
            String snapshot=s(compiled,"snapshotId");var diagram=call(bridge,"diagram",session,params("snapshotId",snapshot));
            require(!s(diagram,"completeness").equals("LIMITED"),"Full corpus projection exceeded the declared default budget");
            var properties=new JsonArray();
            for(var node:rows(diagram,"nodes")) {
                if(node.get("anchor")==null||node.get("anchor").isJsonNull()||node.get("symbol")==null||node.get("symbol").isJsonNull())continue;
                var range=node.getAsJsonObject("anchor").getAsJsonObject("nameRange");
                var query=new JsonObject();query.addProperty("kind","semanticProperties");query.add("sourceId",range.get("source"));query.add("revision",range.get("revision"));query.add("startOffset",range.get("startOffset"));query.add("endOffset",range.get("startOffset"));
                var input=params("snapshotId",snapshot);input.add("query",query);var value=call(bridge,"query",session,input);
                require(s(value,"kind").equals("semanticProperties")&&s(value,"symbolId").equals(s(node.getAsJsonObject("symbol"),"symbol")),"Diagram anchor does not denote its published symbol");properties.add(value);
            }
            JsonObject semantic=new JsonObject();semantic.add("compile",compiled);semantic.add("diagram",diagram);semantic.add("properties",properties);
            JsonElement normalized=normalize(semantic,snapshot);var samples=new JsonArray();
            for(int i=0;i<measuredRuns;i++) {
                long started=System.nanoTime();var repeated=call(bridge,"compile",session,request);samples.add((System.nanoTime()-started)/1_000_000.0);
                require(normalize(repeated,s(repeated,"snapshotId")).equals(normalize(compiled,snapshot)),"Unstable warm corpus compile");
                call(bridge,"releaseSnapshot",session,params("snapshotId",s(repeated,"snapshotId")));
            }
            var report=new JsonObject();report.addProperty("model",s(root,"model"));report.addProperty("version",s(root,"version"));report.addProperty("file",s(root,"file"));report.addProperty("outcome",outcome);report.addProperty("openMs",openMs);report.addProperty("firstCompileMs",firstMs);report.add("warmCompileMs",samples);report.add("semantic",normalized);return report;
        } finally {call(bridge,"closeSession",session,new JsonObject());}
    }
    /** Object keys are sorted; array order, nulls, numeric and diagnostic values are retained. */
    static JsonElement normalize(JsonElement value,String snapshot) {
        if(value.isJsonArray()){var out=new JsonArray();for(var item:value.getAsJsonArray())out.add(normalize(item,snapshot));return out;}
        if(!value.isJsonObject())return value.deepCopy();var out=new JsonObject();
        for(var key:new TreeSet<>(value.getAsJsonObject().keySet())) {
            var item=value.getAsJsonObject().get(key);if(key.equals("schemaId"))continue;
            if(key.equals("snapshotId")||key.equals("snapshot")){require(item.isJsonPrimitive()&&item.getAsString().equals(snapshot),"Foreign snapshot reference in canonical capture");out.addProperty(key,"CURRENT");}
            else out.add(key,normalize(item,snapshot));
        }return out;
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),out=root.resolve("build/reports/performance/full-corpus/jvm.json");Files.deleteIfExists(out);String sourceFingerprint=ProfileFingerprint.current(root),gitCommit=ProfileFingerprint.commit(root);boolean dirty=ProfileFingerprint.dirty(root);
        var cold=run(root,List.of(java().toString(),"-cp",System.getProperty("java.class.path"),FullCorpusCold.class.getName(),root.toString()),java.time.Duration.ofSeconds(30));
        require(cold.exitCode()==0&&cold.stdout().startsWith("FULL_CORPUS_COLD_PASS "),"Fresh JVM process failed: "+cold);
        var prepared=FullCorpus.prepare(root.resolve("docs/evidence/p3/full-corpus"));var bridge=new BridgeDispatcher();var results=new JsonArray();long started=System.nanoTime();
        var report=new JsonObject();report.addProperty("sourceFingerprint",sourceFingerprint);report.addProperty("gitCommit",gitCommit);report.addProperty("dirty",dirty);report.addProperty("profile",s(prepared,"profile"));report.addProperty("manifestSha256",s(prepared,"manifestSha256"));report.addProperty("runtime","jvm");report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("os",System.getProperty("os.name"));report.addProperty("arch",System.getProperty("os.arch"));report.addProperty("processors",Runtime.getRuntime().availableProcessors());report.add("roots",results);report.addProperty("coldProcessMs",cold.durationMs());
        try {
            for(var input:rows(prepared,"roots")){var result=capture(bridge,input,3);results.add(result);System.out.println("FULL_CORPUS_JVM "+results.size()+" "+s(input,"model")+" "+s(result,"outcome"));if(results.size()%10==0){Files.createDirectories(out.getParent());Files.writeString(out,RAW.toJson(report)+"\n");}}
            require(results.size()==347,"Incomplete full corpus execution");report.addProperty("peakHeapBytes",java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==java.lang.management.MemoryType.HEAP).mapToLong(p->p.getPeakUsage().getUsed()).sum());require(ProfileFingerprint.current(root).equals(sourceFingerprint),"Product/profile changed during measurement");report.addProperty("captureStatus","complete");report.addProperty("elapsedMs",(System.nanoTime()-started)/1_000_000.0);
        } finally {Files.createDirectories(out.getParent());Files.writeString(out,RAW.toJson(report)+"\n");}
        System.out.println("FULL_CORPUS_JVM_CAPTURE_COMPLETE 347; acceptance and budget comparison are separate");
    }
}
