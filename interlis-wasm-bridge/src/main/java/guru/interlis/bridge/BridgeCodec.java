package guru.interlis.bridge;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.execution.CancellationReason;
import guru.interlis.model.VersionManifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit protocol 1.0 request/response codec. */
public final class BridgeCodec {
    public static final String PROTOCOL = VersionManifest.PROTOCOL_NAME;
    public static final String PROTOCOL_VERSION = VersionManifest.PROTOCOL_VERSION;
    public static final String TOOL_VERSION = VersionManifest.TOOL_VERSION;

    BridgeRequest decode(String json) {
        JsonValue value;
        try {
            value = Json.parse(json);
        } catch (JsonException e) {
            throw new BridgeException("INVALID_JSON", e.getMessage());
        }
        if (!(value instanceof JsonValue.JsonObject object)) {
            throw new BridgeException("INVALID_REQUEST", "request must be an object");
        }
        String protocol = string(object, "protocol");
        String version = string(object, "protocolVersion");
        if (!PROTOCOL.equals(protocol)) {
            throw new BridgeException("INVALID_REQUEST", "unknown protocol");
        }
        if (!PROTOCOL_VERSION.equals(version)) {
            throw new BridgeException("UNSUPPORTED_PROTOCOL", "unsupported protocol version");
        }
        String requestId = string(object, "requestId");
        String operation = string(object, "operation");
        Optional<String> sessionId = optionalString(object, "sessionId");
        JsonValue paramsValue = object.members().getOrDefault("params", new JsonValue.JsonObject(Map.of()));
        if (!(paramsValue instanceof JsonValue.JsonObject params)) {
            throw new BridgeException("INVALID_REQUEST", "params must be an object");
        }
        return new BridgeRequest(protocol, version, requestId, sessionId, operation, params);
    }

    String success(BridgeRequest request, Optional<String> sessionId, JsonValue result) {
        Map<String, JsonValue> response = new LinkedHashMap<>();
        response.put("protocol", JsonValues.string(PROTOCOL));
        response.put("protocolVersion", JsonValues.string(PROTOCOL_VERSION));
        response.put("requestId", JsonValues.string(request.requestId()));
        sessionId.ifPresent(id -> response.put("sessionId", JsonValues.string(id)));
        response.put("status", JsonValues.string("ok"));
        response.put("result", result);
        return Json.write(new JsonValue.JsonObject(response));
    }

    String error(BridgeRequest request, Optional<String> sessionId, String code,
                 String message, JsonValue.JsonObject details) {
        Map<String, JsonValue> response = new LinkedHashMap<>();
        response.put("protocol", JsonValues.string(PROTOCOL));
        response.put("protocolVersion", JsonValues.string(PROTOCOL_VERSION));
        response.put("requestId", JsonValues.string(request.requestId()));
        sessionId.ifPresent(id -> response.put("sessionId", JsonValues.string(id)));
        response.put("status", JsonValues.string("error"));
        response.put("error", JsonValues.object("code", JsonValues.string(code),
                "messageKey", JsonValues.string(message), "details", details));
        return Json.write(new JsonValue.JsonObject(response));
    }

    String cancelled(BridgeRequest request, Optional<String> sessionId,
                     CancellationReason reason) {
        Map<String, JsonValue> response = new LinkedHashMap<>();
        response.put("protocol", JsonValues.string(PROTOCOL));
        response.put("protocolVersion", JsonValues.string(PROTOCOL_VERSION));
        response.put("requestId", JsonValues.string(request.requestId()));
        sessionId.ifPresent(id -> response.put("sessionId", JsonValues.string(id)));
        response.put("status", JsonValues.string("cancelled"));
        response.put("cancellation", JsonValues.object("reason",
                JsonValues.string(reason.name())));
        return Json.write(new JsonValue.JsonObject(response));
    }

    JsonValue.JsonObject diagnostic(Diagnostic diagnostic) {
        Map<String, JsonValue> object = new LinkedHashMap<>();
        object.put("id", JsonValues.string(diagnostic.id().value()));
        object.put("code", JsonValues.string(diagnostic.code().value()));
        object.put("severity", JsonValues.string(diagnostic.severity().name()));
        object.put("phase", JsonValues.string(diagnostic.phase().name()));
        object.put("messageKey", JsonValues.string(diagnostic.messageKey()));
        diagnostic.primaryRange().ifPresent(range -> object.put("range",
                JsonValues.object("source", JsonValues.string(range.source().value()),
                        "revision", JsonValues.string(Long.toString(range.revision().value())),
                        "startOffset", JsonValues.number(range.startOffset()),
                        "endOffset", JsonValues.number(range.endOffset()))));
        List<JsonValue> arguments = new ArrayList<>();
        for (DiagnosticArgument argument : diagnostic.arguments()) {
            arguments.add(JsonValues.object("name", JsonValues.string(argument.name()),
                    "kind", JsonValues.string(kind(argument)),
                    "value", JsonValues.string(value(argument))));
        }
        object.put("arguments", JsonValues.array(arguments));
        diagnostic.causeId().ifPresent(id -> object.put("causeId",
                JsonValues.string(id.value())));
        diagnostic.symbolId().ifPresent(id -> object.put("symbolId",
                JsonValues.string(id.value())));
        return new JsonValue.JsonObject(object);
    }

