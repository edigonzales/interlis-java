package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class DomainRestrictionContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String wrap(String version,String name,String body){return "INTERLIS "+version+"; CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\" = "+body+" END "+name+".";}
    private static CompilationRequest input(String version,String body){var source=source("memory:/domain.ili",wrap(version,"F",body));return request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());}
    private static CompilationOutcome.Succeeded ok(CompilationRequest input){var result=CompilerEngines.create().compile(input,TOKEN);return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());}
    private static DomainSymbol domain(SemanticGraph graph,String name){return graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals(name)).findFirst().orElseThrow();}
    @Test void mandatoryDomainsRetainTypesAndRequiredValueFlagsInPublicModelAndAttributes() {
        for(var version:List.of("2.3","2.4")) {
            var graph=ok(input(version,"DOMAIN Base=TEXT*20; Required EXTENDS Base=MANDATORY; Derived EXTENDS Required=TEXT*10; TOPIC T=CLASS C=a:Required; b:Derived; END C; END T; PARAMETER p:Required;")).schema().semanticGraph();
            assertFalse(domain(graph,"Base").mandatory());assertTrue(domain(graph,"Required").mandatory());assertTrue(domain(graph,"Derived").mandatory());
            var required=domain(graph,"Required");var reference=assertInstanceOf(DomainReferenceType.class,graph.type(required.typeId()).orElseThrow());
            assertEquals(domain(graph,"Base").id(),reference.targetDomain());
            var effective=new TypeRelations(graph).resolveAliases(new TypeRef(graph.snapshotId(),required.typeId())).known().orElseThrow();
            assertEquals(20,assertInstanceOf(TextType.class,graph.type(effective.effectiveType()).orElseThrow()).maxLength().orElseThrow());
            assertEquals(2,graph.symbols().stream().filter(AttributeSymbol.class::isInstance).count());
            assertTrue(graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(a->Set.of("a","b").contains(a.name().value())).allMatch(AttributeSymbol::mandatory));
            assertTrue(graph.symbols().stream().filter(ParameterSymbol.class::isInstance).map(ParameterSymbol.class::cast).filter(p->p.name().value().equals("p")).allMatch(ParameterSymbol::mandatory));
        }
    }
    @Test void mandatoryWithoutABaseIsASourceErrorAndDoesNotBecomeAnImplementationGap() {
        for(var version:List.of("2.3","2.4")) {
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,"DOMAIN D=MANDATORY;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());assertTrue(result.analysis().diagnostics().toString().contains("INHERITED_TYPE_CONTEXT"));
        }
    }
    @Test void numericExtensionUsesTheSameBoundsAndPrecisionRuleAsPublicQueriesAndAttributeOverrides() {
        for(var version:List.of("2.3","2.4")) {
            var declarations="DOMAIN Base=0.00..10.00; Good EXTENDS Base=1.00..9.00; Fine=1.000..9.000; Wide=-1.00..11.00;";
            var graph=ok(input(version,declarations)).schema().semanticGraph();var query=new TypeRelations(graph);var base=new TypeRef(graph.snapshotId(),domain(graph,"Base").typeId());
            for(var entry:Map.of("Good",RelationDecision.ALLOWED,"Fine",version.equals("2.3")?RelationDecision.ALLOWED:RelationDecision.DISALLOWED,"Wide",RelationDecision.DISALLOWED).entrySet()) {
                var derived=new TypeRef(graph.snapshotId(),domain(graph,entry.getKey()).typeId());
                assertEquals(entry.getValue(),query.canExtend(derived,base,ExtensionContext.DOMAIN_EXTENSION,version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24).known().orElseThrow().decision());
            }
            for(var range:(version.equals("2.3")?List.of("-1.00..9.00","1.00..11.00","NUMERIC"):List.of("1.000..9.000","-1.00..9.00","1.00..11.00","NUMERIC")))for(var attribute:List.of(false,true)) {
                var body=attribute?"TOPIC T=CLASS C=a:0.00..10.00; END C; CLASS D EXTENDS C=a(EXTENDED):"+range+"; END D; END T;":"DOMAIN Base=0.00..10.00; D EXTENDS Base="+range+";";
                var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body),TOKEN));
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
                assertTrue(result.analysis().diagnostics().toString().contains(attribute?"ATTRIBUTE_OVERRIDE_TYPE":"NUMERIC_EXTENSION"));
            }
        }
    }
    @Test void official23PrecisionExamplesRoundIntoTheBaseBut24RequiresUnchangedPrecision() {
        for(var version:List.of("2.3","2.4"))for(var valid:List.of(false,true)) {
            var maximum=valid?"7.9949":"7.9999";
            var body="DOMAIN Normal=0.00..7.99; Genau EXTENDS Normal=0.0000.."+maximum+";";
            var result=CompilerEngines.create().compile(input(version,body),TOKEN);
            if(version.equals("2.3")&&valid)assertInstanceOf(CompilationOutcome.Succeeded.class,result);
            else assertInstanceOf(CompilationOutcome.Rejected.class,result);
        }
        var graph=ok(input("2.4","DOMAIN Normal=0.00..7.99; Genau=0.0000..7.9949;")).schema().semanticGraph();
        var query=new TypeRelations(graph);var a=new TypeRef(graph.snapshotId(),domain(graph,"Genau").typeId());var b=new TypeRef(graph.snapshotId(),domain(graph,"Normal").typeId());
        assertEquals(RelationDecision.ALLOWED,query.canExtend(a,b,ExtensionContext.DOMAIN_EXTENSION,LanguageVersion.ILI23).known().orElseThrow().decision());
        assertEquals(RelationDecision.DISALLOWED,query.canExtend(a,b,ExtensionContext.DOMAIN_EXTENSION,LanguageVersion.ILI24).known().orElseThrow().decision());
    }
    @Test void invalidNumericBoundsAndPrecisionRemainOriginalSyntaxAndProduceSourceDiagnostics() {
        for(var version:List.of("2.3","2.4"))for(var range:List.of("10..0","0.0..10.00"))for(var coordinate:List.of(false,true)) {
            var input=input(version,"DOMAIN D="+(coordinate?"COORD ":"")+range+";");
            var parsed=parse(input.sources().sources().values().iterator().next());
            assertTrue(parsed.diagnostics().isEmpty(),parsed.diagnostics()::toString);
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input,TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
            assertTrue(result.analysis().diagnostics().toString().contains(range.startsWith("10")?"NUMERIC_BOUNDS":"NUMERIC_PRECISION"));
            var error=result.analysis().semanticGraph().types().stream().filter(ErrorType.class::isInstance).map(ErrorType.class::cast).findFirst().orElseThrow();
            assertFalse(error.diagnosticIds().isEmpty());
        }
    }
    @Test void queriesKeepInvalidAndPendingTypeStatesInsteadOfTreatingThemAsKnownTypes() {
        var source=input("2.4","DOMAIN D=10..0;");
        var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(source,TOKEN));
        var graph=result.analysis().semanticGraph();var ref=new TypeRef(graph.snapshotId(),domain(graph,"D").typeId());var queries=new TypeRelations(graph);
        assertInstanceOf(QueryResult.Invalid.class,queries.resolveAliases(ref));
        assertInstanceOf(QueryResult.Invalid.class,queries.canAssign(ref,ref,AssignmentContext.NORMAL));
        assertInstanceOf(QueryResult.Invalid.class,queries.canExtend(ref,ref,ExtensionContext.DOMAIN_EXTENSION,LanguageVersion.ILI24));
        assertInstanceOf(QueryResult.Invalid.class,queries.areComparable(ref,ref,ComparisonOperator.EQUALITY));
        for(var reason:PendingReason.values()) {
            var id=new TypeId("pending");var pending=new PendingType(id,new Origin.PredefinedOrigin(LanguageVersion.ILI24,"test"),reason);
            var partial=new SemanticGraph(new SnapshotId("partial"),List.of(),List.of(pending),List.of(),List.of(),Map.of(),Map.of(),Map.of(),List.of(),List.of(),Set.of(LanguageVersion.ILI24),Map.of());
            var query=new TypeRelations(partial);var p=new TypeRef(partial.snapshotId(),id);
            var expected=switch(reason){case NOT_ANALYZED->NotAnalyzedReason.NOT_REQUESTED;case CANCELLED->NotAnalyzedReason.CANCELLED;case BUDGET_EXCEEDED->NotAnalyzedReason.BUDGET_EXCEEDED;case MISSING_IMPLEMENTATION->NotAnalyzedReason.MISSING_IMPLEMENTATION;};
            assertEquals(expected,assertInstanceOf(QueryResult.NotAnalyzed.class,query.resolveAliases(p)).reason());
            assertEquals(expected,assertInstanceOf(QueryResult.NotAnalyzed.class,query.canExtend(p,p,ExtensionContext.DOMAIN_EXTENSION,LanguageVersion.ILI24)).reason());
            assertEquals(expected,assertInstanceOf(QueryResult.NotAnalyzed.class,query.areComparable(p,p,ComparisonOperator.EQUALITY)).reason());
        }
    }
    @Test void classDomainRestrictionsAndConstraintsAreStructuredAndBound() {
        var graph=ok(input("2.4","TOPIC T=CLASS C=END C; CLASS D EXTENDS C=END D; END T; DOMAIN Kind=CLASS RESTRICTION(F.T.C) CONSTRAINTS Allowed: INTERLIS.isSubClass(THIS, >F.T.C);")).schema().semanticGraph();
        var kind=domain(graph,"Kind");var type=assertInstanceOf(ClassValueType.class,graph.type(kind.typeId()).orElseThrow());
        assertEquals("C",graph.symbol(type.restrictions().getFirst()).orElseThrow().name().value());
        assertTrue(graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast).anyMatch(c->c.owner().equals(Optional.of(kind.id()))));
    }
    @Test void longMandatoryAliasChainsResolveAndInvalidEditsAgreeFreshAndReused() {
        StringBuilder domains=new StringBuilder("DOMAIN D0=TEXT*20;");
        for(int i=1;i<=70;i++)domains.append(" D").append(i).append(" EXTENDS D").append(i-1).append("=MANDATORY;");
        var graph=ok(input("2.4",domains.toString())).schema().semanticGraph();
        var resolved=new TypeRelations(graph).resolveAliases(new TypeRef(graph.snapshotId(),domain(graph,"D70").typeId())).known().orElseThrow();
        assertEquals(70,resolved.aliasPath().size());
        var published=new guru.interlis.model.query.DefaultSemanticQueries(graph).effectiveType(new SymbolRef(graph.snapshotId(),domain(graph,"D70").id())).known().orElseThrow();
        assertEquals(resolved.effectiveType(),published.effectiveType());assertEquals(resolved.aliasPath(),published.aliasPath());
        var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
        for(var bounds:List.of("0..10","10..0","0..10")) {
            var source=new SourceText(new SourceId("memory:/edit.ili"),new SourceRevision(++revision),wrap("2.4","F","DOMAIN D="+bounds+";"));
            var input=request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            var fresh=engine.analyze(input,TOKEN);var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
            assertEquals(fresh.diagnostics(),reused.diagnostics());
            assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().types(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().types(),reused.semanticGraph()));
            assertEquals(bounds.startsWith("10"),reused.diagnostics().stream().anyMatch(d->d.arguments().toString().contains("NUMERIC_BOUNDS")));previous=reused;
        }
    }
    @Test void importedMandatoryDomainsBindBeforeUseAndEditsAgreeWithFreshAnalysis() {
        for(var version:List.of("2.3","2.4"))for(var order:List.of(false,true)) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(var mandatory:List.of(true,false,true)) {
                var a=new SourceText(new SourceId(order?"memory:/a-base.ili":"memory:/z-base.ili"),new SourceRevision(++revision),wrap(version,"Base","DOMAIN D="+(mandatory?"MANDATORY ":"")+"TEXT*20;"));
                var b=source(order?"memory:/z-use.ili":"memory:/a-use.ili",wrap(version,"F","IMPORTS Base; DOMAIN R EXTENDS Base.D=MANDATORY; TOPIC T=CLASS C=a:Base.D; b:R; END C; END T;"));
                var input=request(List.of("F"),Map.of(a.id(),a,b.id(),b),List.of(binding(parse(b),"Base",a,parse(a))),CompilerOptions.defaults());
                var fresh=engine.analyze(input,TOKEN);var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertTrue(fresh.diagnostics().isEmpty(),fresh.diagnostics()::toString);assertEquals(fresh.diagnostics(),reused.diagnostics());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));
                var attrs=reused.semanticGraph().symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).toList();
                assertEquals(mandatory,attrs.stream().filter(x->x.name().value().equals("a")).findFirst().orElseThrow().mandatory());
                assertTrue(attrs.stream().filter(x->x.name().value().equals("b")).findFirst().orElseThrow().mandatory());previous=reused;
            }
        }
    }
}
