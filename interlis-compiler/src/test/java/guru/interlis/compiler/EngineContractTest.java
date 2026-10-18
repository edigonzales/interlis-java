package guru.interlis.compiler;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.ClockSource;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.execution.RunObserver;
import guru.interlis.model.CompiledSchema;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineContractTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Contract AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  height : 0..999;
                  MANDATORY CONSTRAINT height >= 0;
                END Item;
              END Data;
            END Contract.
            """;

    private static CompilationRequest request(String text, CompilerOptions options) {
        SourceText source = new SourceText(new SourceId("memory:/contract.ili"),
                new SourceRevision(1), text);
        return new CompilationRequest(List.of(new ModelSelector.ByName("Contract",
                Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), options);
    }

    @Test
    void cancellationIsTypedAndNeverProducesSchema() {
        CompilerEngine engine = CompilerEngines.create();
        CancellationToken cancelled = CancellationToken.requested(CancellationReason.USER_REQUESTED);
        OperationCancelledException exception = assertThrows(OperationCancelledException.class,
                () -> engine.analyze(request(MODEL, CompilerOptions.defaults()), cancelled));
        assertEquals(CancellationReason.USER_REQUESTED, exception.reason());
        CompilationOutcome outcome = engine.compile(request(MODEL, CompilerOptions.defaults()),
                cancelled);
        CompilationOutcome.Cancelled cancelledOutcome = assertInstanceOf(
                CompilationOutcome.Cancelled.class, outcome);
        assertEquals(CancellationReason.USER_REQUESTED, cancelledOutcome.reason());
    }

    @Test
    void resourceLimitIsCancellationAndNotAFalseNegativeModel() {
        CompilerOptions options = new CompilerOptions(SemanticProfileId.ILI_23_24_V1,
                CompilerOptions.defaults().allowedLanguages(), WarningPolicy.ALLOW_WARNINGS,
                AnalysisBudget.defaults().withMaxSourceUtf16Length(20), SemanticOverlays.empty());
        CompilationOutcome outcome = CompilerEngines.create().compile(request(MODEL, options),
                CancellationToken.none());
        CompilationOutcome.Cancelled cancelled = assertInstanceOf(
                CompilationOutcome.Cancelled.class, outcome);
        assertEquals(CancellationReason.RESOURCE_LIMIT, cancelled.reason());
    }

    @Test
    void failingObserverDoesNotChangeFactualResult() {
        AtomicInteger events = new AtomicInteger();
        RunObserver throwing = event -> {
            events.incrementAndGet();
            throw new IllegalStateException("sink is down");
        };
        CompilerServices services = new CompilerServices(throwing, ClockSource.system(),
                RunContextFactory.defaults());
        CompilationOutcome outcome = CompilerEngines.create(services)
                .compile(request(MODEL, CompilerOptions.defaults()), CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
        assertTrue(events.get() > 0);
    }

    @Test
    void reuseIsOptionalAndFreshSemanticsMatch() {
        CompilerEngine engine = CompilerEngines.create();
        CompilationRequest request = request(MODEL, CompilerOptions.defaults());
        CompilationOutcome.Succeeded fresh = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                engine.compile(request, CancellationToken.none()));
        AnalysisSnapshot previous = fresh.analysis();
        AnalysisReuse reuse = new AnalysisReuse(Optional.of(previous),
                previous.syntaxSnapshots());
        CompilationOutcome.Succeeded reused = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                engine.compile(request, reuse, CancellationToken.none()));
        assertEquals(schemaSymbols(fresh.schema()), schemaSymbols(reused.schema()));
        assertEquals(fresh.analysis().diagnostics().stream().map(Diagnostic::code).toList(),
                reused.analysis().diagnostics().stream().map(Diagnostic::code).toList());
    }

    private static java.util.List<String> schemaSymbols(CompiledSchema schema) {
        return schema.semanticGraph().declarationsInOrder().stream()
                .map(s -> s.kind() + ":" + s.name().value()).toList();
    }
}
