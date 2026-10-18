package guru.interlis.execution;

/** Bounded fallback for a failing optional observation sink. */
@FunctionalInterface
public interface ObservationFailureHandler {
    void onObservationFailure(RunId runId, String sinkType, RuntimeException failure);

    static ObservationFailureHandler ignore() {
        return (runId, sinkType, failure) -> {
        };
    }
}
