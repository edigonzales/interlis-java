package guru.interlis.explorer;

import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.ComparisonOperator;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RelationDecision;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypeRef;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class IqlModel {
    record Role(ObjectDescriptor association, RoleSlot source, RoleSlot target) {
        boolean multiple() {
            return !(target.role().cardinality().upper() instanceof CardinalityUpper.Finite finite)
                    || finite.value().compareTo(BigInteger.ONE) > 0;
        }
    }

    private final TransferPlan plan;
    private final TypeRelations types;
    private final List<ObjectDescriptor> associations;

    IqlModel(TransferPlan plan) {
        this.plan = plan;
        types = new TypeRelations(plan.schema().semanticGraph());
        associations = plan.objects().stream().filter(type -> type.kind() == SymbolKind.ASSOCIATION).toList();
    }

    List<ObjectDescriptor> sources() {
        return plan.objects().stream().filter(type -> type.topic().isPresent()
                && (type.kind() == SymbolKind.CLASS || type.kind() == SymbolKind.ASSOCIATION)).toList();
    }

    List<ObjectDescriptor> matchingSources(String name) {
        return sources().stream().filter(type -> name.indexOf('.') < 0
                        ? type.name().declared().last().equals(name)
                        : type.name().declared().toString().equals(name))
                .toList();
    }

    static boolean validEnumerationValue(EnumerationType type, String name) {
        return type.values().stream().anyMatch(option -> option.path().equals(name))
                && (type.treeValues() || type.values().stream().noneMatch(option -> option.path().startsWith(name + ".")));
    }

    List<ObjectDescriptor> descendants(ObjectDescriptor base) {
        return plan.objects().stream().filter(type -> derives(type, base.symbol())).toList();
    }

    boolean derives(ObjectDescriptor type, SymbolRef base) {
        while (type != null) {
            if (type.symbol().equals(base)) return true;
            type = type.baseType().flatMap(plan::object).orElse(null);
        }
        return false;
    }

    Optional<AttributeSlot> attribute(ObjectDescriptor owner, String name) {
        return owner.slots().stream().filter(AttributeSlot.class::isInstance).map(AttributeSlot.class::cast)
                .filter(slot -> !slot.attribute().transientAttribute() && shortName(slot).equals(name)).findFirst();
    }

    List<Role> roles(ObjectDescriptor owner) {
        if (owner.kind() == SymbolKind.ASSOCIATION) {
            return roleSlots(owner).stream().map(slot -> new Role(owner, null, slot)).toList();
        }
        if (owner.kind() != SymbolKind.CLASS) return List.of();
        var result = new ArrayList<Role>();
        for (var association : associations) {
            var roles = roleSlots(association);
            for (var source : roles) {
                if (source.role().targetRoots().stream().noneMatch(root -> derives(owner, ref(root)))) continue;
                for (var target : roles) {
                    if (source.equals(target) || target.role().hiding()) continue;
                    result.add(new Role(association, source, target));
                }
            }
        }
        return result.stream().filter(candidate -> result.stream().noneMatch(base -> base != candidate
                && !base.association().equals(candidate.association())
                && derives(candidate.association(), base.association().symbol())
                && shortName(base.source()).equals(shortName(candidate.source()))
                && shortName(base.target()).equals(shortName(candidate.target())))).toList();
    }

    List<RoleSlot> roleSlots(ObjectDescriptor association) {
        return association.slots().stream().filter(RoleSlot.class::isInstance).map(RoleSlot.class::cast).toList();
    }

    Optional<ObjectDescriptor> commonTarget(List<SymbolId> roots) {
        if (roots.isEmpty()) return Optional.empty();
        List<ObjectDescriptor> types = roots.stream().map(this::ref).map(plan::object).flatMap(Optional::stream).toList();
        if (types.size() != roots.size()) return Optional.empty();
        ObjectDescriptor base = types.getFirst();
        while (base != null) {
            SymbolRef candidate = base.symbol();
            if (types.stream().allMatch(type -> derives(type, candidate))) return Optional.of(base);
            base = base.baseType().flatMap(plan::object).orElse(null);
        }
        return Optional.empty();
    }

    List<ObjectDescriptor> allowedTargets(List<SymbolId> roots) {
        return plan.objects().stream().filter(type -> roots.stream().anyMatch(root -> derives(type, ref(root)))).toList();
    }

    Optional<ObjectDescriptor> target(TypeDescriptor type) {
        return switch (type) {
            case StructureValueType structure -> commonTarget(structure.targetRoots());
            case ReferenceType reference -> commonTarget(reference.targetRoots());
            default -> Optional.empty();
        };
    }

    TypeDescriptor type(TypeId typeId) {
        return type(plan.type(new TypeRef(plan.snapshotId(), typeId)).known().orElseThrow());
    }

    TypeDescriptor type(AttributeSlot attribute) {
        return type(plan.type(attribute.effectiveType()).known().orElseThrow());
    }

    boolean relatedEnumerations(EnumerationType left, EnumerationType right) {
        return types.areComparable(new TypeRef(plan.snapshotId(), left.id()),
                        new TypeRef(plan.snapshotId(), right.id()), ComparisonOperator.EQUALITY)
                .known().filter(relation -> relation.decision() == RelationDecision.ALLOWED).isPresent();
    }

    boolean hasDeclaredUnitConversion(SymbolId left, SymbolId right) {
        return types.compareUnits(ref(left), ref(right)).known()
                .filter(relation -> relation.decision() == RelationDecision.ALLOWED).isPresent();
    }

    private TypeDescriptor type(TypeDescriptor type) {
        while (type instanceof DomainReferenceType reference) {
            var effective = plan.schema().queries().effectiveType(ref(reference.targetDomain())).known().orElseThrow();
            type = plan.type(new TypeRef(plan.snapshotId(), effective.effectiveType())).known().orElseThrow();
        }
        return type;
    }

    private SymbolRef ref(SymbolId symbol) { return new SymbolRef(plan.snapshotId(), symbol); }

    static String shortName(TransferSlot slot) { return slot.name().declared().segments().getLast(); }
}
