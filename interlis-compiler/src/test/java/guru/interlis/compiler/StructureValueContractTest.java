package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import java.util.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class StructureValueContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final String STRUCTURES="STRUCTURE S=value:TEXT*20; END S; STRUCTURE A EXTENDS S=leftValue:BOOLEAN; END A; STRUCTURE B EXTENDS S=rightValue:BOOLEAN; END B; STRUCTURE Other=END Other;";
    private static String wrap(String version,String name,String body) {return "INTERLIS "+version+"; CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\" = "+body+" END "+name+".";}
    private static CompilationRequest input(String version,String body) {var source=source("memory:/structures.ili",wrap(version,"F",body));return request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());}
    private static CompilationOutcome.Succeeded ok(String version,String body) {var result=CompilerEngines.create().compile(input(version,body),TOKEN);return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());}
    private static AttributeSymbol attribute(SemanticGraph graph,String name) {return graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(a->a.name().value().equals(name)).findFirst().orElseThrow();}
    private static StructureValueType structure(SemanticGraph graph,String name) {var type=graph.type(attribute(graph,name).declaredType()).orElseThrow();if(type instanceof CollectionType c)type=graph.type(c.elementType()).orElseThrow();return assertInstanceOf(StructureValueType.class,type);}
    @Test void anyStructureAndOrderedRestrictionsAreBoundForScalarAndCollectionAttributes() {
        for(var version:List.of("2.3","2.4")) {
            var result=ok(version,STRUCTURES+"TOPIC T=CLASS C=any:ANYSTRUCTURE; detail:ANYSTRUCTURE RESTRICTION(A); details:LIST{1..2} OF S RESTRICTION(A;B); MANDATORY CONSTRAINT detail->leftValue AND INTERLIS.len(details[1]->value)>0; END C; END T;");
            var graph=result.schema().semanticGraph();assertTrue(structure(graph,"any").target().isEmpty());assertTrue(structure(graph,"any").restrictions().isEmpty());
            var detail=structure(graph,"detail");assertTrue(detail.target().isEmpty());assertEquals("A",graph.symbol(detail.restrictions().getFirst()).orElseThrow().name().value());
            var details=structure(graph,"details");assertEquals("S",graph.symbol(details.target().orElseThrow()).orElseThrow().name().value());
            assertEquals(List.of("A","B"),details.restrictions().stream().map(id->graph.symbol(id).orElseThrow().name().value()).toList());
            assertThrows(UnsupportedOperationException.class,()->details.restrictions().clear());
            assertEquals(CollectionKind.LIST,assertInstanceOf(CollectionType.class,graph.type(attribute(graph,"details").declaredType()).orElseThrow()).collectionKind());
            assertTrue(graph.references().stream().filter(r->r.resolution() instanceof BindingResolution.BoundTarget).anyMatch(r->r.range().startOffset()>0 && r.segments().getLast().text().equals("A")));
        }
    }
    @Test void unrelatedMissingAndWrongKindRestrictionsAreSourceErrors() {
        for(var version:List.of("2.3","2.4"))for(var target:List.of("Other","Missing","S.value")) {
            var body=STRUCTURES+"TOPIC T=CLASS C=a:S RESTRICTION("+target+"); END C; END T;";
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
            if(target.equals("Other"))assertTrue(result.analysis().diagnostics().toString().contains("STRUCTURE_RESTRICTION"));
        }
    }
    @Test void functionContextAcceptsObjectsAsTheirStructureAndNormalAssignmentDoesNot() {
        for(var version:List.of("2.3","2.4")) {
            var graph=ok(version,STRUCTURES+"FUNCTION any(v:ANYSTRUCTURE):BOOLEAN; FUNCTION base(v:S):BOOLEAN; TOPIC T=CLASS C EXTENDS S=MANDATORY CONSTRAINT any(THIS) AND base(THIS); END C; END T;").schema().semanticGraph();
            var functions=graph.symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).toList();var any=functions.stream().filter(f->f.name().value().equals("any")).findFirst().orElseThrow();
            var call=graph.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).filter(c->c.targetFunction().equals(Optional.of(any.id()))).findFirst().orElseThrow();
            var from=new TypeRef(graph.snapshotId(),graph.expression(call.children().getFirst()).orElseThrow().typeId());var to=new TypeRef(graph.snapshotId(),any.parameters().getFirst().typeId());
            var query=new TypeRelations(graph);assertEquals(RelationDecision.ALLOWED,query.canAssign(from,to,AssignmentContext.FUNCTION_ARGUMENT).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,query.canAssign(from,to,AssignmentContext.NORMAL).known().orElseThrow().decision());
            for(var callText:List.of("base(THIS)","any(123)","any(ALL)")) {
                var body=STRUCTURES+"FUNCTION any(v:ANYSTRUCTURE):BOOLEAN; FUNCTION base(v:S):BOOLEAN; TOPIC T=CLASS C=MANDATORY CONSTRAINT "+callText+"; END C; END T;";
                assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body),TOKEN));
            }
        }
    }
    @Test void sharedMembersBindAcrossRestrictedAlternativesButBranchOnlyMembersDoNot() {
        for(var version:List.of("2.3","2.4")) {
            ok(version,STRUCTURES+"TOPIC T=CLASS C=a:ANYSTRUCTURE RESTRICTION(A;B); MANDATORY CONSTRAINT INTERLIS.len(a->value)>0; END C; END T;");
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,STRUCTURES+"TOPIC T=CLASS C=a:ANYSTRUCTURE RESTRICTION(A;B); MANDATORY CONSTRAINT a->leftValue; END C; END T;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
        }
    }
    @Test void requiredWildcardsAndUnavailableConcreteStructuresNeedExplicitAbstractAttributes() {
        for(var version:List.of("2.3","2.4")) {
            for(var type:List.of("MANDATORY ANYSTRUCTURE","LIST{1..2} OF ANYSTRUCTURE","MANDATORY S")) {
                var body="STRUCTURE S(ABSTRACT)=END S; TOPIC T=CLASS C=a:"+type+"; END C; END T;";
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body),TOKEN));
                assertTrue(rejected.analysis().diagnostics().toString().contains("STRUCTURE_ABSTRACT_REQUIRED"));
                ok(version,body.replace("TOPIC T=CLASS C=a:","TOPIC T(ABSTRACT)=CLASS C(ABSTRACT)=a(ABSTRACT):"));
            }
            ok(version,"STRUCTURE S(ABSTRACT)=END S; STRUCTURE A EXTENDS S=END A; TOPIC T=CLASS C=a:MANDATORY S; b:ANYSTRUCTURE; END C; END T;");
            for(var owner:List.of("CLASS","STRUCTURE")) {
                var body="TOPIC T="+owner+" C=a(ABSTRACT):TEXT*10; END C; END T;";
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body),TOKEN));
                assertTrue(rejected.analysis().diagnostics().toString().contains("ABSTRACT_MEMBER"));
            }
        }
    }
    @Test void collectionOverridesCanAddOrderAndNarrowRootsButCannotRemoveOrderOrWidenRoots() {
        for(var version:List.of("2.3","2.4")) {
            var accepted=ok(version,STRUCTURES+"FUNCTION count(v:BAG OF ANYSTRUCTURE):NUMERIC; TOPIC T=CLASS C=items:BAG OF S RESTRICTION(A;B); END C; CLASS D EXTENDS C=items(EXTENDED):LIST{1..2} OF A; MANDATORY CONSTRAINT count(items)>0; END D; END T;");
            var graph=accepted.schema().semanticGraph();var attrs=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(a->a.name().value().equals("items")).toList();
            var parent=attrs.stream().filter(a->graph.symbol(a.owner().orElseThrow()).orElseThrow().name().value().equals("C")).findFirst().orElseThrow();
            var derived=attrs.stream().filter(a->graph.symbol(a.owner().orElseThrow()).orElseThrow().name().value().equals("D")).findFirst().orElseThrow();
            assertEquals(RelationDecision.ALLOWED,new TypeRelations(graph).canExtend(new TypeRef(graph.snapshotId(),derived.declaredType()),new TypeRef(graph.snapshotId(),parent.declaredType()),ExtensionContext.DOMAIN_EXTENSION,version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision());
            for(var body:List.of("TOPIC T=CLASS C=items:LIST OF S; END C; CLASS D EXTENDS C=items(EXTENDED):BAG OF A; END D; END T;",
                    "TOPIC T=CLASS C=items:BAG OF S RESTRICTION(A); END C; CLASS D EXTENDS C=items(EXTENDED):BAG OF S RESTRICTION(A;B); END D; END T;")) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,STRUCTURES+body),TOKEN));
                assertTrue(rejected.analysis().diagnostics().toString().contains("ATTRIBUTE_OVERRIDE_TYPE"));
            }
        }
    }
    @Test void concreteStructuresInOtherTopicsAreOnlyAvailableThroughExplicitRoots() {
        for(var version:List.of("2.3","2.4")) {
            var prefix="TOPIC Base(ABSTRACT)=STRUCTURE S(ABSTRACT)=END S; END Base; TOPIC Foreign=STRUCTURE A EXTENDS F.Base.S=END A; END Foreign;";
            var body=prefix+"TOPIC T=CLASS C=a:MANDATORY F.Base.S; END C; END T;";
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body),TOKEN));
            assertTrue(rejected.analysis().diagnostics().toString().contains("STRUCTURE_ABSTRACT_REQUIRED"));
            ok(version,body.replace("MANDATORY F.Base.S;","MANDATORY F.Base.S RESTRICTION(F.Foreign.A);"));
            ok(version,prefix+"TOPIC T=STRUCTURE Local EXTENDS F.Base.S=END Local; CLASS C=a:MANDATORY F.Base.S; END C; END T;");
        }
    }
    @Test void translationsCompareRestrictedIdentitiesEvenWhenStructureShapesAreEqual() {
        for(var version:List.of("2.3","2.4"))for(var wrong:List.of(false,true)) {
            var body="STRUCTURE Root=value:TEXT*20; END Root; STRUCTURE Leaf EXTENDS Root=END Leaf; STRUCTURE Other EXTENDS Root=END Other; TOPIC T=CLASS C=detail:ANYSTRUCTURE RESTRICTION(Leaf); END C; END T;";
            var translated=body.replace("Root","Stamm").replace("Leaf","Ast").replace("Other","Anderer");
            if(wrong)translated=translated.replace("RESTRICTION(Ast)","RESTRICTION(Anderer)");
            var text=wrap(version,"Original",body)+wrap(version,"F",translated).replace("INTERLIS "+version+";","").replace("VERSION \"1\" =","VERSION \"1\" TRANSLATION OF Original [\"1\"] =");
            var source=source("memory:/translation.ili",text);var request=request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            var result=CompilerEngines.create().compile(request,TOKEN);
            if(wrong) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,result);
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
                assertTrue(rejected.analysis().diagnostics().toString().contains("TRANSLATION_REFERENCE"));
            } else assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
        }
    }
    @Test void importedRestrictionsAndEditsRemainBoundInEitherSourceOrder() {
        for(var version:List.of("2.3","2.4"))for(var order:List.of(false,true)) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            var base=source(order?"memory:/a-base.ili":"memory:/z-base.ili",wrap(version,"Base",STRUCTURES));
            for(var name:List.of("A","Missing","A")) {
                var use=new SourceText(new SourceId(order?"memory:/z-use.ili":"memory:/a-use.ili"),new SourceRevision(++revision),wrap(version,"F","IMPORTS Base; FUNCTION f(v:ANYSTRUCTURE):BOOLEAN; TOPIC T=CLASS C=a:ANYSTRUCTURE RESTRICTION(Base."+name+"); MANDATORY CONSTRAINT f(a); END C; END T;"));
                var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(request,TOKEN);var reused=previous==null?fresh:engine.analyze(request,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().types(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().types(),reused.semanticGraph()));
                assertEquals(name.equals("Missing"),reused.diagnostics().stream().anyMatch(d->d.severity()==guru.interlis.diagnostics.Severity.ERROR));previous=reused;
            }
        }
    }

    @Test void longStructureHierarchiesKeepTheirInheritedMembersAndNumericTypes() {
        for(var version:List.of("2.3","2.4")) {
            var body=new StringBuilder("STRUCTURE S0=n:0..100; END S0;");
            for(int i=1;i<=70;i++)body.append("STRUCTURE S").append(i).append(" EXTENDS S").append(i-1).append("=END S").append(i).append(';');
            body.append("TOPIC T=CLASS C EXTENDS S70=n(EXTENDED):MANDATORY; MANDATORY CONSTRAINT n>0; END C; END T;");
            var graph=ok(version,body.toString()).schema().semanticGraph();
            var n=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(a->a.name().value().equals("n") && graph.symbol(a.owner().orElseThrow()).orElseThrow().name().value().equals("C")).findFirst().orElseThrow();
            assertTrue(n.mandatory());assertInstanceOf(NumericType.class,graph.type(n.declaredType()).orElseThrow());
            var members=new guru.interlis.model.query.DefaultSemanticQueries(graph).effectiveMembers(new SymbolRef(graph.snapshotId(),n.owner().orElseThrow())).known().orElseThrow();
            assertEquals(List.of(n.id()),members.stream().map(EffectiveMember::declaredSymbol).toList());
        }
    }

    @Test void runtimeWildcardParametersRetainAnExplicitUnrestrictedType() {
        for(var version:List.of("2.3","2.4")) {
            var result=ok(version,"PARAMETER p:ANYSTRUCTURE; TOPIC T=CLASS C=MANDATORY CONSTRAINT INTERLIS.isOfClass(PARAMETER p, >F.T.C); END C; END T;");
            var graph=result.schema().semanticGraph();var parameter=graph.symbols().stream().filter(ParameterSymbol.class::isInstance).map(ParameterSymbol.class::cast).findFirst().orElseThrow();
            var type=assertInstanceOf(StructureValueType.class,graph.type(parameter.typeId()).orElseThrow());
            assertTrue(type.target().isEmpty());assertTrue(type.restrictions().isEmpty());
        }
    }

}
