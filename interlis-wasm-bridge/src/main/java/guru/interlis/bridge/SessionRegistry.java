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
        if (sessions.size() >= MAX_SESSIONS) {
            throw new BridgeException("LIMIT_EXCEEDED", "session limit reached");
        }
        WorkspaceConfiguration configuration = WorkspaceConfiguration.defaults();
        Optional<JsonValue.JsonObject> config = BridgeCodec.optionalObject(params, "configuration");
        if(config.isPresent())configuration=new WorkspaceConfiguration(
                OverlayCodec.options(config.orElseThrow(),configuration.compilerOptions()),
                configuration.resolutionPolicy(),configuration.resolverCatalog(),configuration.cacheLimits());
        CompilerEngine engine = CompilerEngines.create();
        Workspace workspace = Workspaces.create(engine, configuration);
        if (params.members().get("sources") instanceof JsonValue.JsonArray sources) {
            try {
                for (JsonValue value : sources.values()) {
                    JsonValue.JsonObject source = (JsonValue.JsonObject) value;
                    SourceId id = new SourceId(BridgeCodec.string(source, "sourceId"));
                    String text = BridgeCodec.string(source, "text");
                    workspace.update(new SourceUpdate(id, new SourceExpectation.Absent(),
                            new SourceChange.Replace(text)));
                }
            } catch (RuntimeException failure) {
                workspace.close();
                throw failure;
            }
        }
        String id = "s-" + (++counter);
        sessions.put(id, new CompilerSessionFacade(engine, workspace));
        Map<String, JsonValue> result = new LinkedHashMap<>();
        result.put("sessionId", JsonValues.string(id));
        result.put("workspaceRevision", JsonValues.string(Long.toString(
                workspace.currentRevision().value())));
        result.put("limits", JsonValues.object("maxSources", JsonValues.number(128),
                "maxRetainedSnapshots", JsonValues.number(32),
                "maxMessageUtf16Length", JsonValues.number(4_194_304)));
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
