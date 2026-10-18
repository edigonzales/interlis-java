package guru.interlis.compiler.query;

import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether a rename can be safely offered at the cursor.
 *
 * @param allowed the {@code allowed} component
 * @param range source range
 * @param reason the {@code reason} component
 */
public record RenamePreparation(boolean allowed, Optional<SourceRange> range,
                                Optional<String> reason) {
    /** Creates a validated instance. */
    public RenamePreparation {
        range = Objects.requireNonNull(range, "range");
        reason = Objects.requireNonNull(reason, "reason");
        if (allowed && range.isEmpty()) {
            throw new IllegalArgumentException("allowed rename needs an editable range");
        }
    }

    /**
     * Executes {@code allow}.
     *
     * @param range the {@code range} argument
     *
     * @return the result
     */
    public static RenamePreparation allow(SourceRange range) {
        return new RenamePreparation(true, Optional.of(range), Optional.empty());
    }

    /**
     * Executes {@code reject}.
     *
     * @param reason the {@code reason} argument
     *
     * @return the result
     */
    public static RenamePreparation reject(String reason) {
        return new RenamePreparation(false, Optional.empty(), Optional.of(reason));
    }
}
