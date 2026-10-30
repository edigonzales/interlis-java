package guru.interlis.bridge;
import guru.interlis.compiler.*;
import guru.interlis.compiler.SemanticOverlays.*;
import guru.interlis.compiler.SemanticOverlays.SemanticOverlay.*;
import java.util.*;

/** Wire conversion only; all target binding and semantic checks belong to compiler. */
final class OverlayCodec {
    static CompilerOptions options(JsonValue.JsonObject object,CompilerOptions current) {
        try{return decodeOptions(object,current);}
        catch(ClassCastException|NullPointerException|ArithmeticException invalid){throw new BridgeException("INVALID_REQUEST","invalid overlay configuration shape");}
    }
    private static CompilerOptions decodeOptions(JsonValue.JsonObject object,CompilerOptions current) {
        var languages=current.allowedLanguages();
        if(object.members().containsKey("allowedLanguages")) {
            var selected=new LinkedHashSet<guru.interlis.model.LanguageVersion>();
            for(var value:BridgeCodec.array(object,"allowedLanguages").values()) {
                var text=((JsonValue.JsonString)value).value();selected.add(guru.interlis.model.LanguageVersion.fromLiteral(text)
                        .orElseThrow(()->new BridgeException("INVALID_REQUEST","unsupported language "+text)));
            }
            languages=selected;
        }
        var overlays=current.overlays();
        if(object.members().containsKey("overlays")) {
            var entries=new ArrayList<SemanticOverlay>();
            for(var entry:BridgeCodec.array(object,"overlays").values())entries.add(read((JsonValue.JsonObject)entry));
            overlays=new SemanticOverlays(entries);
        }
        return new CompilerOptions(current.semanticProfile(),languages,current.warningPolicy(),current.budget(),overlays);
    }
    private static Target target(JsonValue value) {
        if(value instanceof JsonValue.JsonString text)return Target.named(text.value());
        var object=(JsonValue.JsonObject)value;return new Target(Target.named(BridgeCodec.string(object,"name")).name(),
                BridgeCodec.optionalString(object,"modelVersion"),BridgeCodec.optionalString(object,"sourceHint").map(guru.interlis.source.SourceId::new));
    }
    private static SemanticOverlay read(JsonValue.JsonObject object) {
        var source=new OverlaySource(BridgeCodec.string(object,"id"),Math.toIntExact(BridgeCodec.longValue(object.members().get("priority"),"priority")),BridgeCodec.optionalObject(object,"range").map(r->new guru.interlis.source.SourceRange(
                new guru.interlis.source.SourceId(BridgeCodec.string(r,"source")),new guru.interlis.source.SourceRevision(BridgeCodec.longValue(r.members().get("revision"),"revision")),
                Math.toIntExact(BridgeCodec.longValue(r.members().get("startOffset"),"startOffset")),Math.toIntExact(BridgeCodec.longValue(r.members().get("endOffset"),"endOffset")))));
        var target=target(object.members().get("target"));
        return switch(BridgeCodec.string(object,"kind")) {
            case "translation"->new TranslationOverlay(source,target,target(object.members().get("original")));
            case "crs"->new CrsOverlay(source,target,BridgeCodec.string(object,"crs"));
            case "meta-attribute"->{
                var attributes=new LinkedHashMap<String,List<String>>();
                var values=BridgeCodec.optionalObject(object,"attributes").orElseThrow(()->new BridgeException("INVALID_REQUEST","missing attributes"));
                values.members().forEach((key,value)->{var list=new ArrayList<String>();for(var item:((JsonValue.JsonArray)value).values())list.add(((JsonValue.JsonString)item).value());attributes.put(key,list);});
                yield new MetaAttributeOverlay(source,target,attributes);
            }
            default->throw new BridgeException("INVALID_REQUEST","unknown overlay kind");
        };
    }
}
