package guru.interlis.workspace;
import guru.interlis.compiler.*;
import guru.interlis.compiler.SemanticOverlays.*;
import guru.interlis.compiler.SemanticOverlays.SemanticOverlay.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayWorkspaceContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final List<ModelSelector> ROOTS=List.of(new ModelSelector.ByName("F",Optional.empty(),Optional.empty()));
    private static String model(String name,String body){return "INTERLIS 2.4;MODEL "+name+" AT \"https://example.org\" VERSION \"1\"="+body+"END "+name+".";}
    private static WorkspaceConfiguration config(SemanticOverlay... overlays) {
        var c=WorkspaceConfiguration.defaults();var d=c.compilerOptions();return new WorkspaceConfiguration(new CompilerOptions(d.semanticProfile(),d.allowedLanguages(),d.warningPolicy(),d.budget(),new SemanticOverlays(List.of(overlays))),c.resolutionPolicy(),c.resolverCatalog(),c.cacheLimits());
    }
    @Test void externalOriginalImportsArePreparedWithoutLeakingTheirNamespace() {
        var overlay=new TranslationOverlay(new OverlaySource("config:/translation",1),Target.named("F"),Target.named("Original"));
        try(var w=Workspaces.create(CompilerEngines.create(),config(overlay))) {
            WorkspaceTestSupport.add(w,"memory:/f.ili",model("F","IMPORTS Leaf;DOMAIN Value=TEXT;"));
            WorkspaceTestSupport.add(w,"memory:/original.ili",model("Original","IMPORTS Leaf;DOMAIN TextValue=TEXT;"));
            WorkspaceTestSupport.add(w,"memory:/leaf.ili",model("Leaf","DOMAIN Number=0..10;"));
            var capture=w.capture(new AnalysisRequest(w.currentRevision(),ROOTS));
            assertInstanceOf(guru.interlis.workspace.resolution.ResolvedClosure.class,capture.resolution());
            assertEquals(Set.of("F","Original"),capture.request().sources().imports().stream().map(b->b.site().importingModel()).collect(java.util.stream.Collectors.toSet()));
            var out=assertInstanceOf(CompilationOutcome.Succeeded.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),ROOTS),TOKEN));
            var g=out.schema().semanticGraph();var model=(ModelSymbol)g.symbols().stream().filter(s->s.kind()==SymbolKind.MODEL&&s.name().value().equals("F")).findFirst().orElseThrow();
            assertTrue(model.translation().isPresent());assertEquals(1,model.imports().size());assertEquals("Leaf",model.imports().getFirst().importedModel());
            assertEquals(Set.of("F","Original","Leaf"),out.schema().models().stream().map(r->g.symbol(r.symbol()).orElseThrow().name().value()).collect(java.util.stream.Collectors.toSet()));
            w.configure(config(),w.currentRevision());
            assertEquals(AnalysisAcceptance.STALE_NO_COMMIT,w.acceptAnalysis(capture,CompilerEngines.create().analyze(capture.request(),capture.reuse(),TOKEN)));
            var plain=assertInstanceOf(CompilationOutcome.Succeeded.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),ROOTS),TOKEN));
            assertEquals(2,plain.schema().models().size());assertTrue(model.translation().isPresent());
        }
    }
    @Test void overlayOnlyChangesInvalidateCapturesWhileCachePolicyDoesNotChangeSemantics() {
        var first=new MetaAttributeOverlay(new OverlaySource("first",1),Target.named("F.P"),Map.of("label",List.of("first")));
        var second=new MetaAttributeOverlay(new OverlaySource("second",1),Target.named("F.P"),Map.of("label",List.of("second")));
        try(var w=Workspaces.create(CompilerEngines.create(),config(first))) {
            WorkspaceTestSupport.add(w,"memory:/f.ili",model("F","DOMAIN P=COORD 0..10,0..10;"));
            var capture=w.capture(new AnalysisRequest(w.currentRevision(),ROOTS));var old=CompilerEngines.create().analyze(capture.request(),capture.reuse(),TOKEN);
            w.configure(config(second),w.currentRevision());assertEquals(AnalysisAcceptance.STALE_NO_COMMIT,w.acceptAnalysis(capture,old));
            var out=assertInstanceOf(CompilationOutcome.Succeeded.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),ROOTS),TOKEN));
            var p=out.schema().semanticGraph().symbols().stream().filter(s->s.name().value().equals("P")).findFirst().orElseThrow();
            assertEquals("second",p.header().metaAttributes().values().get("label").text());
            assertEquals("first",old.semanticGraph().symbols().stream().filter(s->s.name().value().equals("P")).findFirst().orElseThrow().header().metaAttributes().values().get("label").text());
            var c=config(second);w.configure(new WorkspaceConfiguration(c.compilerOptions(),c.resolutionPolicy(),c.resolverCatalog(),new CacheLimits(1,1,1,10000)),w.currentRevision());
            var repeated=assertInstanceOf(CompilationOutcome.Succeeded.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),ROOTS),TOKEN));
            assertEquals(out.schema().fingerprint(),repeated.schema().fingerprint());
        }
    }
    @Test void anOverlayRoundTripRestoresContentIdentityWithoutReusingOldResults() {
        assertNotEquals(new guru.interlis.workspace.resolution.ResolverCatalog(Map.of("a=b","c")).fingerprint(),
                new guru.interlis.workspace.resolution.ResolverCatalog(Map.of("a","b=c")).fingerprint());
        var a=new MetaAttributeOverlay(new OverlaySource("configuration",1),Target.named("F.P"),Map.of("label",List.of("a")));
        var b=new MetaAttributeOverlay(new OverlaySource("configuration",1),Target.named("F.P"),Map.of("label",List.of("b")));
        try(var w=Workspaces.create(CompilerEngines.create(),config(a))) {
            WorkspaceTestSupport.add(w,"memory:/f.ili",model("F","DOMAIN P=COORD 0..10,0..10;"));
            var first=assertInstanceOf(CompilationOutcome.Succeeded.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),ROOTS),TOKEN));
            w.configure(config(b),w.currentRevision());var changed=assertInstanceOf(CompilationOutcome.Succeeded.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),ROOTS),TOKEN));
            assertNotEquals(first.schema().fingerprint(),changed.schema().fingerprint());
            w.configure(config(a),w.currentRevision());var again=assertInstanceOf(CompilationOutcome.Succeeded.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),ROOTS),TOKEN));
            assertEquals(first.schema().fingerprint(),again.schema().fingerprint());
            assertEquals(first.analysis().fingerprint(),again.analysis().fingerprint());
        }
    }

}
