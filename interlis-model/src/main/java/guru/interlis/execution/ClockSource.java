package guru.interlis.execution;

/** Neutral time source; durations derive from the monotonic clock. */
public interface ClockSource {
    /** Returns wall-clock milliseconds since the epoch. */
    long wallTimeEpochMillis();

    /** Returns a monotonic nanosecond value for duration measurement. */
    long monotonicNanos();

    /** Returns the host system clock. */
    static ClockSource system() {
        return new ClockSource() {
            @Override
            public long wallTimeEpochMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public long monotonicNanos() {
                return System.nanoTime();
            }
        };
    }
}
