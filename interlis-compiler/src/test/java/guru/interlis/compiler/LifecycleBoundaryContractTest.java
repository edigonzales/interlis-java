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
    private static CompilationRequest input(String body,CompilerOptions options) {
        var source=new SourceText(new SourceId("memory:/boundary.ili"),new SourceRevision(9),"INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),options);
    }
    private static CompilationRequest input(String body){return input(body,CompilerOptions.defaults());}
    private static CompilerEngine engine(RunObserver observer) {return CompilerEngines.create(new CompilerServices(observer,ClockSource.system(),RunContextFactory.defaults()));}
    @Test void cancellationAtEveryEmittedCompilerPhasePreservesInputsAndAllowsAFollowup() {
        var baselineEvents=new ArrayList<RunEvent>();var request=input("DOMAIN D=TEXT;");
        assertInstanceOf(CompilationOutcome.Succeeded.class,engine(baselineEvents::add).compile(request,CancellationToken.none()));
        var phases=baselineEvents.stream().filter(RunEvent.PhaseStarted.class::isInstance).map(RunEvent.PhaseStarted.class::cast).map(RunEvent.PhaseStarted::phase).distinct().toList();
        assertEquals(Set.of(Phase.SOURCE_RESOLUTION,Phase.LEXING,Phase.DECLARATION_INDEXING,Phase.BINDING),Set.copyOf(phases));
        for(var phase:phases) {
            var events=new ArrayList<RunEvent>();var cancelled=new AtomicBoolean();var armed=new AtomicBoolean(true);
            var engine=engine(event->{events.add(event);if(event instanceof RunEvent.PhaseStarted started&&started.phase()==phase&&armed.compareAndSet(true,false))cancelled.set(true);});
            var source=request.sources().sources().values().iterator().next();String text=source.text();
            var outcome=assertInstanceOf(CompilationOutcome.Cancelled.class,engine.compile(request,cancelled::get));assertEquals(CancellationReason.USER_REQUESTED,outcome.reason());
            assertEquals(1,events.stream().filter(RunEvent.Finished.class::isInstance).count());assertEquals(text,source.text());assertEquals(new SourceRevision(9),source.revision());
            cancelled.set(false);events.clear();var next=assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(request,cancelled::get));
            var fresh=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request,CancellationToken.none()));
            assertEquals(fresh.analysis().diagnostics(),next.analysis().diagnostics());assertEquals(fresh.schema().fingerprint(),next.schema().fingerprint());
            assertEquals(1,events.stream().filter(RunEvent.Finished.class::isInstance).count());
        }
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
    @Test void syntaxNodeRecoveryAndTokenBudgetsRemainResourceCancellationRatherThanLanguageErrors() {
        var b=AnalysisBudget.defaults();
        for(String resource:List.of("maxSyntaxNodes","maxRecoveryEvents","maxTokens")) {
            var limited=new AnalysisBudget(b.maxSourceUtf16Length(),b.maxTotalSourceUtf16Length(),resource.equals("maxTokens")?2:b.maxTokens(),
                    resource.equals("maxSyntaxNodes")?1:b.maxSyntaxNodes(),resource.equals("maxRecoveryEvents")?1:b.maxRecoveryEvents(),b.maxParseRuleDepth(),b.maxSemanticWorkUnits(),b.maxDiagnostics(),b.maxNumericLiteralDigits(),b.maxAbsoluteDecimalExponent());
            var defaults=CompilerOptions.defaults();var options=new CompilerOptions(defaults.semanticProfile(),defaults.allowedLanguages(),defaults.warningPolicy(),limited,defaults.overlays());
            String body=resource.equals("maxRecoveryEvents")?"STRUCTURE S=a:;b:;c:;END S;":"DOMAIN D=TEXT;";
            var request=input(body,options);var outcome=assertInstanceOf(CompilationOutcome.Cancelled.class,CompilerEngines.create().compile(request,CancellationToken.none()),resource);
            assertEquals(CancellationReason.RESOURCE_LIMIT,outcome.reason());
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
}
