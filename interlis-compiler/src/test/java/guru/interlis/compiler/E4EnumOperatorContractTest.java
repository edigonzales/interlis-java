package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class E4EnumOperatorContractTest {
    @Test void everyRelationSpellingRetainsItsOperatorAndOriginalRange() {
        var operators=Map.of("==",BinaryOperator.EQ,"!=",BinaryOperator.NEQ,"<>",BinaryOperator.NEQ,
                "<",BinaryOperator.LT,"<=",BinaryOperator.LTE,">",BinaryOperator.GT,">=",BinaryOperator.GTE);
        for(var v:List.of("2.3","2.4"))for(var entry:operators.entrySet()) {
            var request=input(v,"!! 🥳\r\n TOPIC T=CLASS C=a:0..10;b:0..10;MANDATORY CONSTRAINT a"+entry.getKey()+"b;END C;END T;");
            var r=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request,CancellationToken.none()));
            var expression=r.schema().semanticGraph().expressions().stream().filter(BinaryExpression.class::isInstance).map(BinaryExpression.class::cast).findFirst().orElseThrow();
            assertEquals(entry.getValue(),expression.operator());
            assertEquals("a"+entry.getKey()+"b",request.sources().sources().values().iterator().next().slice(expression.range()));
        }
    }
    private static CompilationRequest input(String v,String body) {
        var s=source("memory:/operators.ili","INTERLIS "+v+"; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "+body+" END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String v,String body) {
        var r=CompilerEngines.create().compile(input(v,body),CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString());
    }
    private static void bad(String v,String body) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,body),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
        assertTrue(r.analysis().diagnostics().toString().contains("ILI-SEM-0005") || r.analysis().diagnostics().toString().contains("ENUM_ARGUMENT") || r.analysis().diagnostics().toString().contains("ENUM_TREE_TARGET"),r.analysis().diagnostics().toString());
    }
    private static TypeRef attr(SemanticGraph g,String name) {
        var a=g.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(x->x.name().value().equals(name)).findFirst().orElseThrow();
        return new TypeRef(g.snapshotId(),a.declaredType());
    }
    @Test void inequalitySpellingsAreBothNeqAndTextOrderingIsRejected() {
        for(var v:List.of("2.3","2.4")) {
            var r=ok(v,"TOPIC T=CLASS C=a:TEXT*10; b:TEXT*10; MANDATORY CONSTRAINT a<>b AND a!=b; END C; END T;");
            var g=r.schema().semanticGraph();assertEquals(2,g.expressions().stream().filter(BinaryExpression.class::isInstance).map(BinaryExpression.class::cast).filter(e->e.operator()==BinaryOperator.NEQ).count());
            var q=new TypeRelations(g);assertEquals(RelationDecision.ALLOWED,q.areComparable(attr(g,"a"),attr(g,"b"),ComparisonOperator.EQUALITY).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,q.areComparable(attr(g,"a"),attr(g,"b"),ComparisonOperator.ORDERING).known().orElseThrow().decision());
            for(var op:List.of("<",">","<=",">="))bad(v,"TOPIC T=CLASS C=a:TEXT*10; b:TEXT*10; MANDATORY CONSTRAINT a"+op+"b; END C; END T;");
        }
    }
    @Test void booleanAndCoordinateOperatorsUseThePublicRule() {
        for(var v:List.of("2.3","2.4")) {
            var g=ok(v,"TOPIC T=CLASS C=a:BOOLEAN; b:BOOLEAN; p:COORD 0..10,0..10; q:COORD 0..10,0..10; MANDATORY CONSTRAINT a<b AND p==q; END C; END T;").schema().semanticGraph();
            var q=new TypeRelations(g);assertEquals(RelationDecision.ALLOWED,q.areComparable(attr(g,"a"),attr(g,"b"),ComparisonOperator.ORDERING).known().orElseThrow().decision());
            assertEquals(RelationDecision.ALLOWED,q.areComparable(attr(g,"p"),attr(g,"q"),ComparisonOperator.EQUALITY).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,q.areComparable(attr(g,"p"),attr(g,"q"),ComparisonOperator.ORDERING).known().orElseThrow().decision());
            bad(v,"TOPIC T=CLASS C=p:COORD 0..10,0..10; q:COORD 0..10,0..10; MANDATORY CONSTRAINT p<q; END C; END T;");
        }
    }
    @Test void geometryOnlySupportsDefinednessAndObjectSetsAreNotComparable() {
        for(var v:List.of("2.3","2.4")) {
            String defs="DOMAIN Point=COORD 0.000..10.000,0.000..10.000; TOPIC T=CLASS C=p:POLYLINE WITH(STRAIGHTS) VERTEX Point; q:POLYLINE WITH(STRAIGHTS) VERTEX Point; ";
            ok(v,defs+"MANDATORY CONSTRAINT p<>UNDEFINED; END C; END T;");
            bad(v,defs+"MANDATORY CONSTRAINT p==q; END C; END T;");
            bad(v,defs+"MANDATORY CONSTRAINT ALL==ALL; END C; END T;");
            bad(v,defs+"MANDATORY CONSTRAINT p<UNDEFINED; END C; END T;");
        }
    }
    @Test void sameNamedEnumElementsKeepTheirDomainTarget() {
        for(var v:List.of("2.3","2.4")) {
            var r=ok(v,"DOMAIN E1=(red,blue); DOMAIN E2=(red,green); TOPIC T=CLASS C=a:E1;b:E2; MANDATORY CONSTRAINT a==#red AND b==#red; END C; END T;");
            var g=r.schema().semanticGraph();
            var constants=g.expressions().stream().filter(ConstantExpression.class::isInstance)
                    .map(ConstantExpression.class::cast).filter(x->x.constantName().equals("#red")).toList();
            assertEquals(2,constants.size());
            assertNotEquals(constants.get(0).typeId(),constants.get(1).typeId());
            var source=r.analysis().syntaxSnapshots().values().iterator().next().source();
            int first=source.text().indexOf("#red",source.text().indexOf("MANDATORY CONSTRAINT"))+1;
            int second=source.text().indexOf("#red",first)+1;
            var queries=guru.interlis.compiler.query.LanguageQueries.create();
            var firstDefinition=queries.definition(r.analysis(),source.range(first,first),CancellationToken.none()).known().orElseThrow();
            var secondDefinition=queries.definition(r.analysis(),source.range(second,second),CancellationToken.none()).known().orElseThrow();
            assertNotEquals(firstDefinition.symbol().symbol(),secondDefinition.symbol().symbol());
        }
    }
    @Test void formalEnumSignaturesAndTreeValuesArePublicAndIdentityBased() {
        for(var v:List.of("2.3","2.4")) {
            var r=ok(v,"DOMAIN E=(root(a,b),other) ORDERED; Tree=ALL OF E; FUNCTION f(a:ENUMVAL; b:ENUMTREEVAL):BOOLEAN; TOPIC T=CLASS C=leaf:E; node:Tree; MANDATORY CONSTRAINT f(leaf,node) AND INTERLIS.isEnumSubVal(leaf,#root) AND INTERLIS.inEnumRange(leaf,#root,#other); END C; END T;");
            var g=r.schema().semanticGraph();var f=g.symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).filter(x->x.name().value().equals("f")).findFirst().orElseThrow();
            assertFalse(assertInstanceOf(EnumArgumentType.class,g.type(f.parameters().get(0).typeId()).orElseThrow()).treeValues());
            assertTrue(assertInstanceOf(EnumArgumentType.class,g.type(f.parameters().get(1).typeId()).orElseThrow()).treeValues());
            var q=new TypeRelations(g);var tree=q.resolveAliases(attr(g,"node")).known().orElseThrow();assertTrue(assertInstanceOf(EnumerationType.class,g.type(tree.effectiveType()).orElseThrow()).treeValues());
            assertEquals(RelationDecision.ALLOWED,q.canAssign(attr(g,"leaf"),attr(g,"node"),AssignmentContext.NORMAL).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,q.canAssign(attr(g,"node"),attr(g,"leaf"),AssignmentContext.NORMAL).known().orElseThrow().decision());
            for(var name:List.of("isEnumSubVal","inEnumRange")) {
                var ref=r.schema().queries().findQualifiedName(QualifiedName.of("INTERLIS",name)).getFirst();var function=(FunctionSymbol)g.symbol(ref.symbol()).orElseThrow();
                assertTrue(function.origin() instanceof Origin.PredefinedOrigin);
                assertTrue(g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).anyMatch(x->x.targetFunction().equals(Optional.of(function.id()))));
                assertTrue(g.references().stream().anyMatch(x->x.resolution().equals(new BindingResolution.BoundTarget(function.id()))));
            }
        }
    }
    @Test void enumFunctionsRejectWrongFamiliesUnrelatedEnumsAndInvalidNodes() {
        for(var v:List.of("2.3","2.4")) {
            String defs="DOMAIN E=(root(a,b),other) ORDERED; Wrong=(root(a,b),other) ORDERED; Tree=ALL OF E; TOPIC T=CLASS C=e:E; w:Wrong; t:Tree; ";
            for(var expression:List.of("INTERLIS.isEnumSubVal(e,w)","INTERLIS.isEnumSubVal(e,#missing)","INTERLIS.isEnumSubVal(123,#root)","INTERLIS.inEnumRange(t,#root,#other)","INTERLIS.inEnumRange(e,#root)","INTERLIS.inEnumRange(e,#root,#missing)"))
                bad(v,defs+"MANDATORY CONSTRAINT "+expression+"; END C; END T;");
            bad(v,"DOMAIN N=0..10; T=ALL OF N;");
            bad(v,"DOMAIN E=(a,b); T=ALL OF E; Wrong=ALL OF T;");
        }
    }
    @Test void enumComparisonConstantsRetainTheBoundTypeAndNavigationTarget() {
        for(var v:List.of("2.3","2.4")) {
            var r=ok(v,"TOPIC T=CLASS C=e:(red,green) ORDERED; MANDATORY CONSTRAINT e==#red; END C; END T;");
            var g=r.schema().semanticGraph();var constant=g.expressions().stream().filter(ConstantExpression.class::isInstance).map(ConstantExpression.class::cast).filter(x->x.constantName().equals("#red")).findFirst().orElseThrow();
            assertEquals(attr(g,"e").type(),constant.typeId());
            assertEquals(RelationDecision.ALLOWED,new TypeRelations(g).areComparable(attr(g,"e"),new TypeRef(g.snapshotId(),constant.typeId()),ComparisonOperator.EQUALITY).known().orElseThrow().decision());
            var reference=g.references().stream().filter(x->x.range().startOffset()==constant.range().startOffset()+1).findFirst().orElseThrow();
            var target=((BindingResolution.BoundTarget)reference.resolution()).target();assertInstanceOf(EnumElementSymbol.class,g.symbol(target).orElseThrow());
        }
    }
    @Test void geometryFunctionsHaveVersionedSignaturesAndAcceptOnlyTheirGeometryFamily() {
        String prefix="DOMAIN Point=COORD 0.000..10.000,0.000..10.000; TOPIC T=CLASS C=";
        for(var entry:List.of(List.of("length","POLYLINE"),List.of("multilength","MULTIPOLYLINE"),
                List.of("surface","SURFACE"),List.of("surface","AREA"),List.of("multisurface","MULTISURFACE"),List.of("multisurface","MULTIAREA"))) {
            String name=entry.get(0),type=entry.get(1);
            String body=prefix+"p:"+type+" WITH(STRAIGHTS) VERTEX Point; MANDATORY CONSTRAINT INTERLIS."+name+"(p)>0; END C; END T;";
            var r=ok("2.4",body);var g=r.schema().semanticGraph();
            var ref=r.schema().queries().findQualifiedName(QualifiedName.of("INTERLIS",name)).getFirst();var f=(FunctionSymbol)g.symbol(ref.symbol()).orElseThrow();
            assertInstanceOf(GeometryType.class,g.type(f.parameters().getFirst().typeId()).orElseThrow());
            assertInstanceOf(NumericType.class,g.type(f.returnType()).orElseThrow());
            bad("2.4",prefix+"p:BOOLEAN; MANDATORY CONSTRAINT INTERLIS."+name+"(p)>0; END C; END T;");
        }
        for(String name:List.of("length","multilength","surface","multisurface")) {
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.3",prefix+"p:POLYLINE WITH(STRAIGHTS) VERTEX Point; MANDATORY CONSTRAINT INTERLIS."+name+"(p)>0; END C; END T;"),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().toString().contains("BUILTIN_NAME"));
            assertTrue(rejected.analysis().semanticGraph().symbols().stream().noneMatch(x->x instanceof FunctionSymbol && x.name().value().equals("INTERLIS."+name)));
        }
    }
    @Test void unqualifiedPredefinedFunctionsRequireTheirImportAndLocalFunctionsKeepTheirIdentity() {
        for(var v:List.of("2.3","2.4")) {
            var r=ok(v,"IMPORTS UNQUALIFIED INTERLIS; TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0; END C; END T;");
            var g=r.schema().semanticGraph();var call=g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
            assertEquals("INTERLIS.len",g.symbol(call.targetFunction().orElseThrow()).orElseThrow().name().value());
            var local=ok(v,"IMPORTS UNQUALIFIED INTERLIS; FUNCTION len(v:BOOLEAN):BOOLEAN; TOPIC T=CLASS C=MANDATORY CONSTRAINT len(#true); END C; END T;").schema().semanticGraph();
            var userCall=local.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
            assertTrue(local.symbol(userCall.targetFunction().orElseThrow()).orElseThrow().origin() instanceof Origin.DeclaredOrigin);
        }
    }
    @Test void importedEnumsAndEditSequencesKeepTheSameBoundResults() {
        for(var v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili","INTERLIS "+v+"; CONTRACTED MODEL Base AT \"https://example.org\" VERSION \"1\" = DOMAIN E=(root(a,b),other) ORDERED; Tree=ALL OF E; FUNCTION f(a:ENUMVAL;b:ENUMTREEVAL):BOOLEAN; END Base.");
            var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String node:List.of("root","missing","root")) {
                var use=new SourceText(id,new SourceRevision(++revision),"INTERLIS "+v+"; MODEL F AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; TOPIC T=CLASS C=e:Base.E; t:Base.Tree; MANDATORY CONSTRAINT Base.f(e,t) AND INTERLIS.isEnumSubVal(e,#"+node+"); END C; END T; END F.");
                var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(request,CancellationToken.none());var reused=previous==null?fresh:engine.analyze(request,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(),reused.semanticGraph()));
                assertEquals(node.equals("root"),fresh.diagnostics().isEmpty(),fresh.diagnostics().toString());previous=reused;
            }
        }
    }
    @Test void assignmentsAndConcreteFunctionFormalsBindEnumAndFormatConstantsOnce() {
        for(var v:List.of("2.3","2.4")) {
            String prefix="DOMAIN E=(root(a,b),other) ORDERED; FUNCTION f(a:E;b:INTERLIS.XMLDate):BOOLEAN; TOPIC T=CLASS C=e:E:=#root.a; d:INTERLIS.XMLDate:=\"2020-01-01\"; ";
            var r=ok(v,prefix+"MANDATORY CONSTRAINT f(#other,\"2020-01-02\"); END C; END T;");var g=r.schema().semanticGraph();
            var q=new TypeRelations(g);
            for(var expression:g.expressions()) {
                if(expression instanceof ConstantExpression c && c.constantName().startsWith("#"))
                    assertEquals(RelationDecision.ALLOWED,q.canAssign(new TypeRef(g.snapshotId(),c.typeId()),attr(g,"e"),AssignmentContext.NORMAL).known().orElseThrow().decision());
                if(expression instanceof LiteralExpression literal && literal.textValue().isPresent())
                    assertInstanceOf(FormattedType.class,g.type(q.resolveAliases(new TypeRef(g.snapshotId(),literal.typeId())).known().orElseThrow().effectiveType()).orElseThrow());
            }
            bad(v,prefix.replace("#root.a","#root")+"END C; END T;");
            bad(v,prefix+"MANDATORY CONSTRAINT f(#missing,\"2020-01-02\"); END C; END T;");
            bad(v,prefix+"MANDATORY CONSTRAINT f(#other,\"2020-13-02\"); END C; END T;");
        }
    }
    @Test void enumerationComparisonsRequireLinearOrderFromTheDefinition() {
        for(var v:List.of("2.3","2.4"))for(var order:List.of("ORDERED","CIRCULAR","")) {
            String body="DOMAIN E=(root(a,b),other) "+order+"; Tree=ALL OF E; TOPIC T=CLASS C=a:Tree;b:Tree; MANDATORY CONSTRAINT a<b; END C; END T;";
            if(!order.equals("ORDERED"))bad(v,body);
            else {
                var g=ok(v,body).schema().semanticGraph();
                assertEquals(RelationDecision.ALLOWED,new TypeRelations(g).areComparable(attr(g,"a"),attr(g,"b"),ComparisonOperator.ORDERING).known().orElseThrow().decision());
            }
        }
    }
    @Test void translatedTreeDefinitionsCannotSwitchToAnIdenticallyShapedTarget() {
        for(var v:List.of("2.3","2.4")) {
            String original="INTERLIS "+v+"; MODEL M AT \"https://example.org\" VERSION \"1\" = DOMAIN E=(a,b); Other=(a,b); Tree=ALL OF E; END M.";
            String translation="MODEL N AT \"https://example.org\" VERSION \"1\" TRANSLATION OF M[\"1\"]=DOMAIN F=(c,d); Wrong=(c,d); Branch=ALL OF F; END N.";
            for(boolean wrong:List.of(false,true)) {
                var source=source("memory:/tree-translation.ili",original+translation.replace("Branch=ALL OF F",wrong?"Branch=ALL OF Wrong":"Branch=ALL OF F"));
                var r=CompilerEngines.create().compile(request(List.of("N"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none());
                if(wrong) {
                    var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,r);assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
                    assertTrue(rejected.analysis().diagnostics().toString().contains("TRANSLATION"));
                } else assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r.toString());
            }
        }
    }
    @Test void formattedComparisonsRequireRelatedBoundComponents() {
        for(var v:List.of("2.3","2.4")) {
            String prefix="TOPIC T=CLASS C=a:INTERLIS.XMLDate;b:INTERLIS.XMLDate;t:INTERLIS.XMLTime;dt:INTERLIS.XMLDateTime;";
            var g=ok(v,prefix+"MANDATORY CONSTRAINT a<=b;END C;END T;").schema().semanticGraph();var q=new TypeRelations(g);
            assertEquals(RelationDecision.ALLOWED,q.areComparable(attr(g,"a"),attr(g,"dt"),ComparisonOperator.ORDERING).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,q.areComparable(attr(g,"a"),attr(g,"t"),ComparisonOperator.EQUALITY).known().orElseThrow().decision());
            bad(v,prefix+"MANDATORY CONSTRAINT a==t;END C;END T;");
        }
    }
    @Test void oidEqualityIsPreservedWithoutIntroducingIdentifierOrdering() {
        for(var v:List.of("2.3","2.4")) {
            String prefix="DOMAIN O=OID TEXT*20; TOPIC T=CLASS C=a:O;b:O;";
            var g=ok(v,prefix+"MANDATORY CONSTRAINT a==b;END C;END T;").schema().semanticGraph();var q=new TypeRelations(g);
            assertEquals(RelationDecision.ALLOWED,q.areComparable(attr(g,"a"),attr(g,"b"),ComparisonOperator.EQUALITY).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,q.areComparable(attr(g,"a"),attr(g,"b"),ComparisonOperator.ORDERING).known().orElseThrow().decision());
            bad(v,prefix+"MANDATORY CONSTRAINT a<b;END C;END T;");
        }
    }
}
