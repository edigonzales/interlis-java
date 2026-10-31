package guru.interlis.cli.verification;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.nio.file.*;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Actual compiler-event load with bounded sinks and two independently injected instances. */
public final class ObservationPressure {
    private ObservationPressure() { }
    private static CompilationRequest input(int revision) {
        var source=new SourceText(new SourceId("memory:/pressure"),new SourceRevision(revision),
                "INTERLIS 2.4;MODEL Pressure AT \"x\" VERSION \"1\"=TOPIC T=CLASS C=n:TEXT;END C;END T;END Pressure.\n!! edit "+revision);
        return new CompilationRequest(List.of(new ModelSelector.ByName("Pressure",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(source.id(),source)),CompilerOptions.defaults());
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]).resolve("build/reports/performance/full-corpus/observation-pressure.json");Files.deleteIfExists(out);
        var first=new BoundedObservationQueue(32);var second=new BoundedObservationQueue(32);
        var engineA=CompilerEngines.create(new CompilerServices(first,ClockSource.system(),RunContextFactory.defaults()));
        var engineB=CompilerEngines.create(new CompilerServices(second,ClockSource.system(),RunContextFactory.defaults()));
        require(engineB.compile(input(1),CancellationToken.none()) instanceof CompilationOutcome.Succeeded,"Second instance failed before pressure");
        var before=second.stats();long started=System.nanoTime();
        for(int i=1;i<=1000;i++)require(engineA.compile(input(i),CancellationToken.none()) instanceof CompilationOutcome.Succeeded,"Compile failed under event pressure");
        double elapsedMs=(System.nanoTime()-started)/1_000_000.0;var stats=first.stats();
        require(second.stats().equals(before),"One compiler instance changed another observer");
        require(stats.capacity()==32&&stats.retained()==32&&stats.offered()>32&&stats.dropped()==stats.offered()-32,"Unbounded or unaccounted observation queue");
        var eventsA=first.drain();var eventsB=second.drain();
        var idsA=new HashSet<RunId>();eventsA.forEach(e->idsA.add(e.header().runId()));
        require(eventsB.stream().noneMatch(e->idsA.contains(e.header().runId())),"Run identities collide across injected factories");
        for(int i=1;i<eventsA.size();i++)if(eventsA.get(i).header().runId().equals(eventsA.get(i-1).header().runId()))
            require(eventsA.get(i).header().sequence()>eventsA.get(i-1).header().sequence(),"Retained event sequence reordered");
        require(engineB.compile(input(2),CancellationToken.none()) instanceof CompilationOutcome.Succeeded,"Second instance failed after pressure");
        write(out,Map.of("sourceFingerprint",ProfileFingerprint.current(Path.of(args[0])),"status","passed","compilerRuns",1000,"queueCapacity",stats.capacity(),"retained",stats.retained(),"offered",stats.offered(),"dropped",stats.dropped(),"secondInstanceUnaffected",true,"elapsedMs",elapsedMs,"scope","Actual JVM compiler events, no logger singleton or environment switches; bounded retained events, exact discard statistics, independent injected observer instances"));
        System.out.println("OBSERVATION_PRESSURE_PASS runs=1000 retained="+stats.retained()+" dropped="+stats.dropped());
    }
}
