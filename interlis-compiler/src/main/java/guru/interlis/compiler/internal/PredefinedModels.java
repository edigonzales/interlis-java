package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import java.util.*;

/** Canonical versioned owner of the predefined declarations materialized in one graph. */
final class PredefinedModels {
    private PredefinedModels() { }
    static SymbolId id(LanguageVersion language) { return new SymbolId("predefined:"+language+":model:INTERLIS"); }
    // Appendix-A order, using actual binding names (unit designations are documentary).
    private static final List<String> ORDER=List.of("STRAIGHTS","ARCS","ANYUNIT","DIMENSIONLESS","LENGTH","MASS","TIME","ELECTRIC_CURRENT",
            "TEMPERATURE","AMOUNT_OF_MATTER","ANGLE","SOLID_ANGLE","LUMINOUS_INTENSITY","MONEY","m","kg","s","A","K","mol","rad","sr","cd",
            "URI","NAME","INTERLIS_1_DATE","BOOLEAN","HALIGNMENT","VALIGNMENT","NOOID","ANYOID","I32OID","STANDARDOID","UUIDOID","LineCoord",
            "myClass","isSubClass","isOfClass","elementCount","objectCount","len","lenM","trim","trimM","isEnumSubVal","inEnumRange","convertUnit",
            "length","multilength","surface","multisurface","areAreas","areAreas2","areAreas3",
            "METAOBJECT","METAOBJECT_TRANSLATION","AXIS","REFSYSTEM","COORDSYSTEM","SCALSYSTEM","SIGN","TIMESYSTEMS",
            "min","h","d","M","Y","BaseTimeSystems","TimeOfDay","UTC","GregorianYear","GregorianDate","GregorianDateTime","XMLTime","XMLDate","XMLDateTime",
            "LineSegment","StartSegment","StraightSegment","ArcSegment","SurfaceEdge","SurfaceBoundary","LineGeometry",
            "areAreasWithoutGaps","areAreasWithoutGaps2");
    static void finish(GraphBuilder graph, Set<LanguageVersion> languages) {
        // Analysis also publishes declarations outside the requested closure.
        // Their predefined values need owners even when their language is not
        // part of the selected schema's language set.
        Set<LanguageVersion> materialized=new LinkedHashSet<>(languages);
        for(var symbol:graph.symbols.values()) {
            graph.checkWork();
            if(symbol.origin() instanceof Origin.PredefinedOrigin p)materialized.add(p.language());
        }
        for(var language:materialized) {
            graph.checkWork();var model=id(language);var origin=new Origin.PredefinedOrigin(language,"INTERLIS");
            var members=graph.symbols.values().stream().filter(s->s.owner().isEmpty()
                    && s.origin() instanceof Origin.PredefinedOrigin p && p.language()==language && p.key().startsWith("INTERLIS."))
                    .sorted(Comparator.comparingInt((Symbol s)->order(((Origin.PredefinedOrigin)s.origin()).key()))
                            .thenComparing(s->((Origin.PredefinedOrigin)s.origin()).key())).toList();
            for(var member:members) {
                graph.checkWork();var h=member.header();
                var owned=new SymbolHeader(h.id(),h.kind(),h.name(),Optional.of(model),order(((Origin.PredefinedOrigin)h.origin()).key()),h.origin(),h.nameRange(),
                        h.languageVersion(),h.documentation(),h.metaAttributes(),h.modifiers(),h.oidRules());
                graph.symbols.put(member.id(),withHeader(member,owned));
            }
            graph.symbols.put(model, declaration(language, members.stream().map(Symbol::id).toList()));
        }
    }
    static SymbolId ensure(GraphBuilder graph, LanguageVersion language) {
        graph.symbols.putIfAbsent(id(language), declaration(language, List.of())); return id(language);
    }
    private static ModelSymbol declaration(LanguageVersion language, List<SymbolId> members) {
        var model=id(language); var origin=new Origin.PredefinedOrigin(language,"INTERLIS");
        var header=new SymbolHeader(model,SymbolKind.MODEL,DeclaredName.declared("INTERLIS"),Optional.empty(),0,origin,Optional.empty(),language,
                Documentation.empty(),MetaAttributeSet.empty(),Set.of(),List.of());
        return new ModelSymbol(header,ModelKind.TYPE,language==LanguageVersion.ILI23,
                language==LanguageVersion.ILI23?"2005-06-16":"2014-07-09","http://www.interlis.ch/",List.of(),members,Optional.empty(),
                new ModelOptions(Optional.of(new ModelOptions.TextValue("en",origin)),Optional.empty(),Optional.empty(),Optional.empty()),
                language==LanguageVersion.ILI23?"http://www.interlis.ch/INTERLIS2.3":"http://www.interlis.ch/xtf/2.4/INTERLIS");
    }
    private static int order(String key) {int index=ORDER.indexOf(key.substring(9));return index<0?ORDER.size():index;}
    private static Symbol withHeader(Symbol s,SymbolHeader h) {
        return switch(s) {
            case DomainSymbol d -> new DomainSymbol(h,d.typeId(),d.baseDomain(),d.generic());
            case StructureSymbol d -> new StructureSymbol(h,d.baseType(),d.members(),d.constraints());
            case ClassSymbol d -> new ClassSymbol(h,d.baseType(),d.members(),d.constraints());
            case UnitSymbol d -> new UnitSymbol(h,d.designation(),d.abstractUnit(),d.base(),d.derivedFrom(),d.components(),d.conversionFactors(),d.conversionFunction(),d.definition());
            case FunctionSymbol d -> new FunctionSymbol(h,d.parameters(),d.returnType(),d.returnMandatory(),d.explanation());
            case LineFormSymbol d -> new LineFormSymbol(h,d.segmentStructure());
            case TopicSymbol d -> new TopicSymbol(h,d.baseTopic(),d.members(),d.dependencies(),d.deferredGenerics(),d.genericBindings());
            case MetadataBasketSymbol d -> new MetadataBasketSymbol(h,d.basketKind(),d.topic(),d.baseBasket(),d.objects());
            default -> throw new IllegalStateException("Unexpected top-level predefined declaration: "+s);
        };
    }
}