    /**
     * Returns the {@code capabilities} value.
     *
     * @return the value
     */
    JsonValue.JsonObject capabilities() {
        return JsonValues.object(
                "protocol", JsonValues.string(PROTOCOL),
                "protocolVersion", JsonValues.string(PROTOCOL_VERSION),
                "toolVersion", JsonValues.string(TOOL_VERSION),
                "runtime", JsonValues.string("teavm-wasm-gc"),
                "languages", JsonValues.strings(List.of("2.3", "2.4")),
                "operations", JsonValues.strings(List.of("capabilities", "openSession",
                        "updateSource", "updateSources", "removeSource", "setConfiguration",
                        "analyze", "compile", "query", "diagram", "releaseSnapshot",
                        "closeSession")),
                "queries", JsonValues.strings(List.of("symbolAt", "definition", "semanticProperties", "hover",
                        "completion")),
                "features", JsonValues.strings(List.of("MODEL", "TOPIC", "CLASS", "STRUCTURE",
                        "DOMAIN", "ATTRIBUTE", "INHERITANCE", "ASSOCIATION_ROLE",
                        "MANDATORY_CONSTRAINT", "IMPORTS", "COORD", "MULTICOORD")),
                "limits", JsonValues.object("maxSourceUtf16Length",
                        JsonValues.number(2_097_152),
                        "maxTotalSourceUtf16Length", JsonValues.string("8388608"),
                        "maxSources", JsonValues.number(128),
                        "maxRetainedSnapshots", JsonValues.number(32),
                        "maxMessageUtf16Length", JsonValues.number(4_194_304),
                        "maxConcurrentSessions", JsonValues.number(8)));
    }

    static String kind(DiagnosticArgument argument) {
        return switch (argument) {
            case DiagnosticArgument.TextArgument ignored -> "TEXT";
            case DiagnosticArgument.IdentifierArgument ignored -> "IDENTIFIER";
            case DiagnosticArgument.TokenArgument ignored -> "TOKEN";
            case DiagnosticArgument.IntegerArgument ignored -> "INTEGER";
            case DiagnosticArgument.DecimalArgument ignored -> "DECIMAL";
            case DiagnosticArgument.TypeNameArgument ignored -> "TYPE_NAME";
            case DiagnosticArgument.IdentifierListArgument ignored -> "IDENTIFIER_LIST";
        };
    }

    static String value(DiagnosticArgument argument) {
        return switch (argument) {
            case DiagnosticArgument.TextArgument a -> a.value();
            case DiagnosticArgument.IdentifierArgument a -> a.value();
            case DiagnosticArgument.TokenArgument a -> a.value();
            case DiagnosticArgument.IntegerArgument a -> a.value().toString();
            case DiagnosticArgument.DecimalArgument a -> a.value().toPlainString();
            case DiagnosticArgument.TypeNameArgument a -> a.value();
            case DiagnosticArgument.IdentifierListArgument a -> String.join(",", a.values());
        };
    }

    static String string(JsonValue.JsonObject object, String name) {
        JsonValue value = object.members().get(name);
        if (!(value instanceof JsonValue.JsonString string)) {
            throw new BridgeException("INVALID_REQUEST", "missing string field " + name);
        }
        return string.value();
    }

    static Optional<String> optionalString(JsonValue.JsonObject object, String name) {
        JsonValue value = object.members().get(name);
        if (value == null || value instanceof JsonValue.JsonNull) {
            return Optional.empty();
        }
        if (!(value instanceof JsonValue.JsonString string)) {
            throw new BridgeException("INVALID_REQUEST", "field " + name + " must be a string");
        }
        return Optional.of(string.value());
    }

    static Optional<JsonValue.JsonObject> optionalObject(JsonValue.JsonObject object, String name) {
        JsonValue value = object.members().get(name);
        if (value == null || value instanceof JsonValue.JsonNull) {
            return Optional.empty();
        }
        if (!(value instanceof JsonValue.JsonObject nested)) {
            throw new BridgeException("INVALID_REQUEST", "field " + name + " must be an object");
        }
        return Optional.of(nested);
    }

    static JsonValue.JsonArray array(JsonValue.JsonObject object, String name) {
        JsonValue value = object.members().get(name);
        if (!(value instanceof JsonValue.JsonArray array)) {
            throw new BridgeException("INVALID_REQUEST", "missing array field " + name);
        }
        return array;
    }

    static JsonValue.JsonObject object(JsonValue value) {
        if (!(value instanceof JsonValue.JsonObject object)) throw new BridgeException("INVALID_REQUEST", "expected object");
        return object;
    }

    static int intValue(JsonValue value, String field) {
        try { return Math.toIntExact(longValue(value, field)); }
        catch (ArithmeticException invalid) { throw new BridgeException("INVALID_REQUEST", "integer out of range " + field); }
    }

    static long longValue(JsonValue value, String field) {
        if(value==null) throw new BridgeException("INVALID_REQUEST","missing number "+field);
        try { return switch (value) {
            case JsonValue.JsonString string -> {
                try {
                    yield Long.parseLong(string.value());
                } catch (NumberFormatException e) {
                    throw new BridgeException("INVALID_REQUEST", "invalid number " + field);
                }
            }
            case JsonValue.JsonNumber number -> number.value().longValueExact();
            default -> throw new BridgeException("INVALID_REQUEST", "invalid number " + field);
        }; } catch(ArithmeticException invalid) { throw new BridgeException("INVALID_REQUEST","invalid integer "+field); }
    }
}
