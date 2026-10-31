package guru.interlis.workspace;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** SYN-009/INC-018: early and partial syntax limits never masquerade as missing models. */
class P3CaptureBudgetTest {
    @Test void tokenAndPartialParseBudgetsCancelWithoutCommittingReuseAndTheSessionRecovers() {
        String text="INTERLIS 2.4;MODEL F AT \"x\" VERSION \"1\"=DOMAIN D=TEXT;END F.";
        var defaults=CompilerOptions.defaults();
        var normal=defaults.budget();
        var shallow=new AnalysisBudget(normal.maxSourceUtf16Length(),normal.maxTotalSourceUtf16Length(),normal.maxTokens(),normal.maxSyntaxNodes(),normal.maxRecoveryEvents(),1,normal.maxSemanticWorkUnits(),normal.maxDiagnostics(),normal.maxNumericLiteralDigits(),normal.maxAbsoluteDecimalExponent());
        for(var budget:List.of(normal.withMaxTokens(1),shallow)) {
            var options=new CompilerOptions(defaults.semanticProfile(),defaults.allowedLanguages(),defaults.warningPolicy(),budget,defaults.overlays());
            var base=WorkspaceConfiguration.defaults();
            try(var workspace=Workspaces.create(CompilerEngines.create(),new WorkspaceConfiguration(options,base.resolutionPolicy(),base.resolverCatalog(),base.cacheLimits()))) {
                var id=new SourceId("memory:/capture-budget");WorkspaceTestSupport.add(workspace,id.value(),text);
                var roots=List.<ModelSelector>of(new ModelSelector.BySource(id));var revision=workspace.currentRevision();
                for(int i=0;i<2;i++) {
                    var failure=assertThrows(OperationCancelledException.class,() -> workspace.capture(new AnalysisRequest(revision,roots)));
                    assertEquals(CancellationReason.RESOURCE_LIMIT,failure.reason());assertEquals(revision,workspace.currentRevision());
                    assertEquals(0,workspace.metrics().acceptedAnalyses());assertEquals(text,workspace.source(id).orElseThrow().text());
                }
                workspace.configure(base,revision);
                assertInstanceOf(CompilationOutcome.Succeeded.class,workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(),roots),CancellationToken.none()));
            }
        }
    }
}
