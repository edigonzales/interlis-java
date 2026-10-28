package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class NumericAbstractionContractTest {
    private static CompilationRequest input(String version,String body) {
        var s=source("memory:/numeric-abstract.ili","INTERLIS "+version+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static SemanticGraph ok(String v,String body) {
        var r=CompilerEngines.create().compile(input(v,body),CancellationToken.none());return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString()).schema().semanticGraph();
    }
    private static CompilationOutcome.Rejected bad(String v,String body,String rule) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,body),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());assertTrue(r.analysis().diagnostics().toString().contains(rule),r.analysis().diagnostics().toString());return r;
    }
    @Test void incompleteNumericAndCoordinateDomainsRequireAnExplicitAbstractDeclaration() {
        for(String v:List.of("2.3","2.4"))for(String type:List.of("NUMERIC","COORD NUMERIC,NUMERIC")) {
            bad(v,"DOMAIN N="+type+";","NUMERIC_ABSTRACT");
            var g=ok(v,"DOMAIN N(ABSTRACT)="+type+";");
            var domain=g.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).findFirst().orElseThrow();
            assertTrue(domain.header().has(Modifier.ABSTRACT));assertFalse(g.type(domain.typeId()).orElseThrow() instanceof ErrorType);
        }
    }
    @Test void anAbstractOwnerDoesNotMakeItsUnboundedNumericAttributeAbstract() {
        for(String v:List.of("2.3","2.4"))for(String type:List.of("NUMERIC","COORD NUMERIC,NUMERIC")) {
            bad(v,"TOPIC T(ABSTRACT)=CLASS C(ABSTRACT)=n:"+type+";END C;END T;","NUMERIC_ABSTRACT");
            ok(v,"TOPIC T(ABSTRACT)=CLASS C(ABSTRACT)=n(ABSTRACT):"+type+";END C;END T;");
            bad(v,"TOPIC T=CLASS C=n(TRANSIENT):NUMERIC:=INTERLIS.len(\"x\");END C;END T;","NUMERIC_ABSTRACT");
        }
    }
    @Test void abstractDomainUsesAndInheritedAttributesAreCheckedAfterEarlyResolution() {
        for(String v:List.of("2.3","2.4")) {
            bad(v,"DOMAIN N(ABSTRACT)=NUMERIC;TOPIC T=CLASS C=n:N;END C;END T;","NUMERIC_ABSTRACT");
            ok(v,"DOMAIN N(ABSTRACT)=NUMERIC;TOPIC T(ABSTRACT)=CLASS C(ABSTRACT)=n(ABSTRACT):N;END C;END T;");
            ok(v,"DOMAIN N(ABSTRACT)=NUMERIC;Small EXTENDS N=0..10;TOPIC T=CLASS C=n:Small;END C;END T;");
            ok(v,"TOPIC T=CLASS Parent(ABSTRACT)=n(ABSTRACT):NUMERIC;END Parent;CLASS Child EXTENDS Parent=n(EXTENDED):0..10;END Child;END T;");
            bad(v,"TOPIC T=CLASS Parent(ABSTRACT)=n(ABSTRACT):NUMERIC;END Parent;CLASS Child EXTENDS Parent=n(EXTENDED):MANDATORY;END Child;END T;","NUMERIC_ABSTRACT");
        }
    }
    @Test void abstractUnitsAreAllowedOnlyWhileTheNumericRangeRemainsAbstract() {
        for(String v:List.of("2.3","2.4")) {
            String units="UNIT L(ABSTRACT);m EXTENDS L;";
            ok(v,units+"DOMAIN N(ABSTRACT)=NUMERIC[L];D EXTENDS N=0..10[m];");
            for(String decl:List.of("DOMAIN N=0..10[L];","DOMAIN N(ABSTRACT)=0..10[L];","DOMAIN P=COORD 0..10[L],0..10[L];"))bad(v,units+decl,"NUMERIC_ABSTRACT_UNIT");
            bad(v,units+"DOMAIN N(ABSTRACT)=NUMERIC[L];D EXTENDS N=0..10;","NUMERIC_ABSTRACT_UNIT");
            bad(v,units+"DOMAIN N(ABSTRACT)=COORD NUMERIC[L],NUMERIC[L];D EXTENDS N=COORD 0..10,0..10;","NUMERIC_ABSTRACT_UNIT");
            bad(v,units+"TOPIC T=CLASS Parent(ABSTRACT)=n(ABSTRACT):NUMERIC[L];END Parent;CLASS Child EXTENDS Parent=n(EXTENDED):0..10;END Child;END T;","NUMERIC_ABSTRACT_UNIT");
        }
    }
    @Test void unknownUnitsAndReferenceTargetsPublishInvalidTypesWithRealDiagnosticIds() {
        for(String v:List.of("2.3","2.4"))for(String type:List.of("0..10[Missing]","0..10<Missing>","0..10{Missing}","COORD 0..10[Missing],0..10")) {
            var r=bad(v,"DOMAIN N="+type+";","ILI-SEM-");var g=r.analysis().semanticGraph();
            var domain=g.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).findFirst().orElseThrow();
            var error=assertInstanceOf(ErrorType.class,g.type(domain.typeId()).orElseThrow());assertFalse(error.diagnosticIds().isEmpty());
            assertTrue(r.analysis().diagnostics().stream().map(guru.interlis.diagnostics.Diagnostic::id).toList().containsAll(error.diagnosticIds()));
            var ref=new TypeRef(g.snapshotId(),domain.typeId());assertInstanceOf(QueryResult.Invalid.class,new TypeRelations(g).canAssign(ref,ref,AssignmentContext.NORMAL));
        }
    }
    @Test void formalNumericArgumentsAndInferredViewResultsAreNotAbstractDeclarations() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"UNIT L(ABSTRACT);FUNCTION check(n:NUMERIC[L]):NUMERIC;TOPIC T=CLASS C=n:0..10;MANDATORY CONSTRAINT F.check(n)>0;END C;VIEW V AGGREGATION OF C ALL;=count:=INTERLIS.elementCount(AGGREGATES);END V;END T;");
        }
    }
    @Test void genericCoordinatesKeepTheirLegalContextAndDeferredState() {
        var g=ok("2.4","DOMAIN G(GENERIC)=COORD NUMERIC,NUMERIC;P=COORD 0..10,0..10;CONTEXT default=G=P;TOPIC T=CLASS C=p:G;END C;END T;");
        var topic=g.symbols().stream().filter(TopicSymbol.class::isInstance).map(TopicSymbol.class::cast).findFirst().orElseThrow();
        assertEquals(TopicSymbol.BindingState.CONCRETE,topic.genericBindings().getFirst().state());
    }
    @Test void importedUnitSpecializationsAndEditsAgreeWithFreshAnalysisInBothSourceOrders() {
        for(String v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili","INTERLIS "+v+";MODEL Original AT \"https://example.org\" VERSION \"1\"=UNIT L(ABSTRACT);m EXTENDS L;DOMAIN N(ABSTRACT)=NUMERIC[L];END Original.");
            var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String unit:List.of("m","L","m")) {
                var use=new SourceText(id,new SourceRevision(++revision),"INTERLIS "+v+";MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS Original;DOMAIN N EXTENDS Original.N=0..10[Original."+unit+"];TOPIC T=CLASS C=n:N;END C;END T;END F.");
                var input=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Original",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(input,CancellationToken.none());var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(unit.equals("m"),fresh.diagnostics().isEmpty(),fresh.diagnostics().toString());previous=reused;
            }
        }
    }
}
