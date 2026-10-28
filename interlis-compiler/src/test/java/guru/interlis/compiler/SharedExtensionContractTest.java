package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class SharedExtensionContractTest {
    private static CompilationRequest input(String version,String body) {
        var s=source("memory:/extensions.ili","INTERLIS "+version+"; MODEL F AT \"https://example.org\" VERSION \"1\" = "+body+" END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static SemanticGraph ok(String version,String body) {
        var r=CompilerEngines.create().compile(input(version,body),CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString()).schema().semanticGraph();
    }
    private static void bad(String version,String body,String rule) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
        assertTrue(r.analysis().diagnostics().toString().contains(rule),r.analysis().diagnostics().toString());
    }
    private static TypeRef domain(SemanticGraph g,String name) {
        var d=g.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(x->x.name().value().equals(name)).findFirst().orElseThrow();
        return new TypeRef(g.snapshotId(),d.typeId());
    }
    @Test void textDomainRestrictionsAndPublicQueriesAgree() {
        for(var v:List.of("2.3","2.4")) {
            var g=ok(v,"DOMAIN B=TEXT*10; D EXTENDS B=TEXT*5; M=MTEXT*10; S EXTENDS M=MTEXT*5;");
            var q=new TypeRelations(g);assertEquals(RelationDecision.ALLOWED,q.canExtend(domain(g,"D"),domain(g,"B"),ExtensionContext.DOMAIN_EXTENSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,q.canExtend(domain(g,"B"),domain(g,"D"),ExtensionContext.DOMAIN_EXTENSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision());
            bad(v,"DOMAIN B=MTEXT*10; D EXTENDS B=TEXT*5;","TYPE_EXTENSION");
            for(var type:List.of("TEXT*20","TEXT","MTEXT*5","0..10"))bad(v,"DOMAIN B=TEXT*10; D EXTENDS B="+type+";","TYPE_EXTENSION");
        }
    }
    @Test void coordinatesCanAddDimensionsAndInheritEstablishedPropertiesBeforeExpressions() {
        for(var v:List.of("2.3","2.4")) {
            var g=ok(v,"DOMAIN B=COORD 0.00..10.00[INTERLIS.m],0.00..10.00[INTERLIS.m],ROTATION 1->2; D EXTENDS B=COORD 1.00..9.00,1.00..9.00,0.00..20.00[INTERLIS.m]; TOPIC T=CLASS C=a:D; END C; END T;");
            var q=new TypeRelations(g);var d=(CoordinateType)g.type(domain(g,"D").type()).orElseThrow();var b=(CoordinateType)g.type(domain(g,"B").type()).orElseThrow();
            assertEquals(3,d.dimensions());assertEquals(b.rotation(),d.rotation());assertEquals(b.axes().getFirst().unit(),d.axes().getFirst().unit());
            assertEquals(RelationDecision.ALLOWED,q.canExtend(domain(g,"D"),domain(g,"B"),ExtensionContext.DOMAIN_EXTENSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,q.canAssign(domain(g,"D"),domain(g,"B"),AssignmentContext.NORMAL).known().orElseThrow().decision());
            for(var type:List.of("COORD 1.00..9.00","COORD 1.00..11.00,1.00..9.00","COORD 1.00..9.00[INTERLIS.s],1.00..9.00","COORD 1.00..9.00,1.00..9.00,ROTATION 2->1"))
                bad(v,"DOMAIN B=COORD 0.00..10.00[INTERLIS.m],0.00..10.00[INTERLIS.m],ROTATION 1->2; D EXTENDS B="+type+";","TYPE_EXTENSION");
        }
    }
    @Test void numericCollectionElementsUseExtensionInsteadOfValueAssignment() {
        var g=ok("2.4","TOPIC T=CLASS C=a:BAG OF 0.00..10.00[INTERLIS.m]; END C; CLASS D EXTENDS C=a(EXTENDED):LIST{1..2} OF 1.00..9.00; END D; END T;");
        var attrs=g.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(x->x.name().value().equals("a")).toList();
        var a=attrs.stream().filter(x->g.symbol(x.owner().orElseThrow()).orElseThrow().name().value().equals("C")).findFirst().orElseThrow();var b=attrs.stream().filter(x->!x.id().equals(a.id())).findFirst().orElseThrow();
        var q=new TypeRelations(g);assertEquals(RelationDecision.ALLOWED,q.canExtend(new TypeRef(g.snapshotId(),b.declaredType()),new TypeRef(g.snapshotId(),a.declaredType()),ExtensionContext.DOMAIN_EXTENSION,LanguageVersion.ILI24).known().orElseThrow().decision());
        for(var element:List.of("1.00..9.00[INTERLIS.s]","1.000..9.000[INTERLIS.m]","1.00..11.00[INTERLIS.m]"))
            bad("2.4","TOPIC T=CLASS C=a:BAG OF 0.00..10.00[INTERLIS.m]; END C; CLASS D EXTENDS C=a(EXTENDED):LIST{1..2} OF "+element+"; END D; END T;","ATTRIBUTE_OVERRIDE_TYPE");
    }
    @Test void numericDirectionIsInheritedAndCannotBeChanged() {
        for(var v:List.of("2.3","2.4")) {
            var g=ok(v,"DOMAIN B=0.00..359.99 CIRCULAR CLOCKWISE; D EXTENDS B=1.00..350.00;");
            var b=(NumericType)g.type(domain(g,"B").type()).orElseThrow();var d=(NumericType)g.type(domain(g,"D").type()).orElseThrow();
            assertEquals(b.range().direction(),d.range().direction());assertTrue(d.range().circular());
            bad(v,"DOMAIN B=0.00..359.99 CIRCULAR CLOCKWISE; D EXTENDS B=1.00..350.00 COUNTERCLOCKWISE;","NUMERIC_EXTENSION");
        }
    }
    @Test void numericReferenceIdentityIsInheritedAndCannotBeReplacedByAnEqualRange() {
        for(var v:List.of("2.3","2.4")) {
            var prefix="DOMAIN R=0..100[INTERLIS.m]; Other=0..100[INTERLIS.m]; B=0..10[INTERLIS.m]<R>; D EXTENDS B=";
            var g=ok(v,prefix+"1..9;");
            assertEquals(((NumericType)g.type(domain(g,"B").type()).orElseThrow()).range().referenceSystem(),
                    ((NumericType)g.type(domain(g,"D").type()).orElseThrow()).range().referenceSystem());
            bad(v,prefix+"1..9<Other>;","NUMERIC_EXTENSION");
        }
    }
    @Test void importedAliasesAndEditSequencesAreIndependentOfSourceOrder() {
        for(var v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili","INTERLIS "+v+"; MODEL Base AT \"https://example.org\" VERSION \"1\" = DOMAIN B=COORD 0.00..10.00[INTERLIS.m],0.00..10.00[INTERLIS.m],ROTATION 1->2; Alias EXTENDS B=MANDATORY; END Base.");
            var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(var unit:List.of("","[INTERLIS.s]","")) {
                var use=new SourceText(id,new SourceRevision(++revision),"INTERLIS "+v+"; MODEL F AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; DOMAIN D EXTENDS Base.Alias=COORD 1.00..9.00"+unit+",1.00..9.00; END F.");
                var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(request,CancellationToken.none());
                var reused=previous==null?fresh:engine.analyze(request,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(List.copyOf(fresh.semanticGraph().types()),List.copyOf(reused.semanticGraph().types()));
                assertEquals(unit.isEmpty(),fresh.diagnostics().isEmpty(),fresh.diagnostics().toString());previous=reused;
            }
        }
    }
}
