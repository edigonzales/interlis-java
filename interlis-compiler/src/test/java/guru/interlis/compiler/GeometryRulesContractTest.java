package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** G2 C: effective geometry, exact versioned tolerances and shared public relations. */
class GeometryRulesContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final String COORD="DOMAIN P = COORD 0.000..100.000, 0.000..100.000; ";
    private static CompilationRequest request(String v,String body,int revision) {
        var source=new SourceText(new SourceId("memory:/geometry.ili"),new SourceRevision(revision),
                "INTERLIS "+v+"; CONTRACTED MODEL M AT \"https://example.org\" VERSION \"1\" = "+body+" END M.");
        return CompilerTestSupport.request(List.of("M"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String v,String body) {
        var result=CompilerEngines.create().compile(request(v,body,1),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->body+"\n"+(result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics():result));
    }
    private static void bad(String v,String body,String rule) {
        var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(v,body,1),TOKEN),body);
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
        assertTrue(result.analysis().diagnostics().toString().contains(rule),result.analysis().diagnostics()::toString);
    }
    private static DomainSymbol domain(SemanticGraph g,String name) {
        return g.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static GeometryType geometry(SemanticGraph g,String name) {return (GeometryType)g.type(domain(g,name).typeId()).orElseThrow();}
    private static TypeRef ref(SemanticGraph g,String name) {return new TypeRef(g.snapshotId(),domain(g,name).typeId());}

    @Test void concreteDefinitionsRequireFormsAndVerticesButAbstractDefinitionsMayDeferThem() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"DOMAIN L (ABSTRACT) = POLYLINE;");
            bad(v,"DOMAIN L = POLYLINE;","GEOMETRY_INCOMPLETE");
            bad(v,COORD+"DOMAIN L = POLYLINE VERTEX P;","GEOMETRY_INCOMPLETE");
            bad(v,"DOMAIN L = POLYLINE WITH (STRAIGHTS);","GEOMETRY_INCOMPLETE");
            bad(v,"DOMAIN P (ABSTRACT) = COORD NUMERIC, NUMERIC; L = POLYLINE WITH (STRAIGHTS) VERTEX P;","GEOMETRY_INCOMPLETE");
            ok(v,"DOMAIN P (ABSTRACT) = COORD NUMERIC, NUMERIC; L (ABSTRACT) = POLYLINE WITH (STRAIGHTS) VERTEX P;");
            bad(v,COORD.replace("P =", "P (ABSTRACT) =")+"DOMAIN L=POLYLINE WITH (STRAIGHTS) VERTEX P;","GEOMETRY_INCOMPLETE");
        }
    }

    @Test void surfaceToleranceIsExplicitIn23AndSymbolicIn24() {
        var body=COORD+"DOMAIN S = SURFACE WITH (STRAIGHTS) VERTEX P;";
        bad("2.3",body,"GEOMETRY_INCOMPLETE");
        var graph=ok("2.4",body).schema().semanticGraph();var surface=geometry(graph,"S");
        assertTrue(surface.withoutOverlaps());
        assertEquals(domain(graph,"P").id(),assertInstanceOf(GeometryType.TrackWidth.class,surface.overlap().orElseThrow()).coordinateDomain());
        ok("2.4",body.replace("VERTEX P;","VERTEX P WITHOUT OVERLAPS;"));
        bad("2.3",body.replace("VERTEX P;","VERTEX P WITHOUT OVERLAPS;"),"OVERLAP_VERSION");
    }

    @Test void positiveToleranceHasNoFixedMinimumAnd24RetainsDeclaredPrecision() {
        for(String v:List.of("2.3","2.4")) {
            var body=COORD.replace(".000",".000000")+"DOMAIN L = POLYLINE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.000010;";
            var graph=ok(v,body).schema().semanticGraph();
            assertEquals("0.000010",assertInstanceOf(GeometryType.Explicit.class,geometry(graph,"L").overlap().orElseThrow()).value().lexeme());
            for(String zero:List.of("0","-0.000000","0.000e2"))bad(v,body.replace("0.000010",zero),"OVERLAP_POSITIVE");
            if(v.equals("2.3"))ok(v,body.replace("0.000010","0.0000001"));
            else bad(v,body.replace("0.000010","0.00001"),"OVERLAP_PRECISION");
        }
        ok("2.3",COORD+"DOMAIN L = POLYLINE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.0001;");
        ok("2.4",COORD+"DOMAIN L = POLYLINE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.1e-2;");
    }

    @Test void lineAxesHaveUniformPrecisionIn24() {
        var body="DOMAIN P=COORD 0.00..100.00, 0.000..100.000; L=POLYLINE WITH (STRAIGHTS) VERTEX P;";
        ok("2.3",body);bad("2.4",body,"LINE_VERTEX_PRECISION");
    }

    @Test void inheritedGeometryIsCompleteBeforeExpressionsAndCannotChangeEstablishedTolerance() {
        for(String v:List.of("2.3","2.4")) {
            var body=COORD+"DOMAIN Base = POLYLINE WITH (STRAIGHTS,ARCS) VERTEX P WITHOUT OVERLAPS > 0.010; "
                    +"Child EXTENDS Base = DIRECTED POLYLINE WITH (STRAIGHTS); TOPIC T = CLASS C = line:Child; END C; "
                    +"VIEW V INSPECTION OF c~C->line; = p:=c->Segments[FIRST]->SegmentEndPoint; END V; END T;";
            var result=ok(v,body);var graph=result.schema().semanticGraph();var child=geometry(graph,"Child");
            assertEquals(geometry(graph,"Base").vertexDomain(),child.vertexDomain());assertTrue(child.directed());
            assertEquals(geometry(graph,"Base").overlap(),child.overlap());assertTrue(child.withoutOverlaps());
            assertEquals(RelationDecision.ALLOWED,new TypeRelations(graph).canExtend(ref(graph,"Child"),ref(graph,"Base"),ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()).known().orElseThrow().decision());
            bad(v,body.replace("DIRECTED POLYLINE WITH (STRAIGHTS);","DIRECTED POLYLINE WITH (STRAIGHTS) WITHOUT OVERLAPS > 0.005;"),"GEOMETRY_EXTENSION");
        }
    }

    @Test void implicitToleranceKeepsItsOriginalPrecisionDomainWhenVertexIsRestricted() {
        var body=COORD+"DOMAIN Narrow EXTENDS P=COORD 1.000..99.000,1.000..99.000; "
                +"S=SURFACE WITH (STRAIGHTS) VERTEX P; A EXTENDS S=AREA VERTEX Narrow;";
        var graph=ok("2.4",body).schema().semanticGraph();
        assertEquals(Optional.of(domain(graph,"Narrow").id()),geometry(graph,"A").vertexDomain());
        assertEquals(domain(graph,"P").id(),((GeometryType.TrackWidth)geometry(graph,"A").overlap().orElseThrow()).coordinateDomain());
        assertEquals(RelationDecision.ALLOWED,new TypeRelations(graph).canExtend(ref(graph,"A"),ref(graph,"S"),ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()).known().orElseThrow().decision());
        bad("2.4",body.replace("AREA VERTEX Narrow;","AREA VERTEX Narrow WITHOUT OVERLAPS > 0.001;"),"GEOMETRY_EXTENSION");
    }

    @Test void extensionAndAssignmentUseDifferentToleranceRulesAndRejectWrongFamilies() {
        for(String v:List.of("2.3","2.4")) {
            var body=COORD+"DOMAIN Big=POLYLINE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.010; "
                    +"Small=POLYLINE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.005;";
            var graph=ok(v,body).schema().semanticGraph();var relations=new TypeRelations(graph);
            assertEquals(RelationDecision.ALLOWED,relations.canAssign(ref(graph,"Small"),ref(graph,"Big"),AssignmentContext.NORMAL).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,relations.canExtend(ref(graph,"Small"),ref(graph,"Big"),ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()).known().orElseThrow().decision());
            bad(v,body+"DOMAIN S EXTENDS Big=SURFACE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.010;","GEOMETRY_EXTENSION");
            bad(v,COORD+"DOMAIN L=DIRECTED POLYLINE WITH (STRAIGHTS) VERTEX P; D EXTENDS L=POLYLINE;","GEOMETRY_EXTENSION");
            bad(v,COORD+"DOMAIN L=POLYLINE WITH (STRAIGHTS) VERTEX P; D EXTENDS L=POLYLINE WITH (STRAIGHTS,ARCS);","GEOMETRY_EXTENSION");
        }
    }

    @Test void inlineAttributeOverridesPublishInheritedPropertiesWithoutMutatingTheBase() {
        for(String v:List.of("2.3","2.4")) {
            var body=COORD+"TOPIC T = CLASS A = line:POLYLINE WITH (STRAIGHTS,ARCS) VERTEX P; END A; "
                    +"CLASS B EXTENDS A = line(EXTENDED):DIRECTED POLYLINE WITH (STRAIGHTS); END B; END T;";
            var graph=ok(v,body).schema().semanticGraph();
            var lines=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(a->a.name().value().equals("line")).toList();
            assertEquals(2,lines.size());var base=(GeometryType)graph.type(lines.getFirst().declaredType()).orElseThrow();var child=(GeometryType)graph.type(lines.getLast().declaredType()).orElseThrow();
            assertFalse(base.directed());assertEquals(2,base.lineForms().size());assertTrue(child.directed());assertEquals(1,child.lineForms().size());assertEquals(base.vertexDomain(),child.vertexDomain());
        }
    }

    @Test void lineAttributeStructuresMayBeRestrictedOrOmittedIn23() {
        var body=COORD+"STRUCTURE Attrs=label:TEXT; END Attrs; STRUCTURE More EXTENDS Attrs=extra:TEXT; END More; "
                +"DOMAIN S=SURFACE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.010 LINE ATTRIBUTES Attrs; "
                +"A EXTENDS S=AREA LINE ATTRIBUTES More; B EXTENDS S=SURFACE;";
        var graph=ok("2.3",body).schema().semanticGraph();assertTrue(geometry(graph,"A").lineAttributes().isPresent());assertTrue(geometry(graph,"B").lineAttributes().isEmpty());
        bad("2.3",body.replace("STRUCTURE More EXTENDS Attrs","STRUCTURE More"),"GEOMETRY_EXTENSION");
        bad("2.3",COORD+"STRUCTURE Attrs=END Attrs; DOMAIN L=POLYLINE WITH (STRAIGHTS) VERTEX P LINE ATTRIBUTES Attrs;","LINE_ATTRIBUTES_FAMILY");
    }

    @Test void editsWithInheritedGeometryAgreeWithFreshAnalysis() {
        for(String v:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
            for(int step=1;step<=3;step++) {
                var input=request(v,COORD+"DOMAIN Base=POLYLINE WITH (STRAIGHTS) VERTEX P WITHOUT OVERLAPS > 0.010; "
                        +"Child EXTENDS Base=POLYLINE"+(step==2?" WITHOUT OVERLAPS > 0.005":"")+";",step);
                var actual=engine.analyze(input,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                var fresh=CompilerEngines.create().analyze(input,TOKEN);assertEquals(fresh.diagnostics(),actual.diagnostics());assertEquals(step!=2,actual.diagnostics().isEmpty());
                var a=geometry(actual.semanticGraph(),"Child");var b=geometry(fresh.semanticGraph(),"Child");assertEquals(a.overlap(),b.overlap());assertEquals(a.lineForms(),b.lineForms());
                previous=actual;
            }
        }
    }
    @Test void symbolicToleranceAssignmentComparesExactlyAcrossCoordinateDomains() {
        var body=COORD+"DOMAIN Q=COORD 1.000..99.000,1.000..99.000; S=SURFACE WITH (STRAIGHTS) VERTEX P; "
                +"T=SURFACE WITH (STRAIGHTS) VERTEX Q; "
                +"Above=SURFACE WITH (STRAIGHTS) VERTEX Q WITHOUT OVERLAPS > 0.001;";
        var graph=ok("2.4",body).schema().semanticGraph();var relations=new TypeRelations(graph);
        assertEquals(RelationDecision.ALLOWED,relations.canAssign(ref(graph,"T"),ref(graph,"S"),AssignmentContext.NORMAL).known().orElseThrow().decision());
        assertEquals(RelationDecision.DISALLOWED,relations.canAssign(ref(graph,"Above"),ref(graph,"S"),AssignmentContext.NORMAL).known().orElseThrow().decision());
        assertEquals(RelationDecision.ALLOWED,relations.canAssign(ref(graph,"T"),ref(graph,"Above"),AssignmentContext.NORMAL).known().orElseThrow().decision());
    }

    @Test void cartesianVerticesRejectAngularUnitsIn24() {
        bad("2.4","DOMAIN P=COORD 0.000..6.000 [INTERLIS.rad],0.000..6.000 [INTERLIS.rad]; L=POLYLINE WITH (STRAIGHTS) VERTEX P;","LINE_VERTEX_CARTESIAN");
        ok("2.4",COORD.replace("0.000..100.000", "0.000..100.000 [INTERLIS.m]")+"DOMAIN L=POLYLINE WITH (STRAIGHTS) VERTEX P;");
    }

    @Test void importedVertexUnitsBindBeforeTheCartesianCheckRegardlessOfFileOrder() {
        for(String version:List.of("2.3","2.4")) for(boolean reverse:List.of(false,true)) {
            var base=CompilerTestSupport.source(reverse?"memory:/a-base.ili":"memory:/z-base.ili", "INTERLIS "+version+"; MODEL Base AT \"https://example.org\" VERSION \"1\" = UNIT Kilometre[km]=1000 [INTERLIS.m]; Short[short]=0.001 [km]; DOMAIN P=COORD 0.000..100.000 [short],0.000..100.000 [short]; END Base.");
            var use=CompilerTestSupport.source(reverse?"memory:/z-use.ili":"memory:/a-use.ili", "INTERLIS "+version+"; MODEL M AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; DOMAIN L=POLYLINE WITH (STRAIGHTS) VERTEX Base.P; END M.");
            var input=CompilerTestSupport.request(List.of("M"),Map.of(use.id(),use,base.id(),base),List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(use),"Base",base,CompilerTestSupport.parse(base))),CompilerOptions.defaults());
            var result=CompilerEngines.create().compile(input,TOKEN);
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
            assertTrue(geometry(graph,"L").vertexDomain().isPresent());
        }
    }

}
