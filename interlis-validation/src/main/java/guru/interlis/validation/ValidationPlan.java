package guru.interlis.validation;

import guru.interlis.model.SchemaFingerprint;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferPlan;
import java.util.List;
import java.util.Map;

/** Immutable and shareable rules for one checked schema revision. */
public final class ValidationPlan {
    private final TransferPlan transferPlan;
    private final Map<SymbolRef, ObjectValidationPlan> objects;
    private final List<UncheckedRuleInfo> uncheckedRules;

    ValidationPlan(TransferPlan transferPlan, Map<SymbolRef, ObjectValidationPlan> objects,
                   List<UncheckedRuleInfo> uncheckedRules) {
        this.transferPlan = transferPlan;
        this.objects = Map.copyOf(objects);
        this.uncheckedRules = List.copyOf(uncheckedRules);
    }

    /** Identifies the snapshot that owns all plan references. */
    public SnapshotId snapshotId() {
        return transferPlan.snapshotId();
    }

    /** Identifies the checked schema used to build the plan. */
    public SchemaFingerprint schemaFingerprint() {
        return transferPlan.schemaFingerprint();
    }

    /** Immutable list in object, effective slot and constraint declaration order. */
    public List<UncheckedRuleInfo> uncheckedRules() {
        return uncheckedRules;
    }

    TransferPlan transferPlan() {
        return transferPlan;
    }

    ObjectValidationPlan object(SymbolRef type) {
        return objects.get(type);
    }
}
