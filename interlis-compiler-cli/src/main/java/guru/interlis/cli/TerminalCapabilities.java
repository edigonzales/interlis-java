package guru.interlis.cli;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Immutable capability of the actual stderr sink. Unknown capabilities are false. */
public record TerminalCapabilities(boolean stderrInteractive) {
    /**
     * Probes inherited FD 2 on POSIX with a fixed host command, never model text.
     * No console/stdout inference, native access, shell, or global terminal changes.
     * Unsupported hosts and unsuccessful or interrupted probes fail closed.
     */
    public static TerminalCapabilities system() {
        if (java.io.File.separatorChar != '/') return new TerminalCapabilities(false);
        Process process = null;
        try {
            process = new ProcessBuilder("/bin/test", "-t", "2").inheritIO().start();
            return new TerminalCapabilities(process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0);
        } catch (IOException failure) {
            return new TerminalCapabilities(false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new TerminalCapabilities(false);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
