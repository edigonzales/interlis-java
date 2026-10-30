package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** AC2-01/02: the approved SET condition context is distinct from its WHERE. */
class SetConditionContextContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String wrap(String v,String body) {
        return "INTERLIS "+v+";\r\n!! 🦊\r\nCONTRACTED MODEL F AT \"x\" VERSION \"1\"="+body+"END F.";
    }
    private static CompilationRequest input(String v,String body) {
        var s=new SourceText(new SourceId("memory:/set-context.ili"),new SourceRevision(9),wrap(v,body));
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest in) {
        var out=CompilerEngines.create().compile(in,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static CompilationOutcome.Rejected bad(CompilationRequest in,String segment) {
        var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(in,TOKEN));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
        assertEquals(1,out.analysis().diagnostics().size(),out.analysis().diagnostics().toString());
        var diagnostic=out.analysis().diagnostics().getFirst();
        assertEquals("ILI-SEM-0008",diagnostic.code().value());
        assertEquals(guru.interlis.diagnostics.Phase.TYPE_CHECKING,diagnostic.phase());
        var source=in.sources().sources().values().iterator().next();
        assertEquals(segment,source.slice(diagnostic.primaryRange().orElseThrow()));
        assertEquals(source.revision(),diagnostic.primaryRange().orElseThrow().revision());
        assertTrue(diagnostic.failureId().isEmpty());
        var g=out.analysis().semanticGraph();
        assertTrue(g.expressions().stream().anyMatch(e->e instanceof ErrorExpression error&&error.causes().contains(diagnostic.id())));
        return out;
    }
    @Test void directObjectValuesAndNestedActualsAreRejectedWithTypedSourceErrors() {
        for(String v:List.of("2.3","2.4"))for(var example:List.of(
                List.of("n>0","n"),List.of("NOT (flag)","flag"),List.of("INTERLIS.len(label)>0","label"),List.of("DEFINED(THIS)","THIS"),
                List.of("F.check(THIS)","THIS"),List.of("F.check((THIS))","THIS"),List.of("DEFINED(items[FIRST]->code)","items"))) {
            bad(input(v,"FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;STRUCTURE S=code:TEXT;END S;"
                    +"TOPIC T=CLASS C=n:0..10;flag:BOOLEAN;label:TEXT;items:LIST OF S;SET CONSTRAINT "+example.getFirst()+";END C;END T;"),example.get(1));
        }
    }
    @Test void whereRetainsIndividualValuesAndPublicReferencesWhileTheBodyUsesTheSet() {
        for(String v:List.of("2.3","2.4")) {
            var in=input(v,"PARAMETER enabled:BOOLEAN;FUNCTION check(cls:CLASS;attr:ATTRIBUTE OF @cls):BOOLEAN;"
                    +"TOPIC T=CLASS C=n:0..10;label:TEXT;SET CONSTRAINT WHERE n>0 AND DEFINED(THIS):"
                    +"INTERLIS.objectCount(ALL)>=0 AND PARAMETER enabled AND F.check(>C,>>label);END C;END T;");
            var out=ok(in);var g=out.schema().semanticGraph();
            var set=g.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast)
                    .map(ConstraintSymbol::definition).filter(SetConstraint.class::isInstance).map(SetConstraint.class::cast).findFirst().orElseThrow();
            assertTrue(set.where().isPresent());assertEquals(ExpressionTypeState.RESOLVED,g.expression(set.condition()).orElseThrow().typeState());
            var source=in.sources().sources().values().iterator().next();int at=source.text().indexOf("WHERE n")+6;
            var target=LanguageQueries.create().definition(out.analysis(),source.range(at,at),TOKEN).known().orElseThrow();
            assertEquals("n",g.symbol(target.symbol().symbol()).orElseThrow().name().value());
            assertTrue(g.expressions().stream().anyMatch(ObjectSetExpression.class::isInstance));
        }
    }
    @Test void confirmedObjectSetFunctionsStillAcceptSingleObjectsOutsideSetConditions() {
        for(String v:List.of("2.3","2.4")) {
            ok(input(v,"FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C="
                    +"MANDATORY CONSTRAINT F.check(THIS);MANDATORY CONSTRAINT F.check(ALL);"
                    +"CONSTRAINT >=90% F.check(THIS);CONSTRAINT >=90% F.check(ALL);"
                    +"SET CONSTRAINT F.check(ALL);END C;END T;"));
            bad(input(v,"FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C=SET CONSTRAINT F.check(THIS);END C;END T;"),"THIS");
        }
    }
    @Test void approvedAreaNamesKeepSetSignaturesAndSeparatelyBoundStringPaths() {
        for(String name:List.of("areAreas2","areAreas3"))for(String actual:List.of("ALL","THIS")) {
            var in=input("2.4","DOMAIN P=COORD 0.000..10.000,0.000..10.000;TOPIC T=CLASS C="
                    +"surface:SURFACE WITH(STRAIGHTS) VERTEX P WITHOUT OVERLAPS>0.010;"
                    +(actual.equals("ALL")?"SET":"MANDATORY")+" CONSTRAINT INTERLIS."+name+"("+actual+",UNDEFINED,\"surface\");END C;END T;");
            var g=ok(in).schema().semanticGraph();
            var call=g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
            var function=(FunctionSymbol)g.symbol(call.targetFunction().orElseThrow()).orElseThrow();
            assertTrue(((ObjectArgumentType)g.type(function.parameters().getFirst().typeId()).orElseThrow()).objects());
            var bound=assertInstanceOf(PathExpression.class,g.expression(call.argumentPaths().get(2).binding().orElseThrow()).orElseThrow());
            assertEquals("surface",((PathStep.AttributeStep)bound.path().steps().getFirst()).name());
        }
    }
    @Test void externalConstraintBodiesUseTheSameSetRuleAndKeepTheIndividualWhere() {
        for(String v:List.of("2.3","2.4")) {
            String prefix="TOPIC T=CLASS C=n:0..10;END C;CONSTRAINTS OF C=";
            bad(input(v,prefix+"SET CONSTRAINT n>0;END;END T;"),"n");
            var g=ok(input(v,prefix+"SET CONSTRAINT WHERE n>0:INTERLIS.objectCount(ALL)>=0;END;END T;")).schema().semanticGraph();
            var constraint=g.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast).findFirst().orElseThrow();
            assertEquals("C",g.symbol(constraint.target().orElseThrow()).orElseThrow().name().value());
            assertTrue(((SetConstraint)constraint.definition()).where().isPresent());
        }
    }
    @Test void associationRoleValuesAreIndividualOnlyInWhere() {
        for(String v:List.of("2.3","2.4")) {
            String prefix="TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS C=END C;ASSOCIATION Link=a--{1}C;b--{1}A;END Link;CONSTRAINTS OF C=";
            String suffix="END;END T;";
            bad(input(v,prefix+"SET CONSTRAINT INTERLIS.isOfClass(b,>A);"+suffix),"b");
            var g=ok(input(v,prefix+"SET CONSTRAINT WHERE INTERLIS.isOfClass(b,>A):INTERLIS.objectCount(ALL)>=0;"+suffix)).schema().semanticGraph();
            var role=g.symbols().stream().filter(RoleSymbol.class::isInstance).filter(s->s.name().value().equals("b")).findFirst().orElseThrow();
            assertTrue(g.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast)
                    .flatMap(e->e.path().steps().stream()).anyMatch(s->s instanceof PathStep.AssociationAccessStep access&&access.targetRole().equals(role.id())));
        }
    }
    @Test void only24RequiresAnObjectSetFunctionInTheConditionItself() {
        var nonBoolean=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(
                input("2.4","TOPIC T=CLASS C=SET CONSTRAINT 5;END C;END T;"),TOKEN));
        assertEquals(1,nonBoolean.analysis().diagnostics().size(),"No additional function-presence error for a non-boolean condition");
        for(String body:List.of("#true","INTERLIS.len(\"fixed\")>0")) {
            ok(input("2.3","TOPIC T=CLASS C=SET CONSTRAINT "+body+";END C;END T;"));
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(
                    input("2.4","TOPIC T=CLASS C=SET CONSTRAINT WHERE INTERLIS.objectCount(ALL)>=0:"+body+";END C;END T;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertEquals(1,out.analysis().diagnostics().size());assertEquals("ILI-SEM-0005",out.analysis().diagnostics().getFirst().code().value());
            var g=out.analysis().semanticGraph();var set=g.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast)
                    .map(ConstraintSymbol::definition).filter(SetConstraint.class::isInstance).map(SetConstraint.class::cast).findFirst().orElseThrow();
            assertInstanceOf(ErrorExpression.class,g.expression(set.condition()).orElseThrow());
            ok(input("2.4","TOPIC T=CLASS C=SET CONSTRAINT "+body+" AND INTERLIS.objectCount(ALL)>=0;END C;END T;"));
        }
    }
    @Test void alreadyErroneousSetExpressionsDoNotProduceDependentTranslationMismatches() {
        for(String v:List.of("2.3","2.4")) {
            var s=source("memory:/set-translation.ili","INTERLIS "+v+";MODEL A AT \"x\" VERSION \"1\"=TOPIC T=CLASS C=flag:BOOLEAN;SET CONSTRAINT flag;END C;END T;END A."
                    +"MODEL B AT \"x\" VERSION \"1\" TRANSLATION OF A[\"1\"]=TOPIC U=CLASS D=fahne:BOOLEAN;SET CONSTRAINT fahne;END D;END U;END B.");
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(List.of("B"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults()),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());assertEquals(2,out.analysis().diagnostics().size());
            assertTrue(out.analysis().diagnostics().stream().allMatch(d->d.code().value().equals("ILI-SEM-0008")));
        }
    }
    @Test void movingAnObjectPathBetweenWhereAndConditionMatchesFreshAnalysisAcrossRevisions() {
        for(String v:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(boolean valid:List.of(true,false,true)) {
                var source=new SourceText(new SourceId("memory:/set-edit.ili"),new SourceRevision(++revision),wrap(v,"TOPIC T=CLASS C=n:0..10;"
                        +(valid?"SET CONSTRAINT WHERE n>0:INTERLIS.objectCount(ALL)>=0;":"SET CONSTRAINT n>0;")+"END C;END T;"));
                var in=request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
                var fresh=CompilerEngines.create().analyze(in,TOKEN);
                var reused=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(valid,reused.diagnostics().isEmpty());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(),reused.semanticGraph()));
                if(!valid)assertEquals(source.revision(),reused.diagnostics().getFirst().primaryRange().orElseThrow().revision());
                previous=reused;
            }
        }
    }
}
