package guru.interlis.compiler;

import guru.interlis.model.LanguageVersion;

import java.util.Objects;
import java.util.Set;

/**
 * Per-request semantic options.
 *
 * @param semanticProfile the {@code semanticProfile} component
 * @param allowedLanguages the {@code allowedLanguages} component
 * @param warningPolicy the {@code warningPolicy} component
 * @param budget the {@code budget} component
 * @param overlays the {@code overlays} component
 */
public record CompilerOptions(SemanticProfileId semanticProfile,
                              Set<LanguageVersion> allowedLanguages,
                              WarningPolicy warningPolicy,
                              AnalysisBudget budget,
                              SemanticOverlays overlays) {
    /** Creates a validated instance. */
    public CompilerOptions {
        Objects.requireNonNull(semanticProfile, "semanticProfile");
        allowedLanguages = Set.copyOf(Objects.requireNonNull(allowedLanguages, "allowedLanguages"));
        if (allowedLanguages.isEmpty()) {
            throw new IllegalArgumentException("at least one language version must be allowed");
        }
        Objects.requireNonNull(warningPolicy, "warningPolicy");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(overlays, "overlays");
    }

    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static CompilerOptions defaults() {
        return new CompilerOptions(SemanticProfileId.ILI_23_24_V1,
                Set.of(LanguageVersion.ILI23, LanguageVersion.ILI24),
                WarningPolicy.ALLOW_WARNINGS, AnalysisBudget.defaults(),
                SemanticOverlays.empty());
    }

    /**
     * Returns the {@code fingerprint} value.
     *
     * @return the value
     */
    public String fingerprint() {
        return "profile=" + semanticProfile.value()
                + ";languages=" + allowedLanguages.stream().map(LanguageVersion::literal).sorted()
                .toList()
                + ";warnings=" + warningPolicy
                + ";budget=" + budget
                + ";overlays=" + overlays.fingerprint();
    }
}
