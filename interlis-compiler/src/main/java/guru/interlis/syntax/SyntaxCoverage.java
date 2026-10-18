package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Syntax processing coverage and explicit recovery/budget state.
 *
 * @param consumedEntireInput the {@code consumedEntireInput} component
 * @param lexicallyComplete the {@code lexicallyComplete} component
 * @param parseComplete the {@code parseComplete} component
 * @param recoveredRanges the {@code recoveredRanges} component
 * @param unsupportedFeatures the {@code unsupportedFeatures} component
 * @param budgetExceeded the {@code budgetExceeded} component
 * @param cancelled the {@code cancelled} component
 */
public record SyntaxCoverage(boolean consumedEntireInput, boolean lexicallyComplete,
                             boolean parseComplete, List<SourceRange> recoveredRanges,
                             Set<String> unsupportedFeatures, boolean budgetExceeded,
                             boolean cancelled) {
    /** Creates a validated instance. */
    public SyntaxCoverage {
        recoveredRanges = List.copyOf(Objects.requireNonNull(recoveredRanges, "recoveredRanges"));
        unsupportedFeatures = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(unsupportedFeatures, "unsupportedFeatures")));
    }

    /**
     * Executes {@code complete}.
     *
     * @return the result
     */
    public static SyntaxCoverage complete() {
        return new SyntaxCoverage(true, true, true, List.of(), Set.of(), false, false);
    }

    /**
     * Executes {@code failed}.
     *
     * @param consumed the {@code consumed} argument
     * @param budget the {@code budget} argument
     * @param cancelled the {@code cancelled} argument
     *
     * @return the result
     */
    public static SyntaxCoverage failed(boolean consumed, boolean budget, boolean cancelled) {
        return new SyntaxCoverage(consumed, false, false, List.of(), Set.of(), budget, cancelled);
    }

    /**
     * Executes {@code withUnsupported}.
     *
     * @param feature the {@code feature} argument
     *
     * @return the result
     */
    public SyntaxCoverage withUnsupported(String feature) {
        Set<String> features = new LinkedHashSet<>(unsupportedFeatures);
        features.add(Objects.requireNonNull(feature, "feature"));
        return new SyntaxCoverage(consumedEntireInput, lexicallyComplete, parseComplete,
                recoveredRanges, features, budgetExceeded, cancelled);
    }
}
