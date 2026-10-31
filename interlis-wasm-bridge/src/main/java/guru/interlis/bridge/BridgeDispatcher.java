package guru.interlis.bridge;

import guru.interlis.execution.OperationCancelledException;
import guru.interlis.workspace.UpdateRejectedException;

import java.util.Objects;
import java.util.Optional;

/** Synchronous protocol dispatcher used by TeaVM, Web Image and tests. */
public final class BridgeDispatcher {
    private final BridgeCodec codec = new BridgeCodec();
    private final SessionRegistry sessions;
    private long failureSequence;

    /** Creates a dispatcher with its own session registry. */
    public BridgeDispatcher() {
        this(new SessionRegistry());
    }

    /**
     * Creates a dispatcher over an explicit session registry.
     *
     * @param sessions session registry owned by the host
     */
    public BridgeDispatcher(SessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Handles one JSON request synchronously and returns one JSON envelope.
     *
     * <p>Transport/protocol errors, modelled rejections, cancellation and internal
     * failures are represented by distinct status/error fields.</p>
     *
     * @param requestJson one protocol request
     * @return one protocol response envelope
     */
    public String handle(String requestJson) {
        BridgeRequest request;
        MessageHeader header;
        try {
            header=MessageHeader.read(requestJson);
            if(header.sessionId().isPresent()) {
                var existing=sessions.find(header.sessionId().orElseThrow());
                if(existing.isPresent() && requestJson.length()>existing.orElseThrow().limits().maxMessageUtf16Length())
                    return errorEnvelope(header.requestId(),header.sessionId().orElseThrow(),"LIMIT_EXCEEDED","session message UTF-16 limit reached",new JsonValue.JsonObject(java.util.Map.of()));
            }
            request = codec.decode(requestJson);
        } catch (BridgeException failure) {
            return errorEnvelope("unknown", "unknown", failure.code(),
                    failure.getMessage(), failure.details());
        }
        Optional<String> sessionId = request.sessionId();
        try {
            if ("capabilities".equals(request.operation())) {
                return codec.success(request, Optional.empty(), codec.capabilities());
            }
            if ("openSession".equals(request.operation())) {
                if(requestJson.length()>SessionLimits.read(request.params()).maxMessageUtf16Length())
                    throw new BridgeException("LIMIT_EXCEEDED","session opening message UTF-16 limit reached");
                JsonValue.JsonObject opened = sessions.open(request.params());
                String id = BridgeCodec.string(opened, "sessionId");
                return codec.success(request, Optional.of(id), opened);
            }
            if (sessionId.isEmpty()) {
                throw new BridgeException("INVALID_REQUEST", "sessionId required");
            }
            if ("closeSession".equals(request.operation())) {
                sessions.close(sessionId.get());
                return codec.success(request, sessionId, JsonValues.object("closed", JsonValues.bool(true)));
            }
            CompilerSessionFacade session = sessions.require(sessionId.get());
            return switch (request.operation()) {
                case "updateSource" -> codec.success(request, sessionId,
                        session.updateSource(request.params()));
                case "updateSources" -> codec.success(request, sessionId,
                        session.updateSources(request.params()));
                case "removeSource" -> codec.success(request, sessionId,
                        session.removeSource(request.params()));
                case "setConfiguration" -> codec.success(request, sessionId,
                        session.setConfiguration(request.params()));
                case "analyze" -> codec.success(request, sessionId,
                        session.analyze(request.params()));
                case "compile" -> codec.success(request, sessionId,
                        session.compile(request.params()));
                case "query" -> codec.success(request, sessionId,
                        session.query(request.params()));
                case "diagram" -> codec.success(request, sessionId,
                        session.diagram(request.params()));
                case "releaseSnapshot" -> {
                    session.releaseSnapshot(BridgeCodec.string(request.params(), "snapshotId"));
                    yield codec.success(request, sessionId,
                            JsonValues.object("released", JsonValues.bool(true)));
                }
                default -> throw new BridgeException("UNSUPPORTED_OPERATION",
                        "unknown operation " + request.operation());
            };
        } catch (BridgeException failure) {
            return codec.error(request, sessionId, failure.code(), failure.getMessage(),
                    failure.details());
        } catch (UpdateRejectedException failure) {
            String code=switch(failure.code()) {
                case SOURCE_LIMIT_EXCEEDED -> "LIMIT_EXCEEDED";
                case INVALID_EDIT,INVALID_CONFIGURATION -> "INVALID_REQUEST";
                case WORKSPACE_CLOSED -> "SESSION_NOT_FOUND";
                default -> "VERSION_CONFLICT";
            };
            return codec.error(request, sessionId, code, failure.getMessage(),
                    JsonValues.object("updateCode", JsonValues.string(failure.code().name())));
        } catch (OperationCancelledException cancelled) {
            return codec.cancelled(request, sessionId, cancelled.reason());
        } catch (IllegalArgumentException failure) {
            return codec.error(request, sessionId, "INVALID_REQUEST",
                    failure.getMessage() == null ? "invalid request" : failure.getMessage(),
                    new JsonValue.JsonObject(java.util.Map.of()));
        } catch (RuntimeException failure) {
            return codec.error(request, sessionId, "INTERNAL_FAILURE",
                    "bridge.internalFailure",
                    JsonValues.object("failureId", JsonValues.string(failure instanceof guru.interlis.compiler.CompilerInternalException internal
                            ? internal.failureId().value() : "bridge-failure-" + (++failureSequence))));
        }
    }

    private static String errorEnvelope(String requestId, String sessionId, String code,
                                        String message, JsonValue.JsonObject details) {
        return Json.write(JsonValues.object(
                "protocol", JsonValues.string(BridgeCodec.PROTOCOL),
                "protocolVersion", JsonValues.string(BridgeCodec.PROTOCOL_VERSION),
                "requestId", JsonValues.string(requestId),
                "sessionId", JsonValues.string(sessionId),
                "status", JsonValues.string("error"),
                "error", JsonValues.object("code", JsonValues.string(code),
                        "messageKey", JsonValues.string(message),
                        "details", details)));
    }
}
