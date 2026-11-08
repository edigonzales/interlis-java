package guru.interlis.compiler;

import guru.interlis.diagnostics.Phase;
import guru.interlis.execution.*;
import guru.interlis.model.*;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class SemanticDependencyDepthContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();

    @Test
    void moderateTextChainsKeepEveryBaseInEitherDeclarationOrder() {
        for (boolean reverse : List.of(false, true)) {
            var schema = succeeded(CompilerEngines.create().compile(request(chain(240, reverse, false, false, 20, 1),
                    AnalysisBudget.defaults()), TOKEN)).schema();
            assertChain(schema.semanticGraph(), 240, false, 20);
        }
    }

    @Test
    void deepTextAndEnumChainsReachThePublishedSchema() {
        for (boolean enumeration : List.of(false, true)) {
            int count = 4_000;
            var result = CompilerEngines.create().compile(request(chain(count, false, enumeration, false, 20, 1),
                    AnalysisBudget.defaults().withMaxSemanticWorkUnits(10_000_000)), TOKEN);
            assertChain(succeeded(result).schema().semanticGraph(), count, enumeration, 20);
        }
    }

    @Test
    void deepAndMixedCyclesReportTheDomainDiagnosticAndLeaveTheEngineUsable() {
        var engine = CompilerEngines.create();
        var published = succeeded(engine.compile(request(chain(20, false, false, false, 20, 1),
                AnalysisBudget.defaults()), TOKEN)).schema();
        var mixed = CompilerTestSupport.source("memory:/mixed-domain-cycle.ili", """
                INTERLIS 2.4; MODEL M AT "x" VERSION "1" =
                  DOMAIN A EXTENDS B = TEXT*20;
                    B = FORMAT A "00" .. "99";
                END M.
                """);
        var deep = chain(4_000, false, false, true, 20, 2);
        for (var source : List.of(deep, mixed)) {
            var result = engine.compile(request(source, AnalysisBudget.defaults()), TOKEN);
            if (source == deep && result instanceof CompilationOutcome.Cancelled cancelled) {
                assertEquals(CancellationReason.RESOURCE_LIMIT, cancelled.reason());
                // The existing mandatory-flag traversal visits the full cycle
                // for each domain when it builds the published headers.
                result = engine.compile(request(source,
                        AnalysisBudget.defaults().withMaxSemanticWorkUnits(25_000_000)), TOKEN);
            }
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, result);
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
            var cycle = rejected.analysis().diagnostics().stream()
                    .filter(d -> d.code().value().equals("ILI-SEM-0010")
                            && d.arguments().toString().contains("DOMAIN_TYPE_CYCLE")).findFirst().orElseThrow();
            var range = cycle.primaryRange().orElseThrow();
            assertEquals(source.revision(), range.revision());
            assertFalse(source.slice(range).isEmpty());
        }
        assertChain(succeeded(engine.compile(request(chain(20, true, false, false, 15, 3),
                AnalysisBudget.defaults()), TOKEN)).schema().semanticGraph(), 20, false, 15);
        assertChain(published.semanticGraph(), 20, false, 20);
    }

    @Test
    void bindingBudgetAndUserCancellationHaveDistinctTerminalReasons() {
        var events = new ArrayList<RunEvent>();
        var binding = new AtomicBoolean();
        var checks = new AtomicInteger();
        var services = new CompilerServices(event -> {
            events.add(event);
            if (event instanceof RunEvent.PhaseStarted phase && phase.phase() == Phase.BINDING) binding.set(true);
        }, ClockSource.system(), RunContextFactory.defaults());
        var engine = CompilerEngines.create(services);
        var source = chain(300, false, false, false, 20, 1);
        var limited = request(source, AnalysisBudget.defaults().withMaxSemanticWorkUnits(120));
        var outcome = assertInstanceOf(CompilationOutcome.Cancelled.class, engine.compile(limited, TOKEN));
        assertEquals(CancellationReason.RESOURCE_LIMIT, outcome.reason());
        assertTerminal(events, CancellationReason.RESOURCE_LIMIT);
        events.clear();
        var abortedAnalysis = assertThrows(OperationCancelledException.class, () -> engine.analyze(limited, TOKEN));
        assertEquals(CancellationReason.RESOURCE_LIMIT, abortedAnalysis.reason());
        assertTerminal(events, CancellationReason.RESOURCE_LIMIT);

        events.clear();
        binding.set(false);
        var full = request(source, AnalysisBudget.defaults());
        CancellationToken token = () -> binding.get() && checks.incrementAndGet() >= 120;
        var cancelled = assertInstanceOf(CompilationOutcome.Cancelled.class, engine.compile(full, token));
        assertEquals(CancellationReason.USER_REQUESTED, cancelled.reason());
        assertTerminal(events, CancellationReason.USER_REQUESTED);
        assertChain(succeeded(engine.compile(full, TOKEN)).schema().semanticGraph(), 300, false, 20);
    }

    @Test
    void reusedAnalysisAgreesAfterAnEditAndTheOldSchemaKeepsItsTypes() {
        var engine = CompilerEngines.create();
        var initial = succeeded(engine.compile(request(chain(120, false, false, false, 20, 1),
                AnalysisBudget.defaults()), TOKEN));
        var edited = request(chain(120, false, false, false, 19, 2), AnalysisBudget.defaults());
        var fresh = succeeded(engine.compile(edited, TOKEN));
        var reused = succeeded(engine.compile(edited,
                new AnalysisReuse(Optional.of(initial.analysis()), initial.analysis().syntaxSnapshots()), TOKEN));
        assertEquals(fresh.analysis().diagnostics(), reused.analysis().diagnostics());
        assertChain(fresh.schema().semanticGraph(), 120, false, 19);
        assertChain(reused.schema().semanticGraph(), 120, false, 19);
        assertChain(initial.schema().semanticGraph(), 120, false, 20);
    }

    private static void assertTerminal(List<RunEvent> events, CancellationReason reason) {
        assertTrue(events.stream().anyMatch(event -> event instanceof RunEvent.PhaseStarted phase
                && phase.phase() == Phase.BINDING));
        assertEquals(1, events.stream().filter(RunEvent.Finished.class::isInstance).count());
        var terminal = assertInstanceOf(RunEvent.Finished.class, events.getLast());
        assertEquals(reason, assertInstanceOf(RunCompletion.Cancelled.class, terminal.completion()).reason());
    }

    private static void assertChain(SemanticGraph graph, int count, boolean enumeration, int textLength) {
        var domains = graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .filter(domain -> domain.name().value().matches("D[0-9]+"))
                .collect(java.util.stream.Collectors.toMap(domain -> domain.name().value(), domain -> domain));
        assertEquals(count, domains.size());
        for (int i = 0; i < count; i++) {
            var current = domains.get("D" + i);
            var base = domains.get("D" + (i + 1));
            assertEquals(Optional.ofNullable(base).map(DomainSymbol::id), current.baseDomain());
            var type = graph.type(current.typeId()).orElseThrow();
            if (enumeration) {
                var domain = assertInstanceOf(EnumerationType.class, type);
                assertEquals(Optional.ofNullable(base).map(DomainSymbol::typeId), domain.baseType());
                assertEquals(List.of("a"), domain.values().stream().map(EnumerationValue::path).toList());
            } else {
                assertEquals(textLength, assertInstanceOf(TextType.class, type).maxLength().orElseThrow());
            }
        }
    }

    private static SourceText chain(int count, boolean reverse, boolean enumeration, boolean cycle,
                                    int textLength, long revision) {
        var text = new StringBuilder("INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = DOMAIN\n");
        for (int i = 0; i < count; i++) {
            int position = reverse ? count - 1 - i : i;
            text.append('D').append(position);
            boolean hasBase = position + 1 < count || cycle;
            if (hasBase) text.append(" EXTENDS D").append((position + 1) % count);
            text.append(" = ");
            if (enumeration) text.append(hasBase ? "(FINAL)" : "(a)");
            else text.append("TEXT*").append(textLength);
            text.append(";\n");
        }
        var source = CompilerTestSupport.source("memory:/deep-domains.ili", text.append("END M.").toString());
        return new SourceText(source.id(), new SourceRevision(revision), source.text());
    }

    private static CompilationRequest request(SourceText source, AnalysisBudget budget) {
        var defaults = CompilerOptions.defaults();
        var options = new CompilerOptions(defaults.semanticProfile(), defaults.allowedLanguages(),
                defaults.warningPolicy(), budget, defaults.overlays());
        return CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source), List.of(), options);
    }

    private static CompilationOutcome.Succeeded succeeded(CompilationOutcome outcome) {
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
    }
}
