package guru.interlis.bridge;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeDispatcherTest {
    private static final String BASE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN Count = 0..999;
              TOPIC Common =
                CLASS BaseBuilding =
                  height : Count;
                END BaseBuilding;
              END Common;
            END Base.
            """;
    private static final String SURVEY = """
            INTERLIS 2.4;
            MODEL Survey AT "https://example.interlis.ch" VERSION "1.0" =
              IMPORTS Base;
              TOPIC Data =
    DEPENDS ON Base.Common;
                CLASS Building EXTENDS Base.Common.BaseBuilding =
                  height (EXTENDED) : Base.Count;
                  MANDATORY CONSTRAINT height > 0;
                END Building;
              END Data;
            END Survey.
            """;

    @Test
    void protocolRoundTripFromOpenToDiagram() {
        BridgeDispatcher bridge = new BridgeDispatcher();
        JsonValue.JsonObject opened = call(bridge, "openSession", null, Map.of());
        String sessionId = BridgeCodec.string(result(opened), "sessionId");
        JsonValue.JsonObject updated = call(bridge, "updateSources", sessionId, Map.of(
                "expectedWorkspaceRevision", JsonValues.string("0"),
                "updates", JsonValues.array(List.of(
                        source("memory:/base.ili", BASE),
                        source("memory:/survey.ili", SURVEY)))));
        assertEquals("ok", status(updated));
        JsonValue.JsonObject compiled = call(bridge, "compile", sessionId, Map.of(
                "expectedWorkspaceRevision", JsonValues.string("1"),
                "roots", roots("Survey")));
        assertEquals("ok", status(compiled), compiled::toString);
        assertEquals("succeeded", BridgeCodec.string(result(compiled), "outcome"),
                compiled::toString);
        String snapshotId = BridgeCodec.string(result(compiled), "snapshotId");
        String schemaId = BridgeCodec.string(result(compiled), "schemaId");
        int offset = SURVEY.indexOf("Building");
        JsonValue.JsonObject query = call(bridge, "query", sessionId, Map.of(
                "snapshotId", JsonValues.string(snapshotId),
                "query", JsonValues.object("kind", JsonValues.string("symbolAt"),
                        "sourceId", JsonValues.string("memory:/survey.ili"),
                        "revision", JsonValues.string("1"),
                        "startOffset", JsonValues.number(offset),
                        "endOffset", JsonValues.number(offset))));
        assertTrue(query.toString().contains("known"), query::toString);
        JsonValue.JsonObject diagram = call(bridge, "diagram", sessionId,
                Map.of("schemaId", JsonValues.string(schemaId)));
        assertFalse(((JsonValue.JsonArray) result(diagram).members().get("nodes"))
                .values().isEmpty());
        JsonValue.JsonObject released = call(bridge, "releaseSnapshot", sessionId,
                Map.of("snapshotId", JsonValues.string(snapshotId)));
        assertEquals("ok", status(released));
        JsonValue.JsonObject expired = call(bridge, "analyze", sessionId, Map.of(
                "expectedWorkspaceRevision", JsonValues.string("999"),
                "roots", roots("Survey")));
        assertEquals("VERSION_CONFLICT", errorCode(expired));
    }

    @Test
    void missingModelsAndInvalidProtocolAreTyped() {
        BridgeDispatcher bridge = new BridgeDispatcher();
        JsonValue.JsonObject opened = call(bridge, "openSession", null, Map.of());
        String sessionId = BridgeCodec.string(result(opened), "sessionId");
        call(bridge, "updateSource", sessionId, Map.of(
                "sourceId", JsonValues.string("memory:/survey.ili"),
                "expectation", JsonValues.object("kind", JsonValues.string("absent")),
                "change", JsonValues.object("kind", JsonValues.string("replace"),
                        "text", JsonValues.string(SURVEY))));
        JsonValue.JsonObject analyzed = call(bridge, "analyze", sessionId, Map.of(
                "expectedWorkspaceRevision", JsonValues.string("1"),
                "roots", roots("Survey")));
        assertFalse(((JsonValue.JsonArray) result(analyzed).members().get("missingModels"))
                .values().isEmpty());
        JsonValue.JsonObject invalid = (JsonValue.JsonObject) Json.parse(bridge.handle("{"));
        assertEquals("INVALID_JSON", errorCode(invalid));
        JsonValue.JsonObject version = (JsonValue.JsonObject) Json.parse(bridge.handle(
                "{\"protocol\":\"interlis.compiler\",\"protocolVersion\":\"9.0\","
                        + "\"requestId\":\"r\",\"operation\":\"capabilities\",\"params\":{}}"));
        assertEquals("UNSUPPORTED_PROTOCOL", errorCode(version));
    }


    @Test
    void closedSessionIsNotReusable() {
        BridgeDispatcher bridge = new BridgeDispatcher();
        JsonValue.JsonObject opened = call(bridge, "openSession", null, Map.of());
        String sessionId = BridgeCodec.string(result(opened), "sessionId");
        JsonValue.JsonObject closed = call(bridge, "closeSession", sessionId, Map.of());
        assertEquals("ok", status(closed));
        JsonValue.JsonObject afterClose = call(bridge, "analyze", sessionId, Map.of(
                "expectedWorkspaceRevision", JsonValues.string("0"),
                "roots", roots("Missing")));
        assertEquals("SESSION_NOT_FOUND", errorCode(afterClose));
    }

    private static JsonValue.JsonObject source(String id, String text) {
        return JsonValues.object("sourceId", JsonValues.string(id),
                "expectation", JsonValues.object("kind", JsonValues.string("absent")),
                "change", JsonValues.object("kind", JsonValues.string("replace"),
                        "text", JsonValues.string(text)));
    }

    private static JsonValue.JsonArray roots(String name) {
        return JsonValues.array(List.of(JsonValues.object("kind", JsonValues.string("byName"),
                "modelName", JsonValues.string(name))));
    }

    private static JsonValue.JsonObject call(BridgeDispatcher bridge, String operation,
                                             String sessionId, Map<String, JsonValue> params) {
        Map<String, JsonValue> request = new LinkedHashMap<>();
        request.put("protocol", JsonValues.string("interlis.compiler"));
        request.put("protocolVersion", JsonValues.string("1.0"));
        request.put("requestId", JsonValues.string("r-" + operation));
        if (sessionId != null) {
            request.put("sessionId", JsonValues.string(sessionId));
        }
        request.put("operation", JsonValues.string(operation));
        request.put("params", new JsonValue.JsonObject(params));
        return (JsonValue.JsonObject) Json.parse(bridge.handle(
                Json.write(new JsonValue.JsonObject(request))));
    }

    private static String status(JsonValue.JsonObject response) {
        return BridgeCodec.string(response, "status");
    }

    private static JsonValue.JsonObject result(JsonValue.JsonObject response) {
        return (JsonValue.JsonObject) response.members().get("result");
    }

    private static String errorCode(JsonValue.JsonObject response) {
        return BridgeCodec.string((JsonValue.JsonObject) response.members().get("error"), "code");
    }
}
