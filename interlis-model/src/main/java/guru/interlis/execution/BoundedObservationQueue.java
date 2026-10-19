package guru.interlis.execution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe bounded observer queue for host event sinks.
 *
 * <p>Events beyond capacity are discarded and counted; the queue never grows
 * without bound. The observer never performs IO and never blocks the compiler.</p>
 */
public final class BoundedObservationQueue implements RunObserver {
    private final int capacity;
    private final ArrayDeque<RunEvent> events;
    private long offered;
    private long dropped;

    /**
     * Creates a bounded queue.
     *
     * @param capacity positive maximum number of retained events
     */
    public BoundedObservationQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.events = new ArrayDeque<>(capacity);
    }

    @Override
    public synchronized void onEvent(RunEvent event) {
        offered++;
        if (events.size() >= capacity) {
            dropped++;
            return;
        }
        events.addLast(Objects.requireNonNull(event, "event"));
    }

    /**
     * Removes the oldest retained event.
     *
     * @return event or empty
     */
    public synchronized Optional<RunEvent> poll() {
        return Optional.ofNullable(events.pollFirst());
    }

    /**
     * Removes all retained events in order.
     *
     * @return immutable snapshot
     */
    public synchronized List<RunEvent> drain() {
        List<RunEvent> result = new ArrayList<>(events);
        events.clear();
        return List.copyOf(result);
    }

    /**
     * Returns current bounded-queue statistics.
     *
     * @return immutable statistics
     */
    public synchronized Stats stats() {
        return new Stats(capacity, events.size(), offered, dropped);
    }

    /**
     * Queue counters.
     *
     * @param capacity configured maximum
     * @param retained currently retained events
     * @param offered total offered events
     * @param dropped total dropped events
     */
    public record Stats(int capacity, int retained, long offered, long dropped) {
        /** Creates a validated instance. */
        public Stats {
            if (capacity <= 0 || retained < 0 || retained > capacity
                    || offered < 0 || dropped < 0 || retained + dropped > offered) {
                throw new IllegalArgumentException("invalid queue statistics");
            }
        }
    }
}
