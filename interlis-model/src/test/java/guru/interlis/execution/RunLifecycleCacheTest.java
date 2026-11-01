package guru.interlis.execution;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RunLifecycleCacheTest {
    @Test void cacheEventsShareTheirRunningLifecycleSequenceAndRejectOtherStates() {
        var events=new ArrayList<RunEvent>();
        var context=new RunContext(new RunId("cache-parent"),Optional.empty(),Map.of());
        var run=RunLifecycle.create(context,events::add,ClockSource.system());
        assertThrows(IllegalStateException.class,()->run.cacheLookup("repository",true,"OFFLINE_HIT"));
        run.start(new RunEvent.RunMetadata("cli",List.of("2.4"),0,"test"));
        run.cacheLookup("repository",true,"NOT_MODIFIED");
        run.finish(new RunCompletion.Succeeded(0,0,true,false));
        assertEquals(List.of(1L,2L,3L),events.stream().map(e->e.header().sequence()).toList());
        assertTrue(events.stream().allMatch(e->e.header().runId().equals(context.runId())));
        assertInstanceOf(RunEvent.CacheLookup.class,events.get(1));
        assertThrows(IllegalStateException.class,()->run.cacheLookup("repository",false,"DOWNLOAD"));
    }
    @Test void aBrokenObserverDoesNotChangeTheLifecycle() {
        var context=new RunContext(new RunId("broken-cache-observer"),Optional.empty(),Map.of());
        var run=RunLifecycle.create(context,event->{throw new IllegalStateException("observer");},ClockSource.system());
        run.start(new RunEvent.RunMetadata("cli",List.of("2.4"),0,"test"));
        assertDoesNotThrow(()->run.cacheLookup("repository",false,"WRITE_UNAVAILABLE"));
        run.finish(new RunCompletion.Succeeded(0,0,true,false));
        assertEquals(3,run.discardedEvents());
    }
}
