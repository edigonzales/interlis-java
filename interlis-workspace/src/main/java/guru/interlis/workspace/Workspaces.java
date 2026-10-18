package guru.interlis.workspace;

import guru.interlis.compiler.CompilerEngine;

import java.util.Objects;

/** Public workspace factory; creates an empty workspace without IO. */
public final class Workspaces {
    /**
     * Returns the {@code Workspaces} value.
     *
     * @return the value
     */
    private Workspaces() {
    }

    /**
     * Executes {@code create}.
     *
     * @param engine the {@code engine} argument
     * @param configuration the {@code configuration} argument
     *
     * @return the result
     */
    public static Workspace create(CompilerEngine engine, WorkspaceConfiguration configuration) {
        return new DefaultWorkspace(Objects.requireNonNull(engine, "engine"),
                Objects.requireNonNull(configuration, "configuration"));
    }
}
