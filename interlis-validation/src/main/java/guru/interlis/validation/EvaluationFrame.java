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
                       java.util.function.BiFunction<guru.interlis.model.ObjectArgumentType, Boolean, RuntimeObjectSet> populations, Optional<ViewRow> row,
                       Optional<LocalGeometryContext> localGeometry) {
    EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit,
                    OptionalLong count, CancellationToken cancellation) {
        this(plan, current, partial, visit, count, cancellation, Set.of(), Optional.empty(), List.of(), (type, explicit) -> () -> count, Optional.empty(), Optional.empty());
    }

    guru.interlis.model.TypeDescriptor effectiveType(ObjectValidationPlan.ValueRules rules) {
        return rules.genericDomain().flatMap(generic -> Optional.ofNullable(domains().get(generic)))
                .flatMap(plan::domainType).orElse(rules.type());
    }

    EvaluationFrame withCurrent(TransferValue value) {
        return new EvaluationFrame(plan, value, partial, visit, objectCount, cancellation, activeDerivations, dataset, parents, populations, row, localGeometry);
    }

    EvaluationFrame descend(TransferValue.Structured parent, TransferValue value) {
        var ancestry = new ArrayList<>(parents);
        ancestry.add(parent);
        return new EvaluationFrame(plan, value, partial, visit, objectCount, cancellation,
                activeDerivations, dataset, ancestry, populations, row, localGeometry);
    }

    EvaluationFrame ascend() {
        if (parents.isEmpty()) {
            return this;
        }
        return new EvaluationFrame(plan, parents.getLast(), partial, visit, objectCount, cancellation,
                activeDerivations, dataset, parents.subList(0, parents.size() - 1), populations, row, localGeometry);
    }

    EvaluationFrame withDataset(DatasetPathContext context) {
        return new EvaluationFrame(plan, current, context.occurrence().partial(), visit, objectCount, cancellation,
                activeDerivations, Optional.of(context), List.of(), populations, row, localGeometry);
    }

    EvaluationFrame forBase(ViewRow.Base base) {
        var context = dataset.map(path -> new DatasetPathContext(path.dataset(), base.origin()));
        return new EvaluationFrame(plan, base.value(), base.origin().partial(), visit,
                base.row().map(ViewRow::groupSize).orElse(OptionalLong.empty()), cancellation,
                activeDerivations, context, base.parents(), populations, base.row(), localGeometry);
    }

    EvaluationFrame withPopulations(java.util.function.BiFunction<guru.interlis.model.ObjectArgumentType, Boolean, RuntimeObjectSet> sets) {
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation,
                activeDerivations, dataset, parents, sets, row, localGeometry);
    }

    EvaluationFrame withParents(List<TransferValue.Structured> ancestry) {
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation, activeDerivations,
                dataset, ancestry, populations, row, localGeometry);
    }

    EvaluationFrame withRow(ViewRow currentRow) {
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation, activeDerivations,
                dataset, parents, populations, Optional.of(currentRow), localGeometry);
    }

    EvaluationFrame withLocalGeometry(DataLocation location, java.util.Map<SymbolId, SymbolId> domains) {
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation, activeDerivations,
                dataset, parents, populations, row, Optional.of(new LocalGeometryContext(location, domains)));
    }

    java.util.Map<SymbolId, SymbolId> domains() {
        return dataset.map(DatasetPathContext::domains)
                .or(() -> localGeometry.map(LocalGeometryContext::domains)).orElseGet(java.util.Map::of);
    }

    DataLocation geometryLocation(List<DataLocation.Step> path) {
        if (dataset.isPresent()) {
            return dataset.orElseThrow().occurrence().location(path);
        }
        var root = localGeometry.map(LocalGeometryContext::location).orElseGet(() ->
                new DataLocation(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of()));
        var combined = new ArrayList<>(root.path());
        combined.addAll(path);
        return new DataLocation(root.source(), root.basket(), root.tid(), root.oid(), root.objectType(), combined);
    }

    record LocalGeometryContext(DataLocation location, java.util.Map<SymbolId, SymbolId> domains) { }

    boolean evaluating(SymbolId attribute) {
        return activeDerivations.stream().anyMatch(key -> key.attribute().equals(attribute)
                && key.occurrence().equals(dataset.map(path -> path.occurrence().handle()))
                && (dataset.isPresent() && parents.isEmpty() || key.value() == current));
    }

    record Derivation(SymbolId attribute, Optional<ObjectHandle> occurrence, TransferValue value) { }

    EvaluationFrame enterDerivation(SymbolId id) {
        var entered = new HashSet<>(activeDerivations);
        entered.add(new Derivation(id, dataset.map(path -> path.occurrence().handle()), current));
        return new EvaluationFrame(plan, current, partial, visit, objectCount, cancellation, entered, dataset, parents, populations, row, localGeometry);
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
