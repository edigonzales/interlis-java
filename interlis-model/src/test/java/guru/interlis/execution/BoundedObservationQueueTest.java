package guru.interlis.execution;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedObservationQueueTest {
    private static RunEvent event(int sequence) {
        return new RunEvent.CancelRequested(new RunEvent.RunEventHeader(
                new RunId("run-1"), Optional.empty(), sequence, 1, 0, sequence),
                CancellationReason.USER_REQUESTED, "test");
    }

    @Test
    void loadTestKeepsBoundAndCountsDiscards() {
        BoundedObservationQueue queue = new BoundedObservationQueue(1024);
        for (int i = 1; i <= 100_000; i++) {
            queue.onEvent(event(i));
        }
        BoundedObservationQueue.Stats stats = queue.stats();
        assertEquals(1024, stats.capacity());
        assertEquals(1024, stats.retained());
        assertEquals(100_000, stats.offered());
        assertEquals(100_000 - 1024, stats.dropped());
        assertFalse(queue.poll().isEmpty());
        assertTrue(queue.stats().retained() < 1024);
    }

    @Test
    void drainPreservesOrder() {
        BoundedObservationQueue queue = new BoundedObservationQueue(2);
        queue.onEvent(event(1));
        queue.onEvent(event(2));
        queue.onEvent(event(3));
        assertEquals(2, queue.drain().size());
        assertTrue(queue.poll().isEmpty());
    }
}
