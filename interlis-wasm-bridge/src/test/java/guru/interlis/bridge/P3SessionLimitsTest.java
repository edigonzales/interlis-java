package guru.interlis.bridge;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** WASM-011/015: limits reject atomically and never expire explicitly retained results. */
class P3SessionLimitsTest {
    private final BridgeDispatcher bridge=new BridgeDispatcher();
    private int sequence;
    private static final String MODEL="INTERLIS 2.4;MODEL F AT \"x\" VERSION \"1\"=END F.";

    @Test void allAcceptedLimitsAreReturnedAndInvalidLimitsDoNotAllocateASession() {
        var invalid=call("openSession",null,JsonValues.object("limits",JsonValues.object("maxSources",JsonValues.number(0))));
        assertError(invalid,"INVALID_REQUEST");
        var limits=limits(1,1);
        var opened=call("openSession",null,JsonValues.object("limits",limits));
        assertEquals("ok",BridgeCodec.string(opened,"status"));
        assertEquals(limits,result(opened).members().get("limits"));
        assertError(call("openSession",null,JsonValues.object()),"LIMIT_EXCEEDED");
        String id=BridgeCodec.string(result(opened),"sessionId"); call("closeSession",id,JsonValues.object());
        assertEquals("ok",BridgeCodec.string(call("openSession",null,JsonValues.object()),"status"));
    }

    @Test void sourceAndBatchLimitsAreAtomicAndEqualBytesStillCountAsDistinctSources() {
        String id=open(limits(1,2));
        var batch=JsonValues.object("expectedWorkspaceRevision",JsonValues.string("0"),"updates",JsonValues.array(List.of(source("memory:/a",MODEL),source("memory:/b",MODEL))));
        assertError(call("updateSources",id,batch),"LIMIT_EXCEEDED");
        var first=call("updateSource",id,source("memory:/a",MODEL));
        assertEquals("1",BridgeCodec.string(result(first),"workspaceRevision"));
        assertError(call("updateSource",id,source("memory:/b",MODEL)),"LIMIT_EXCEEDED");
        var compiled=call("compile",id,roots("1"));
        assertEquals("succeeded",BridgeCodec.string(result(compiled),"outcome"));
    }

    @Test void initialSourceRejectionDoesNotLeaveAPartlyOpenedSession() {
        var opened=call("openSession",null,JsonValues.object("limits",limits(1,1),"sources",JsonValues.array(List.of(
                JsonValues.object("sourceId",JsonValues.string("memory:/a"),"text",JsonValues.string(MODEL)),
                JsonValues.object("sourceId",JsonValues.string("memory:/b"),"text",JsonValues.string(MODEL))))));
        assertError(opened,"LIMIT_EXCEEDED");
        assertEquals("ok",BridgeCodec.string(call("openSession",null,JsonValues.object("limits",limits(1,1))),"status"));
    }

    @Test void retainedRejectedResultsCountAndReleaseExpiresOnlyThatSnapshot() {
        String id=open(limits(2,2));
        var update=call("updateSource",id,source("memory:/a",MODEL.replace("END F.","DOMAIN D=Missing; END F.")));
        assertEquals("ok",BridgeCodec.string(update,"status"));
        var rejected=call("compile",id,roots("1"));
        assertEquals("rejected",BridgeCodec.string(result(rejected),"outcome"));
        String snapshot=BridgeCodec.string(result(rejected),"snapshotId");
        assertError(call("analyze",id,roots("1")),"LIMIT_EXCEEDED");
        call("releaseSnapshot",id,JsonValues.object("snapshotId",JsonValues.string(snapshot)));
        var analyzed=call("analyze",id,roots("1"));
        assertEquals("ok",BridgeCodec.string(analyzed,"status"));
        assertError(call("query",id,JsonValues.object("snapshotId",JsonValues.string(snapshot),"query",JsonValues.object("kind",JsonValues.string("completion")))),"SNAPSHOT_EXPIRED");
    }

    @Test void utf16LengthIsCheckedBeforeAnySourceCommit() {
        String id=open(limits(2,2));
        assertError(call("updateSource",id,source("memory:/a","😀".repeat(501))),"LIMIT_EXCEEDED");
        var valid=call("updateSource",id,source("memory:/a",MODEL));
        assertEquals("1",BridgeCodec.string(result(valid),"workspaceRevision"));
    }

