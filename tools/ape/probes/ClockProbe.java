import java.time.Instant;

/** Checks the wall-clock contract independently of HTTP, TLS and INTERLIS. */
public final class ClockProbe {
    public static void main(String[] args) {
        long systemMillis = System.currentTimeMillis();
        long instantMillis = Instant.now().toEpochMilli();
        long difference = Math.abs(systemMillis - instantMillis);
        System.out.println("SystemMillis=" + systemMillis + " InstantMillis=" + instantMillis
                + " DifferenceMillis=" + difference);
        if (difference > 10_000) throw new AssertionError("Wall clocks disagree by more than ten seconds");
    }
}
