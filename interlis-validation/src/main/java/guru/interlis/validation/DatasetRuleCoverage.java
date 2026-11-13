package guru.interlis.validation;

import java.util.Objects;

/** Aggregated executions of one rule with the same scope kind and outcome. A rule without an applicable scope can report zero scopes. */
public record DatasetRuleCoverage(RuleKey rule, ScopeKind scopeKind, Outcome outcome,
                                  long scopes, long candidates, long evaluations, long violations,
                                  long undecidedCandidates) {
    public DatasetRuleCoverage {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(scopeKind, "scopeKind");
        Objects.requireNonNull(outcome, "outcome");
        if (scopes < 0 || scopes == 0 && (outcome != Outcome.NOT_APPLICABLE
                || candidates != 0 || evaluations != 0 || violations != 0)
                || candidates < 0 || evaluations < 0 || violations < 0
                || undecidedCandidates < 0 || undecidedCandidates > candidates) {
            throw new IllegalArgumentException("Invalid dataset coverage counts");
        }
    }

    /** Kind of evaluated population. */
    public enum ScopeKind { BASKET, DATASET }

    /** Conclusion about the rule in this population. */
    public enum Outcome {
        SATISFIED, VIOLATED, NO_VIOLATION_IN_AVAILABLE_SCOPE, UNDETERMINED, UNSUPPORTED, UNSUPPORTED_VALUE_SEMANTICS, NOT_APPLICABLE
    }
}
