package guru.interlis.compiler;

import guru.interlis.execution.ClockSource;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.execution.RunObserver;

import java.util.Objects;

/**
 * Immutable service bundle; safe for concurrent compiler runs when its parts are.
 *
 * @param observer the {@code observer} component
 * @param clock the {@code clock} component
 * @param runContexts the {@code runContexts} component
 */
public record CompilerServices(RunObserver observer, ClockSource clock,
                               RunContextFactory runContexts) {
    /** Creates a validated instance. */
    public CompilerServices {
        Objects.requireNonNull(observer, "observer");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(runContexts, "runContexts");
    }

    /** Returns the no-op observer, system clock and default run-context factory. */
    public static CompilerServices defaults() {
        return new CompilerServices(RunObserver.noop(), ClockSource.system(),
                RunContextFactory.defaults());
    }
}
