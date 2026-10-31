package guru.interlis.bridge;

import guru.interlis.compiler.CompilerEngine;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.SemanticProfileId;
import guru.interlis.compiler.SemanticOverlays;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded session registry; sessions are independent workspaces. */
public final class SessionRegistry {
    private static final int MAX_SESSIONS = 8;
    private final Map<String, CompilerSessionFacade> sessions = new LinkedHashMap<>();
    private long counter;

    /**
     * Executes {@code open}.
     *
     * @param params the {@code params} argument
     *
     * @return the result
     */
    public JsonValue.JsonObject open(JsonValue.JsonObject params) {
        if(!java.util.Set.of("configuration","sources","limits").containsAll(params.members().keySet())) throw new BridgeException("INVALID_REQUEST","unknown session option");
        if(params.members().containsKey("sources") && !(params.members().get("sources") instanceof JsonValue.JsonArray)) throw new BridgeException("INVALID_REQUEST","sources must be an array");
        SessionLimits limits=SessionLimits.read(params);
        int concurrency=Math.min(MAX_SESSIONS,limits.maxConcurrentSessions());
        for(var session:sessions.values()) concurrency=Math.min(concurrency,session.limits().maxConcurrentSessions());
        if (sessions.size() >= concurrency) {
            throw new BridgeException("LIMIT_EXCEEDED", "session limit reached");
        }
        WorkspaceConfiguration configuration = WorkspaceConfigurationCodec.read(BridgeCodec.optionalObject(params,"configuration").orElseGet(() -> JsonValues.object()));
        CompilerEngine engine = CompilerEngines.create();
        Workspace workspace = Workspaces.create(engine, configuration);
        var facade=new CompilerSessionFacade(engine,workspace,limits);
        if (params.members().get("sources") instanceof JsonValue.JsonArray sources) {
            try {
                var updates=new java.util.ArrayList<JsonValue>();
                for (JsonValue value : sources.values()) {
                    JsonValue.JsonObject source = BridgeCodec.object(value);
                    SourceId id = new SourceId(BridgeCodec.string(source, "sourceId"));
                    String text = BridgeCodec.string(source, "text");
                    updates.add(JsonValues.object("sourceId",JsonValues.string(id.value()),"expectation",JsonValues.object("kind",JsonValues.string("absent")),
                            "change",JsonValues.object("kind",JsonValues.string("replace"),"text",JsonValues.string(text))));
                }
                facade.updateSources(JsonValues.object("expectedWorkspaceRevision",JsonValues.string("0"),"updates",JsonValues.array(updates)));
            } catch (RuntimeException failure) {
                workspace.close();
                throw failure;
            }
        }
        String id = "s-" + (++counter);
        sessions.put(id, facade);
        Map<String, JsonValue> result = new LinkedHashMap<>();
        result.put("sessionId", JsonValues.string(id));
        result.put("workspaceRevision", JsonValues.string(Long.toString(
                workspace.currentRevision().value())));
        result.put("limits", limits.encode());
        var sourceRevisions=new LinkedHashMap<String,JsonValue>();workspace.state().sourceRevisions().forEach((source,revision) -> sourceRevisions.put(source.value(),JsonValues.string(Long.toString(revision.value()))));
        result.put("sourceRevisions",new JsonValue.JsonObject(sourceRevisions));
        result.put("configurationFingerprint",JsonValues.string(configuration.semanticFingerprint()));
        return new JsonValue.JsonObject(result);
    }

    /**
     * Executes {@code require}.
     *
     * @param sessionId the {@code sessionId} argument
     *
     * @return the result
     */
    public CompilerSessionFacade require(String sessionId) {
        CompilerSessionFacade session = sessions.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (session == null) {
            throw new BridgeException("SESSION_NOT_FOUND", "unknown session");
        }
        return session;
    }

    Optional<CompilerSessionFacade> find(String id) { return Optional.ofNullable(sessions.get(id)); }

    /**
     * Executes {@code close}.
     *
     * @param sessionId the {@code sessionId} argument
     */
    public void close(String sessionId) {
        CompilerSessionFacade session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    /** Closes all sessions and clears the registry. */
    public void closeAll() {
        sessions.values().forEach(CompilerSessionFacade::close);
        sessions.clear();
    }
}
