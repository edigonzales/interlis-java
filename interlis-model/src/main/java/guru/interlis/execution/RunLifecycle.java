package guru.interlis.execution;

import guru.interlis.diagnostics.Phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Local run state machine. Exactly one owner starts and finishes a run. The
 * lifecycle attempts exactly one terminal emission; it never claims exactly
 * once delivery across process boundaries.
 */
public final class RunLifecycle {
    private final RunContext context;
    private final RunObserver observer;
    private final ClockSource clock;
    private final ObservationFailureHandler failureHandler;
    private final List<OpenPhase> openPhases = new ArrayList<>();
    private long sequence;
    private State state = State.NEW;
    private long startedNanos;
    private int discardedEvents;

    private enum State {
        NEW, RUNNING, FINISHED
    }

    private RunLifecycle(RunContext context, RunObserver observer, ClockSource clock,
                         ObservationFailureHandler failureHandler) {
        this.context = Objects.requireNonNull(context, "context");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    /**
     * Executes {@code create}.
     *
     * @param context the {@code context} argument
     * @param observer the {@code observer} argument
     * @param clock the {@code clock} argument
     *
     * @return the result
     */
    public static RunLifecycle create(RunContext context, RunObserver observer, ClockSource clock) {
        return new RunLifecycle(context, observer, clock, ObservationFailureHandler.ignore());
    }

    public static RunLifecycle create(RunContext context, RunObserver observer, ClockSource clock,
                                      ObservationFailureHandler failureHandler) {
        return new RunLifecycle(context, observer, clock, failureHandler);
    }

    /**
     * Executes {@code context}.
     *
     * @return the result
     */
    public RunContext context() {
        return context;
    }

    /**
     * Executes {@code start}.
     *
     * @param metadata the {@code metadata} argument
     */
    public void start(RunEvent.RunMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        if (state != State.NEW) {
            throw new IllegalStateException("run already started");
        }
        state = State.RUNNING;
        startedNanos = clock.monotonicNanos();
        emit(header -> new RunEvent.Started(header, metadata));
    }

    /**
     * Executes {@code beginPhase}.
     *
     * @param phase the {@code phase} argument
     * @param metadata the {@code metadata} argument
     *
     * @return the result
     */
    public PhaseHandle beginPhase(Phase phase, RunEvent.PhaseMetadata metadata) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(metadata, "metadata");
        if (state != State.RUNNING) {
            throw new IllegalStateException("run is not running");
        }
        OpenPhase open = new OpenPhase(phase, metadata.unitCount(), clock.monotonicNanos());
        openPhases.add(open);
        emit(header -> new RunEvent.PhaseStarted(header, phase, metadata));
        return new PhaseHandle(this, open);
    }

    /**
     * Executes {@code finish}.
     *
     * @param completion the {@code completion} argument
     */
    public void finish(RunCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        if (state == State.FINISHED) {
            throw new IllegalStateException("run already finished");
        }
        if (state == State.NEW) {
            throw new IllegalStateException("run was not started");
        }
        for (OpenPhase phase : List.copyOf(openPhases)) {
            phaseFinished(phase, PhaseStatus.CANCELLED);
        }
        state = State.FINISHED;
        emit(header -> new RunEvent.Finished(header, completion));
    }

    private void emit(java.util.function.Function<RunEvent.RunEventHeader, RunEvent> eventFactory) {
        RunEvent.RunEventHeader header = new RunEvent.RunEventHeader(
                context.runId(), context.parentRunId(), ++sequence, 1,
                clock.wallTimeEpochMillis(), clock.monotonicNanos());
        RunEvent event = eventFactory.apply(header);
        try {
            observer.onEvent(event);
        } catch (RuntimeException failure) {
            discardedEvents++;
            try {
                failureHandler.onObservationFailure(context.runId(), observer.getClass().getName(), failure);
            } catch (RuntimeException ignored) {
                // A failing fallback must not replace the factual result.
            }
        }
    }

    private void phaseFinished(OpenPhase phase, PhaseStatus status) {
        if (openPhases.remove(phase)) {
            long duration = Math.max(0, clock.monotonicNanos() - phase.startNanos);
            emit(header -> new RunEvent.PhaseFinished(header, phase.phase, status, duration,
                    phase.unitCount));
        }
    }

    /**
     * Executes {@code sequence}.
     *
     * @return the result
     */
    public int sequence() {
        return (int) Math.min(Integer.MAX_VALUE, sequence);
    }

    /**
     * Executes {@code discardedEvents}.
     *
     * @return the result
     */
    public int discardedEvents() {
        return discardedEvents;
    }

    public static final class PhaseHandle {
        private final RunLifecycle owner;
        private final OpenPhase phase;
        private boolean finished;

        private PhaseHandle(RunLifecycle owner, OpenPhase phase) {
            this.owner = owner;
            this.phase = phase;
        }

        /**
         * Executes {@code finish}.
         *
         * @param status the {@code status} argument
         */
        public void finish(PhaseStatus status) {
            if (finished) {
                throw new IllegalStateException("phase already finished");
            }
            finished = true;
            owner.phaseFinished(phase, status);
        }
    }

    private static final class OpenPhase {
        private final Phase phase;
        private final int unitCount;
        private final long startNanos;

        private OpenPhase(Phase phase, int unitCount, long startNanos) {
            this.phase = phase;
            this.unitCount = unitCount;
            this.startNanos = startNanos;
        }
    }
}
