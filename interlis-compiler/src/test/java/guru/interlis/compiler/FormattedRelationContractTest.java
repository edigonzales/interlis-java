package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.*;
import guru.interlis.model.internal.FormattedValues;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class FormattedRelationContractTest {
    private static final String BASE="STRUCTURE S=n:0..99;END S;DOMAIN Base=FORMAT BASED ON S(n/2) \"00\"..\"99\";Small EXTENDS Base=FORMAT Base \"10\"..\"20\";";
    private static CompilationRequest input(String v,String body) {
        var s=source("memory:/formatted-relations.ili","INTERLIS "+v+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static SemanticGraph ok(String v,String body) {
        var r=CompilerEngines.create().compile(input(v,body),CancellationToken.none());return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString()).schema().semanticGraph();
    }
    private static TypeRef domain(SemanticGraph g,String name) {return new TypeRef(g.snapshotId(),g.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals(name)).findFirst().orElseThrow().typeId());}
    private static RelationDecision assign(SemanticGraph g,String a,String b) {return new TypeRelations(g).canAssign(domain(g,a),domain(g,b),AssignmentContext.NORMAL).known().orElseThrow().decision();}
    private static RelationDecision extend(SemanticGraph g,String a,String b,String v) {return new TypeRelations(g).canExtend(domain(g,a),domain(g,b),ExtensionContext.DOMAIN_EXTENSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision();}
    private static void bad(String v,String body,String rule) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,body),CancellationToken.none()));assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());assertTrue(r.analysis().diagnostics().toString().contains(rule),r.analysis().diagnostics().toString());
    }
    @Test void boundDomainsHaveTheSameAssignmentAndExtensionRulesInCompilerAndQueries() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(v,BASE+"FUNCTION check(value:Base):BOOLEAN;TOPIC T=CLASS C=value:Small;MANDATORY CONSTRAINT F.check(value);END C;END T;");
            assertEquals(RelationDecision.ALLOWED,assign(g,"Small","Base"));assertEquals(RelationDecision.DISALLOWED,assign(g,"Base","Small"));
            assertEquals(RelationDecision.ALLOWED,extend(g,"Small","Base",v));assertEquals(RelationDecision.DISALLOWED,extend(g,"Base","Small",v));
            bad(v,BASE+"FUNCTION check(value:Small):BOOLEAN;TOPIC T=CLASS C=value:Base;MANDATORY CONSTRAINT F.check(value);END C;END T;","ILI-SEM-0005");
        }
    }
    @Test void inheritedPrefixesKeepActualOverriddenAttributeTargetsAndMayAppendComponents() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(v,BASE+"STRUCTURE Derived EXTENDS S=n(EXTENDED):10..20;extra:0..9;END Derived;DOMAIN Extended EXTENDS Base=FORMAT BASED ON Derived(INHERITANCE \"-\" extra) \"10-0\"..\"20-9\";FUNCTION check(value:Base):BOOLEAN;TOPIC T=CLASS C=value:Extended;MANDATORY CONSTRAINT F.check(value);END C;END T;");
            assertEquals(RelationDecision.ALLOWED,assign(g,"Extended","Base"));assertEquals(RelationDecision.ALLOWED,extend(g,"Extended","Base",v));
            assertEquals(RelationDecision.DISALLOWED,assign(g,"Base","Extended"));
            var extended=(FormattedType)g.type(domain(g,"Extended").type()).orElseThrow();var original=(FormattedType)g.type(domain(g,"Base").type()).orElseThrow();
            assertNotEquals(original.parts().getFirst().attribute(),extended.parts().getFirst().attribute());
            assertEquals("Derived",g.symbol(g.symbol(extended.parts().getFirst().attribute().orElseThrow()).orElseThrow().owner().orElseThrow()).orElseThrow().name().value());
        }
    }
    @Test void overridesDoNotReceiveAFormattedTypeBypass() {
        for(String v:List.of("2.3","2.4")) {
            String classes="TOPIC T=CLASS Parent=value:Base;END Parent;CLASS Child EXTENDS Parent=value(EXTENDED):%s;END Child;END T;";
            ok(v,BASE+classes.formatted("Small"));
            bad(v,BASE+"STRUCTURE Other=n:0..99;END Other;DOMAIN Wrong=FORMAT BASED ON Other(n/2) \"00\"..\"99\";"+classes.formatted("Wrong"),"ATTRIBUTE_OVERRIDE_TYPE");
            bad(v,BASE+"DOMAIN Different=FORMAT BASED ON S(n/3) \"000\"..\"099\";"+classes.formatted("Different"),"ATTRIBUTE_OVERRIDE_TYPE");
        }
    }
    @Test void inlineAttributeFormatsUseTheSameInheritedPrefixAsDomains() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,BASE+"STRUCTURE Derived EXTENDS S=n(EXTENDED):10..20;extra:0..9;END Derived;TOPIC T=CLASS Parent=value:Base;END Parent;CLASS Child EXTENDS Parent=value(EXTENDED):FORMAT BASED ON Derived(INHERITANCE \"-\" extra) \"10-0\"..\"20-9\";END Child;END T;");
        }
    }
    @Test void absentBoundsUseComponentIntervalsAndNestedBoundDomains() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(v,"STRUCTURE S=a:0..9;b:0..9;END S;DOMAIN Wide=FORMAT BASED ON S(a \"-\" b);Base=FORMAT BASED ON S(a \"-\" b) \"2-3\"..\"8-7\";Small EXTENDS Base=FORMAT Base \"3-1\"..\"7-8\";STRUCTURE Outer=inner:S;n:0..9;END Outer;DOMAIN Full=FORMAT BASED ON Outer(inner/Base \"#\" n);Narrow EXTENDS Full=FORMAT Full \"3-1#1\"..\"7-8#8\";");
            assertEquals(RelationDecision.ALLOWED,assign(g,"Small","Base"));assertEquals(RelationDecision.DISALLOWED,assign(g,"Wide","Base"));
            assertEquals(RelationDecision.ALLOWED,assign(g,"Narrow","Full"));assertEquals(RelationDecision.DISALLOWED,assign(g,"Full","Narrow"));
            var values=new FormattedValues(id->g.declaredType(id).flatMap(t->new TypeRelations(g).resolveAliases(new TypeRef(g.snapshotId(),t)).known()).flatMap(e->g.type(e.effectiveType())).orElse(null),()->{});
            var bounds=values.interval((FormattedType)g.type(domain(g,"Full").type()).orElseThrow()).orElseThrow();
            assertEquals("[2, 3, 0]",bounds.lower().orElseThrow().toString());assertEquals("[8, 7, 9]",bounds.upper().orElseThrow().toString());
        }
    }
    @Test void importedFormatsAndEditsHaveTheSameBoundRelationsWithReuse() {
        for(String v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili","INTERLIS "+v+";MODEL Original AT \"https://example.org\" VERSION \"1\"="+BASE+"END Original.");
            var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String upper:List.of("19","21","19")) {
                var use=new SourceText(id,new SourceRevision(++revision),"INTERLIS "+v+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS Original;DOMAIN Value EXTENDS Original.Small=FORMAT Original.Small \"11\"..\""+upper+"\";FUNCTION check(value:Original.Base):BOOLEAN;TOPIC T=CLASS C=value:Value;MANDATORY CONSTRAINT F.check(value);END C;END T;END F.");
                var input=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Original",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(input,CancellationToken.none());var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(upper.equals("19"),fresh.diagnostics().isEmpty(),fresh.diagnostics().toString());
                var g=reused.semanticGraph();var q=new TypeRelations(g);var target=domain(g,"Small");
                if(upper.equals("19"))assertEquals(RelationDecision.ALLOWED,q.canExtend(domain(g,"Value"),target,ExtensionContext.DOMAIN_EXTENSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision());
                else assertInstanceOf(QueryResult.Invalid.class,q.canAssign(domain(g,"Value"),target,AssignmentContext.NORMAL));
                previous=reused;
            }
        }
    }
    @Test void derivedComponentBoundsRemainEffectiveWithoutExplicitFormatBounds() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(v,BASE+"STRUCTURE Derived EXTENDS S=n(EXTENDED):10..20;extra:0..9;END Derived;DOMAIN Auto EXTENDS Base=FORMAT BASED ON Derived(INHERITANCE \"-\" extra);Same=FORMAT BASED ON S(n/2) \"10\"..\"20\";Other=FORMAT BASED ON S(\"[\" n/2 \"]\") \"[10]\"..\"[20]\";");
            assertEquals(RelationDecision.ALLOWED,extend(g,"Auto","Base",v));
            assertEquals(RelationDecision.ALLOWED,assign(g,"Auto","Base"));
            assertEquals(RelationDecision.DISALLOWED,assign(g,"Base","Auto"));
            assertEquals(RelationDecision.ALLOWED,assign(g,"Same","Other"));
            assertEquals(RelationDecision.DISALLOWED,extend(g,"Other","Same",v));
        }
    }
    @Test void invalidFormatBindingsPublishRealErrorTypesAndInvalidQueries() {
        for(String v:List.of("2.3","2.4")) {
            var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,BASE+"DOMAIN Bad=FORMAT Base \"21\"..\"10\";"),CancellationToken.none()));
            var g=r.analysis().semanticGraph();var bad=domain(g,"Bad");var error=assertInstanceOf(ErrorType.class,g.type(bad.type()).orElseThrow());
            assertFalse(error.diagnosticIds().isEmpty());assertTrue(r.analysis().diagnostics().stream().map(guru.interlis.diagnostics.Diagnostic::id).toList().containsAll(error.diagnosticIds()));
            assertInstanceOf(QueryResult.Invalid.class,new TypeRelations(g).canExtend(bad,domain(g,"Base"),ExtensionContext.DOMAIN_EXTENSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24));
        }
    }
}
