package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ExistenceConstraint;
import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.NumericType;
import guru.interlis.model.PathStep;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.transfer.RoleSlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Proves which payloads can be reduced before the complete dataset is available. */
final class DatasetRetention {
    private final Map<SymbolRef, Set<SymbolRef>> projections;
    private final Map<RuleKey, Set<SymbolRef>> projectedUnique;

    private DatasetRetention(Map<SymbolRef, Set<SymbolRef>> projections,
                             Map<RuleKey, Set<SymbolRef>> projectedUnique) {
        this.projections = Map.copyOf(projections);
        this.projectedUnique = Map.copyOf(projectedUnique);
    }

    static DatasetRetention compile(ValidationPlan plan) {
        if (plan.profile() != ValidationProfile.DATASET || !plan.views().isEmpty()
                || !plan.bindings().functions().isEmpty() || !plan.bindings().parameters().isEmpty()
                || plan.hasPopulationExpressions() || plan.datasetRules().stream().anyMatch(rule -> switch (
                        rule.declaration().definition()) {
                    case UniquenessConstraint ignored -> false;
                    case ExistenceConstraint ignored -> false;
                    default -> true;
                })) {
            return new DatasetRetention(Map.of(), Map.of());
        }
        var targets = plan.hasDeferredPaths() ? possiblePathTargets(plan) : List.<List<SymbolId>>of();
        var projections = new HashMap<SymbolRef, Set<SymbolRef>>();
        for (var descriptor : plan.transferPlan().objects()) {
            var object = plan.object(descriptor.symbol());
            if (object != null && local(plan, object) && targets.stream().noneMatch(
                    roots -> ModelValueRules.specializes(plan, descriptor, roots))) {
                projections.put(descriptor.symbol(), new HashSet<>());
            }
        }
        var projectedUnique = new HashMap<RuleKey, Set<SymbolRef>>();
        for (var rule : plan.datasetRules()) {
            if (rule.declaration().definition() instanceof ExistenceConstraint existence) {
                var targetsOfExistence = existence.targets().stream()
                        .map(target -> plan.canonical(target.viewable())).toList();
                projections.keySet().removeIf(type -> rule.candidateTypes().contains(type)
                        || !targetsOfExistence.isEmpty() && ModelValueRules.specializes(plan,
                                plan.transferPlan().object(type).orElseThrow(), targetsOfExistence));
                continue;
            }
            for (var type : rule.candidateTypes()) {
                if (!projections.containsKey(type)) {
                    continue;
                }
                var slot = uniqueSlot(plan, rule, plan.object(type));
                if (slot == null) {
                    projections.remove(type);
                } else {
                    projections.get(type).add(slot);
                    projectedUnique.computeIfAbsent(rule.key(), ignored -> new HashSet<>()).add(type);
                }
            }
        }
        projections.replaceAll((type, slots) -> Set.copyOf(slots));
        projectedUnique.replaceAll((rule, types) -> Set.copyOf(types));
        return new DatasetRetention(projections, projectedUnique);
    }

    /** Null means the full payload is needed. An empty set means the header suffices. */
    Set<SymbolRef> slots(SymbolRef type) {
        return projections.get(type);
    }

    boolean supportsUnique(RuleKey rule, SymbolRef type) {
        return projectedUnique.getOrDefault(rule, Set.of()).contains(type);
    }

    private static boolean local(ValidationPlan plan, ObjectValidationPlan object) {
        var descriptor = object.descriptor();
        return (descriptor.kind() == SymbolKind.CLASS || descriptor.kind() == SymbolKind.STRUCTURE)
                && descriptor.slots().stream().noneMatch(RoleSlot.class::isInstance)
                && !plan.objectMayHaveDeferredChecks(descriptor.symbol())
                && object.fields().stream().noneMatch(field -> field.derived() || field.values().stream()
                        .anyMatch(value -> value.genericDomain().isPresent()
                                || !localType(value.type())));
    }

    private static boolean localType(guru.interlis.model.TypeDescriptor type) {
        return switch (type.kind()) {
            case TEXT, BOOLEAN, ALIGNMENT, NUMERIC, ENUMERATION, COORDINATE, BLACKBOX, OID,
                    FORMATTED, CLASS_VALUE, ATTRIBUTE_PATH, COLLECTION -> true;
            case STRUCTURE_VALUE -> !((StructureValueType) type).targetRoots().isEmpty();
            case GEOMETRY -> {
                var geometry = (GeometryType) type;
                yield geometry.lineAttributes().isEmpty() && geometry.geometryKind() != GeometryKind.AREA
                        && geometry.geometryKind() != GeometryKind.MULTIAREA;
            }
            default -> false;
        };
    }

    private static List<List<SymbolId>> possiblePathTargets(ValidationPlan plan) {
        var targets = new HashSet<List<SymbolId>>();
        // Paths can bind to signatures on abstract bases rather than a concrete transfer slot.
        for (var symbol : plan.transferPlan().schema().semanticGraph().symbols()) {
            if (symbol instanceof RoleSymbol role) {
                targets.add(role.targetRoots());
            } else if (symbol instanceof AttributeSymbol attribute) {
                var type = plan.effectiveType(attribute.declaredType()).orElse(null);
                while (type instanceof CollectionType collection) {
                    type = plan.effectiveType(collection.elementType()).orElse(null);
                }
                if (type instanceof ReferenceType reference) {
                    targets.add(reference.targetRoots());
                }
            }
        }
        return List.copyOf(targets);
    }

    private static SymbolRef uniqueSlot(ValidationPlan plan, DatasetRulePlan rule, ObjectValidationPlan object) {
        var definition = (UniquenessConstraint) rule.declaration().definition();
        if (object.descriptor().kind() != SymbolKind.CLASS || definition.basket() || definition.where().isPresent()
                || !definition.localKeys().isEmpty() || definition.keys().size() != 1) {
            return null;
        }
        var expression = definition.keys().getFirst();
        var path = plan.boundPath(expression);
        if (path.isEmpty() || plan.datasetExpression(expression).isEmpty()) {
            return null;
        }
        var steps = path.orElseThrow().path().steps();
        boolean direct = steps.size() == 1 || steps.size() == 2 && steps.getFirst() instanceof PathStep.ThisStep;
        if (!direct || !(steps.getLast() instanceof PathStep.AttributeStep attribute)) {
            return null;
        }
        var field = plan.field(object, attribute.target());
        if (field == null || field.derived() || field.values().size() != 1
                || field.values().getFirst().genericDomain().isPresent()
                || !(field.values().getFirst().type() instanceof NumericType)) {
            return null;
        }
        return field.slot().symbol();
    }
}
