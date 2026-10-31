package guru.interlis.cli.verification;

import com.google.gson.*;
import guru.interlis.bridge.BridgeDispatcher;
import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.source.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static guru.interlis.cli.verification.HostChecks.*;

/** Real pinned closure edits, explicit retained-result bounds and cooperative abort. */
public final class EditorPressure {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private static long sequence;
    private EditorPressure() { }
    private static JsonObject raw(BridgeDispatcher bridge,String operation,String session,JsonObject params) {
        var request=new JsonObject();request.addProperty("protocol","interlis.compiler");request.addProperty("protocolVersion","1.0");request.addProperty("requestId","editor-"+(++sequence));request.addProperty("operation",operation);request.add("params",params);if(session!=null)request.addProperty("sessionId",session);
        return JsonParser.parseString(bridge.handle(JSON.toJson(request))).getAsJsonObject();
    }
    private static JsonObject call(BridgeDispatcher bridge,String operation,String session,JsonObject params) {
        var response=raw(bridge,operation,session,params);require(s(response,"status").equals("ok"),"Editor protocol failure: "+response);return response.getAsJsonObject("result");
    }
    private static JsonObject object(String key,String value) {var result=new JsonObject();result.addProperty(key,value);return result;}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),output=root.resolve("build/reports/performance/full-corpus/editor-jvm.json");Files.deleteIfExists(output);String sourceFingerprint=ProfileFingerprint.current(root),gitCommit=ProfileFingerprint.commit(root);boolean dirty=ProfileFingerprint.dirty(root);
        var prepared=FullCorpus.prepare(root.resolve("docs/evidence/p3/full-corpus"));
        var input=rows(prepared,"roots").stream().max(Comparator.comparingInt(r->rows(r,"sources").stream().mapToInt(v->s(v,"text").length()).sum())).orElseThrow();
        int closure=rows(input,"sources").stream().mapToInt(v->s(v,"text").length()).sum();require(closure>=200000,"Editor closure too small");
        var source=rows(input,"sources").stream().max(Comparator.comparingInt(v->s(v,"text").length())).orElseThrow();
        var opening=new JsonObject();var sources=new JsonArray();for(var value:rows(input,"sources")){var copy=object("sourceId",s(value,"sourceId"));copy.addProperty("text",s(value,"text"));sources.add(copy);}opening.add("sources",sources);
        var configuration=input.getAsJsonObject("configuration").deepCopy();configuration.add("cacheLimits",JsonParser.parseString("{\"maxSyntaxEntries\":4,\"maxAnalysisEntries\":4,\"maxQueryEntries\":32,\"maxRetainedSourceUtf16Units\":2000000}"));opening.add("configuration",configuration);opening.add("limits",JsonParser.parseString("{\"maxRetainedSnapshots\":4}"));
        var bridge=new BridgeDispatcher();var opened=call(bridge,"openSession",null,opening);String session=s(opened,"sessionId"),revision="1",workspace=s(opened,"workspaceRevision");
        var retained=new ArrayDeque<String>();var samples=new ArrayList<Double>();int released=0,maxRetained=0;
        try {
            for(int i=0;i<1000;i++) {
                long start=System.nanoTime();String previous=workspace;
                var update=object("sourceId",s(source,"sourceId"));var expectation=object("kind","at");expectation.addProperty("revision",revision);update.add("expectation",expectation);var change=object("kind","replace");change.addProperty("text",s(source,"text")+"\n!! editor "+i+"\n");update.add("change",change);
                var receipt=call(bridge,"updateSource",session,update);revision=s(receipt,"sourceRevision");workspace=s(receipt,"workspaceRevision");require(!workspace.equals(previous),"Edit did not advance workspace");
                if(retained.size()==4){String expired=retained.removeFirst();call(bridge,"releaseSnapshot",session,object("snapshotId",expired));var probe=raw(bridge,"diagram",session,object("snapshotId",expired));require(s(probe,"status").equals("error")&&s(probe.getAsJsonObject("error"),"code").equals("SNAPSHOT_EXPIRED"),"Released snapshot accepted");released++;}
                var request=object("expectedWorkspaceRevision",previous);request.add("roots",input.get("roots"));var stale=raw(bridge,"compile",session,request);require(s(stale,"status").equals("error")&&s(stale.getAsJsonObject("error"),"code").equals("VERSION_CONFLICT"),"Stale workspace accepted: "+stale);
                request.addProperty("expectedWorkspaceRevision",workspace);var compiled=call(bridge,"compile",session,request);require(Set.of("succeeded","rejected").contains(s(compiled,"outcome")),"Unexpected edit outcome");require(compiled.has("schemaId")==s(compiled,"outcome").equals("succeeded"),"Edit schema gate violated");
                retained.add(s(compiled,"snapshotId"));maxRetained=Math.max(maxRetained,retained.size());samples.add((System.nanoTime()-start)/1_000_000.0);if(i%100==99)System.out.println("EDITOR_JVM "+(i+1));
            }
            for(var snapshot:retained)call(bridge,"releaseSnapshot",session,object("snapshotId",snapshot));
        } finally {call(bridge,"closeSession",session,new JsonObject());}
        // Cancellation is requested after actual compiler checkpoints, never simulated as a successful compile.
        Map<SourceId,SourceText> texts=new LinkedHashMap<>();for(var value:rows(input,"sources")){var id=new SourceId(s(value,"sourceId"));texts.put(id,new SourceText(id,new SourceRevision(1),s(value,"text")));}
        var request=new CompilationRequest(List.of(new ModelSelector.ByName(s(input,"model"),Optional.empty(),Optional.empty())),SourceSet.of(texts),CompilerOptions.defaults());
        var engine=CompilerEngines.create();var checkpoints=new AtomicInteger();long abortStarted=System.nanoTime();boolean aborted=false;
        try {aborted=engine.compile(request,()->checkpoints.incrementAndGet()>1000) instanceof CompilationOutcome.Cancelled;}catch(OperationCancelledException expected){aborted=true;}
        require(aborted&&checkpoints.get()>1000,"Compiler did not cooperatively abort after work");double abortMs=(System.nanoTime()-abortStarted)/1_000_000.0;
        var recoverySource=new SourceText(new SourceId("memory:/recovery"),new SourceRevision(1),"INTERLIS 2.4;MODEL Recovery AT \"x\" VERSION \"1\"=DOMAIN D=TEXT;END Recovery.");
        require(engine.compile(new CompilationRequest(List.of(new ModelSelector.ByName("Recovery",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(recoverySource.id(),recoverySource)),CompilerOptions.defaults()),CancellationToken.none()) instanceof CompilationOutcome.Succeeded,"Engine did not recover after abort");
        var sorted=samples.stream().sorted().toList();var report=new JsonObject();report.addProperty("sourceFingerprint",sourceFingerprint);report.addProperty("gitCommit",gitCommit);report.addProperty("dirty",dirty);require(ProfileFingerprint.current(root).equals(sourceFingerprint),"Product/profile changed during measurement");report.addProperty("captureStatus","complete");report.addProperty("runtime","jvm");report.addProperty("manifestSha256",s(prepared,"manifestSha256"));report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("model",s(input,"model"));report.addProperty("closureUtf16",closure);report.addProperty("edits",1000);report.add("samplesMs",JSON.toJsonTree(samples));report.addProperty("p95Ms",sorted.get(949));report.addProperty("maxMs",sorted.getLast());report.addProperty("maxRetainedSnapshots",maxRetained);report.addProperty("releasedDuringEdits",released);report.addProperty("staleRevisionRejections",1000);report.addProperty("staleHandleAcceptances",0);report.addProperty("abortMs",abortMs);report.addProperty("abortCheckpoints",checkpoints.get());report.addProperty("recoveredAfterAbort",true);
        long peakHeap=ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==java.lang.management.MemoryType.HEAP).mapToLong(p->p.getPeakUsage().getUsed()).sum();report.addProperty("peakHeapBytes",peakHeap);Files.createDirectories(output.getParent());Files.writeString(output,JSON.toJson(report)+"\n");System.out.println("EDITOR_JVM_COMPLETE 1000 p95="+sorted.get(949));
    }
}