    @Test void sourceTotalLengthIncludesUnchangedSourcesAndReplacementDeltas() {
        String id=open(limits(4,2));
        for(int i=0;i<2;i++) assertEquals("ok",BridgeCodec.string(call("updateSource",id,source("memory:/"+i,"a".repeat(900))),"status"));
        assertError(call("updateSource",id,source("memory:/2","a".repeat(300))),"LIMIT_EXCEEDED");
        var replacement=JsonValues.object("sourceId",JsonValues.string("memory:/0"),"expectation",JsonValues.object("kind",JsonValues.string("at"),"revision",JsonValues.string("1")),
                "change",JsonValues.object("kind",JsonValues.string("replace"),"text",JsonValues.string("a")));
        assertEquals("ok",BridgeCodec.string(call("updateSource",id,replacement),"status"));
        assertEquals("ok",BridgeCodec.string(call("updateSource",id,source("memory:/2","a".repeat(300))),"status"));
    }

    @Test void sessionMessageLimitPrecedesFullPayloadParsingAndUnderstandsEscapedHeaderKeys() {
        String id=open(limits(2,2));
        String prefix="{\"protocol\":\"interlis.compiler\",\"protocolVersion\":\"1.0\",\"requestId\":\"large\",\"sessionId\":\""+id+"\",\"operation\":\"updateSource\",\"params\":{\"text\":\"";
        // The oversized payload is deliberately malformed, so JSON-tree decoding
        // would return INVALID_JSON instead of the required predecode limit.
        String request=prefix+"a".repeat(10_001)+"\" BROKEN}";
        var response=(JsonValue.JsonObject)Json.parse(bridge.handle(request));
        assertError(response,"LIMIT_EXCEEDED");assertEquals("large",BridgeCodec.string(response,"requestId"));
        String escaped=request.replace("\"sessionId\"","\"session\\u0049d\"");
        assertError((JsonValue.JsonObject)Json.parse(bridge.handle(escaped)),"LIMIT_EXCEEDED");
        assertEquals("ok",BridgeCodec.string(call("updateSource",id,source("memory:/a",MODEL)),"status"));
    }

    @Test void configurationIsCompleteAtOpenAndReplacementAndInvalidChangesAreAtomic() {
        var configuration=JsonValues.object("allowedLanguages",JsonValues.strings(List.of("2.3")),
                "warningPolicy",JsonValues.string("ALLOW_WARNINGS"),
                "resolverCatalog",JsonValues.object("F",JsonValues.string("memory:/catalogue")),
                "cacheLimits",JsonValues.object("maxSyntaxEntries",JsonValues.number(0)));
        var opened=call("openSession",null,JsonValues.object("configuration",configuration));
        String id=BridgeCodec.string(result(opened),"sessionId");
        assertTrue(BridgeCodec.string(result(opened),"configurationFingerprint").contains("warnings=ALLOW_WARNINGS"));
        call("updateSource",id,source("memory:/a",MODEL));
        assertEquals("rejected",BridgeCodec.string(result(call("compile",id,roots("1"))),"outcome"));
        assertError(call("setConfiguration",id,JsonValues.object("expectedWorkspaceRevision",JsonValues.string("1"),"configuration",
                JsonValues.object("cacheLimits",JsonValues.object("maxSyntaxEntries",JsonValues.number(-1))))),"INVALID_REQUEST");
        assertEquals("rejected",BridgeCodec.string(result(call("compile",id,roots("1"))),"outcome"));
        var reset=call("setConfiguration",id,JsonValues.object("expectedWorkspaceRevision",JsonValues.string("1"),"configuration",JsonValues.object()));
        assertEquals("2",BridgeCodec.string(result(reset),"workspaceRevision"));
        assertEquals("succeeded",BridgeCodec.string(result(call("compile",id,roots("2"))),"outcome"));
    }

