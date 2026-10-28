package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class FunctionContextContractTest {
    private static CompilationRequest input(String v,String body) {
        var s=source("memory:/functions.ili","INTERLIS "+v+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    @Test void attributeRelationsKeepValueErrorsSeparateFromARequiredCallContext() {
        for(String v:List.of("2.3","2.4")) {
            var result=CompilerEngines.create().compile(input(v,"DOMAIN Point=COORD 0.000..100.000,0.000..100.000; TOPIC T=CLASS C=p:SURFACE WITH(STRAIGHTS) VERTEX Point WITHOUT OVERLAPS>0.010; text:TEXT; SET CONSTRAINT INTERLIS.areAreas(ALL,UNDEFINED,>>p); END C;END T;"),CancellationToken.none());
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result).schema().semanticGraph();
            var function=graph.symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).findFirst().orElseThrow();
            var constant=graph.expressions().stream().filter(SymbolValueExpression.class::isInstance).map(SymbolValueExpression.class::cast).findFirst().orElseThrow();
            var query=new TypeRelations(graph);var formal=new TypeRef(graph.snapshotId(),function.parameters().get(2).typeId());
            assertEquals(NotAnalyzedReason.PENDING,assertInstanceOf(QueryResult.NotAnalyzed.class,query.canAssign(new TypeRef(graph.snapshotId(),constant.typeId()),formal,AssignmentContext.FUNCTION_ARGUMENT)).reason());
            assertEquals(RelationDecision.ALLOWED,query.canAssign(formal,formal,AssignmentContext.FUNCTION_ARGUMENT).known().orElseThrow().decision());
        }
    }
    @Test void importsEscapedPathQueriesAndEditReuseKeepActualIdentities() {
        for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili","INTERLIS 2.4;MODEL Base AT \"https://example.org\" VERSION \"1\"=DOMAIN P=COORD 0.000..10.000,0.000..10.000;STRUCTURE S=surface:SURFACE WITH(STRAIGHTS) VERTEX P WITHOUT OVERLAPS>0.010;END S;END Base.");
            var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String member:List.of("surface","missing","surface")) {
                var use=new SourceText(id,new SourceRevision(++revision),"INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS Base;TOPIC T=CLASS C=geometry:BAG OF Base.S;SET CONSTRAINT INTERLIS.areAreas2(ALL,\"geom\\u0065try\",\""+member+"\");END C;END T;END F.");
                var input=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(input,CancellationToken.none());var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(member.equals("surface"),fresh.diagnostics().isEmpty(),fresh.diagnostics().toString());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(),reused.semanticGraph()));
                if(member.equals("surface")) {
                    var queries=LanguageQueries.create();int offset=use.text().indexOf("geom\\u0065try");
                    var at=queries.symbolAt(reused,use.range(offset+5,offset+5),CancellationToken.none()).known().orElseThrow();
                    var attribute=reused.semanticGraph().symbol(at.symbol()).orElseThrow();assertEquals("geometry",attribute.name().value());
                    var call=reused.semanticGraph().expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
                    assertTrue(call.argumentPaths().values().stream().allMatch(p->p.binding().isPresent()));
                }
                previous=reused;
            }
        }
    }
    @Test void translatedCallsCompareBoundFunctionIdentitiesIncludingSameShapedWrongTargets() {
        for(String v:List.of("2.3","2.4"))for(boolean correct:List.of(true,false)) {
            String text="INTERLIS "+v+";CONTRACTED MODEL Base AT \"https://example.org\" VERSION \"1\"=FUNCTION first(a:BOOLEAN):BOOLEAN;FUNCTION second(a:BOOLEAN):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT first(#true);END C;END T;END Base. CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Base[\"1\"]=FUNCTION eins(a:BOOLEAN):BOOLEAN;FUNCTION zwei(a:BOOLEAN):BOOLEAN;TOPIC U=CLASS D=MANDATORY CONSTRAINT "+(correct?"eins":"zwei")+"(#true);END D;END U;END F.";
            var source=source("memory:/translation.ili",text);var input=request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            var outcome=CompilerEngines.create().compile(input,CancellationToken.none());
            if(correct)assertInstanceOf(CompilationOutcome.Succeeded.class,outcome,()->outcome.toString());
            else {var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,outcome);assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());assertTrue(rejected.analysis().diagnostics().toString().contains("TRANSLATION"));}
        }
    }
}
