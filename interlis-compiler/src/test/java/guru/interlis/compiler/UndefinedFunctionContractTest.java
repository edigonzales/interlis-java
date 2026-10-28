package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class UndefinedFunctionContractTest {
    private static CompilationRequest input(String v,String body) {
        var s=source("memory:/undefined.ili","INTERLIS "+v+"; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static SemanticGraph ok(String v,String body) {
        var r=CompilerEngines.create().compile(input(v,body),CancellationToken.none());return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString()).schema().semanticGraph();
    }
    private static void bad(String v,String body,String rule) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,body),CancellationToken.none()));assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());assertTrue(r.analysis().diagnostics().toString().contains(rule),r.analysis().diagnostics().toString());
    }
    @Test void undefinedAndEmptyTextHaveSharedTypeAssignmentRulesAndVersionedOrigins() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(v,"TOPIC T=CLASS C=n:0..10;text:TEXT;flag:BOOLEAN;date:INTERLIS.XMLDate;e:(a,b);obj:CLASS;attribute:ATTRIBUTE;MANDATORY CONSTRAINT n==UNDEFINED AND text==\"\";END C;END T;");
            var query=new TypeRelations(g);var undefined=g.expressions().stream().filter(UndefinedExpression.class::isInstance).findFirst().orElseThrow();
            var empty=g.expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast).filter(e->e.textValue().filter(String::isEmpty).isPresent()).findFirst().orElseThrow();
            assertEquals(v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,assertInstanceOf(Origin.PredefinedOrigin.class,g.type(undefined.typeId()).orElseThrow().origin()).language());
            for(var symbol:g.symbols())if(symbol instanceof AttributeSymbol attribute)for(TypeId value:List.of(undefined.typeId(),empty.typeId())) {
                assertEquals(RelationDecision.DISALLOWED,query.canExtend(new TypeRef(g.snapshotId(),value),new TypeRef(g.snapshotId(),attribute.declaredType()),ExtensionContext.DOMAIN_EXTENSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision());
                assertEquals(RelationDecision.ALLOWED,query.canAssign(new TypeRef(g.snapshotId(),value),new TypeRef(g.snapshotId(),attribute.declaredType()),AssignmentContext.NORMAL).known().orElseThrow().decision());
            }
        }
    }
    @Test void ordinaryBuiltinAndUserArgumentsAcceptUndefinedWithoutInventingAValue() {
        for(String v:List.of("2.3","2.4")) {
            var calls=List.of("INTERLIS.len(UNDEFINED)","INTERLIS.trim(UNDEFINED)","INTERLIS.objectCount(UNDEFINED)","INTERLIS.elementCount(UNDEFINED)","INTERLIS.myClass(UNDEFINED)","INTERLIS.isOfClass(UNDEFINED,UNDEFINED)","INTERLIS.isEnumSubVal(UNDEFINED,UNDEFINED)","INTERLIS.inEnumRange(UNDEFINED,#a,#b)","F.check(UNDEFINED)");
            String constraints=String.join(" AND ",calls.stream().map(c->"DEFINED("+c+")").toList());
            var g=ok(v,"FUNCTION check(value:0..10):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT "+constraints+";END C;END T;");
            assertEquals(calls.size(),g.expressions().stream().filter(FunctionCallExpression.class::isInstance).count());
            bad(v,"FUNCTION check(value:0..10):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT F.check(#true);END C;END T;","ILI-SEM-0005");
        }
    }
    @Test void convertUnitUndefinedUsesTheDestinationWithoutAFalseDimensionFailure() {
        for(String v:List.of("2.3","2.4"))ok(v,"UNIT m;TOPIC T=CLASS C=value(TRANSIENT):0..10[m]:=INTERLIS.convertUnit(UNDEFINED);END C;END T;");
    }
    @Test void mandatoryFunctionSignaturesAreRetainedAndRejectExplicitUndefined() {
        for(String v:List.of("2.3","2.4")) {
            String declarations="DOMAIN Required=MANDATORY BOOLEAN; More EXTENDS Required=BOOLEAN;FUNCTION direct(value:MANDATORY BOOLEAN):MANDATORY BOOLEAN;FUNCTION inherited(value:More):More;";
            var g=ok(v,declarations+"TOPIC T=CLASS C=MANDATORY CONSTRAINT F.direct(#true) AND F.inherited(#true);END C;END T;");
            for(var symbol:g.symbols())if(symbol instanceof FunctionSymbol function) {assertTrue(function.parameters().getFirst().mandatory());assertTrue(function.returnMandatory());}
            for(String value:List.of("UNDEFINED","\"\""))for(String name:List.of("direct","inherited"))
                bad(v,declarations+"TOPIC T=CLASS C=MANDATORY CONSTRAINT F."+name+"("+value+");END C;END T;","FUNCTION_ARGUMENT_MANDATORY");
        }
    }
    @Test void emptyTextCountsAsUndefinedButNonemptyWrongKindsRemainErrors() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"FUNCTION check(value:BOOLEAN):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT F.check(\"\");END C;END T;");
            bad(v,"FUNCTION check(value:BOOLEAN):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT F.check(\"x\");END C;END T;","ILI-SEM-0005");
            bad(v,"TOPIC T=CLASS C=MANDATORY CONSTRAINT INTERLIS.len(UNDEFINED,UNDEFINED)>0;END C;END T;","ILI-SEM-0005");
        }
    }
    @Test void undefinedBooleanContextsAreLegalButOrderingAnUndefinedValueIsNot() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"TOPIC T=CLASS C=MANDATORY CONSTRAINT UNDEFINED AND NOT(UNDEFINED);END C;END T;");
            bad(v,"TOPIC T=CLASS C=n:0..10;MANDATORY CONSTRAINT n<UNDEFINED;END C;END T;","ILI-SEM-0005");
        }
    }
    @Test void mandatoryAttributesDoNotAcquireAnExplicitlyUndefinedDerivedValue() {
        for(String v:List.of("2.3","2.4"))for(String value:List.of("UNDEFINED","\"\"")) {
            bad(v,"TOPIC T=CLASS C=value(TRANSIENT):MANDATORY BOOLEAN:="+value+";END C;END T;","MANDATORY_VALUE");
            bad(v,"DOMAIN Required=MANDATORY BOOLEAN;TOPIC T=CLASS C=value(TRANSIENT):Required:="+value+";END C;END T;","MANDATORY_VALUE");
            ok(v,"TOPIC T=CLASS C=value(TRANSIENT):BOOLEAN:="+value+";END C;END T;");
        }
    }
    @Test void publicQueriesRetainRequiredDomainsThroughAliasesAndExtensions() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(v,"DOMAIN Required=MANDATORY BOOLEAN; More EXTENDS Required=BOOLEAN; Alias EXTENDS Required=MANDATORY;TOPIC T=CLASS C=value:BOOLEAN;MANDATORY CONSTRAINT value==UNDEFINED;END C;END T;");
            var value=g.expressions().stream().filter(UndefinedExpression.class::isInstance).findFirst().orElseThrow();var query=new TypeRelations(g);
            for(var symbol:g.symbols())if(symbol instanceof DomainSymbol domain) {
                assertTrue(domain.mandatory(),domain.name().value());
                assertEquals(RelationDecision.DISALLOWED,query.canAssign(new TypeRef(g.snapshotId(),value.typeId()),new TypeRef(g.snapshotId(),domain.typeId()),AssignmentContext.NORMAL).known().orElseThrow().decision(),domain.name().value());
            }
        }
    }
    @Test void importedMandatoryDomainsAndEditsAreIndependentOfFileOrderAndReuse() {
        for(String v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili","INTERLIS "+v+";MODEL Base AT \"https://example.org\" VERSION \"1\"=DOMAIN Required=MANDATORY BOOLEAN;END Base.");
            var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String value:List.of("#true","UNDEFINED","#true")) {
                var use=new SourceText(id,new SourceRevision(++revision),"INTERLIS "+v+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS Base;FUNCTION check(value:Base.Required):Base.Required;TOPIC T=CLASS C=MANDATORY CONSTRAINT F.check("+value+");END C;END T;END F.");
                var input=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(input,CancellationToken.none());var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(value.equals("UNDEFINED"),fresh.diagnostics().toString().contains("FUNCTION_ARGUMENT_MANDATORY"));
                var function=reused.semanticGraph().symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).findFirst().orElseThrow();
                assertTrue(function.parameters().getFirst().mandatory());assertTrue(function.returnMandatory());previous=reused;
            }
        }
    }
    @Test void translatedSignaturesCompareMandatoryArgumentsAndResults() {
        for(String v:List.of("2.3","2.4"))for(String changed:List.of("argument","result","none")) {
            var s=source("memory:/translated-required.ili","INTERLIS "+v+";CONTRACTED MODEL Original AT \"https://example.org\" VERSION \"1\"=FUNCTION f(value:MANDATORY BOOLEAN):MANDATORY BOOLEAN;END Original. CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Original[\"1\"]=FUNCTION g(value:"+(changed.equals("argument")?"":"MANDATORY ")+"BOOLEAN):"+(changed.equals("result")?"":"MANDATORY ")+"BOOLEAN;END F.");
            var outcome=CompilerEngines.create().compile(request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults()),CancellationToken.none());
            if(changed.equals("none"))assertInstanceOf(CompilationOutcome.Succeeded.class,outcome);
            else {var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,outcome);assertTrue(rejected.analysis().diagnostics().toString().contains("TRANSLATION_FUNCTION"));}
        }
    }
}
