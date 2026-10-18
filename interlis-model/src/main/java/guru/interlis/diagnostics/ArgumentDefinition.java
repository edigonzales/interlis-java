package guru.interlis.diagnostics;

import java.util.Objects;

/** Catalog definition of one argument. */
public record ArgumentDefinition(String name, ArgumentKind kind) {
    /** Creates a validated instance. */
    public ArgumentDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
    }
}
