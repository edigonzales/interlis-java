package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.ClockSource;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.execution.RunEvent;
import guru.interlis.execution.RunObserver;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunEventContractTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Events AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  value : 0..9;
                END Item;
              END Data;
            END Events.
            """;

    @Test
    void oneOrderedTerminalEventAndIndependentObservers() {
        List<RunEvent> first = new ArrayList<>();
        List<RunEvent> second = new ArrayList<>();
        CompilerServices servicesFirst = new CompilerServices(first::add, ClockSource.system(),
                RunContextFactory.defaults());
        CompilerServices servicesSecond = new CompilerServices(second::add, ClockSource.system(),
                RunContextFactory.defaults());
        CompilationOutcome firstOutcome = CompilerEngines.create(servicesFirst)
                .compile(request(), CancellationToken.none());
        CompilationOutcome secondOutcome = CompilerEngines.create(servicesSecond)
                .compile(request(), CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Succeeded.class, firstOutcome);
        assertInstanceOf(CompilationOutcome.Succeeded.class, secondOutcome);
        assertOneRun(first);
        assertOneRun(second);
        var firstRun = first.getFirst().header().runId();
        var secondRun = second.getFirst().header().runId();
        assertTrue(!firstRun.equals(secondRun));
        assertTrue(first.stream().allMatch(e -> e.header().runId().equals(firstRun)));
        assertTrue(second.stream().allMatch(e -> e.header().runId().equals(secondRun)));
    }

    @Test
    void throwingObserverStillGetsExactlyOneTerminalAttempt() {
        AtomicInteger terminalAttempts = new AtomicInteger();
        RunObserver throwing = event -> {
            if (event instanceof RunEvent.Finished) {
                terminalAttempts.incrementAndGet();
                throw new IllegalStateException("finish sink failed");
            }
        };
        CompilerServices services = new CompilerServices(throwing, ClockSource.system(),
                RunContextFactory.defaults());
        CompilationOutcome outcome = CompilerEngines.create(services)
                .compile(request(), CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
        assertEquals(1, terminalAttempts.get());
    }

    private static void assertOneRun(List<RunEvent> events) {
        assertTrue(events.size() >= 2);
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i + 1L, events.get(i).header().sequence());
        }
        assertEquals(1, events.stream().filter(RunEvent.Started.class::isInstance).count());
        assertEquals(1, events.stream().filter(RunEvent.Finished.class::isInstance).count());
        assertEquals(events.size(), events.stream().filter(RunEvent.Finished.class::isInstance)
                .findFirst().orElseThrow().header().sequence());
    }

    private static CompilationRequest request() {
        SourceText source = new SourceText(new SourceId("memory:/events.ili"),
                new SourceRevision(1), MODEL);
        return new CompilationRequest(
                List.of(new ModelSelector.ByName("Events", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
    }
}
