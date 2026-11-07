package guru.interlis.compiler;

import guru.interlis.execution.*;
import guru.interlis.diagnostics.Phase;
import guru.interlis.source.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** SYN-009, INC-013, DIAG-007 and LOG-008 with actual public outcomes. */
class LifecycleBoundaryContractTest {
    private static final List<Phase> COMPILER_PHASES =
            List.of(Phase.SOURCE_RESOLUTION, Phase.LEXING, Phase.DECLARATION_INDEXING, Phase.BINDING);

    private static CompilationRequest input(String body,CompilerOptions options) {
        var source=new SourceText(new SourceId("memory:/boundary.ili"),new SourceRevision(9),"INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),options);
    }
    private static CompilationRequest input(String body){return input(body,CompilerOptions.defaults());}
    private static CompilerEngine engine(RunObserver observer) {
        ClockSource clock = new ClockSource() {
            private long ticks;

            @Override public long wallTimeEpochMillis() { return 0; }
            @Override public long monotonicNanos() { return ++ticks; }
        };
        return CompilerEngines.create(new CompilerServices(observer, clock, RunContextFactory.defaults()));
    }

    @Test void cancellationAtEveryEmittedCompilerPhasePreservesInputsAndAllowsAFollowup() {
        var baselineEvents=new ArrayList<RunEvent>();var request=input("DOMAIN D=TEXT;");
        assertInstanceOf(CompilationOutcome.Succeeded.class,engine(baselineEvents::add).compile(request,CancellationToken.none()));
        assertInstanceOf(RunCompletion.Succeeded.class, assertFinishedRun(baselineEvents, Phase.BINDING, PhaseStatus.SUCCEEDED));
        for (var phase : COMPILER_PHASES) {
            var events = new ArrayList<RunEvent>();
            var remaining = new AtomicInteger(-1);
            var armed = new AtomicBoolean(true);
            var engine = engine(event -> {
                events.add(event);
                if (event instanceof RunEvent.PhaseStarted started && started.phase() == phase
                        && armed.compareAndSet(true, false)) {
                    // Let binding reach its semantic work checkpoints before requesting cancellation.
                    remaining.set(phase == Phase.BINDING ? 3 : 1);
                }
            });
            CancellationToken token = () -> remaining.get() >= 0
                    && remaining.updateAndGet(count -> Math.max(0, count - 1)) == 0;
            var source = request.sources().sources().values().iterator().next();
            String text = source.text();
            var outcome = assertInstanceOf(CompilationOutcome.Cancelled.class, engine.compile(request, token));
            assertEquals(CancellationReason.USER_REQUESTED, outcome.reason());
            var completion = assertInstanceOf(RunCompletion.Cancelled.class,
                    assertFinishedRun(events, phase, PhaseStatus.CANCELLED));
            assertEquals(outcome.reason(), completion.reason());
            assertEquals(text, source.text());
            assertEquals(new SourceRevision(9), source.revision());

            remaining.set(-1);
            events.clear();
            var next = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request, token));
            var fresh = assertInstanceOf(CompilationOutcome.Succeeded.class,
                    CompilerEngines.create().compile(request, CancellationToken.none()));
            assertEquals(fresh.analysis().diagnostics(), next.analysis().diagnostics());
            assertEquals(fresh.schema().fingerprint(), next.schema().fingerprint());
            assertInstanceOf(RunCompletion.Succeeded.class,
                    assertFinishedRun(events, Phase.BINDING, PhaseStatus.SUCCEEDED));
        }
    }

    @Test void sourceRejectionKeepsTechnicallyCompletedPhasesSuccessful() {
        var events = new ArrayList<RunEvent>();
        var outcome = assertInstanceOf(CompilationOutcome.Rejected.class, engine(events::add)
                .compile(input("TOPIC T=CLASS C=x:Missing;END C;END T;"), CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), outcome.rejection().reasons());
        var completion = assertInstanceOf(RunCompletion.Rejected.class,
                assertFinishedRun(events, Phase.BINDING, PhaseStatus.SUCCEEDED));
        assertEquals(Set.of("SOURCE_ERRORS"), completion.reasons());
    }

    @Test void repeatedMalformedSourcesAndDeeplyNestedValidSourcesTerminateWithExactOutcomeKinds() {
        var engine=CompilerEngines.create();
        for(String malformed:List.of("TOPIC T=CLASS C=x:;END C;END T;","/* outer /* inner */","DOMAIN D=((((;"))for(int i=0;i<20;i++) {
            var outcome=assertInstanceOf(CompilationOutcome.Rejected.class,engine.compile(input(malformed),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),outcome.rejection().reasons());assertFalse(outcome.analysis().diagnostics().isEmpty());
        }
        var b=AnalysisBudget.defaults();var limited=new AnalysisBudget(b.maxSourceUtf16Length(),b.maxTotalSourceUtf16Length(),b.maxTokens(),b.maxSyntaxNodes(),b.maxRecoveryEvents(),32,b.maxSemanticWorkUnits(),b.maxDiagnostics(),b.maxNumericLiteralDigits(),b.maxAbsoluteDecimalExponent());
        var defaults=CompilerOptions.defaults();var options=new CompilerOptions(defaults.semanticProfile(),defaults.allowedLanguages(),defaults.warningPolicy(),limited,defaults.overlays());
        var deep=input("TOPIC T=CLASS C=MANDATORY CONSTRAINT "+"(".repeat(200)+"#true"+")".repeat(200)+";END C;END T;",options);
        var outcome=assertInstanceOf(CompilationOutcome.Cancelled.class,engine.compile(deep,CancellationToken.none()));assertEquals(CancellationReason.RESOURCE_LIMIT,outcome.reason());
        assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(input("DOMAIN D=TEXT;"),CancellationToken.none()));
    }
    @Test void internalFailuresAreTypedAndNeitherEventsNorSafeDiagnosticsExposeSecretInputs() {
        String secret="TEST_PASSWORD_private_5271";var events=new ArrayList<RunEvent>();
        var calls=new AtomicLong();var faultClock=new ClockSource(){public long wallTimeEpochMillis(){return 0;}public long monotonicNanos(){long n=calls.incrementAndGet();if(n==3)throw new IllegalStateException(secret);return n;}};
        var engine=CompilerEngines.create(new CompilerServices(events::add,faultClock,RunContextFactory.defaults()));
        var failed=assertInstanceOf(CompilationOutcome.Failed.class,engine.compile(input("DOMAIN D=TEXT;"),CancellationToken.none()));
        assertEquals("ILI-INF-0001",failed.error().code().value());assertEquals(Optional.of(failed.failureId()),failed.error().failureId());
        assertFalse(failed.toString().contains(secret));assertFalse(events.toString().contains(secret));assertEquals(1,events.stream().filter(RunEvent.Finished.class::isInstance).count());
        events.clear();var valid=input("STRUCTURE S=secret:TEXT:=\""+secret+"\";END S;");
        assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(valid,CancellationToken.none()));
        assertFalse(events.toString().contains(secret));assertFalse(events.toString().contains("STRUCTURE S"));
    }

    @Test void technicalFaultsInBindingCloseTheActivePhaseAsFailed() {
        for (boolean fatal : List.of(false, true)) {
            var events = new ArrayList<RunEvent>();
            var inBinding = new AtomicBoolean();
            var polls = new AtomicInteger();
            var engine = engine(event -> {
                events.add(event);
                if (event instanceof RunEvent.PhaseStarted started && started.phase() == Phase.BINDING) {
                    inBinding.set(true);
                }
            });
            String secret = "private test service detail";
            CancellationToken faultingToken = () -> {
                if (inBinding.get() && polls.incrementAndGet() == 3) {
                    if (fatal) throw new AssertionError(secret);
                    throw new IllegalStateException(secret);
                }
                return false;
            };
            var factory = RunContextFactory.defaults();
            var context = factory.createChild(factory.createRoot());
            var request = input("DOMAIN D=TEXT;");
            if (fatal) {
                var error = assertThrows(AssertionError.class,
                        () -> engine.compile(request, AnalysisReuse.none(), context, faultingToken));
                assertEquals(secret, error.getMessage());
                assertPhaseEvents(events, Phase.BINDING, PhaseStatus.FAILED);
                assertFalse(events.stream().anyMatch(e ->
                        e instanceof RunEvent.Finished end && end.completion() instanceof RunCompletion.Succeeded));
            } else {
                var failed = assertInstanceOf(CompilationOutcome.Failed.class,
                        engine.compile(request, AnalysisReuse.none(), context, faultingToken));
                var completion = assertInstanceOf(RunCompletion.Failed.class,
                        assertFinishedRun(events, Phase.BINDING, PhaseStatus.FAILED));
                assertEquals(failed.failureId(), completion.failureId());
                assertFalse(failed.toString().contains(secret));
                assertFalse(events.toString().contains(secret));
            }
            assertEquals(3, polls.get());
            assertEquals(context.runId(), events.getFirst().header().runId());
            assertEquals(context.parentRunId(), events.getFirst().header().parentRunId());
        }
    }

    @Test void semanticWorkBudgetCancelsTheActiveBindingPhase() {
        var events = new ArrayList<RunEvent>();
        var defaults = CompilerOptions.defaults();
        var options = new CompilerOptions(defaults.semanticProfile(), defaults.allowedLanguages(), defaults.warningPolicy(),
                defaults.budget().withMaxSemanticWorkUnits(1), defaults.overlays());
        var outcome = assertInstanceOf(CompilationOutcome.Cancelled.class, engine(events::add)
                .compile(input("DOMAIN D=TEXT;", options), CancellationToken.none()));
        assertEquals(CancellationReason.RESOURCE_LIMIT, outcome.reason());
        var completion = assertInstanceOf(RunCompletion.Cancelled.class,
                assertFinishedRun(events, Phase.BINDING, PhaseStatus.CANCELLED));
        assertEquals(outcome.reason(), completion.reason());
    }

    @Test void syntaxNodeRecoveryAndTokenBudgetsRemainResourceCancellationRatherThanLanguageErrors() {
        var b=AnalysisBudget.defaults();
        for(String resource:List.of("maxSyntaxNodes","maxRecoveryEvents","maxTokens")) {
            var limited=new AnalysisBudget(b.maxSourceUtf16Length(),b.maxTotalSourceUtf16Length(),resource.equals("maxTokens")?2:b.maxTokens(),
                    resource.equals("maxSyntaxNodes")?1:b.maxSyntaxNodes(),resource.equals("maxRecoveryEvents")?1:b.maxRecoveryEvents(),b.maxParseRuleDepth(),b.maxSemanticWorkUnits(),b.maxDiagnostics(),b.maxNumericLiteralDigits(),b.maxAbsoluteDecimalExponent());
            var defaults=CompilerOptions.defaults();var options=new CompilerOptions(defaults.semanticProfile(),defaults.allowedLanguages(),defaults.warningPolicy(),limited,defaults.overlays());
            String body=resource.equals("maxRecoveryEvents")?"STRUCTURE S=a:;b:;c:;END S;":"DOMAIN D=TEXT;";
            var events = new ArrayList<RunEvent>();
            var request=input(body,options);var outcome=assertInstanceOf(CompilationOutcome.Cancelled.class,engine(events::add).compile(request,CancellationToken.none()),resource);
            assertEquals(CancellationReason.RESOURCE_LIMIT,outcome.reason());
            var completion = assertInstanceOf(RunCompletion.Cancelled.class,
                    assertFinishedRun(events, Phase.LEXING, PhaseStatus.CANCELLED));
            assertEquals(outcome.reason(), completion.reason());
            if(!resource.equals("maxTokens")) {
                var source=request.sources().sources().values().iterator().next();
                var syntax=guru.interlis.syntax.SyntaxServices.create().parse(source,new guru.interlis.syntax.ParseOptions(guru.interlis.syntax.ParseMode.EDITOR,Optional.empty(),limited),CancellationToken.none());
                assertTrue(syntax.coverage().budgetExceeded());assertFalse(syntax.coverage().parseComplete());
                assertEquals("ILI-INF-0002",syntax.diagnostics().getFirst().code().value());assertTrue(syntax.diagnostics().getFirst().arguments().toString().contains(resource));
            }
        }
        var source=new SourceText(new SourceId("memory:/deep-fragment.ili"),new SourceRevision(1),"(".repeat(300)+"#true"+")".repeat(300));
        var fragment=guru.interlis.syntax.SyntaxServices.create().parseFragment(source,source.range(0,source.length()),guru.interlis.syntax.FragmentKind.EXPRESSION,guru.interlis.model.LanguageVersion.ILI24,CancellationToken.none());
        assertFalse(fragment.consumedEntireFragment());assertTrue(fragment.diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-INF-0002")));
    }

    private static RunCompletion assertFinishedRun(List<RunEvent> events, Phase lastPhase, PhaseStatus status) {
        assertPhaseEvents(events, lastPhase, status);
        assertEquals(1, events.stream().filter(RunEvent.Finished.class::isInstance).count());
        return assertInstanceOf(RunEvent.Finished.class, events.getLast()).completion();
    }

    private static void assertPhaseEvents(List<RunEvent> events, Phase lastPhase, PhaseStatus status) {
        var start = assertInstanceOf(RunEvent.Started.class, events.getFirst()).header();
        assertEquals(1, events.stream().filter(RunEvent.Started.class::isInstance).count());
        for (int i = 0; i < events.size(); i++) {
            var header = events.get(i).header();
            assertEquals(i + 1L, header.sequence());
            assertEquals(start.runId(), header.runId());
            assertEquals(start.parentRunId(), header.parentRunId());
        }

        int count = COMPILER_PHASES.indexOf(lastPhase) + 1;
        var phases = events.stream()
                .filter(e -> e instanceof RunEvent.PhaseStarted || e instanceof RunEvent.PhaseFinished).toList();
        assertEquals(2 * count, phases.size());
        for (int i = 0; i < count; i++) {
            var opened = assertInstanceOf(RunEvent.PhaseStarted.class, phases.get(2 * i));
            var closed = assertInstanceOf(RunEvent.PhaseFinished.class, phases.get(2 * i + 1));
            assertEquals(COMPILER_PHASES.get(i), opened.phase());
            assertEquals(opened.phase(), closed.phase());
            assertEquals(opened.metadata().unitCount(), closed.unitCount());
            assertEquals(i == count - 1 ? status : PhaseStatus.SUCCEEDED, closed.status());
        }
    }
}
