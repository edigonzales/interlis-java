package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** Translation preserves each verified inspection context, including its generated members. */
class GeometryTranslationContextContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String model(String name,String original,String body) {
        return "CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\" "
                +(original==null?"":"TRANSLATION OF "+original+"[\"1\"] ")+"="+body+"END "+name+".";
    }
    private static CompilationRequest input(String version,String original,String translated,long revision,boolean reverse) {
        var a=new SourceText(new SourceId(reverse?"memory:/z-original.ili":"memory:/a-original.ili"),new SourceRevision(revision),
                "INTERLIS "+version+";"+model("Original",null,original));
        var b=new SourceText(new SourceId(reverse?"memory:/a-translated.ili":"memory:/z-translated.ili"),new SourceRevision(revision),
                "INTERLIS "+version+";"+model("F","Original",translated));
        var sources=new LinkedHashMap<SourceId,SourceText>();
        for(var s:reverse?List.of(b,a):List.of(a,b))sources.put(s.id(),s);
        return request(List.of("F"),sources,List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String v,String a,String b) {return ok(input(v,a,b,1,false));}
    private static CompilationOutcome.Succeeded ok(CompilationRequest input) {
        var result=CompilerEngines.create().compile(input,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
    }
    private static void bad(String v,String a,String b) {
        var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,a,b,1,false),TOKEN));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
        assertTrue(result.analysis().diagnostics().toString().contains("TRANSLATION_"),result.analysis().diagnostics().toString());
    }
    private static ModelSymbol model(SemanticGraph g,String name) {
        return g.symbols().stream().filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast).filter(m->m.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static boolean ownedBy(SemanticGraph g,SymbolId id,SymbolId model) {
        var seen=new HashSet<SymbolId>();
        while(id!=null && seen.add(id)) {
            if(id.equals(model))return true;
            id=g.symbol(id).flatMap(Symbol::owner).orElse(null);
        }
        return false;
    }
    private static Map<SymbolId,SymbolId> links(SemanticGraph g) {
        var result=new LinkedHashMap<SymbolId,SymbolId>();
        for(var link:model(g,"F").translation().orElseThrow().correspondences())assertNull(result.put(link.original(),link.translated()));
        return result;
    }
    private static StructureSymbol structure(SemanticGraph g,SymbolId id) {return assertInstanceOf(StructureSymbol.class,g.symbol(id).orElseThrow());}
    private static StructureSymbol listElement(SemanticGraph g,SymbolId attribute) {
        var collection=assertInstanceOf(CollectionType.class,g.type(g.declaredType(attribute).orElseThrow()).orElseThrow());
        var element=assertInstanceOf(StructureValueType.class,g.type(collection.elementType()).orElseThrow());
        return structure(g,element.target().orElseThrow());
    }
    private static void assertLineContext(CompilationOutcome.Succeeded result,SymbolId original,SymbolId translated) {
        var g=result.schema().semanticGraph();var map=links(g);
        assertEquals(translated,map.get(original));
        var a=structure(g,original);var b=structure(g,translated);
        assertEquals(List.of("Segments"),a.members().stream().map(id->g.symbol(id).orElseThrow().name().value()).toList());
        assertEquals(b.members().getFirst(),map.get(a.members().getFirst()));
        assertEquals(1,a.constraints().size());assertEquals(b.constraints().getFirst(),map.get(a.constraints().getFirst()));
        var segment=listElement(g,a.members().getFirst());var targetSegment=listElement(g,b.members().getFirst());
        assertEquals(targetSegment.id(),map.get(segment.id()));
        assertInstanceOf(Origin.DerivedOrigin.class,segment.origin());
        for(int i=0;i<segment.members().size();i++)assertEquals(targetSegment.members().get(i),map.get(segment.members().get(i)));
        // The ordinary public query exposes the same final contextual correspondence.
        var queried=assertInstanceOf(ModelSymbol.class,result.analysis().queries().symbol(new SymbolRef(result.analysis().snapshotId(),model(g,"F").id())).known().orElseThrow());
        assertTrue(queried.translation().orElseThrow().correspondences().contains(new TranslationLink(original,translated)));
    }
    private static String coordinates() {return "DOMAIN Coord=COORD 0.00..100.00,0.00..100.00;OtherCoord=COORD 0.00..100.00,0.00..100.00;";}
    private static String inlineBody() {
        return coordinates()+"TOPIC T=CLASS A=first:POLYLINE WITH(STRAIGHTS) VERTEX Coord;second:POLYLINE WITH(STRAIGHTS) VERTEX Coord;END A;"
                +"VIEW V PROJECTION OF a~A;=n:=INTERLIS.elementCount(INSPECTION OF A->first OF a);END V;END T;";
    }
    private static List<InspectionExpression> factors(SemanticGraph g,String ownerModel) {
        var owner=model(g,ownerModel).id();
        return g.expressions().stream().filter(InspectionExpression.class::isInstance).map(InspectionExpression.class::cast).filter(i->ownedBy(g,i.source(),owner)).toList();
    }
    @Test void explicitToleranceComparesItsExactValueRatherThanItsLexeme() {
        for(String v:List.of("2.3","2.4")) {
            String a="DOMAIN Coord=COORD 0.0..10.0,0.0..10.0;Line=POLYLINE WITH(STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS >0.1;";
            ok(v,a,a.replace(">0.1",">00.1"));
            bad(v,a,a.replace(">0.1",">0.2"));
        }
    }
    @Test void inlineFactorsExposeCorrespondingElementsMembersAndStartConstraints() {
        for(String v:List.of("2.3","2.4")) {
            var result=ok(v,inlineBody(),inlineBody());var g=result.schema().semanticGraph();
            assertLineContext(result,factors(g,"Original").getFirst().element(),factors(g,"F").getFirst().element());
            bad(v,inlineBody(),inlineBody().replace("INSPECTION OF A->first","INSPECTION OF A->second"));
            bad(v,inlineBody(),inlineBody().replace("first:POLYLINE WITH(STRAIGHTS) VERTEX Coord","first:POLYLINE WITH(STRAIGHTS) VERTEX OtherCoord"));
        }
    }
    @Test void inlineFactorsInsideConstraintsExtendCorrespondenceBeforeReferenceComparison() {
        for(String v:List.of("2.3","2.4")) {
            String a=coordinates()+"TOPIC T=CLASS A=first:POLYLINE WITH(STRAIGHTS) VERTEX Coord;second:POLYLINE WITH(STRAIGHTS) VERTEX Coord;"
                    +"MANDATORY CONSTRAINT INTERLIS.elementCount(INSPECTION OF A->first OF THIS)>0;END A;END T;";
            var result=ok(v,a,a);var g=result.schema().semanticGraph();
            assertLineContext(result,factors(g,"Original").getFirst().element(),factors(g,"F").getFirst().element());
            bad(v,a,a.replace("INSPECTION OF A->first","INSPECTION OF A->second"));
        }
    }
    @Test void namedInspectionsMapTheirContextBeforeBasePathsAndDerivedAttributes() {
        for(String v:List.of("2.3","2.4")) {
            String a=coordinates()+"TOPIC T=CLASS A=first:POLYLINE WITH(STRAIGHTS,ARCS) VERTEX Coord;second:POLYLINE WITH(STRAIGHTS,ARCS) VERTEX Coord;END A;"
                    +"VIEW I INSPECTION OF l~A->first;=p:=l->Segments[FIRST]->SegmentEndPoint;END I;END T;";
            var result=ok(v,a,a);var g=result.schema().semanticGraph();var map=links(g);
            var i=g.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).filter(s->ownedBy(g,s.id(),model(g,"Original").id())).findFirst().orElseThrow();
            var translated=assertInstanceOf(ViewSymbol.class,g.symbol(map.get(i.id())).orElseThrow());
            var x=assertInstanceOf(ViewSymbol.Inspection.class,i.formation().orElseThrow());var y=assertInstanceOf(ViewSymbol.Inspection.class,translated.formation().orElseThrow());
            assertLineContext(result,x.element(),y.element());assertEquals(y.parent(),map.get(x.parent()));
            var attr=assertInstanceOf(AttributeSymbol.class,g.symbol(i.members().getFirst()).orElseThrow());
            var path=assertInstanceOf(PathExpression.class,g.expression(attr.derivations().getFirst()).orElseThrow());
            var targetAttr=assertInstanceOf(AttributeSymbol.class,g.symbol(map.get(attr.id())).orElseThrow());
            var targetPath=assertInstanceOf(PathExpression.class,g.expression(targetAttr.derivations().getFirst()).orElseThrow());
            for(int n=0;n<path.path().steps().size();n++)if(path.path().steps().get(n) instanceof PathStep.AttributeStep step)
                assertEquals(((PathStep.AttributeStep)targetPath.path().steps().get(n)).target(),map.get(step.target()));
            bad(v,a,a.replace("l~A->first","l~A->second"));
        }
    }
    @Test void ownSegmentsAndTheirConstraintsRetainCanonicalAndContextualLinks() {
        for(String v:List.of("2.3","2.4")) {
            String a="STRUCTURE Segment EXTENDS INTERLIS.LineSegment=extra:TEXT*20;MANDATORY CONSTRAINT DEFINED(extra);END Segment;LINE FORM Curve:Segment;"
                    +coordinates()+"TOPIC T=CLASS A=first:POLYLINE WITH(Curve) VERTEX Coord;END A;VIEW V INSPECTION OF l~A->first;=p:=l->Segments[FIRST]->SegmentEndPoint;END V;END T;";
            String b=a.replace("STRUCTURE Segment","STRUCTURE Teil").replace("END Segment","END Teil").replace("Curve:Segment","Kurve:Teil").replace("WITH(Curve)","WITH(Kurve)")
                    .replace("extra:","zusatz:").replace("DEFINED(extra)","DEFINED(zusatz)");
            var result=ok(v,a,b);var g=result.schema().semanticGraph();var map=links(g);
            var canonical=g.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast).filter(s->s.name().value().equals("Segment") && s.origin() instanceof Origin.DeclaredOrigin).findFirst().orElseThrow();
            var copied=g.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast).filter(s->s.origin() instanceof Origin.DerivedOrigin d && d.kind()==DerivationKind.CONTEXTUALIZATION && d.declaredAt().equals(canonical.id())).findFirst().orElseThrow();
            var translated=structure(g,map.get(copied.id()));var targetOrigin=assertInstanceOf(Origin.DerivedOrigin.class,translated.origin());
            assertEquals(map.get(canonical.id()),targetOrigin.declaredAt());assertEquals(copied.constraints().size(),translated.constraints().size());
            assertFalse(copied.constraints().isEmpty());
            for(int n=0;n<copied.constraints().size();n++)assertEquals(translated.constraints().get(n),map.get(copied.constraints().get(n)));
            for(int n=0;n<copied.members().size();n++)assertEquals(translated.members().get(n),map.get(copied.members().get(n)));
        }
    }
    @Test void fileOrderAndEditReuseDoNotChangeContextPairingOrCollapseCopies() {
        for(String v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            String a=inlineBody();
            for(String b:List.of(a,a.replace("INSPECTION OF A->first","INSPECTION OF A->second"),a)) {
                var request=input(v,a,b,++revision,reverse);var fresh=engine.analyze(request,TOKEN);
                var reused=previous==null?fresh:engine.analyze(request,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(revision==2,!fresh.diagnostics().isEmpty());
                if(revision!=2) {
                    var g=reused.semanticGraph();var map=links(g);
                    var copies=map.keySet().stream().filter(id->g.symbol(id).orElseThrow().origin() instanceof Origin.DerivedOrigin d && d.kind()==DerivationKind.CONTEXTUALIZATION).toList();
                    assertFalse(copies.isEmpty());assertEquals(copies.size(),copies.stream().map(map::get).distinct().count());
                }
                previous=reused;
            }
        }
    }
    @Test void twoVertexContextsOfTheSameSegmentKeepDistinctTranslatedCopies() {
        for(String v:List.of("2.3","2.4")) {
            String a="STRUCTURE Segment EXTENDS INTERLIS.LineSegment=extra:TEXT*20;MANDATORY CONSTRAINT DEFINED(extra);END Segment;LINE FORM Curve:Segment;"
                    +"DOMAIN P2=COORD 0.00..100.00,0.00..100.00;P3=COORD 0.000..100.000,0.000..100.000,0.000..100.000;"
                    +"TOPIC T=CLASS A=first:POLYLINE WITH(Curve) VERTEX P2;second:POLYLINE WITH(Curve) VERTEX P3;END A;"
                    +"VIEW I INSPECTION OF l~A->first;=p:=l->Segments[FIRST]->SegmentEndPoint;END I;"
                    +"VIEW J INSPECTION OF l~A->second;=p:=l->Segments[FIRST]->SegmentEndPoint;END J;END T;";
            var result=ok(v,a,a);var g=result.schema().semanticGraph();var map=links(g);
            var canonical=g.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast)
                    .filter(x->x.name().value().equals("Segment") && x.origin() instanceof Origin.DeclaredOrigin && ownedBy(g,x.id(),model(g,"Original").id())).findFirst().orElseThrow();
            var copies=g.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast)
                    .filter(x->x.origin() instanceof Origin.DerivedOrigin d && d.kind()==DerivationKind.CONTEXTUALIZATION && d.declaredAt().equals(canonical.id())).toList();
            assertEquals(2,copies.size());assertNotEquals(map.get(copies.getFirst().id()),map.get(copies.getLast().id()));
            var dimensions=new HashSet<Integer>();
            for(var copy:copies) {
                var translated=structure(g,map.get(copy.id()));
                var endpoint=g.effectiveMembers(copy.id()).stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow()).filter(m->m.name().value().equals("SegmentEndPoint")).findFirst().orElseThrow();
                var originalAttribute=assertInstanceOf(AttributeSymbol.class,endpoint);
                var translatedAttribute=assertInstanceOf(AttributeSymbol.class,g.symbol(map.get(originalAttribute.id())).orElseThrow());
                assertTrue(g.effectiveMembers(translated.id()).stream().anyMatch(m->m.declaredSymbol().equals(translatedAttribute.id())));
                var sourceVertex=assertInstanceOf(CoordinateType.class,g.type(originalAttribute.declaredType()).orElseThrow());
                var targetVertex=assertInstanceOf(CoordinateType.class,g.type(translatedAttribute.declaredType()).orElseThrow());
                dimensions.add(sourceVertex.dimensions());assertEquals(sourceVertex.dimensions(),targetVertex.dimensions());
                assertEquals(translated.constraints().getFirst(),map.get(copy.constraints().getFirst()));
            }
            assertEquals(Set.of(2,3),dimensions);
            var canonicalAttribute=assertInstanceOf(AttributeSymbol.class,g.symbol(canonical.members().getFirst()).orElseThrow());
            assertEquals(20,assertInstanceOf(TextType.class,g.type(canonicalAttribute.declaredType()).orElseThrow()).maxLength().orElseThrow());
            var base=structure(g,canonical.baseType().orElseThrow());
            var canonicalEnd=assertInstanceOf(AttributeSymbol.class,g.symbol(base.members().getFirst()).orElseThrow());
            assertEquals(new Origin.PredefinedOrigin(v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,"INTERLIS.LineCoord"),g.type(canonicalEnd.declaredType()).orElseThrow().origin());
        }
    }
    @Test void surfaceBoundaryAndAreaEdgeKeepTheirVersionedMembersAndParentContexts() {
        for(String v:List.of("2.3","2.4"))for(boolean area:List.of(false,true)) {
            String a="STRUCTURE Attrs=label:TEXT;END Attrs;"+coordinates()+"TOPIC T=CLASS A=kind:TEXT*10;shape:"
                    +(area?"AREA":"SURFACE")+" WITH(STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS >0.01"
                    +(v.equals("2.3")?" LINE ATTRIBUTES Attrs":"")+";END A;VIEW I "+(area?"AREA ":"")+"INSPECTION OF edge~A->shape;="
                    +(area?"p:=edge->Geometry;left:=THISAREA->kind;right:=THATAREA->kind;":"p:=edge->Lines[FIRST]->Geometry;")+"END I;END T;";
            var result=ok(v,a,a);var g=result.schema().semanticGraph();var map=links(g);
            var originalView=g.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast)
                    .filter(x->ownedBy(g,x.id(),model(g,"Original").id())).findFirst().orElseThrow();
            var targetView=assertInstanceOf(ViewSymbol.class,g.symbol(map.get(originalView.id())).orElseThrow());
            var x=assertInstanceOf(ViewSymbol.Inspection.class,originalView.formation().orElseThrow());
            var y=assertInstanceOf(ViewSymbol.Inspection.class,targetView.formation().orElseThrow());
            assertEquals(y.element(),map.get(x.element()));assertEquals(y.parent(),map.get(x.parent()));
            var element=structure(g,x.element());var target=structure(g,y.element());
            assertEquals(area?(v.equals("2.3")?List.of("Geometry","LineAttrs"):List.of("Geometry")):List.of("Lines"),element.members().stream().map(id->g.symbol(id).orElseThrow().name().value()).toList());
            for(int i=0;i<element.members().size();i++)assertEquals(target.members().get(i),map.get(element.members().get(i)));
        }
    }

}
