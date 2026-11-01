package guru.interlis.lsp;

import com.google.gson.*;
import guru.interlis.projection.*;
import guru.interlis.model.*;
import java.util.*;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

/** Strict host decoder of the shared DiagramOptions contract. Core defaults and semantic validation stay authoritative. */
final class DiagramOptionsDecoder {
    static DiagramOptions decode(JsonObject value,DiagramSelection selection) {
        var defaults=DiagramOptions.defaults(selection);
        if(value==null)value=new JsonObject();
        else {keys(value,"schemaVersion","scope","visibleKinds","inheritedMembers","showTypes","showRoleCardinalities","showConstraints","showImports","labelPolicy","associationPresentation","budget","translationModel","neighbourhoodDepth");
            if(!text(value,"schemaVersion","").equals("1.0"))throw invalid("unsupported diagram options schema");}
        var kinds=defaults.visibleKinds();
        if(value.has("visibleKinds")) {
            if(!value.get("visibleKinds").isJsonArray())throw invalid("visibleKinds must be an array");
            var selected=EnumSet.noneOf(DiagramNodeKind.class);
            for(var item:value.getAsJsonArray("visibleKinds")) {
                if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString())throw invalid("visibleKinds must contain strings");
                try {if(!selected.add(DiagramNodeKind.valueOf(item.getAsString())))throw invalid("duplicate visible kind");}
                catch(IllegalArgumentException bad) {throw invalid("unknown visible kind");}
            }
            kinds=selected;
        }
        var budget=new ProjectionBudget(500,1000,200);
        if(value.has("budget")) {var b=object(value,"budget");keys(b,"maxNodes","maxEdges","maxMembersPerNode");
            budget=new ProjectionBudget(integer(b,"maxNodes",500,1,500),integer(b,"maxEdges",1000,1,1000),integer(b,"maxMembersPerNode",200,1,200));}
        Optional<SymbolRef> translation=Optional.empty();
        if(value.has("translationModel")) {var ref=object(value,"translationModel");keys(ref,"snapshot","symbol");
            translation=Optional.of(new SymbolRef(new SnapshotId(text(ref,"snapshot","")),new SymbolId(text(ref,"symbol",""))));}
        var depth=value.has("neighbourhoodDepth")?OptionalInt.of(integer(value,"neighbourhoodDepth",0,0,32)):OptionalInt.empty();
        return new DiagramOptions(selection,enumeration(value,"scope",DiagramScope.class,defaults.scope()),kinds,
                enumeration(value,"inheritedMembers",InheritedMembers.class,defaults.inheritedMembers()),
                bool(value,"showTypes",defaults.showTypes()),bool(value,"showRoleCardinalities",defaults.showRoleCardinalities()),
                bool(value,"showConstraints",defaults.showConstraints()),bool(value,"showImports",defaults.showImports()),
                enumeration(value,"labelPolicy",LabelPolicy.class,defaults.labelPolicy()),
                enumeration(value,"associationPresentation",AssociationPresentation.class,defaults.associationPresentation()),budget,translation,depth);
    }
    private static JsonObject object(JsonObject value,String name) {if(!value.get(name).isJsonObject())throw invalid(name+" must be an object");return value.getAsJsonObject(name);}
    private static void keys(JsonObject value,String...allowed) {if(!Set.of(allowed).containsAll(value.keySet()))throw invalid("unknown diagram option");}
    private static String text(JsonObject value,String name,String fallback) {if(!value.has(name))return fallback;var item=value.get(name);if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString())throw invalid(name+" must be text");return item.getAsString();}
    private static boolean bool(JsonObject value,String name,boolean fallback) {if(!value.has(name))return fallback;var item=value.get(name);if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isBoolean())throw invalid(name+" must be boolean");return item.getAsBoolean();}
    private static int integer(JsonObject value,String name,int fallback,int min,int max) {
        if(!value.has(name))return fallback;var item=value.get(name);
        try {if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isNumber())throw invalid(name+" must be an integer");int number=item.getAsBigDecimal().toBigIntegerExact().intValueExact();if(number<min||number>max)throw invalid(name+" outside presentation budget");return number;}
        catch(ArithmeticException bad) {throw invalid(name+" must be an integer");}
    }
    private static <E extends Enum<E>> E enumeration(JsonObject value,String name,Class<E> type,E fallback) {
        if(!value.has(name))return fallback;try{return Enum.valueOf(type,text(value,name,""));}catch(IllegalArgumentException bad){throw invalid("unknown diagram option "+name);}
    }
    private static RuntimeException invalid(String message) {return LspFailures.error(ResponseErrorCode.InvalidParams,message);}
}
