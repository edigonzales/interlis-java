package guru.interlis.cli;

import java.util.Map;
import java.util.Objects;

/** Pure policy for the human diagnostic channel; no IO or retained run state. */
public final class ColorPolicy {
    /** Null-free inputs; explicit modes override environment defaults, never machine output. */
    public boolean useAnsi(ColorMode mode, TerminalCapabilities terminal,
                           Map<String, String> environment, boolean machineOutput) {
        Objects.requireNonNull(mode); Objects.requireNonNull(terminal); Objects.requireNonNull(environment);
        if (machineOutput || mode == ColorMode.NEVER) return false;
        if (mode == ColorMode.ALWAYS) return true;
        return environment.getOrDefault("NO_COLOR", "").isEmpty()
                && !environment.getOrDefault("TERM", "").equalsIgnoreCase("dumb")
                && terminal.stderrInteractive();
    }
}
