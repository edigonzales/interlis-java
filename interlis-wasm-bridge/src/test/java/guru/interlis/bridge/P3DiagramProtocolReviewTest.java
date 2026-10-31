package guru.interlis.bridge;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class P3DiagramProtocolReviewTest {
    private final BridgeDispatcher bridge=new BridgeDispatcher();private int sequence;
    private record Handles(String session,String snapshot,String schema){}
    private Handles compiled(){
        var opened=result(call("openSession",null,JsonValues.object("sources",JsonValues.array(List.of(JsonValues.object("sourceId",JsonValues.string("memory:/f"),"text",JsonValues.string("INTERLIS 2.4;MODEL F AT \"x\" VERSION \"1\"=TOPIC T=CLASS A=n:TEXT;END A;CLASS B EXTENDS A=END B;END T;END F.")))))));
        String session=BridgeCodec.string(opened,"sessionId");var compiled=result(call("compile",session,JsonValues.object("expectedWorkspaceRevision",opened.members().get("workspaceRevision"),"roots",JsonValues.array(List.of(JsonValues.object("kind",JsonValues.string("byName"),"modelName",JsonValues.string("F")))))));
        assertEquals("succeeded",BridgeCodec.string(compiled,"outcome"));return new Handles(session,BridgeCodec.string(compiled,"snapshotId"),BridgeCodec.string(compiled,"schemaId"));
    }
    @Test void absentAndExplicitlyEmptySelectionsAreDifferentInputs(){
        var h=compiled();assertEquals("ok",BridgeCodec.string(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()))),"status"));
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"selection",JsonValues.array(List.of()))),"INVALID_REQUEST");
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"snapshotId",JsonValues.string(h.snapshot()))),"INVALID_REQUEST");
    }
    @Test void versionedBudgetsAndUnsupportedOptionsAreNeverSilentlyIgnored(){
        var h=compiled();var options=JsonValues.object("schemaVersion",JsonValues.string("1.0"),"budget",JsonValues.object("maxNodes",JsonValues.number(1),"maxEdges",JsonValues.number(1),"maxMembersPerNode",JsonValues.number(1)));
        var limited=result(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"options",options)));
        assertEquals("LIMITED",BridgeCodec.string(limited,"completeness"));assertEquals(1,BridgeCodec.array(limited,"nodes").values().size());
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"options",JsonValues.object("schemaVersion",JsonValues.string("1.0"),"scope",JsonValues.string("NEIGHBOURHOOD")))),"UNSUPPORTED_OPERATION");
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"options",JsonValues.object("schemaVersion",JsonValues.string("2.0")))),"UNSUPPORTED_OPERATION");
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"options",JsonValues.object("schemaVersion",JsonValues.string("1.0"),"unknown",JsonValues.bool(true)))),"INVALID_REQUEST");
    }
    @Test void invalidOptionPayloadsAreRejectedWithoutEchoingUnknownValues(){
        var h=compiled();var secret="sensitive-value-".repeat(2000);
        var response=call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"options",JsonValues.object("schemaVersion",JsonValues.string("1.0"),"scope",JsonValues.string(secret))));
        error(response,"INVALID_REQUEST");assertFalse(Json.write(response).contains("sensitive-value"));
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"options",JsonValues.object("schemaVersion",JsonValues.string("1.0"),"budget",JsonValues.object("maxNodes",JsonValues.string("1"))))),"INVALID_REQUEST");
    }
    @Test void repeatedCloseIsSuccessfulAndReleasesAllOtherSessionClaims(){
        var h=compiled();for(int i=0;i<2;i++)assertEquals("ok",BridgeCodec.string(call("closeSession",h.session(),JsonValues.object()),"status"));
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()))),"SESSION_NOT_FOUND");
    }
    @Test void explicitNeighbourhoodDepthCrossesTheWireAndRejectsForeignLabelSelections(){
        var h=compiled();var doc=result(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()))));
        var a=BridgeCodec.array(doc,"nodes").values().stream().map(BridgeCodec::object).filter(n->BridgeCodec.string(n,"label").equals("A")).findFirst().orElseThrow();
        var selection=JsonValues.array(List.of(a.members().get("symbol")));
        for(int depth:List.of(0,1)) {
            var options=JsonValues.object("schemaVersion",JsonValues.string("1.0"),"scope",JsonValues.string("NEIGHBOURHOOD"),"neighbourhoodDepth",JsonValues.number(depth));
            var near=result(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"selection",selection,"options",options)));
            assertEquals(depth+1,BridgeCodec.array(near,"nodes").values().stream().map(BridgeCodec::object).filter(n->!BridgeCodec.string(n,"kind").equals("EXTERNAL_REFERENCE")).count());
        }
        error(call("diagram",h.session(),JsonValues.object("schemaId",JsonValues.string(h.schema()),"options",JsonValues.object("schemaVersion",JsonValues.string("1.0"),"labelPolicy",JsonValues.string("TRANSLATED"),"translationModel",JsonValues.object("snapshot",JsonValues.string("foreign"),"symbol",JsonValues.string("unknown"))))),"SNAPSHOT_MISMATCH");
    }
    private JsonValue.JsonObject call(String operation,String session,JsonValue.JsonObject params){
        var request=new LinkedHashMap<String,JsonValue>();request.put("protocol",JsonValues.string("interlis.compiler"));request.put("protocolVersion",JsonValues.string("1.0"));request.put("requestId",JsonValues.string("review-"+(++sequence)));request.put("operation",JsonValues.string(operation));request.put("params",params);if(session!=null)request.put("sessionId",JsonValues.string(session));
        return BridgeCodec.object(Json.parse(bridge.handle(Json.write(new JsonValue.JsonObject(request)))));
    }
    private static JsonValue.JsonObject result(JsonValue.JsonObject value){assertEquals("ok",BridgeCodec.string(value,"status"),value::toString);return BridgeCodec.object(value.members().get("result"));}
    private static void error(JsonValue.JsonObject value,String code){assertEquals("error",BridgeCodec.string(value,"status"),value::toString);assertEquals(code,BridgeCodec.string(BridgeCodec.object(value.members().get("error")),"code"));}
}
