package guru.interlis.bridge;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class P3RootSelectorWireTest {
    @Test void rejectedCompilePreservesEverySelectorAsStructuredWireData() {
        var bridge=new BridgeDispatcher();var codec=new BridgeCodec();int sequence=0;
        var opening=codec.decode("{\"protocol\":\"interlis.compiler\",\"protocolVersion\":\"1.0\",\"requestId\":\"open\",\"operation\":\"openSession\",\"params\":{\"sources\":[{\"sourceId\":\"memory:/bad\",\"text\":\"INTERLIS 2.4;MODEL Bad AT \\\"x\\\" VERSION \\\"v1\\\"=STRUCTURE S=n:Missing;END S;END Bad.\"}]}}");
        var opened=BridgeCodec.object(BridgeCodec.object(Json.parse(bridge.handle(Json.write(request(opening))))).members().get("result"));
        var session=BridgeCodec.string(opened,"sessionId");
        try {
            for(var selector:List.of(
                    JsonValues.object("kind",JsonValues.string("byName"),"modelName",JsonValues.string("Bad")),
                    JsonValues.object("kind",JsonValues.string("byName"),"modelName",JsonValues.string("Bad"),"modelVersion",JsonValues.string("v1"),"sourceHint",JsonValues.string("memory:/bad")),
                    JsonValues.object("kind",JsonValues.string("bySource"),"source",JsonValues.string("memory:/bad")))) {
                var roots=JsonValues.array(List.of(selector));
                var envelope=JsonValues.object("protocol",JsonValues.string("interlis.compiler"),"protocolVersion",JsonValues.string("1.0"),"requestId",JsonValues.string("compile-"+(++sequence)),"operation",JsonValues.string("compile"),"sessionId",JsonValues.string(session),"params",JsonValues.object("expectedWorkspaceRevision",opened.members().get("workspaceRevision"),"roots",roots));
                var response=BridgeCodec.object(Json.parse(bridge.handle(Json.write(envelope))));assertEquals("ok",BridgeCodec.string(response,"status"),Json.write(response));
                var result=BridgeCodec.object(response.members().get("result"));assertEquals("rejected",BridgeCodec.string(result,"outcome"));
                var rejection=BridgeCodec.object(result.members().get("rejection"));assertEquals(roots,rejection.members().get("requestedRoots"));
                var resolved=BridgeCodec.string(selector,"kind").equals("bySource")?roots:JsonValues.array(List.of(JsonValues.object(
                        "kind",JsonValues.string("byName"),"modelName",JsonValues.string("Bad"),"modelVersion",JsonValues.string("v1"),"sourceHint",JsonValues.string("memory:/bad"))));
                assertEquals(resolved,rejection.members().get("resolvedRoots"));
            }
        } finally {bridge.handle("{\"protocol\":\"interlis.compiler\",\"protocolVersion\":\"1.0\",\"requestId\":\"close\",\"operation\":\"closeSession\",\"sessionId\":\""+session+"\",\"params\":{}}");}
    }
    private static JsonValue.JsonObject request(BridgeRequest request) {
        return JsonValues.object("protocol",JsonValues.string(request.protocol()),"protocolVersion",JsonValues.string(request.protocolVersion()),"requestId",JsonValues.string(request.requestId()),"operation",JsonValues.string(request.operation()),"params",request.params());
    }
}
