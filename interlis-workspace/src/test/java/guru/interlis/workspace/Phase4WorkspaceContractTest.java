package guru.interlis.workspace;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** INC-004/014/016/018/022: stale captures and eviction cannot replace public results. */
class Phase4WorkspaceContractTest {
    private static final List<ModelSelector> ROOTS=List.of(new ModelSelector.ByName("F",Optional.empty(),Optional.empty()));
    private static String text(int maximum){return "INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=DOMAIN D=0.."+maximum+";STRUCTURE C=value:D;END C;END F.";}
    private static String maximum(AnalysisSnapshot snapshot) {
        var domain=(DomainSymbol)snapshot.queries().symbol(snapshot.queries().findQualifiedName(QualifiedName.of("F","D")).getFirst()).known().orElseThrow();
        return ((NumericType)snapshot.semanticGraph().type(domain.typeId()).orElseThrow()).range().maximum().orElseThrow().exactValue().toString();
    }
    @Test void lateDetachedAnalysisCannotPoisonNextReuseOrHeldSnapshotsAfterEviction() throws Exception {
        try(var workspace=WorkspaceTestSupport.workspace()) {
            SourceId source=new SourceId("memory:/late.ili");WorkspaceTestSupport.add(workspace,source.value(),text(10));
            var first=workspace.analyze(new AnalysisRequest(workspace.currentRevision(),ROOTS),CancellationToken.none());var capture=workspace.capture(new AnalysisRequest(workspace.currentRevision(),ROOTS));
            var released=new CountDownLatch(1);
            var detached=CompletableFuture.supplyAsync(()->{try{if(!released.await(5,TimeUnit.SECONDS))throw new AssertionError("worker was not released");}catch(InterruptedException e){throw new AssertionError(e);}return CompilerEngines.create().analyze(capture.request(),capture.reuse(),CancellationToken.none());});
            WorkspaceTestSupport.replace(workspace,source,text(99));released.countDown();var old=detached.get(5,TimeUnit.SECONDS);
            assertEquals(AnalysisAcceptance.STALE_NO_COMMIT,workspace.acceptAnalysis(capture,old));assertEquals("10",maximum(old));
            var request=new AnalysisRequest(workspace.currentRevision(),ROOTS);var current=workspace.analyze(request,CancellationToken.none());assertEquals("99",maximum(current));
            var repeated=workspace.analyze(request,CancellationToken.none());assertEquals("99",maximum(repeated));assertEquals(current.fingerprint(),repeated.fingerprint());
            assertEquals("10",maximum(first));assertEquals("10",maximum(old));assertTrue(workspace.metrics().syntaxReuses()>0);
            var configuration=workspace.configuration();workspace.configure(new WorkspaceConfiguration(configuration.compilerOptions(),configuration.resolutionPolicy(),configuration.resolverCatalog(),new CacheLimits(0,0,0,0)),workspace.currentRevision());
            assertEquals(0,workspace.metrics().cachedSyntaxEntries());assertEquals(0,workspace.metrics().cachedAnalysisEntries());
            var uncached=workspace.analyze(new AnalysisRequest(workspace.currentRevision(),ROOTS),CancellationToken.none());assertEquals("99",maximum(uncached));
            assertEquals(current.fingerprint(),uncached.fingerprint());assertTrue(workspace.metrics().evictions()>0);
            assertEquals("10",maximum(first));assertEquals("99",maximum(current));
        }
    }
    @Test void parallelConsumersWithDifferentOverlaysCannotMutateACommonInputOrPublishedGraph() throws Exception {
        var source=new SourceText(new SourceId("memory:/shared.ili"),new SourceRevision(1),text(10));var sources=SourceSet.of(Map.of(source.id(),source));
        var baseline=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(new CompilationRequest(ROOTS,sources,CompilerOptions.defaults()),CancellationToken.none()));
        var futures=new ArrayList<CompletableFuture<CompilationOutcome>>();
        for(String label:List.of("left","right")) {
            var overlay=new SemanticOverlays.SemanticOverlay.MetaAttributeOverlay(new SemanticOverlays.OverlaySource(label,1),SemanticOverlays.Target.named("F.D"),Map.of("label",List.of(label)));
            var d=CompilerOptions.defaults();var options=new CompilerOptions(d.semanticProfile(),d.allowedLanguages(),d.warningPolicy(),d.budget(),new SemanticOverlays(List.of(overlay)));
            futures.add(CompletableFuture.supplyAsync(()->CompilerEngines.create().compile(new CompilationRequest(ROOTS,sources,options),CancellationToken.none())));
        }
        for(int i=0;i<futures.size();i++) {
            var out=assertInstanceOf(CompilationOutcome.Succeeded.class,futures.get(i).get(5,TimeUnit.SECONDS));
            var domain=out.schema().queries().symbol(out.schema().queries().findQualifiedName(QualifiedName.of("F","D")).getFirst()).known().orElseThrow();
            assertEquals(i==0?"left":"right",domain.header().metaAttributes().values().get("label").text());assertEquals("10",maximum(out.analysis()));
        }
        var domain=baseline.schema().queries().symbol(baseline.schema().queries().findQualifiedName(QualifiedName.of("F","D")).getFirst()).known().orElseThrow();
        assertTrue(domain.header().metaAttributes().values().isEmpty());assertEquals(text(10),source.text());assertEquals("10",maximum(baseline.analysis()));
    }
}
