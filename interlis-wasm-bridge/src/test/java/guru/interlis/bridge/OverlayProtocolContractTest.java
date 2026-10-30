package guru.interlis.bridge;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayProtocolContractTest {
    private static JsonValue.JsonObject call(BridgeDispatcher bridge,String operation,String session,Map<String,JsonValue> params) {
        var request=new LinkedHashMap<String,JsonValue>();request.put("protocol",JsonValues.string("interlis.compiler"));request.put("protocolVersion",JsonValues.string("1.0"));
        request.put("requestId",JsonValues.string("overlay"));request.put("operation",JsonValues.string(operation));if(session!=null)request.put("sessionId",JsonValues.string(session));request.put("params",new JsonValue.JsonObject(params));
        return (JsonValue.JsonObject)Json.parse(bridge.handle(Json.write(new JsonValue.JsonObject(request))));
    }
    @Test void invalidOverlayWireShapesAreRequestsErrorsAndDoNotConsumeSessions() {
        var bridge=new BridgeDispatcher();var cases=List.of("null","7","{\"kind\":\"unknown\",\"id\":\"x\",\"priority\":1,\"target\":\"F\"}",
                "{\"kind\":\"crs\",\"id\":\"x\",\"priority\":1.2,\"target\":\"F\",\"crs\":\"EPSG:2056\"}",
                "{\"kind\":\"meta-attribute\",\"id\":\"x\",\"priority\":1,\"target\":\"F\",\"attributes\":{\"label\":7}}",
                "{\"kind\":\"crs\",\"id\":\"x\",\"priority\":1,\"target\":null,\"crs\":\"EPSG:2056\"}");
        for(String value:cases) {
            var response=call(bridge,"openSession",null,Map.of("configuration",JsonValues.object("overlays",JsonValues.array(List.of(Json.parse(value))))));
            assertEquals("error",BridgeCodec.string(response,"status"));assertEquals("INVALID_REQUEST",BridgeCodec.string((JsonValue.JsonObject)response.members().get("error"),"code"),response.toString());
        }
        var opened=call(bridge,"openSession",null,Map.of());assertEquals("ok",BridgeCodec.string(opened,"status"));
        assertEquals("s-1",BridgeCodec.string((JsonValue.JsonObject)opened.members().get("result"),"sessionId"));
    }
    @Test void overlayPropertiesArePublicAndSessionIsolationIsPreserved() {
        var bridge=new BridgeDispatcher();String text="INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=DOMAIN P=COORD 0..10,0..10;END F.";
        var entry=Json.parse("{\"kind\":\"meta-attribute\",\"id\":\"external\",\"priority\":7,\"target\":\"F.P\",\"attributes\":{\"label\":[\"configured\"]}}");
        for(boolean configured:List.of(true,false)) {
            var params=new LinkedHashMap<String,JsonValue>();params.put("sources",JsonValues.array(List.of(JsonValues.object("sourceId",JsonValues.string("memory:/f.ili"),"text",JsonValues.string(text)))));
            if(configured)params.put("configuration",JsonValues.object("overlays",JsonValues.array(List.of(entry))));
            var open=call(bridge,"openSession",null,params);String id=BridgeCodec.string((JsonValue.JsonObject)open.members().get("result"),"sessionId");
            var compile=call(bridge,"compile",id,Map.of("expectedWorkspaceRevision",JsonValues.string("1"),"roots",JsonValues.array(List.of(JsonValues.object("kind",JsonValues.string("byName"),"modelName",JsonValues.string("F"))))));
            var result=(JsonValue.JsonObject)compile.members().get("result");assertEquals("succeeded",BridgeCodec.string(result,"outcome"));
            var query=call(bridge,"query",id,Map.of("snapshotId",result.members().get("snapshotId"),"query",JsonValues.object("kind",JsonValues.string("semanticProperties"),"sourceId",JsonValues.string("memory:/f.ili"),"revision",JsonValues.string("1"),"startOffset",JsonValues.number(text.indexOf("P=COORD")),"endOffset",JsonValues.number(text.indexOf("P=COORD")))));
            var metadata=(JsonValue.JsonArray)((JsonValue.JsonObject)query.members().get("result")).members().get("metadata");assertEquals(configured?1:0,metadata.values().size());
            if(configured)assertTrue(Json.write(metadata).contains("\"overlayId\":\"external\""));
            call(bridge,"closeSession",id,Map.of());
        }
    }
}
