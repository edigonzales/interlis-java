package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class ConvertUnitFunctionContractTest {
    private static final String UNITS="UNIT L(ABSTRACT); m EXTENDS L; km=1000[m]; Other EXTENDS L; s; ";
    private static CompilationRequest input(String v,String body) {
        var s=source("memory:/convert.ili","INTERLIS "+v+"; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "+UNITS+body+" END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static SemanticGraph ok(String v,String body) {
        var r=CompilerEngines.create().compile(input(v,body),CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString()).schema().semanticGraph();
    }
    private static void bad(String v,String body) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,body),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());assertTrue(r.analysis().diagnostics().toString().contains("CONVERT_UNIT"),r.analysis().diagnostics().toString());
    }
    @Test void conversionsUseTheDeclaredDestinationAndRetainTheSourceUnit() {
        for(String v:List.of("2.3","2.4"))for(String from:List.of("value","1[m]","PI[m]")) {
            var g=ok(v,"TOPIC T=CLASS C=value:0..10000[m];result(TRANSIENT):0..10[km]:=INTERLIS.convertUnit("+from+");END C;END T;");
            var attribute=g.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(a->a.name().value().equals("result")).findFirst().orElseThrow();
            var call=g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
            assertEquals(attribute.declaredType(),call.typeId());
            var sourceType=assertInstanceOf(NumericType.class,g.type(g.expression(call.children().getFirst()).orElseThrow().typeId()).orElseThrow());
            assertEquals("m",g.symbol(sourceType.range().unit().orElseThrow()).orElseThrow().name().value());
            assertEquals("km",g.symbol(((NumericType)g.type(call.typeId()).orElseThrow()).range().unit().orElseThrow()).orElseThrow().name().value());
        }
    }
    @Test void wrongDimensionsUnrelatedConcreteUnitsAndMissingSourceUnitsAreRejected() {
        for(String v:List.of("2.3","2.4"))for(String target:List.of("s","Other"))
            bad(v,"TOPIC T=CLASS C=value:0..100[m];result(TRANSIENT):0..100["+target+"]:=INTERLIS.convertUnit(value);END C;END T;");
        for(String v:List.of("2.3","2.4")) {
            bad(v,"TOPIC T=CLASS C=result(TRANSIENT):0..100[m]:=INTERLIS.convertUnit(1);END C;END T;");
            bad(v,"TOPIC T=CLASS C=result(TRANSIENT):0..100:=INTERLIS.convertUnit(1[m]);END C;END T;");
        }
    }
    @Test void comparisonsAndDeclaredFunctionParametersProvideDestinationUnits() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"FUNCTION check(value:0..10[km]):BOOLEAN;TOPIC T=CLASS C=value:0..10000[m];other:0..10[km];MANDATORY CONSTRAINT INTERLIS.convertUnit(value)>other AND F.check(INTERLIS.convertUnit(value));END C;END T;");
            bad(v,"TOPIC T=CLASS C=value:0..100[m];other:0..100[s];MANDATORY CONSTRAINT INTERLIS.convertUnit(value)>other;END C;END T;");
        }
    }
    @Test void unitlessIdentityAndUnrestrictedCallsRetainExplicitSourceUnits() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"TOPIC T=CLASS C=value:0..10;result(TRANSIENT):0..100:=INTERLIS.convertUnit(value);END C;END T;");
            var g=ok(v,"TOPIC T=CLASS C=value:0..10[m];MANDATORY CONSTRAINT INTERLIS.convertUnit(value)>0;END C;END T;");
            var call=g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
            assertEquals("m",g.symbol(((NumericType)g.type(call.typeId()).orElseThrow()).range().unit().orElseThrow()).orElseThrow().name().value());
        }
    }
    @Test void convertedBoundsApplyToAssignmentsAndArgumentsButNotComparisons() {
        var definitions = "UNIT Centimeter=0.01[m]; Negative=-2[m]; Twice=2[Negative]; Turn=PI[INTERLIS.rad]; "
                + "Fahrenheit=FUNCTION // (Fahrenheit + 459.67) / 1.8 // [INTERLIS.K]; ";
        for (String version : List.of("2.3", "2.4")) {
            var graph = ok(version, definitions + """
                    TOPIC T=CLASS C=
                      angle(TRANSIENT):0..10 [INTERLIS.rad] := INTERLIS.convertUnit(1 [Turn]);
                      heat(TRANSIENT):0..1000 [INTERLIS.K] := INTERLIS.convertUnit(32 [Fahrenheit]);
                      x:0..2 [Twice]; meters(TRANSIENT):-8..0 [m] := INTERLIS.convertUnit(x);
                      limit:0..1 [m];
                      MANDATORY CONSTRAINT INTERLIS.convertUnit(2 [Twice]) < limit;
                    END C; END T;
                    """);
            var heat = graph.symbols().stream().filter(AttributeSymbol.class::isInstance)
                    .map(AttributeSymbol.class::cast).filter(a -> a.name().value().equals("heat"))
                    .findFirst().orElseThrow();
            assertEquals(heat.declaredType(), graph.expression(heat.derivations().getFirst()).orElseThrow().typeId());
            bad(version, definitions + "TOPIC T=CLASS C=value(TRANSIENT):0..10 [Centimeter] := "
                    + "INTERLIS.convertUnit(2 [m]); END C; END T;");
            bad(version, definitions + "FUNCTION fits(value:0..10 [Centimeter]):BOOLEAN;"
                    + " TOPIC T=CLASS C=MANDATORY CONSTRAINT F.fits(INTERLIS.convertUnit(2 [m])); END C; END T;");
        }
    }

    @Test void changedDestinationsProduceTheSameDiagnosticsWithFreshAndReusedAnalysis() {
        for(String v:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
            for(String unit:List.of("km","s","km")) {
                var input=input(v,"TOPIC T=CLASS C=value:0..10000[m];result(TRANSIENT):0..10["+unit+"]:=INTERLIS.convertUnit(value);END C;END T;");
                var fresh=engine.analyze(input,CancellationToken.none());var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics().toString(),reused.diagnostics().toString());assertEquals(unit.equals("s"),fresh.diagnostics().toString().contains("CONVERT_UNIT"));previous=reused;
            }
        }
    }
}
