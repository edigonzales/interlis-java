package guru.interlis.workspace;

import guru.interlis.source.SourceId;

import java.util.Objects;

/**
 * Conditional change of exactly one source.
 *
 * @param source source identifier
 * @param expectation the {@code expectation} component
 * @param change the {@code change} component
 */
public record SourceUpdate(SourceId source, SourceExpectation expectation, SourceChange change) {
    /** Creates a validated instance. */
    public SourceUpdate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expectation, "expectation");
        Objects.requireNonNull(change, "change");
    }
}
