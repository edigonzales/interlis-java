package guru.interlis.bridge;

import guru.interlis.projection.*;
import java.util.*;

/** Versioned wire options mapped to the one shared projection contract. */
final class DiagramOptionsCodec {
    private DiagramOptionsCodec() { }
    static DiagramOptions read(JsonValue.JsonObject params,DiagramSelection selection) {
        var defaults=DiagramOptions.defaults(selection);
        if(!params.members().containsKey("options"))return defaults;
        var value=BridgeCodec.object(params.members().get("options"));
        keys(value,"schemaVersion","scope","visibleKinds","inheritedMembers","showTypes","showRoleCardinalities","showConstraints","showImports","labelPolicy","associationPresentation","budget","translationModel","neighbourhoodDepth");
        if(!BridgeCodec.string(value,"schemaVersion").equals("1.0"))throw new BridgeException("UNSUPPORTED_OPERATION","unsupported diagram options version");
        var kinds=defaults.visibleKinds();
        if(value.members().containsKey("visibleKinds")) {
            var selected=EnumSet.noneOf(DiagramNodeKind.class);
            for(var item:BridgeCodec.array(value,"visibleKinds").values()) {
                if(!(item instanceof JsonValue.JsonString text))throw new BridgeException("INVALID_REQUEST","visibleKinds must contain strings");
                DiagramNodeKind kind;try{kind=DiagramNodeKind.valueOf(text.value());}catch(IllegalArgumentException invalid){throw new BridgeException("INVALID_REQUEST","unknown visible kind");}
                if(!selected.add(kind))throw new BridgeException("INVALID_REQUEST","duplicate visible kind");
            }
            kinds=selected;
        }
        var budget=defaults.budget();
        if(value.members().containsKey("budget")) {
            var b=BridgeCodec.object(value.members().get("budget"));keys(b,"maxNodes","maxEdges","maxMembersPerNode");
            budget=new ProjectionBudget(integer(b,"maxNodes",budget.maxNodes()),integer(b,"maxEdges",budget.maxEdges()),integer(b,"maxMembersPerNode",budget.maxMembersPerNode()));
        }
        Optional<guru.interlis.model.SymbolRef> translation=Optional.empty();
        if(value.members().containsKey("translationModel")) {
            var ref=BridgeCodec.object(value.members().get("translationModel"));keys(ref,"snapshot","symbol");
            translation=Optional.of(new guru.interlis.model.SymbolRef(new guru.interlis.model.SnapshotId(BridgeCodec.string(ref,"snapshot")),
                    new guru.interlis.model.SymbolId(BridgeCodec.string(ref,"symbol"))));
        }
        if(translation.isPresent()&&!translation.orElseThrow().snapshot().equals(selection.symbols().getFirst().snapshot()))
            throw new BridgeException("SNAPSHOT_MISMATCH","translation selection belongs to another snapshot");
        var depth=value.members().containsKey("neighbourhoodDepth")?OptionalInt.of(integer(value,"neighbourhoodDepth",0)):OptionalInt.empty();
        return new DiagramOptions(selection,enumeration(value,"scope",DiagramScope.class,defaults.scope()),kinds,
                enumeration(value,"inheritedMembers",InheritedMembers.class,defaults.inheritedMembers()),
                bool(value,"showTypes",defaults.showTypes()),bool(value,"showRoleCardinalities",defaults.showRoleCardinalities()),
                bool(value,"showConstraints",defaults.showConstraints()),bool(value,"showImports",defaults.showImports()),
                enumeration(value,"labelPolicy",LabelPolicy.class,defaults.labelPolicy()),
                enumeration(value,"associationPresentation",AssociationPresentation.class,defaults.associationPresentation()),budget,translation,depth);
    }
    private static void keys(JsonValue.JsonObject value,String... allowed) {
        if(!Set.of(allowed).containsAll(value.members().keySet()))throw new BridgeException("INVALID_REQUEST","unknown diagram option");
    }
    private static int integer(JsonValue.JsonObject value,String name,int fallback) {
        if(!value.members().containsKey(name))return fallback;
        if(!(value.members().get(name) instanceof JsonValue.JsonNumber))throw new BridgeException("INVALID_REQUEST","diagram integer required: "+name);
        return BridgeCodec.intValue(value.members().get(name),name);
    }
    private static boolean bool(JsonValue.JsonObject value,String name,boolean fallback) {
        if(!value.members().containsKey(name))return fallback;
        if(value.members().get(name) instanceof JsonValue.JsonBoolean bool)return bool.value();
        throw new BridgeException("INVALID_REQUEST","diagram boolean required: "+name);
    }
    private static <E extends Enum<E>> E enumeration(JsonValue.JsonObject value,String name,Class<E> type,E fallback) {
        if(!value.members().containsKey(name))return fallback;
        try{return Enum.valueOf(type,BridgeCodec.string(value,name));}
        catch(IllegalArgumentException invalid){throw new BridgeException("INVALID_REQUEST","unknown diagram option value: "+name);}
    }
}
