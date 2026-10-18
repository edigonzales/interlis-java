package guru.interlis.compiler;

import java.util.Objects;

/** Public compiler factory; no repository, plugin scan or network access. */
public final class CompilerEngines {
    /**
     * Returns the {@code CompilerEngines} value.
     *
     * @return the value
     */
    private CompilerEngines() {
    }

    /**
     * Returns the {@code create} value.
     *
     * @return the value
     */
    public static CompilerEngine create() {
        return create(CompilerServices.defaults());
    }

    /**
     * Executes {@code create}.
     *
     * @param services the {@code services} argument
     *
     * @return the result
     */
    public static CompilerEngine create(CompilerServices services) {
        return new guru.interlis.compiler.internal.DefaultCompilerEngine(
                Objects.requireNonNull(services, "services"));
    }
}
