package guru.interlis.execution;

/** Optional observation sink. It has no IO contract and must not throw for control flow. */
@FunctionalInterface
public interface RunObserver {
    void onEvent(RunEvent event);

    static RunObserver noop() {
        return event -> {
        };
    }
}
