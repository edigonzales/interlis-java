package guru.interlis.bridge;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.source.SourceId;
import guru.interlis.workspace.resolution.ResolutionPolicy;
import java.util.*;

/** Converts explicit wire selection data only; the workspace resolver selects actual headers. */
final class ResolutionPolicyCodec {
    static ResolutionPolicy read(JsonValue.JsonObject configuration) {
        try {
            if (configuration.members().containsKey("resolutionPolicy")
                    && !(configuration.members().get("resolutionPolicy") instanceof JsonValue.JsonObject))
                throw new IllegalArgumentException("policy must be an object");
            var object = BridgeCodec.optionalObject(configuration, "resolutionPolicy");
            if (object.isEmpty()) return ResolutionPolicy.defaults();
            var value = object.orElseThrow();
            boolean prefer = true;
            if (value.members().containsKey("preferWorkspaceSource"))
                prefer = ((JsonValue.JsonBoolean) value.members().get("preferWorkspaceSource")).value();
            var selections = new LinkedHashMap<String, ModelSelector.ByName>();
            if (value.members().containsKey("modelSelections")) for (var element : BridgeCodec.array(value, "modelSelections").values()) {
                var selection = (JsonValue.JsonObject) element;
                var query = new ModelSelector.ByName(BridgeCodec.string(selection, "modelName"),
                        BridgeCodec.optionalString(selection, "modelVersion"), BridgeCodec.optionalString(selection, "sourceHint").map(SourceId::new));
                if (selections.putIfAbsent(query.modelName(), query) != null) throw new IllegalArgumentException("duplicate selection");
            }
            var ranks = new LinkedHashMap<SourceId, Integer>();
            if (value.members().containsKey("sourcePriorities")) for (var element : BridgeCodec.array(value, "sourcePriorities").values()) {
                var rank = (JsonValue.JsonObject) element;
                var id = new SourceId(BridgeCodec.string(rank, "sourceId"));
                if (ranks.putIfAbsent(id, Math.toIntExact(BridgeCodec.longValue(rank.members().get("priority"), "priority"))) != null)
                    throw new IllegalArgumentException("duplicate source rank");
            }
            return new ResolutionPolicy(prefer, selections, ranks);
        } catch (ClassCastException | NullPointerException | ArithmeticException | IllegalArgumentException invalid) {
            throw new BridgeException("INVALID_REQUEST", "invalid resolution policy");
        }
    }
}
