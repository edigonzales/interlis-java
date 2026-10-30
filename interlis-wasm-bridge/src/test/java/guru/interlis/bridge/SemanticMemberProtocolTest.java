package guru.interlis.bridge;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticMemberProtocolTest {
    private static JsonValue.JsonObject call(BridgeDispatcher bridge,String operation,String session,JsonValue.JsonObject params) {
        var request=new LinkedHashMap<String,JsonValue>();request.put("protocol",JsonValues.string("interlis.compiler"));request.put("protocolVersion",JsonValues.string("1.0"));request.put("requestId",JsonValues.string("members"));request.put("operation",JsonValues.string(operation));if(session!=null)request.put("sessionId",JsonValues.string(session));request.put("params",params);
        return (JsonValue.JsonObject)Json.parse(bridge.handle(Json.write(new JsonValue.JsonObject(request))));
    }
    private static JsonValue.JsonObject result(JsonValue.JsonObject response) {
        assertEquals("ok",BridgeCodec.string(response,"status"),response.toString());return (JsonValue.JsonObject)response.members().get("result");
    }
    @Test void effectiveMemberProjectionKeepsSnapshotOrderAndRejectsForeignRevisions() {
        String text="INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=STRUCTURE Base=a:TEXT;b:BOOLEAN;END Base;STRUCTURE Child EXTENDS Base=c:0..10;END Child;END F.";
        var bridge=new BridgeDispatcher();String id=BridgeCodec.string(result(call(bridge,"openSession",null,JsonValues.object("sources",JsonValues.array(List.of(JsonValues.object("sourceId",JsonValues.string("memory:/m.ili"),"text",JsonValues.string(text))))))),"sessionId");
        var compiled=result(call(bridge,"compile",id,JsonValues.object("expectedWorkspaceRevision",JsonValues.string("1"),"roots",JsonValues.array(List.of(JsonValues.object("kind",JsonValues.string("byName"),"modelName",JsonValues.string("F")))))));
        var query=JsonValues.object("kind",JsonValues.string("semanticProperties"),"sourceId",JsonValues.string("memory:/m.ili"),"revision",JsonValues.string("1"),"startOffset",JsonValues.number(text.indexOf("Child EXTENDS")),"endOffset",JsonValues.number(text.indexOf("Child EXTENDS")));
        var parameters=JsonValues.object("snapshotId",compiled.members().get("snapshotId"),"query",query);
        var old=result(call(bridge,"query",id,parameters));assertEquals("F.Child",BridgeCodec.string(old,"qualifiedName"));
        var members=(JsonValue.JsonArray)old.members().get("effectiveMembers");assertEquals(List.of("a","b","c"),members.values().stream().map(m->BridgeCodec.string((JsonValue.JsonObject)m,"name")).toList());
        assertEquals(List.of("F.Base","F.Child"),((JsonValue.JsonArray)((JsonValue.JsonObject)members.values().getFirst()).members().get("inheritancePath")).values().stream().map(v->((JsonValue.JsonString)v).value()).toList());
        result(call(bridge,"updateSource",id,JsonValues.object("sourceId",JsonValues.string("memory:/m.ili"),"expectation",JsonValues.object("kind",JsonValues.string("at"),"revision",JsonValues.string("1")),"change",JsonValues.object("kind",JsonValues.string("replace"),"text",JsonValues.string(text.replace("c:0..10;","c:0..10;d:TEXT;"))))));
        assertEquals(old,result(call(bridge,"query",id,parameters)));
        var wrong=new LinkedHashMap<>(query.members());wrong.put("revision",JsonValues.string("2"));
        var stale=call(bridge,"query",id,JsonValues.object("snapshotId",compiled.members().get("snapshotId"),"query",new JsonValue.JsonObject(wrong)));
        assertEquals("error",BridgeCodec.string(stale,"status"));
        result(call(bridge,"closeSession",id,JsonValues.object()));
    }
}
