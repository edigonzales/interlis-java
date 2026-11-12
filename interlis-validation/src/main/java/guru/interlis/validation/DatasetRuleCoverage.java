package guru.interlis.validation;

import java.util.Objects;

/** Aggregated executions of one rule with the same scope kind, completeness and outcome. A rule without an applicable scope can report zero scopes. */
public record DatasetRuleCoverage(RuleKey rule, ScopeKind scopeKind, Completeness completeness, Outcome outcome,
                                  long scopes, long candidates, long evaluations, long violations,
                                  long undecidedCandidates) {
    public DatasetRuleCoverage {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(scopeKind, "scopeKind");
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(outcome, "outcome");
        if (scopes < 0 || scopes == 0 && (outcome != Outcome.NOT_APPLICABLE
                || candidates != 0 || evaluations != 0 || violations != 0)
                || candidates < 0 || evaluations < 0 || violations < 0
                || undecidedCandidates < 0 || undecidedCandidates > candidates) {
            throw new IllegalArgumentException("Invalid dataset coverage counts");
        }
    }

    /** Kind of evaluated population. */
    public enum ScopeKind { BASKET, DATASET, DECLARED_EXTENT }

    /** Basis for treating the population as complete. */
    public enum Completeness { OPEN, CLOSED_BY_TRANSFER, DECLARED_COMPLETE }

    /** Conclusion about the rule in this population. */
    public enum Outcome {
        SATISFIED, VIOLATED, NO_VIOLATION_IN_AVAILABLE_SCOPE, UNDETERMINED, UNSUPPORTED, UNSUPPORTED_VALUE_SEMANTICS, NOT_APPLICABLE
    }
}
