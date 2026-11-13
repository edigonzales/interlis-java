package guru.interlis.validation;

import guru.interlis.model.SnapshotId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Ordered, snapshot-bound selection of class or topic hierarchies to validate. */
public record SubjectSelection(SnapshotId snapshot, List<SymbolRef> roots) {
    public SubjectSelection {
        Objects.requireNonNull(snapshot, "snapshot");
        roots = List.copyOf(new LinkedHashSet<>(roots));
        for (var root : roots) {
            if (!snapshot.equals(root.snapshot())) {
                throw new IllegalArgumentException("Subject belongs to another snapshot");
            }
        }
    }

    /** Selects all topic hierarchies declared by the compilation roots. */
    public static SubjectSelection fromRoots(ValidationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new SubjectSelection(plan.snapshotId(), plan.rootModels().stream()
                .flatMap(model -> plan.transferPlan().model(model).orElseThrow().topics().stream()).toList());
    }

    boolean selects(ValidationPlan plan, InputRole role, SymbolRef topic, SymbolRef type) {
        if (role != InputRole.PRIMARY) {
            return false;
        }
        var currentTopic = plan.transferPlan().topic(topic);
        while (currentTopic.isPresent()) {
            var descriptor = currentTopic.orElseThrow();
            if (roots.contains(descriptor.symbol())) {
                return true;
            }
            currentTopic = descriptor.baseTopic().flatMap(plan.transferPlan()::topic);
        }
        var currentType = plan.transferPlan().object(type);
        while (currentType.isPresent()) {
            var descriptor = currentType.orElseThrow();
            if (roots.contains(descriptor.symbol())) {
                return true;
            }
            currentType = descriptor.baseType().flatMap(plan.transferPlan()::object);
        }
        return false;
    }

    boolean selectsView(ValidationPlan plan, guru.interlis.model.ViewSymbol view) {
        if (roots.contains(plan.ref(view.id()))) {
            return true;
        }
        var topic = view.owner().map(plan::ref).flatMap(plan.transferPlan()::topic);
        while (topic.isPresent()) {
            var current = topic.orElseThrow();
            if (roots.contains(current.symbol())) {
                return true;
            }
            topic = current.baseTopic().flatMap(plan.transferPlan()::topic);
        }
        return false;
    }

    void check(ValidationPlan plan) {
        if (!snapshot.equals(plan.snapshotId())) {
            throw new IllegalArgumentException("Subjects belong to another snapshot");
        }
        for (var root : roots) {
            if (plan.transferPlan().topic(root).isEmpty()
                    && plan.transferPlan().object(root).filter(type -> type.kind() == SymbolKind.CLASS).isEmpty()
                    && !plan.views().containsKey(root.symbol())) {
                throw new IllegalArgumentException("Subject must select a class, topic or view in the plan: " + root);
            }
        }
    }
}
