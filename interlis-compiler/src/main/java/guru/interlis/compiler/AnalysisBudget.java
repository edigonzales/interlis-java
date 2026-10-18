package guru.interlis.compiler;

/**
 * Conservative resource limits. They are not language rules and never prove a
 * model invalid; exceeding a limit is a controlled resource cancellation.
 *
 * @param maxSourceUtf16Length the {@code maxSourceUtf16Length} component
 * @param maxTotalSourceUtf16Length the {@code maxTotalSourceUtf16Length} component
 * @param maxTokens the {@code maxTokens} component
 * @param maxSyntaxNodes the {@code maxSyntaxNodes} component
 * @param maxRecoveryEvents the {@code maxRecoveryEvents} component
 * @param maxParseRuleDepth the {@code maxParseRuleDepth} component
 * @param maxSemanticWorkUnits the {@code maxSemanticWorkUnits} component
 * @param maxDiagnostics the {@code maxDiagnostics} component
 * @param maxNumericLiteralDigits the {@code maxNumericLiteralDigits} component
 * @param maxAbsoluteDecimalExponent the {@code maxAbsoluteDecimalExponent} component
 */
public record AnalysisBudget(int maxSourceUtf16Length, long maxTotalSourceUtf16Length,
                             long maxTokens, long maxSyntaxNodes, int maxRecoveryEvents,
                             int maxParseRuleDepth, long maxSemanticWorkUnits,
                             int maxDiagnostics, int maxNumericLiteralDigits,
                             int maxAbsoluteDecimalExponent) {
    /** Creates a validated instance. */
    public AnalysisBudget {
        if (maxSourceUtf16Length <= 0 || maxTotalSourceUtf16Length <= 0
                || maxTokens <= 0 || maxSyntaxNodes <= 0 || maxRecoveryEvents <= 0
                || maxParseRuleDepth <= 0 || maxSemanticWorkUnits <= 0
                || maxDiagnostics <= 0 || maxNumericLiteralDigits <= 0
                || maxAbsoluteDecimalExponent <= 0) {
            throw new IllegalArgumentException("all analysis limits must be positive");
        }
        if (maxTotalSourceUtf16Length < maxSourceUtf16Length) {
            throw new IllegalArgumentException("total source limit must allow one source");
        }
    }

    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static AnalysisBudget defaults() {
        return new AnalysisBudget(2_097_152, 8_388_608L, 500_000L, 750_000L,
                10_000, 256, 10_000_000L, 1_000, 4_096, 100_000);
    }

    /**
     * Executes {@code withMaxDiagnostics}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public AnalysisBudget withMaxDiagnostics(int value) {
        return new AnalysisBudget(maxSourceUtf16Length, maxTotalSourceUtf16Length, maxTokens,
                maxSyntaxNodes, maxRecoveryEvents, maxParseRuleDepth, maxSemanticWorkUnits,
                value, maxNumericLiteralDigits, maxAbsoluteDecimalExponent);
    }

    /**
     * Executes {@code withMaxTokens}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public AnalysisBudget withMaxTokens(long value) {
        return new AnalysisBudget(maxSourceUtf16Length, maxTotalSourceUtf16Length, value,
                maxSyntaxNodes, maxRecoveryEvents, maxParseRuleDepth, maxSemanticWorkUnits,
                maxDiagnostics, maxNumericLiteralDigits, maxAbsoluteDecimalExponent);
    }

    /**
     * Executes {@code withMaxSemanticWorkUnits}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public AnalysisBudget withMaxSemanticWorkUnits(long value) {
        return new AnalysisBudget(maxSourceUtf16Length, maxTotalSourceUtf16Length, maxTokens,
                maxSyntaxNodes, maxRecoveryEvents, maxParseRuleDepth, value,
                maxDiagnostics, maxNumericLiteralDigits, maxAbsoluteDecimalExponent);
    }

    /**
     * Executes {@code withMaxSourceUtf16Length}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public AnalysisBudget withMaxSourceUtf16Length(int value) {
        return new AnalysisBudget(value, maxTotalSourceUtf16Length, maxTokens,
                maxSyntaxNodes, maxRecoveryEvents, maxParseRuleDepth, maxSemanticWorkUnits,
                maxDiagnostics, maxNumericLiteralDigits, maxAbsoluteDecimalExponent);
    }
}
