package guru.interlis.bridge;

import java.util.Objects;
import java.util.Optional;

/** Decoded request envelope. */
record BridgeRequest(String protocol, String protocolVersion, String requestId,
                     Optional<String> sessionId, String operation,
                     JsonValue.JsonObject params) {
    BridgeRequest {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(requestId, "requestId");
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(params, "params");
    }
}
