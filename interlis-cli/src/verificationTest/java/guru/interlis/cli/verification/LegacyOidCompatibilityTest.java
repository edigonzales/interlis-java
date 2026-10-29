package guru.interlis.cli.verification;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyOidCompatibilityTest {
    static CompilationOutcome compile(String version,boolean contracted,String body) {
        var source=new SourceText(new SourceId("memory:/legacy-oid.ili"),new SourceRevision(1),"INTERLIS "+version+";"+(contracted?"CONTRACTED ":"")+"MODEL M AT \"x\" VERSION \"1\"="+body+"END M.");
        var request=new CompilationRequest(List.of(new ModelSelector.ByName("M",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(source.id(),source)),CompilerOptions.defaults());
        return CompilerEngines.create().compile(request,CancellationToken.none());
    }
    @Test void legacyOidAndBasketRulesRemainBoundWithoutContracted() {
        for(String version:List.of("2.3","2.4"))for(boolean contracted:List.of(false,true)) {
            var result=compile(version,contracted,"DOMAIN O=OID TEXT*32;TOPIC T=BASKET OID AS O;OID AS O;CLASS Base=OID AS O;END Base;CLASS Child EXTENDS Base=END Child;END T;");
            var success=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
            var graph=success.schema().semanticGraph();var domain=graph.symbols().stream().filter(s->s.name().value().equals("O")).findFirst().orElseThrow();
            for(String name:List.of("T","Base","Child")) {
                var symbol=graph.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow();
                assertFalse(symbol.header().oidRules().isEmpty());
                assertTrue(symbol.header().oidRules().stream().allMatch(r->r.domain().orElseThrow().equals(domain.id())));
                if(name.equals("T"))assertEquals(2,symbol.header().oidRules().size());
            }
        }
    }
    @Test void wrongOidDomainAndIncompatibleOverrideRemainSourceErrors() {
        for(String version:List.of("2.3","2.4"))for(String body:List.of("DOMAIN NotOid=TEXT;TOPIC T=OID AS NotOid;END T;",
                "DOMAIN O=OID TEXT*32;Other=OID TEXT*64;TOPIC T=CLASS Base=OID AS O;END Base;CLASS Child EXTENDS Base=OID AS Other;END Child;END T;")) {
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,false,body));assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.arguments().toString().contains("OID_DOMAIN")||d.arguments().toString().contains("OID_OVERRIDE")));
        }
    }
    @Test void unrelatedContractedRulesAreNotWaived() {
        assertInstanceOf(CompilationOutcome.Rejected.class,compile("2.3",false,"PARAMETER p:BOOLEAN;"));
        assertInstanceOf(CompilationOutcome.Succeeded.class,compile("2.4",false,"PARAMETER p:BOOLEAN;"));
    }
}
