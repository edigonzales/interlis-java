package guru.interlis.execution;

import java.util.Map;
import java.util.Objects;

final class DefaultRunContextFactory implements RunContextFactory {
    private static long namespaces;
    private final String namespace;
    private long counter;

    DefaultRunContextFactory() {
        synchronized (DefaultRunContextFactory.class) {
            namespace = "ctx-" + (++namespaces);
        }
    }

    @Override
    public RunContext createRoot() {
        return createRoot(Map.of());
    }

    @Override
    public RunContext createRoot(Map<String, String> correlationAttributes) {
        return new RunContext(nextId(), java.util.Optional.empty(),
                Objects.requireNonNull(correlationAttributes, "correlationAttributes"));
    }

    @Override
    public RunContext createChild(RunContext parent) {
        Objects.requireNonNull(parent, "parent");
        return new RunContext(nextId(), java.util.Optional.of(parent.runId()),
                parent.correlationAttributes());
    }

    private synchronized RunId nextId() {
        long value = ++counter;
        if (value <= 0) {
            throw new IllegalStateException("run id counter exhausted");
        }
        return new RunId(namespace + "-" + value);
    }
}
