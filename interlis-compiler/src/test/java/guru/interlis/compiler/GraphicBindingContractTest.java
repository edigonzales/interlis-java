package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphicBindingContractTest {
    static final String BODY = """
        DOMAIN Coord = COORD 0.00..100.00,0.00..100.00;
        TOPIC Signs =
          CLASS Mark EXTENDS INTERLIS.SIGN = PARAMETER Pos: MANDATORY Coord; Priority: 0..200; END Mark;
        END Signs;
        SIGN BASKET Library ~ Signs OBJECTS OF Mark: Dot,Square;
        TOPIC Data = CLASS Point = pos: Coord; kind: (a,b,c) ORDERED; label: TEXT*20; END Point; END Data;
        TOPIC Maps = DEPENDS ON Data;
          GRAPHIC Map BASED ON M.Data.Point = WHERE kind != #c;
            Draw OF M.Signs.Mark: WHERE kind == #a (Sign := {Dot}; Pos := pos; Priority := 10),
              WHERE kind == #b (Sign := {Square}; Pos := pos; Priority := 20);
          END Map;
        END Maps;
        """;
    static String model(String version,String body) { return "INTERLIS "+version+"; CONTRACTED MODEL M AT \"https://example.org\" VERSION \"1\" = "+body+" END M."; }
    static CompilationOutcome compile(String text) {
        var s=CompilerTestSupport.source("memory:/graphic.ili",text);
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults()),CancellationToken.none());
    }
    static CompilationOutcome.Succeeded ok(String text) { var r=compile(text); return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString()); }
    static void bad(String text,String rule) { var r=assertInstanceOf(CompilationOutcome.Rejected.class,compile(text)); assertTrue(r.analysis().diagnostics().toString().contains(rule),r.analysis().diagnostics().toString()); }
    static Symbol named(SemanticGraph graph,String name) { return graph.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow(); }

    static List<ImportBinding> bindings(Map<guru.interlis.source.SourceId,guru.interlis.source.SourceText> sources) {
        var parsed=sources.values().stream().map(CompilerTestSupport::parse).toList(); List<ImportBinding> bindings=new ArrayList<>();
        for(var source:parsed) for(var model:source.root().models()) for(var imported:model.imports()) {
            var target=parsed.stream().filter(t->t.root().models().stream().anyMatch(m->m.name().equals(imported.modelName()))).findFirst().orElseThrow();
            bindings.add(CompilerTestSupport.binding(source,imported.modelName(),target.source(),target));
        }
        return bindings;
    }

    @Test void importedSignaturesAndInheritedTopicTargetsAreIndependentOfSourceOrder() {
        for(var v:List.of("2.3","2.4")) for(boolean reverse:List.of(false,true)) {
            String baseBody=BODY.substring(0,BODY.indexOf("TOPIC Maps"))+" TOPIC More EXTENDS Data = END More;";
            var base=CompilerTestSupport.source(reverse?"memory:/z-base.ili":"memory:/a-base.ili",model(v,baseBody).replace("MODEL M","MODEL Base").replace("END M.","END Base."));
            var use=CompilerTestSupport.source(reverse?"memory:/a-use.ili":"memory:/z-use.ili",model(v,"IMPORTS Base; SIGN BASKET B ~ Base.Signs OBJECTS OF Mark: Dot; TOPIC Maps = DEPENDS ON Base.Data,Base.More; GRAPHIC G BASED ON Base.More.Point = Draw OF Base.Signs.Mark: (Sign := {Dot}; Pos := pos); END G; END Maps;"));
            var sources=Map.of(base.id(),base,use.id(),use);
            var result=CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"),sources,bindings(sources),CompilerOptions.defaults()),CancellationToken.none());
            var success=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
            var graph=success.schema().semanticGraph();assertEquals(named(graph,"Point").id(),((GraphicSymbol)named(graph,"G")).basis().orElseThrow());
        }
    }

    @Test void graphicsExposeBasisClausesBoundParametersAndPaths() {
        for(var version:List.of("2.3","2.4")) {
            var result=ok(model(version,BODY));
            var g=result.schema().semanticGraph();
            var graphic=(GraphicSymbol)named(g,"Map"); assertEquals(named(g,"Point").id(),graphic.basis().orElseThrow());
            assertEquals(1,graphic.selections().size()); assertEquals(graphic.rules(),graphic.effectiveRules());
            var rule=(DrawingRuleSymbol)g.symbol(graphic.rules().getFirst()).orElseThrow(); assertEquals(named(g,"Mark").id(),rule.signatureClass().orElseThrow()); assertEquals(2,rule.clauses().size());
            var clause=rule.clauses().getFirst(); assertTrue(clause.condition().isPresent()); assertEquals(3,clause.assignments().size());
            var sign=clause.assignments().getFirst(); assertEquals("Sign",g.symbol(sign.parameter().orElseThrow()).orElseThrow().name().value());
            assertEquals(named(g,"Dot").id(),assertInstanceOf(DrawingRuleSymbol.MetaObjectValue.class,sign.value()).object().orElseThrow());
            var source=result.analysis().syntaxSnapshots().values().iterator().next().source();
            int at=source.text().indexOf("Pos :=");
            assertEquals(clause.assignments().get(1).parameter().orElseThrow(),LanguageQueries.create().symbolAt(result.analysis(),source.range(at,at),CancellationToken.none()).known().orElseThrow().symbol());
            var pos=assertInstanceOf(DrawingRuleSymbol.ExpressionValue.class,clause.assignments().get(1).value());
            assertInstanceOf(PathExpression.class,g.expression(pos.expression()).orElseThrow());
            assertTrue(g.references().stream().anyMatch(r->r.resolution() instanceof BindingResolution.BoundTarget target && target.target().equals(sign.parameter().orElseThrow())));
        }
    }

    @Test void signatureParametersRetainExplicitMetadataTargetsAndOverrides() {
        for(var version:List.of("2.3","2.4")) {
            String body="TOPIC Signs = CLASS Colour EXTENDS INTERLIS.METAOBJECT = END Colour; CLASS Mark EXTENDS INTERLIS.SIGN = PARAMETER Colour: METAOBJECT OF Colour; Width: 0..100; END Mark; CLASS Special EXTENDS Mark = PARAMETER Width (EXTENDED): 0..10; END Special; END Signs;";
            var g=ok(model(version,body)).schema().semanticGraph();
            var cls=(ClassSymbol)named(g,"Special"); var parameter=(ParameterSymbol)g.symbol(cls.members().getFirst()).orElseThrow(); assertTrue(parameter.overrides().isPresent());
            var colour=g.symbols().stream().filter(s->s instanceof ParameterSymbol && s.name().value().equals("Colour")).map(ParameterSymbol.class::cast).findFirst().orElseThrow();
            assertEquals(named(g,"Colour").id(),assertInstanceOf(MetaObjectType.class,g.type(colour.typeId()).orElseThrow()).target().orElseThrow());
            bad(model(version,body.replace("Width (EXTENDED): 0..10","Width (EXTENDED): 0..200")),"PARAMETER_TYPE");
            bad(model(version,body.replace("Width (EXTENDED)","Width")),"PARAMETER_OVERRIDE");
            bad(model(version,body.replace("EXTENDS INTERLIS.METAOBJECT", "")),"PARAMETER_METAOBJECT_TARGET");
        }
    }

    @Test void conditionsTargetsAssignmentsAndDependenciesHaveSpecificErrors() {
        for(var version:List.of("2.3","2.4")) {
            bad(model(version,BODY.replace("M.Data.Point","Data.Point")),"ILI-SEM-0001");
            bad(model(version,BODY.replace("M.Signs.Mark","Signs.Mark")),"ILI-SEM-0001");
            bad(model(version,BODY.replace("WHERE kind != #c", "WHERE 10")),"GRAPHIC_CONDITION");
            bad(model(version,BODY.replace("Priority := 10", "Priority := label")),"GRAPHIC_PARAMETER_TYPE");
            bad(model(version,BODY.replace("Priority := 10", "Unknown := 10")),"GRAPHIC_PARAMETER");
            bad(model(version,BODY.replace("Pos := pos; ", "")),"GRAPHIC_PARAMETER_MISSING");
            bad(model(version,BODY.replace("{Dot}","{Missing}")),"METAOBJECT_REFERENCE");
            bad(model(version,BODY.replace("DEPENDS ON Data;", "")),"GRAPHIC_TOPIC_DEPENDENCY");
            bad(model(version,BODY.replace("OF M.Signs.Mark", "OF M.Data.Point")),"GRAPHIC_SIGNATURE");
            bad(model(version,BODY.replace("END Map;", "END Wrong;")),"GRAPHIC_END_NAME");
        }
    }

    @Test void accordingRetainsOrderedEnumRangesAndValues() {
        for(var version:List.of("2.3","2.4")) {
            var body=BODY.replace("Priority := 10", "Priority := ACCORDING kind (10 WHEN IN #a..#b, 20 WHEN IN #c)");
            var g=ok(model(version,body)).schema().semanticGraph();
            var rule=(DrawingRuleSymbol)named(g,"Draw");
            var value=assertInstanceOf(DrawingRuleSymbol.AccordingValue.class,rule.clauses().getFirst().assignments().get(2).value());
            assertEquals("a",value.cases().getFirst().first());assertEquals("b",value.cases().getFirst().last().orElseThrow());assertEquals(2,value.cases().size());
            bad(model(version,body.replace(" ORDERED", "")),"GRAPHIC_ENUM_RANGE");
            bad(model(version,body.replace("#a..#b", "#c..#a")),"GRAPHIC_ENUM_RANGE");
            bad(model(version,body.replace("ACCORDING kind", "ACCORDING label")),"GRAPHIC_ENUM_SELECTOR");
            bad(model(version,body.replace("#a..#b", "#a..#missing")),"GRAPHIC_ENUM_RANGE");
        }
    }

    @Test void graphicAndDrawingInheritanceKeepEffectiveOrderAndBaseIdentity() {
        for(var version:List.of("2.3","2.4")) {
            var body=BODY.replace("END Maps;", "GRAPHIC Child EXTENDS Map = Draw (EXTENDED): (Priority := 30); END Child; END Maps;");
            var g=ok(model(version,body)).schema().semanticGraph();
            var child=(GraphicSymbol)named(g,"Child");var base=(GraphicSymbol)named(g,"Map");
            assertEquals(base.id(),child.baseGraphic().orElseThrow());assertEquals(base.basis(),child.basis());assertEquals(base.selections(),child.selections());
            var rule=(DrawingRuleSymbol)g.symbol(child.effectiveRules().getFirst()).orElseThrow();assertEquals(base.rules().getFirst(),rule.overrides().orElseThrow());
            bad(model(version,body.replace("Draw (EXTENDED)","Draw")),"GRAPHIC_RULE_OVERRIDE");
            bad(model(version,body.replace("GRAPHIC Map BASED", "GRAPHIC Map (FINAL) BASED")),"GRAPHIC_FINAL");
            bad(model(version,body.replace("Draw OF M.Signs.Mark", "Draw (FINAL) OF M.Signs.Mark")),"GRAPHIC_RULE_FINAL");
        }
    }

    @Test void translatedGraphicsCompareBoundAssignmentsAndTargets() {
        for(var version:List.of("2.3","2.4")) {
            var source=model(version,BODY).replace("MODEL M", "MODEL Original").replace("END M.","END Original.").replace("M.Data.Point","Original.Data.Point").replace("M.Signs.Mark","Original.Signs.Mark");
            var translation=model(version,BODY.replace("GRAPHIC Map", "GRAPHIC Carte").replace("END Map;","END Carte;").replace("Draw OF", "Dessiner OF"))
                    .replace("INTERLIS "+version+"; ", "").replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Original [\"1\"] =");
            ok(source+translation);
            bad(source+translation.replace("Priority := 10", "Priority := 11"),"TRANSLATION_DRAWING_RULE");
            bad(source+translation.replace("{Dot}","{Square}"),"TRANSLATION_DRAWING_RULE");
        }
    }

    @Test void reusedAnalysisRebindsChangedSignatureParameters() {
        for(var version:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();
            var source=CompilerTestSupport.source("memory:/graphic.ili",model(version,BODY));
            var request=CompilerTestSupport.request(List.of("M"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            var first=assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(request,CancellationToken.none()));
            var edited=new guru.interlis.source.SourceText(source.id(),new guru.interlis.source.SourceRevision(2),source.text().replace("Priority: 0..200","Priority: TEXT"));
            var changed=CompilerTestSupport.request(List.of("M"),Map.of(edited.id(),edited),List.of(),CompilerOptions.defaults());
            var fresh=engine.analyze(changed,CancellationToken.none());
            var reused=engine.analyze(changed,new AnalysisReuse(Optional.of(first.analysis()),first.analysis().syntaxSnapshots()),CancellationToken.none());
            assertEquals(fresh.diagnostics(),reused.diagnostics());
            assertTrue(fresh.diagnostics().toString().contains("GRAPHIC_PARAMETER_TYPE"));
        }
    }

    @Test void abstractGeometryParameterUsesTheSamePublicAssignmentRule() {
        for(var version:List.of("2.3","2.4")) {
            var body="DOMAIN Coord = COORD 0.00..100.00,0.00..100.00; General (ABSTRACT) = COORD NUMERIC,NUMERIC; TOPIC Signs = CLASS Mark EXTENDS INTERLIS.SIGN = PARAMETER Shape: POLYLINE WITH (STRAIGHTS,ARCS) VERTEX General; END Mark; END Signs; SIGN BASKET B ~ Signs OBJECTS OF Mark: Dot; TOPIC T = CLASS C = line: POLYLINE WITH (STRAIGHTS) VERTEX Coord; END C; GRAPHIC G BASED ON C = D OF M.Signs.Mark: (Sign := {Dot}; Shape := line); END G; END T;";
            var g=ok(model(version,body)).schema().semanticGraph();
            var source=((AttributeSymbol)named(g,"line")).declaredType();var target=((ParameterSymbol)named(g,"Shape")).typeId();
            assertEquals(RelationDecision.ALLOWED,new guru.interlis.model.query.TypeRelations(g).canAssign(new TypeRef(g.snapshotId(),source),new TypeRef(g.snapshotId(),target),AssignmentContext.NORMAL).known().orElseThrow().decision());
            bad(model(version,body.replace("line: POLYLINE WITH (STRAIGHTS)","line: POLYLINE WITH (STRAIGHTS,ARCS)").replace("Shape: POLYLINE WITH (STRAIGHTS,ARCS)","Shape: POLYLINE WITH (STRAIGHTS)")),"GRAPHIC_PARAMETER_TYPE");
        }
    }

    @Test void handBookControlPublishesAllGraphicsAndInspections() throws Exception {
        var folder=java.nio.file.Path.of("../testdata/conformance/normative/roadsExgm2ien");
        Map<guru.interlis.source.SourceId,guru.interlis.source.SourceText> sources=new LinkedHashMap<>();
        try(var files=java.nio.file.Files.list(folder)) {
            for(var path:files.sorted().toList()) { var source=CompilerTestSupport.source("memory:/"+path.getFileName(),java.nio.file.Files.readString(path));sources.put(source.id(),source); }
        }
        var result=CompilerEngines.create().compile(CompilerTestSupport.request(List.of("RoadsExgm2ien"),sources,bindings(sources),CompilerOptions.defaults()),CancellationToken.none());
        var success=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
        var graph=success.schema().semanticGraph();assertEquals(5,graph.symbols().stream().filter(GraphicSymbol.class::isInstance).count());
        assertEquals(10,graph.symbols().stream().filter(DrawingRuleSymbol.class::isInstance).count());
        assertTrue(graph.expressions().stream().anyMatch(e->e instanceof PathExpression p && p.path().steps().stream().anyMatch(PathStep.AssociationAccessStep.class::isInstance)));
    }
}
