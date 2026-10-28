package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** 2.4 named constraints are unique in their declaring viewable, including external blocks. */
class ConstraintNameContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final String RULE="MANDATORY CONSTRAINT Rule:#true;";
    private static String wrap(String version,String name,String body) {
        return "INTERLIS "+version+";\r\n!! 🦊\r\nMODEL "+name+" AT \"https://example.org\" VERSION \"1\"="+body+"END "+name+".";
    }
    private static CompilationRequest input(String body) {
        var text=source("memory:/constraint-names.ili",wrap("2.4","F",body));
        return request(List.of("F"),Map.of(text.id(),text),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String body) {
        var result=CompilerEngines.create().compile(input(body),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
    }
    private static void duplicate(String body) {
        var request=input(body);
        var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request,TOKEN));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
        assertEquals(1,rejected.analysis().diagnostics().size(),rejected.analysis().diagnostics().toString());
        var d=rejected.analysis().diagnostics().getFirst();
        assertEquals("ILI-SEM-0010",d.code().value());assertTrue(d.arguments().toString().contains("CONSTRAINT_NAME"));
        var text=request.sources().sources().values().iterator().next();
        assertEquals("Rule",text.slice(d.primaryRange().orElseThrow()));
        assertEquals(text.revision(),d.primaryRange().orElseThrow().revision());
    }
    @Test void localConstraintNamesAreUniqueAcrossAllViewableFamilies() {
        duplicate("TOPIC T=CLASS C="+RULE+RULE+"END C;END T;");
        duplicate("STRUCTURE S="+RULE+RULE+"END S;");
        duplicate("TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=END B;ASSOCIATION AB=a--A;b--B;"+RULE+RULE+"END AB;END T;");
        duplicate("TOPIC T=CLASS C=x:TEXT;END C;VIEW V PROJECTION OF C;=ALL OF C;"+RULE+RULE+"END V;END T;");
    }
    @Test void differentConstraintFamiliesShareTheSameNameNamespace() {
        for(String constraint:List.of("CONSTRAINT Rule:>=90% #true;","UNIQUE Rule:value;",
                "EXISTENCE CONSTRAINT Rule:value REQUIRED IN F.T.C:value;",
                "SET CONSTRAINT Rule:INTERLIS.objectCount(ALL)>=0;"))
            duplicate("TOPIC T=CLASS C=value:0..10;"+RULE+constraint+"END C;END T;");
    }
    @Test void externalBlocksShareTheTargetNamespaceAndRetainNameRangesForQueries() {
        duplicate("TOPIC T=CLASS C="+RULE+"END C;CONSTRAINTS OF C="+RULE+"END;END T;");
        duplicate("TOPIC T=CLASS C=END C;CONSTRAINTS OF C="+RULE+"END;CONSTRAINTS OF C="+RULE+"END;END T;");
        var result=ok("TOPIC T=CLASS C=END C;CONSTRAINTS OF C="+RULE+"END;END T;");
        var graph=result.schema().semanticGraph();
        var constraint=graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast).findFirst().orElseThrow();
        assertEquals("C",graph.symbol(constraint.target().orElseThrow()).orElseThrow().name().value());
        var text=result.analysis().syntaxSnapshots().values().iterator().next().source();
        var range=constraint.header().nameRange().orElseThrow();assertEquals("Rule",text.slice(range));
        var cursor=text.range(range.startOffset(),range.startOffset());
        assertEquals(constraint.id(),LanguageQueries.create().symbolAt(result.analysis(),cursor,TOKEN).known().orElseThrow().symbol());
    }
    @Test void unnamedConstraintsDifferentNamesAndSeparateScopesRemainValid() {
        for(String v:List.of("2.3","2.4")) {
            var text=source("memory:/unnamed.ili",wrap(v,"F","TOPIC T=CLASS C=MANDATORY CONSTRAINT #true;MANDATORY CONSTRAINT #true;END C;END T;"));
            assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request(List.of("F"),Map.of(text.id(),text),List.of(),CompilerOptions.defaults()),TOKEN));
        }
        ok("TOPIC T=CLASS C="+RULE+RULE.replace("Rule","Other")+"END C;CLASS D="+RULE+"END D;END T;");
        ok("DOMAIN D=0..10 CONSTRAINTS Rule:THIS>0;TOPIC T=CLASS C="+RULE+"END C;END T;");
    }
    @Test void importedConstraintNameEditsMatchFreshAnalysisInBothFileOrders() {
        for(boolean reverse:List.of(false,true)) {
            var use=source(reverse?"memory:/a-use.ili":"memory:/z-use.ili",wrap("2.4","F","IMPORTS Base;STRUCTURE S=value:Base.D;END S;"));
            var baseId=new SourceId(reverse?"memory:/z-base.ili":"memory:/a-base.ili");
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String name:List.of("Other","Rule","Other")) {
                var base=new SourceText(baseId,new SourceRevision(++revision),wrap("2.4","Base","DOMAIN D=TEXT;TOPIC T=CLASS C="+RULE+RULE.replace("Rule",name)+"END C;END T;"));
                var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=CompilerEngines.create().analyze(request,TOKEN);
                var reused=engine.analyze(request,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(name.equals("Rule"),!fresh.diagnostics().isEmpty());
                if(name.equals("Rule")) {
                    assertEquals(1,reused.diagnostics().size());var d=reused.diagnostics().getFirst();
                    assertEquals(base.id(),d.primaryRange().orElseThrow().source());assertEquals(base.revision(),d.primaryRange().orElseThrow().revision());
                    assertEquals("Rule",base.slice(d.primaryRange().orElseThrow()));
                }
                previous=reused;
            }
        }
    }
}
