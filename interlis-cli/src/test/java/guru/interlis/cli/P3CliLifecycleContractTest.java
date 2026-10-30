package guru.interlis.cli;

import guru.interlis.compiler.CompilerServices;
import guru.interlis.execution.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class P3CliLifecycleContractTest {
    @TempDir Path directory;

    @Test void compilerChildAndHostParentFinishExactlyOnceInOrder() throws Exception {
        var events = new ArrayList<RunEvent>();
        var result = run(List.of("check", model().toString()), events,
                new PrintStream(new ByteArrayOutputStream()), CancellationToken.none());
        assertEquals(CliTermination.SUCCESS, result.termination());
        var starts = events.stream().filter(RunEvent.Started.class::isInstance)
                .map(RunEvent.Started.class::cast).toList();
        assertEquals(2, starts.size());
        var parent = starts.getFirst().header().runId();
        assertEquals(Optional.empty(), starts.getFirst().header().parentRunId());
        assertEquals(Optional.of(parent), starts.getLast().header().parentRunId());
        assertEquals(Optional.of(parent), result.runId());
        var finishes = events.stream().filter(RunEvent.Finished.class::isInstance)
                .map(RunEvent.Finished.class::cast).toList();
        assertEquals(2, finishes.size());
        assertEquals(starts.getLast().header().runId(), finishes.getFirst().header().runId());
        assertEquals(parent, finishes.getLast().header().runId());
        assertInstanceOf(RunCompletion.Succeeded.class, finishes.getLast().completion());
        assertEquals(events.getLast(), finishes.getLast());
    }

    @Test void compilerSuccessCannotHideFinalOutputFailure() throws Exception {
        var events = new ArrayList<RunEvent>();
        var broken = new PrintStream(new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("injected"); }
        });
        var result = run(List.of("check", "--json", model().toString()), events, broken,
                CancellationToken.none());
        assertEquals(CliTermination.OPERATIONAL_FAILURE, result.termination());
        var finishes = events.stream().filter(RunEvent.Finished.class::isInstance)
                .map(RunEvent.Finished.class::cast).toList();
        assertEquals(2, finishes.size());
        assertInstanceOf(RunCompletion.Succeeded.class, finishes.getFirst().completion());
        var parent = assertInstanceOf(RunCompletion.Rejected.class, finishes.getLast().completion());
        assertEquals(Set.of("OPERATIONAL_FAILURE"), parent.reasons());
    }

    @Test void versionHasAHostRunWithoutAFictitiousCompilerChildOrSchema() throws Exception {
        var events = new ArrayList<RunEvent>();
        var result = run(List.of("version"), events, new PrintStream(new ByteArrayOutputStream()),
                CancellationToken.none());
        assertEquals(CliTermination.SUCCESS, result.termination());
        assertEquals(2, events.size());
        assertInstanceOf(RunEvent.Started.class, events.getFirst());
        var completion = assertInstanceOf(RunEvent.Finished.class, events.getLast()).completion();
        assertInstanceOf(RunCompletion.Succeeded.class, completion);
        assertFalse(completion.compiledSchemaProduced());
    }

    @Test void cancellationBeforeHostLoadingPreservesItsActualReason() throws Exception {
        var events = new ArrayList<RunEvent>();
        var result = run(List.of("check", model().toString()), events,
                new PrintStream(new ByteArrayOutputStream()),
                CancellationToken.requested(CancellationReason.DEADLINE_EXCEEDED));
        assertEquals(CliTermination.CANCELLED, result.termination());
        assertEquals(2, events.size());
        var completion = assertInstanceOf(RunCompletion.Cancelled.class,
                assertInstanceOf(RunEvent.Finished.class, events.getLast()).completion());
        assertEquals(CancellationReason.DEADLINE_EXCEEDED, completion.reason());
    }

    @Test void anAnalysisReportsCompletenessWithoutPublishingACompiledSchema() throws Exception {
        var events = new ArrayList<RunEvent>();
        var result = run(List.of("analyze", model().toString()), events,
                new PrintStream(new ByteArrayOutputStream()), CancellationToken.none());
        assertEquals(CliTermination.SUCCESS, result.termination());
        var completions = events.stream().filter(RunEvent.Finished.class::isInstance)
                .map(RunEvent.Finished.class::cast).map(RunEvent.Finished::completion).toList();
        assertEquals(2, completions.size());
        for (var completion : completions) {
            assertTrue(assertInstanceOf(RunCompletion.Succeeded.class, completion).analysisComplete());
            assertFalse(completion.compiledSchemaProduced());
        }
    }

    @Test void aCompilerRejectionRetainsItsTypedGateSummaryAfterAHostFlushFailure() throws Exception {
        Path source = model();
        Files.writeString(source, "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = TOPIC T = CLASS C = a: Missing; END C; END T; END F.");
        var broken = new PrintStream(new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("injected"); }
        });
        var result = run(List.of("check", "--json", source.toString()), new ArrayList<>(), broken, CancellationToken.none());
        assertEquals(CliTermination.OPERATIONAL_FAILURE, result.termination());
        assertEquals(Set.of(guru.interlis.compiler.RejectionReason.SOURCE_ERRORS),
                result.rejectionSummary().orElseThrow().reasons());
        assertFalse(result.rejectionSummary().orElseThrow().diagnosticIds().isEmpty());
    }

    @Test void diagramParentReportsItsActualRequestedRootCount() throws Exception {
        var events = new ArrayList<RunEvent>();
        var result = run(List.of("diagram", model().toString()), events,
                new PrintStream(new ByteArrayOutputStream()), CancellationToken.none());
        assertEquals(CliTermination.SUCCESS, result.termination(), result.output());
        var parent = assertInstanceOf(RunCompletion.Succeeded.class,
                assertInstanceOf(RunEvent.Finished.class, events.getLast()).completion());
        assertEquals(1, parent.rootCount());
        assertTrue(parent.compiledSchemaProduced());
    }

    @Test void concurrentInvocationsOnOneApplicationKeepIndependentParentsAndCorrelation() throws Exception {
        Path source = model();
        var contexts = new java.util.concurrent.ConcurrentHashMap<RunId, RunContext>();
        var delegate = RunContextFactory.defaults();
        var roots = new java.util.concurrent.atomic.AtomicInteger();
        RunContextFactory factory = new RunContextFactory() {
            public RunContext createRoot() { return createRoot(Map.of("invocation", Integer.toString(roots.incrementAndGet()))); }
            public RunContext createRoot(Map<String,String> attributes) {
                var value = delegate.createRoot(attributes); contexts.put(value.runId(), value); return value;
            }
            public RunContext createChild(RunContext parent) {
                var value = delegate.createChild(parent); contexts.put(value.runId(), value); return value;
            }
        };
        var events = new java.util.concurrent.ConcurrentLinkedQueue<RunEvent>();
        var parentsEntered = new java.util.concurrent.CountDownLatch(2);
        var services = new CompilerServices(event -> {
            events.add(event);
            if (event instanceof RunEvent.Started && event.header().parentRunId().isEmpty()) {
                parentsEntered.countDown();
                try { assertTrue(parentsEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
            }
        }, ClockSource.system(), factory);
        var application = new DefaultCompilerCli();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<CliExecutionResult>) () -> application.run(
                    List.of("check", source.toString()), new CliEnvironment(directory,
                            new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()),
                            new PrintStream(new ByteArrayOutputStream()), Map.of(), services), CancellationToken.none());
            var results = executor.invokeAll(List.of(task, task));
            var first = results.getFirst().get(5, java.util.concurrent.TimeUnit.SECONDS);
            var second = results.getLast().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(CliTermination.SUCCESS, first.termination()); assertEquals(CliTermination.SUCCESS, second.termination());
            assertNotEquals(first.runId(), second.runId()); assertEquals(0, parentsEntered.getCount());
            assertEquals(4, contexts.size());
            for (var context : contexts.values()) {
                context.parentRunId().ifPresent(parent -> assertEquals(contexts.get(parent).correlationAttributes(), context.correlationAttributes()));
                var observed = events.stream().filter(event -> event.header().runId().equals(context.runId())).toList();
                assertEquals(1, observed.stream().filter(RunEvent.Started.class::isInstance).count());
                assertEquals(1, observed.stream().filter(RunEvent.Finished.class::isInstance).count());
                long sequence = 0;
                for (var event : observed) { assertTrue(event.header().sequence() > sequence); sequence = event.header().sequence(); }
            }
        }
    }

    private Path model() throws IOException {
        Path file = directory.resolve("model.ili");
        Files.writeString(file, "INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = END F.");
        return file;
    }

    private CliExecutionResult run(List<String> arguments, List<RunEvent> events,
                                   PrintStream out, CancellationToken cancellation) {
        var services = new CompilerServices(events::add, ClockSource.system(), RunContextFactory.defaults());
        var environment = new CliEnvironment(directory, new ByteArrayInputStream(new byte[0]), out,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), Map.of(), services);
        return new DefaultCompilerCli().run(arguments, environment, cancellation);
    }
}
