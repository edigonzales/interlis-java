package guru.interlis.compiler;

import guru.interlis.source.SourceId;
import guru.interlis.syntax.SyntaxSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Optional, fingerprint-checked reuse input; never accepted as truth.
 *
 * @param previousAnalysis the {@code previousAnalysis} component
 * @param previousSyntax the {@code previousSyntax} component
 */
public record AnalysisReuse(Optional<AnalysisSnapshot> previousAnalysis,
                            Map<SourceId, SyntaxSnapshot> previousSyntax) {
    /** Creates a validated instance. */
    public AnalysisReuse {
        previousAnalysis = Objects.requireNonNull(previousAnalysis, "previousAnalysis");
        previousSyntax = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(previousSyntax, "previousSyntax")));
    }

    /**
     * Returns the {@code none} value.
     *
     * @return the value
     */
    public static AnalysisReuse none() {
        return new AnalysisReuse(Optional.empty(), Map.of());
    }
}