    @Test void analysisBudgetIsAppliedAndCancellationDoesNotConsumeSnapshotCapacity() {
        var opened=call("openSession",null,JsonValues.object("limits",limits(2,2),"configuration",
                JsonValues.object("analysisBudget",JsonValues.object("maxTokens",JsonValues.string("1")))));
        String id=BridgeCodec.string(result(opened),"sessionId");call("updateSource",id,source("memory:/a",MODEL));
        for(int i=0;i<2;i++) {
            var outcome=call("compile",id,roots("1"));
            assertEquals("ok",BridgeCodec.string(outcome,"status"));
            assertEquals("cancelled",BridgeCodec.string(result(outcome),"outcome"));
            JsonValue.JsonObject cancellation=(JsonValue.JsonObject)result(outcome).members().get("cancellation");
            assertEquals("RESOURCE_LIMIT",BridgeCodec.string(cancellation,"reason"));
        }
    }


    @Test void malformedTransportAndOutOfRangeOffsetsRemainExplicitInputErrors() {
        assertError(call("openSession",null,JsonValues.object("sources",JsonValues.array(List.of(JsonValues.string("bad"))))),"INVALID_REQUEST");
        String id=open(limits(2,2));call("updateSource",id,source("memory:/a",MODEL));
        var compiled=result(call("compile",id,roots("1")));String snapshot=BridgeCodec.string(compiled,"snapshotId");
        assertError(call("query",id,JsonValues.object("snapshotId",JsonValues.string(snapshot),"query",JsonValues.array(List.of()))),"INVALID_REQUEST");
        var position=JsonValues.object("kind",JsonValues.string("hover"),"sourceId",JsonValues.string("memory:/a"),"revision",JsonValues.string("1"),"startOffset",JsonValues.string("4294967296"),"endOffset",JsonValues.string("4294967296"));
        assertError(call("query",id,JsonValues.object("snapshotId",JsonValues.string(snapshot),"query",position)),"INVALID_REQUEST");
        var wrong=JsonValues.array(List.of(JsonValues.object("snapshot",JsonValues.string("foreign"),"symbol",JsonValues.string("irrelevant"))));
        assertError(call("diagram",id,JsonValues.object("snapshotId",JsonValues.string(snapshot),"selection",wrong)),"SNAPSHOT_MISMATCH");
        assertEquals("ok",BridgeCodec.string(call("diagram",id,JsonValues.object("schemaId",compiled.members().get("schemaId"))),"status"));
    }

    private String open(JsonValue.JsonObject limits) { return BridgeCodec.string(result(call("openSession",null,JsonValues.object("limits",limits))),"sessionId"); }
    private static JsonValue.JsonObject limits(int sources,int concurrent) { return JsonValues.object(
            "maxSourceUtf16Length",JsonValues.number(1000),"maxTotalSourceUtf16Length",JsonValues.string("2000"),
            "maxSources",JsonValues.number(sources),"maxRetainedSnapshots",JsonValues.number(1),
            "maxMessageUtf16Length",JsonValues.number(10000),"maxConcurrentSessions",JsonValues.number(concurrent)); }
    private static JsonValue.JsonObject source(String id,String text) { return JsonValues.object("sourceId",JsonValues.string(id),"expectation",JsonValues.object("kind",JsonValues.string("absent")),"change",JsonValues.object("kind",JsonValues.string("replace"),"text",JsonValues.string(text))); }
    private static JsonValue.JsonObject roots(String revision) { return JsonValues.object("expectedWorkspaceRevision",JsonValues.string(revision),"roots",JsonValues.array(List.of(JsonValues.object("kind",JsonValues.string("byName"),"modelName",JsonValues.string("F"))))); }
    private JsonValue.JsonObject call(String operation,String session,JsonValue.JsonObject params) {
        var request=new LinkedHashMap<String,JsonValue>();request.put("protocol",JsonValues.string("interlis.compiler"));request.put("protocolVersion",JsonValues.string("1.0"));request.put("requestId",JsonValues.string("limits-"+(++sequence)));request.put("operation",JsonValues.string(operation));request.put("params",params);if(session!=null)request.put("sessionId",JsonValues.string(session));
        return (JsonValue.JsonObject)Json.parse(bridge.handle(Json.write(new JsonValue.JsonObject(request))));
    }
    private static JsonValue.JsonObject result(JsonValue.JsonObject value) { return (JsonValue.JsonObject)value.members().get("result"); }
    private static void assertError(JsonValue.JsonObject value,String code) { assertEquals("error",BridgeCodec.string(value,"status"),value::toString);assertEquals(code,BridgeCodec.string((JsonValue.JsonObject)value.members().get("error"),"code"),value::toString); }
}
