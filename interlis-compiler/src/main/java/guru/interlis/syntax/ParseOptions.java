package guru.interlis.syntax;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.model.LanguageVersion;

import java.util.Objects;
import java.util.Optional;

/**
 * Per-parse options; no clock, thread pool or global timeout.
 *
 * @param mode the {@code mode} component
 * @param expectedVersion the {@code expectedVersion} component
 * @param budget the {@code budget} component
 */
public record ParseOptions(ParseMode mode, Optional<LanguageVersion> expectedVersion,
                           AnalysisBudget budget) {
    /** Creates a validated instance. */
    public ParseOptions {
        Objects.requireNonNull(mode, "mode");
        expectedVersion = Objects.requireNonNull(expectedVersion, "expectedVersion");
        Objects.requireNonNull(budget, "budget");
    }

    /**
     * Executes {@code defaults}.
     *
     * @param mode the {@code mode} argument
     *
     * @return the result
     */
    public static ParseOptions defaults(ParseMode mode) {
        return new ParseOptions(mode, Optional.empty(), AnalysisBudget.defaults());
    }
}
