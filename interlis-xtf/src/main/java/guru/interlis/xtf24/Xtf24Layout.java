package guru.interlis.xtf24;

import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;

import javax.xml.namespace.QName;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Derives the XTF 2.4 wire order and association placement from the neutral plan. */
final class Xtf24Layout {
    record Embedded(ObjectDescriptor association, RoleSlot owner, RoleSlot opposite, RoleSlot rootOpposite) { }

    record Field(TransferSlot slot, QName name,
                 List<Embedded> embeddings, int inheritanceLevel) {
        Field {
            embeddings = List.copyOf(embeddings);
        }

        private int group() {
            return embeddings.isEmpty() ? (slot instanceof RoleSlot ? 0 : 1) : 2;
        }
    }

    private static final Comparator<Field> WIRE_ORDER = Comparator.comparingInt(Field::inheritanceLevel)
            .thenComparingInt(Field::group)
            .thenComparing(field -> field.embeddings().isEmpty() ? "" : field.name().getLocalPart())
            .thenComparingInt(field -> field.slot().member().position());

    private final TransferPlan plan;
    private final TypeRelations types;
    private final Xtf24Names names;
    private final Map<SymbolRef, Embedded> embedded;

    Xtf24Layout(TransferPlan plan) {
        this.plan = plan;
        this.types = new TypeRelations(plan.schema().semanticGraph());
        names = new Xtf24Names(plan);
        var result = new HashMap<SymbolRef, Embedded>();
        for (var descriptor : plan.objects()) {
            if (descriptor.kind() == SymbolKind.ASSOCIATION && descriptor.transferable()) {
                placement(descriptor).ifPresent(value -> result.put(descriptor.symbol(), value));
            }
        }
        embedded = Map.copyOf(result);
    }

    Xtf24Names names() {
        return names;
    }

    Optional<Embedded> embedded(ObjectDescriptor descriptor) {
        return Optional.ofNullable(embedded.get(descriptor.symbol()));
    }

    boolean stableTargets(List<SymbolId> roots) {
        return !roots.isEmpty() && roots.stream().allMatch(id -> plan.object(new SymbolRef(plan.snapshotId(), id))
                .filter(this::identified).isPresent());
    }

    boolean hasTid(ObjectDescriptor object) {
        return object.kind() == SymbolKind.CLASS || identified(object);
    }

    boolean identified(ObjectDescriptor association) {
        if (!association.oidRules().isEmpty()) {
            return association.oidRules().stream().anyMatch(rule -> !rule.basket() && rule.domain().isPresent());
        }
        return ancestors(association).stream().anyMatch(descriptor -> descriptor.modifiers().contains(Modifier.OID));
    }

    List<Field> fields(ObjectDescriptor descriptor) {
        return fields(descriptor, Set.of(), false);
    }

    List<Field> fields(ObjectDescriptor descriptor, Set<SymbolRef> activeTypes, boolean payloadOnly) {
        var inheritance = ancestors(descriptor);
        var levels = new HashMap<Integer, Integer>();
        for (int i = 0; i < inheritance.size(); i++) {
            for (var slot : inheritance.get(i).slots()) {
                levels.putIfAbsent(slot.member().position(), i);
            }
        }
        var fields = new ArrayList<Field>();
        for (var slot : descriptor.slots()) {
            if (slot instanceof AttributeSlot attribute && !attribute.attribute().transientAttribute()
                    || slot instanceof RoleSlot && !payloadOnly) {
                fields.add(new Field(slot, names.slotName(slot), List.of(), levels.get(slot.member().position())));
            }
        }
        if (!payloadOnly) {
            addEmbedded(fields, descriptor, activeTypes, inheritance);
        }
        fields.sort(WIRE_ORDER);
        return List.copyOf(fields);
    }

    private void addEmbedded(List<Field> fields, ObjectDescriptor descriptor, Set<SymbolRef> activeTypes,
                             List<ObjectDescriptor> inheritance) {
        if (descriptor.kind() == SymbolKind.STRUCTURE) {
            return;
        }
        var byName = new HashMap<QName, List<Embedded>>();
        for (var entry : embedded.entrySet()) {
            var value = entry.getValue();
            if (activeTypes.contains(entry.getKey()) && matchesRoots(descriptor, value.owner().role().targetRoots())) {
                byName.computeIfAbsent(names.slotName(value.rootOpposite()), unused -> new ArrayList<>()).add(value);
            }
        }
        for (var variants : byName.values()) {
            // Resolve concrete variants in a stable order even if the plan was indexed in a hash map.
            variants.sort(Comparator.comparing(value -> value.association().name().declared().toString()));
            var rootRole = variants.getFirst().rootOpposite();
            int level = inheritance.size() - 1;
            for (var variant : variants) {
                var targets = variant.owner().role().targets().stream().map(target -> target.base()).toList();
                for (int i = 0; i < level; i++) {
                    if (targets.contains(inheritance.get(i).symbol().symbol())) {
                        level = i;
                        break;
                    }
                }
            }
            fields.add(new Field(rootRole, names.slotName(rootRole), variants, level));
        }
    }

