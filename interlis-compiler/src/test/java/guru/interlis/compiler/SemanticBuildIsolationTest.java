package guru.interlis.compiler;

import guru.interlis.compiler.SemanticOverlays.OverlaySource;
import guru.interlis.compiler.SemanticOverlays.SemanticOverlay.MetaAttributeOverlay;
import guru.interlis.compiler.SemanticOverlays.Target;
import guru.interlis.diagnostics.Phase;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunEvent;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.NumericType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SemanticBuildIsolationTest {
    @Test
    void overlappingCompilesUseTheirOwnSourcesAndOverlaysAndKeepEarlierGraph() throws Exception {
        var parallel = new AtomicBoolean();
        var timedOut = new AtomicBoolean();
        var binding = new CountDownLatch(2);
        var defaults = CompilerServices.defaults();
        var engine = CompilerEngines.create(new CompilerServices(event -> {
            if (parallel.get() && event instanceof RunEvent.PhaseStarted start && start.phase() == Phase.BINDING) {
                binding.countDown();
                try {
                    if (!binding.await(10, TimeUnit.SECONDS)) timedOut.set(true);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    timedOut.set(true);
                }
            }
        }, defaults.clock(), defaults.runContexts()));
        var old = compile(engine, input("memory:/old.ili", 1, "old", 10));
        parallel.set(true);

        CompilationOutcome.Succeeded first;
        CompilationOutcome.Succeeded second;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> compile(engine, input("memory:/a.ili", 2, "first", 20)));
            var b = executor.submit(() -> compile(engine, input("memory:/b.ili", 3, "second", 30)));
            first = a.get(15, TimeUnit.SECONDS);
            second = b.get(15, TimeUnit.SECONDS);
        }
        assertFalse(timedOut.get());
        assertEquals(0, binding.getCount());
        assertNotEquals(first.analysis().snapshotId(), second.analysis().snapshotId());
        assertDomain(old, "old", "10");
        assertDomain(first, "first", "20");
        assertDomain(second, "second", "30");
    }

    private static CompilationRequest input(String sourceId, long revision, String label, int upper) {
        var source = new SourceText(new SourceId(sourceId), new SourceRevision(revision),
                "INTERLIS 2.4; MODEL Sample AT \"https://example.org\" VERSION \"1\" = DOMAIN Value = 0.."
                        + upper + "; END Sample.");
        var defaults = CompilerOptions.defaults();
        var metadata = new MetaAttributeOverlay(new OverlaySource("config:/sample", 1),
                Target.named("Sample.Value"), Map.of("label", List.of(label)));
        var options = new CompilerOptions(defaults.semanticProfile(), defaults.allowedLanguages(),
                defaults.warningPolicy(), defaults.budget(), new SemanticOverlays(List.of(metadata)));
        return CompilerTestSupport.request(List.of("Sample"), Map.of(source.id(), source), List.of(), options);
    }

    private static CompilationOutcome.Succeeded compile(CompilerEngine engine, CompilationRequest request) {
        return assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request, CancellationToken.none()));
    }

    private static void assertDomain(CompilationOutcome.Succeeded result, String label, String upper) {
        var graph = result.schema().semanticGraph();
        var domain = graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .filter(symbol -> symbol.name().value().equals("Value")).findFirst().orElseThrow();
        assertEquals(label, domain.header().metaAttributes().values().get("label").text());
        var type = assertInstanceOf(NumericType.class, graph.type(domain.typeId()).orElseThrow());
        assertEquals(upper, type.range().maximum().orElseThrow().exactValue().toPlainString());
    }
}
