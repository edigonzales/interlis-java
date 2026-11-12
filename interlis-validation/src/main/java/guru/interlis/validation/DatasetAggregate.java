package guru.interlis.validation;

import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.SetConstraint;
import guru.interlis.transfer.TransferValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalLong;

import static guru.interlis.validation.DatasetRuleCoverage.Completeness.OPEN;
import static guru.interlis.validation.DatasetRuleCoverage.Outcome.*;
import static guru.interlis.validation.DatasetRuleCoverage.ScopeKind.*;

final class DatasetAggregate {
    private final DatasetRuleContext context;
    private final ValidationSession report;
    private final DatasetRulePlan rule;

    DatasetAggregate(DatasetRuleContext context, ValidationSession report, DatasetRulePlan rule) {
        this.context = context;
        this.report = report;
        this.rule = rule;
    }

    List<DatasetRuleCoverage> check(SetConstraint set) {
        boolean basket = set.scope() == SetConstraint.SetConstraintScope.BASKET;
        var populations = populations(basket, set.where().isPresent());
        if (populations.isEmpty()) {
            return List.of(empty(basket));
        }
        var where = set.where().flatMap(context::program);
        var condition = context.program(set.condition());
        boolean supported = where.isPresent() == set.where().isPresent() && condition.isPresent();
        context.forObjects(occurrence -> {
            var population = applicable(populations, basket, occurrence);
            if (population == null) {
                return;
            }
            if (!supported) {
                population.undecided++;
            } else if (where.isEmpty()) {
                population.included++;
            } else {
                switch (context.evaluate(where.orElseThrow(), occurrence)) {
                    case LocalExpressions.Known known when known.value() instanceof TransferValue.BooleanValue value -> {
                        if (value.value()) {
                            population.included++;
                        }
                    }
                    case LocalExpressions.UndefinedByData ignored -> { }
                    default -> population.undecided++;
                }
            }
        });
        var result = new ArrayList<DatasetRuleCoverage>();
        for (var population : populations.values()) {
            context.visit();
            result.add(supported ? evaluateSet(population, condition.orElseThrow())
                    : population.coverage(UNSUPPORTED, 0, 0));
        }
        return DatasetScopeCounts.aggregate(result);
    }

    List<DatasetRuleCoverage> check(PlausibilityConstraint plausibility) {
        var populations = populations(false, true);
        if (populations.isEmpty()) {
            return List.of(empty(false));
        }
        var condition = context.program(plausibility.condition());
        context.forObjects(occurrence -> {
            var population = applicable(populations, false, occurrence);
            if (population == null) {
                return;
            }
            population.included++;
            if (condition.isEmpty()) {
                population.undecided++;
                return;
            }
            switch (context.evaluate(condition.orElseThrow(), occurrence)) {
                case LocalExpressions.Known known when known.value() instanceof TransferValue.BooleanValue value -> {
                    if (value.value()) {
                        population.satisfied++;
                    }
                }
                case LocalExpressions.UndefinedByData ignored -> population.satisfied++;
                default -> population.undecided++;
            }
        });
        var population = populations.get(0L);
        return List.of(condition.isPresent() ? evaluatePercentage(population, plausibility)
                : population.coverage(UNSUPPORTED, 0, 0));
    }

    private DatasetRuleCoverage evaluateSet(Population population, LocalExpressions.Program condition) {
        var count = population.complete() ? OptionalLong.of(population.included) : OptionalLong.empty();
        var result = condition.evaluate(new LocalExpressions.Context(context.plan, TransferValue.Undefined.INSTANCE,
                !population.complete(), context::visit, count));
        if (result instanceof LocalExpressions.Known known && known.value() instanceof TransferValue.BooleanValue value) {
            if (!value.value()) {
                report.datasetViolation(rule.declaration(), ValidationCode.SET_CONSTRAINT_FALSE,
                        population.scope.location(), count.isPresent() ? List.of(Long.toString(count.getAsLong())) : List.of());
                return population.coverage(VIOLATED, 1, 1);
            }
            return population.coverage(SATISFIED, 1, 0);
        }
        if (result == LocalExpressions.Unsupported.INSTANCE) {
            return population.coverage(UNSUPPORTED, 0, 0);
        }
        return population.coverage(UNDETERMINED, 0, 0);
    }

    private DatasetRuleCoverage evaluatePercentage(Population population, PlausibilityConstraint ruleDefinition) {
        if (!population.complete()) {
            return population.coverage(UNDETERMINED, 0, 0);
        }
        if (population.included == 0) {
            return population.coverage(NOT_APPLICABLE, 0, 0);
        }
        ScalarRules.requireNumericBudget(ruleDefinition.percentage());
        var numerator = BigDecimal.valueOf(population.satisfied).multiply(BigDecimal.valueOf(100));
        var required = ruleDefinition.percentage().multiply(BigDecimal.valueOf(population.included));
        int comparison = numerator.compareTo(required);
        boolean satisfied = switch (ruleDefinition.direction()) {
            case GTE -> comparison >= 0;
            case LTE -> comparison <= 0;
            default -> throw new IllegalStateException("Unsupported plausibility direction");
        };
        if (!satisfied) {
            report.datasetViolation(rule.declaration(), ValidationCode.PLAUSIBILITY_CONSTRAINT_FALSE,
                    population.scope.location(), List.of(Long.toString(population.satisfied),
                            Long.toString(population.included), ruleDefinition.direction().name(),
                            ruleDefinition.percentage().toPlainString()));
        }
        return population.coverage(satisfied ? SATISFIED : VIOLATED, 1, satisfied ? 0 : 1);
    }

    private LinkedHashMap<Long, Population> populations(boolean basket, boolean requiresValues) {
        var populations = new LinkedHashMap<Long, Population>();
        for (var scope : context.aggregateScopes(rule, basket, requiresValues)) {
            populations.put(scope.ordinal(), new Population(scope));
        }
        return populations;
    }

    private Population applicable(LinkedHashMap<Long, Population> populations, boolean basket,
                                  DatasetRuleContext.Occurrence occurrence) {
        return occurrence.input().descriptor().role() == InputRole.PRIMARY && context.candidate(rule, occurrence)
                ? populations.get(basket ? occurrence.stored().basket() : 0L) : null;
    }

    private DatasetRuleCoverage empty(boolean basket) {
        return new DatasetRuleCoverage(rule.key(), basket ? BASKET : DATASET, OPEN, NOT_APPLICABLE,
                0, 0, 0, 0, 0);
    }

    private final class Population {
        private final DatasetRuleContext.AggregateScope scope;
        private long included;
        private long satisfied;
        private long undecided;

        Population(DatasetRuleContext.AggregateScope scope) {
            this.scope = scope;
        }

        boolean complete() {
            return scope.completeness() != OPEN && undecided == 0;
        }

        DatasetRuleCoverage coverage(DatasetRuleCoverage.Outcome outcome, long evaluations, long violations) {
            boolean set = rule.declaration().definition() instanceof SetConstraint;
            long candidates = set ? Math.addExact(included, undecided) : included;
            return new DatasetRuleCoverage(rule.key(), scope.kind(), scope.completeness(), outcome,
                    1, candidates, evaluations, violations, undecided);
        }
    }
}
