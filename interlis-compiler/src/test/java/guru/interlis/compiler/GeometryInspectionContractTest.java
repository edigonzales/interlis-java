package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryInspectionContractTest {
    private static final String COORD = "DOMAIN Coord = COORD 0.00..100.00 [INTERLIS.m], 0.00..100.00 [INTERLIS.m], 0.00..100.00 [INTERLIS.m]; ";
    private static String model(String version, String body) { return "INTERLIS " + version + "; CONTRACTED MODEL M AT \"https://example.org\" VERSION \"1\" = " + COORD + body + " END M."; }
    private static CompilationOutcome compile(String text) {
        var source = CompilerTestSupport.source("memory:/inspection.ili", text);
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none());
    }
    private static SemanticGraph ok(String text) {
        var result = compile(text);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result, ()->result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
    }
    private static Symbol named(SemanticGraph g,String name) { return g.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow(); }
    private static String line() { return "TOPIC T = CLASS A = start: Coord; line: POLYLINE WITH (STRAIGHTS,ARCS) VERTEX Coord; END A; VIEW V INSPECTION OF line~A->line; = MANDATORY CONSTRAINT line->Segments[FIRST]->SegmentEndPoint == PARENT->start; END V; END T;"; }

    @Test void lineInspectionExposesContextualHierarchyAndActualStartConstraint() {
        for (String v : List.of("2.3","2.4")) {
            var g = ok(model(v,line()));
            var geometry = (StructureSymbol)named(g,"LineGeometry"); assertEquals(1,geometry.constraints().size());
            var constraint = (ConstraintSymbol)g.symbol(geometry.constraints().getFirst()).orElseThrow();
            var call = (FunctionCallExpression)g.expression(((MandatoryConstraint)constraint.definition()).condition()).orElseThrow();
            assertEquals("INTERLIS.isOfClass",call.functionName()); assertEquals(2,call.children().size());
            var path = (PathExpression)g.expression(call.children().getFirst()).orElseThrow();
            assertEquals(PathIndex.Kind.FIRST,((PathStep.IndexStep)path.path().steps().getLast()).index().kind());
            var start = (StructureSymbol)g.symbol(((SymbolValueExpression)g.expression(call.children().getLast()).orElseThrow()).target()).orElseThrow();
            var segment = (StructureSymbol)named(g,"LineSegment"); assertEquals(segment.id(),start.baseType().orElseThrow());
            assertTrue(start.header().has(Modifier.FINAL)); assertTrue(segment.header().has(Modifier.ABSTRACT));
            var origin = assertInstanceOf(Origin.DerivedOrigin.class,start.origin());
            assertEquals(named(g,"INTERLIS.StartSegment").id(),origin.declaredAt());
            var arc = (StructureSymbol)named(g,"ArcSegment"); assertEquals(segment.id(),arc.baseType().orElseThrow());
            assertEquals(List.of("SegmentEndPoint","ArcPoint","Radius"),g.effectiveMembers(arc.id()).stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            for (var member : g.effectiveMembers(arc.id())) {
                var a = (AttributeSymbol)g.symbol(member.declaredSymbol()).orElseThrow();
                if (!a.name().value().equals("Radius")) { assertEquals(((DomainSymbol)named(g,"Coord")).typeId(),a.declaredType()); assertTrue(a.mandatory()); }
                else assertFalse(a.mandatory());
            }
        }
    }

    @Test void surfaceEdgesRetainBoundLineAttributesAndDirectedGeometry() {
        for (String v : List.of("2.3","2.4")) {
            String body = "STRUCTURE Attrs = label: TEXT; END Attrs; TOPIC T = CLASS A = area: SURFACE WITH (STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Attrs; END A; VIEW V INSPECTION OF edge~A->area; = ALL OF edge; END V; END T;";
            if (v.equals("2.4")) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,compile(model(v,body)));
                assertTrue(rejected.analysis().diagnostics().toString().contains("LINE_ATTRIBUTES_VERSION"));
                body=body.replace(" LINE ATTRIBUTES Attrs", "");
            }
            var g=ok(model(v,body)); var edge=(StructureSymbol)named(g,"SurfaceEdge");
            assertEquals(v.equals("2.3") ? List.of("Geometry","LineAttrs") : List.of("Geometry"),edge.members().stream().map(id->g.symbol(id).orElseThrow().name().value()).toList());
            if (v.equals("2.3")) {
                var a=(AttributeSymbol)g.symbol(edge.members().get(1)).orElseThrow(); assertFalse(a.mandatory());
                assertEquals(named(g,"Attrs").id(),((StructureValueType)g.type(a.declaredType()).orElseThrow()).target().orElseThrow());
            }
            var geometry=(GeometryType)g.type(((AttributeSymbol)g.symbol(edge.members().getFirst()).orElseThrow()).declaredType()).orElseThrow();
            assertTrue(geometry.directed()); assertEquals(GeometryKind.POLYLINE,geometry.geometryKind());
        }
    }

    @Test void areaContextsAreBoundAndAllOfRemainsAnExplicitLanguageError() {
        for (String v : List.of("2.3","2.4")) {
            String body="TOPIC T = CLASS A = kind: TEXT*10; area: AREA WITH (STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS > 0.01; END A; VIEW V AREA INSPECTION OF edge~A->area; = left := THISAREA->kind; right := THATAREA->kind; END V; END T;";
            var g=ok(model(v,body)); var view=(ViewSymbol)named(g,"V");
            for(var id:view.members()) {
                var attr=(AttributeSymbol)g.symbol(id).orElseThrow();
                var path=(PathExpression)g.expression(attr.derivations().getFirst()).orElseThrow();
                assertEquals(named(g,"A").id(),((PathStep.InspectionStep)path.path().steps().getFirst()).target());
            }
            var bad=assertInstanceOf(CompilationOutcome.Rejected.class,compile(model(v,body.replace("left :=", "ALL OF edge; left :="))));
            assertFalse(bad.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
            assertTrue(bad.analysis().diagnostics().toString().contains("VIEW_ALL_OF"));
        }
    }

    @Test void customSegmentsPreserveMembersConstraintsAndCanonicalIdentity() {
        for (String v : List.of("2.3","2.4")) {
            var g=ok(model(v,"STRUCTURE S(ABSTRACT) EXTENDS INTERLIS.LineSegment = x: TEXT; middle(ABSTRACT): INTERLIS.LineCoord; MANDATORY CONSTRAINT DEFINED(middle); END S; LINE FORM Curve: S; "+line().replace("STRAIGHTS,ARCS","Curve")));
            var original=(StructureSymbol)named(g,"S");
            var contextual=g.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast)
                    .filter(s->s.origin() instanceof Origin.DerivedOrigin d && d.declaredAt().equals(original.id())).findFirst().orElseThrow();
            assertEquals(List.of("x","middle"),contextual.members().stream().map(id->g.symbol(id).orElseThrow().name().value()).toList());
            assertEquals(List.of("SegmentEndPoint","x","middle"),g.effectiveMembers(contextual.id()).stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            var middle=(AttributeSymbol)g.symbol(contextual.members().getLast()).orElseThrow();
            assertEquals(named(g,"Coord").id(),((DomainReferenceType)g.type(middle.declaredType()).orElseThrow()).targetDomain());
            var canonicalMiddle=(AttributeSymbol)g.symbol(original.members().getLast()).orElseThrow();
            assertEquals(named(g,"INTERLIS.LineCoord").id(),((DomainReferenceType)g.type(canonicalMiddle.declaredType()).orElseThrow()).targetDomain());
            var constraint=(ConstraintSymbol)g.symbol(contextual.constraints().getFirst()).orElseThrow();
            var defined=(DefinedExpression)g.expression(((MandatoryConstraint)constraint.definition()).condition()).orElseThrow();
            var path=(PathExpression)g.expression(defined.operand()).orElseThrow();
            assertEquals(middle.id(),((PathStep.AttributeStep)path.path().steps().getFirst()).target());
            assertEquals(middle.declaredType(),path.typeId());
            assertTrue(contextual.header().nameRange().isEmpty());
        }
    }
    @Test void importedNestedSegmentsKeepOverridesAndAreIsolatedBetweenVertexContexts() {
        for (String version:List.of("2.3","2.4")) for (boolean reverse:List.of(false,true)) {
            var base=CompilerTestSupport.source("memory:/base.ili","INTERLIS "+version+"; CONTRACTED MODEL Base AT \"x\" VERSION \"1\" = "
                    +"STRUCTURE Foreign=label:TEXT; END Foreign; "
                    +"STRUCTURE Data(ABSTRACT)=p(ABSTRACT):INTERLIS.LineCoord; END Data; STRUCTURE S(ABSTRACT) EXTENDS INTERLIS.LineSegment = "
                    +"SegmentEndPoint(EXTENDED,ABSTRACT):MANDATORY; data:BAG OF Data; other:Foreign; copy(ABSTRACT):INTERLIS.LineCoord:=SegmentEndPoint; "
                    +"MANDATORY CONSTRAINT DEFINED(SegmentEndPoint); MANDATORY CONSTRAINT DEFINED(other); END S; LINE FORM Curve:S; END Base.");
            var source=CompilerTestSupport.source("memory:/inspection.ili", "INTERLIS "+version+"; CONTRACTED MODEL M AT \"x\" VERSION \"1\" = IMPORTS Base; "
                    +"DOMAIN P2=COORD 0.00..100.00,0.00..100.00; P3=COORD 0.000..100.000,0.000..100.000,0.000..100.000; "
                    +"TOPIC T=CLASS C=line2:POLYLINE WITH (Base.Curve) VERTEX P2; line3:POLYLINE WITH (Base.Curve) VERTEX P3; END C; "
                    +"VIEW V2 INSPECTION OF l~C->line2; = p:=l->Segments[FIRST]->SegmentEndPoint; END V2; "
                    +"VIEW V3 INSPECTION OF l~C->line3; = p:=l->Segments[FIRST]->SegmentEndPoint; END V3; END T; END M.");
            var sources=new LinkedHashMap<guru.interlis.source.SourceId,guru.interlis.source.SourceText>();
            for(var file:reverse?List.of(source,base):List.of(base,source))sources.put(file.id(),file);
            var request=CompilerTestSupport.request(List.of("M"),sources,List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(source),"Base",base,CompilerTestSupport.parse(base))),CompilerOptions.defaults());
            var result=CompilerEngines.create().compile(request,CancellationToken.none());
            var g=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
            var canonical=(StructureSymbol)named(g,"S");
            var contexts=g.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast)
                    .filter(x->x.origin() instanceof Origin.DerivedOrigin d && d.declaredAt().equals(canonical.id())).toList();
            assertEquals(2,contexts.size());
            Set<SymbolId> vertices=new HashSet<>();
            for(var contextual:contexts) {
                assertEquals(2,contextual.constraints().size());
                var attrs=contextual.members().stream().map(id->(AttributeSymbol)g.symbol(id).orElseThrow()).toList();
                assertEquals(List.of("SegmentEndPoint","data","other","copy"),attrs.stream().map(a->a.name().value()).toList());
                var end=attrs.getFirst();assertTrue(end.mandatory());assertTrue(end.overrides().isPresent());
                var coord=(DomainSymbol)g.symbols().stream().filter(DomainSymbol.class::isInstance).filter(d->((DomainSymbol)d).typeId().equals(end.declaredType())).findFirst().orElseThrow();
                vertices.add(coord.id());
                var data=(CollectionType)g.type(attrs.get(1).declaredType()).orElseThrow();
                var nested=(StructureSymbol)g.symbol(((StructureValueType)g.type(data.elementType()).orElseThrow()).target().orElseThrow()).orElseThrow();
                assertInstanceOf(Origin.DerivedOrigin.class,nested.origin());
                var point=(AttributeSymbol)g.symbol(nested.members().getFirst()).orElseThrow();
                assertEquals(coord.id(),((DomainReferenceType)g.type(point.declaredType()).orElseThrow()).targetDomain());
                assertEquals(named(g,"Foreign").id(),((StructureValueType)g.type(attrs.get(2).declaredType()).orElseThrow()).target().orElseThrow());
                var derivation=(PathExpression)g.expression(attrs.getLast().derivations().getFirst()).orElseThrow();
                assertEquals(end.id(),((PathStep.AttributeStep)derivation.path().steps().getFirst()).target());
                assertEquals(end.declaredType(),derivation.typeId());
                assertTrue(g.effectiveMembers(contextual.id()).getFirst().overridden());
            }
            assertEquals(Set.of(named(g,"P2").id(),named(g,"P3").id()),vertices);
            var canonicalData=(StructureSymbol)named(g,"Data");
            var canonicalPoint=(AttributeSymbol)g.symbol(canonicalData.members().getFirst()).orElseThrow();
            assertEquals(named(g,"INTERLIS.LineCoord").id(),((DomainReferenceType)g.type(canonicalPoint.declaredType()).orElseThrow()).targetDomain());
        }
    }

    @Test void contextualSegmentEditsMatchFreshAnalysisAndRecoverAfterAnError() {
        for(String version:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
            for(int revision=1;revision<=3;revision++) {
                var text=model(version,"STRUCTURE S EXTENDS INTERLIS.LineSegment = flag:"+(revision==2?"TEXT":"BOOLEAN")+"; MANDATORY CONSTRAINT flag; END S; LINE FORM Curve:S; "+line().replace("STRAIGHTS,ARCS","Curve"));
                var source=new guru.interlis.source.SourceText(new guru.interlis.source.SourceId("memory:/inspection.ili"),new guru.interlis.source.SourceRevision(revision),text);
                var request=CompilerTestSupport.request(List.of("M"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
                var reused=engine.analyze(request,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                var fresh=CompilerEngines.create().analyze(request,CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(revision!=2,reused.diagnostics().isEmpty());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(),reused.semanticGraph()));
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().types(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().types(),reused.semanticGraph()));
                previous=reused;
            }
        }
    }

}