    boolean matchesRoots(ObjectDescriptor descriptor, List<SymbolId> allowed) {
        return allowed.isEmpty() || types.objectLineage(descriptor.symbol()).stream()
                .anyMatch(base -> allowed.contains(base.symbol()));
    }

    ObjectDescriptor structure(QName name, Set<SymbolRef> availableTypes, StructureValueType type,
                               SymbolRef containingModel) {
        var candidates = names.objectCandidates(name, availableTypes);
        if (candidates.size() > 1) {
            // Bound roots can distinguish translations of the same wire name.
            // Keep decodable values when none of those translations is assignable.
            var roots = type.targetRoots();
            var matching = candidates.stream().filter(candidate -> matchesRoots(candidate, roots)).toList();
            if (!matching.isEmpty()) {
                candidates = matching;
            }
        }
        if (candidates.size() > 1) {
            var local = candidates.stream().filter(candidate -> candidate.model().equals(containingModel)).toList();
            if (!local.isEmpty()) {
                candidates = local;
            }
        }
        if (candidates.size() > 1) {
            var imports = plan.modelImportClosure(Set.of(containingModel));
            var imported = candidates.stream().filter(candidate -> imports.contains(candidate.model())).toList();
            if (!imported.isEmpty()) {
                candidates = imported;
            }
        }
        if (candidates.size() > 1) {
            var original = candidates.stream()
                    .filter(candidate -> candidate.symbol().equals(candidate.name().canonical())).findFirst();
            if (original.isPresent()) {
                return original.orElseThrow();
            }
        }
        return Xtf24Names.unique(name, candidates);
    }

    TypeDescriptor type(TypeRef ref, Map<SymbolRef, TypeRef> concreteTypes) {
        var descriptor = plan.type(ref).known().orElseThrow();
        while (descriptor instanceof DomainReferenceType alias) {
            var domainRef = new SymbolRef(plan.snapshotId(), alias.targetDomain());
            var concrete = concreteTypes.get(domainRef);
            if (concrete == null) {
                var domain = (DomainSymbol) plan.symbol(domainRef).known().orElseThrow();
                concrete = new TypeRef(plan.snapshotId(), domain.typeId());
            }
            descriptor = plan.type(concrete).known().orElseThrow();
        }
        return descriptor;
    }

    private Optional<Embedded> placement(ObjectDescriptor association) {
        if (identified(association)) {
            return Optional.empty();
        }
        var root = ancestors(association).getFirst();
        var rootRoles = roles(root);
        var effective = roles(association);
        if (rootRoles.size() != 2 || effective.size() != 2) {
            return Optional.empty();
        }
        boolean firstMany = many(rootRoles.get(0));
        boolean secondMany = many(rootRoles.get(1));
        if (firstMany && secondMany) {
            return Optional.empty();
        }
        int owner = firstMany ? 0 : 1;
        if (!local(root, rootRoles.get(owner))) {
            if (firstMany || secondMany || !local(root, rootRoles.getFirst())) {
                return Optional.empty();
            }
            owner = 0;
        }
        int opposite = 1 - owner;
        return Optional.of(new Embedded(association, effective.get(owner), effective.get(opposite), rootRoles.get(opposite)));
    }

    private boolean local(ObjectDescriptor association, RoleSlot role) {
        return role.role().targets().stream().allMatch(target ->
                plan.object(new SymbolRef(plan.snapshotId(), target.base()))
                        .filter(object -> object.topic().equals(association.topic())).isPresent());
    }

    private static boolean many(RoleSlot role) {
        return !(role.role().cardinality().upper() instanceof CardinalityUpper.Finite finite)
                || finite.value().compareTo(BigInteger.ONE) > 0;
    }

    private static List<RoleSlot> roles(ObjectDescriptor association) {
        return association.slots().stream().filter(RoleSlot.class::isInstance).map(RoleSlot.class::cast).toList();
    }

    private List<ObjectDescriptor> ancestors(ObjectDescriptor descriptor) {
        return types.objectLineage(descriptor.symbol()).stream()
                .map(plan::object).flatMap(Optional::stream).toList();
    }
}
