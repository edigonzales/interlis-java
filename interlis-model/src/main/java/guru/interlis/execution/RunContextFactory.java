package guru.interlis.execution;

import java.util.Map;

/** Factory for fresh immutable run metadata. It never starts a run. */
public interface RunContextFactory {
    RunContext createRoot();

    RunContext createRoot(Map<String, String> correlationAttributes);

    RunContext createChild(RunContext parent);

    static RunContextFactory defaults() {
        return new DefaultRunContextFactory();
    }
}
