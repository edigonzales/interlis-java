package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferValue;
import guru.interlis.model.SymbolId;

import java.util.OptionalLong;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

record EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit,
               OptionalLong objectCount, CancellationToken cancellation, Set<Derivation> activeDerivations,
                       Optional<DatasetPathContext> dataset, List<TransferValue.Structured> parents,
                       java.util.function.BiFunction<guru.interlis.model.ObjectArgumentType, Boolean, RuntimeObjectSet> populations) {
    EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit,
                    OptionalLong count, CancellationToken cancellation) {
        this(plan, current, partial, visit, count, cancellation, Set.of(), Optional.empty(), List.of(), (type, explicit) -> () -> count);
    }

    guru.interlis.model.TypeDescriptor effectiveType(ObjectValidationPlan.ValueRules rules) {
        return rules.genericDomain().flatMap(generic -> dataset.flatMap(path -> Optional.ofNullable(path.domains().get(generic))))
                .flatMap(plan::domainType).orElse(rules.type());
    }

    EvaluationFrame withCurrent(TransferValue value) {
        return new EvaluationFrame(plan, value, partial, visit, objectCount, cancellation, activeDerivations, dataset, parents, populations);
    }

    EvaluationFrame descend(TransferValue.Structured parent, TransferValue value) {
        var ancestry = new ArrayList<>(parents);
        ancestry.add(parent);
        return new EvaluationFrame(plan, value, partial, visit, objectCount, cancellation,
                activeDerivations, dataset, ancestry, populations);
    }

    EvaluationFrame ascend() {
        if (parents.isEmpty()) {
            return this;
        }
        return new EvaluationFrame(plan, parents.getLast(), partial, visit, objectCount, cancellation,
                activeDerivations, dataset, parents.subList(0, parents.size() - 1), populations);
    }

    EvaluationFrame withDataset(DatasetPathContext context) {
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation,
                activeDerivations, Optional.of(context), List.of(), populations);
    }

    EvaluationFrame withPopulations(java.util.function.BiFunction<guru.interlis.model.ObjectArgumentType, Boolean, RuntimeObjectSet> sets) {
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation,
                activeDerivations, dataset, parents, sets);
    }

    boolean evaluating(SymbolId attribute) {
        return activeDerivations.stream().anyMatch(key -> key.attribute().equals(attribute)
                && key.occurrence().equals(dataset.map(path -> path.occurrence().handle()))
                && (dataset.isPresent() && parents.isEmpty() || key.value() == current));
    }

    record Derivation(SymbolId attribute, Optional<ObjectHandle> occurrence, TransferValue value) { }

    EvaluationFrame enterDerivation(SymbolId id) {
        var entered = new HashSet<>(activeDerivations);
        entered.add(new Derivation(id, dataset.map(path -> path.occurrence().handle()), current));
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation, entered, dataset, parents, populations);
    }

    EvaluationFrame {
        activeDerivations = Set.copyOf(activeDerivations);
        parents = List.copyOf(parents);
    }

    EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit,
                    OptionalLong count) {
        this(plan, current, partial, visit, count, CancellationToken.none());
    }

    EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit) {
        this(plan, current, partial, visit, OptionalLong.empty(), CancellationToken.none());
    }
}
