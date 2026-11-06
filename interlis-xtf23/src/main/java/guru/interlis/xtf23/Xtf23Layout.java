package guru.interlis.xtf23;

import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.Modifier;
import guru.interlis.model.Origin;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Derives the XTF 2.3 wire order and association placement from the neutral plan. */
final class Xtf23Layout {
    record Embedded(ObjectDescriptor association, RoleSlot owner, RoleSlot opposite, RoleSlot rootOpposite) { }

    record Field(TransferSlot slot, List<Embedded> embeddings, int level) {
        String name() {
            return embeddings.isEmpty() ? slot.name().declared().last() : embeddings.getFirst().opposite().name().declared().last();
        }

        private int group() {
            return embeddings.isEmpty() ? (slot instanceof RoleSlot ? 0 : 1) : 2;
        }
    }

    private static final Comparator<Field> WIRE_ORDER = Comparator.comparingInt(Field::level)
            .thenComparingInt(Field::group)
            .thenComparing(field -> field.embeddings().isEmpty() ? "" : field.name())
            .thenComparingInt(field -> field.slot().member().position());

    private final TransferPlan plan;
    private final Map<SymbolRef, Embedded> embedded;

    Xtf23Layout(TransferPlan plan) {
        this.plan = plan;
        var result = new HashMap<SymbolRef, Embedded>();
        for (var descriptor : plan.objects()) {
            if (descriptor.kind() == SymbolKind.ASSOCIATION && descriptor.transferable()) {
                placement(descriptor).ifPresent(value -> result.put(descriptor.symbol(), value));
            }
        }
        embedded = Map.copyOf(result);
    }

    Optional<Embedded> embedded(ObjectDescriptor descriptor) {
        return Optional.ofNullable(embedded.get(descriptor.symbol()));
    }

    boolean identified(ObjectDescriptor association) {
        if (!association.oidRules().isEmpty()) {
            return association.oidRules().stream().anyMatch(rule -> !rule.basket() && rule.domain().isPresent());
        }
        return ancestors(association).stream().anyMatch(descriptor -> descriptor.modifiers().contains(Modifier.OID));
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
                fields.add(new Field(slot, List.of(), levels.get(slot.member().position())));
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
        var byName = new HashMap<String, List<Embedded>>();
        for (var entry : embedded.entrySet()) {
            var value = entry.getValue();
            if (activeTypes.contains(entry.getKey()) && matchesRoots(descriptor, value.owner().role().targetRoots())) {
                byName.computeIfAbsent(value.opposite().name().declared().last(), unused -> new ArrayList<>()).add(value);
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
            fields.add(new Field(rootRole, List.copyOf(variants), level));
        }
    }

    boolean matchesRoots(ObjectDescriptor descriptor, List<SymbolId> allowed) {
        return allowed.isEmpty() || ancestors(descriptor).stream()
                .anyMatch(base -> allowed.contains(base.symbol().symbol()));
    }

    TypeDescriptor type(TypeRef ref) {
        var descriptor = plan.type(ref).known().orElseThrow();
        while (descriptor instanceof DomainReferenceType alias) {
            var domain = (DomainSymbol) plan.symbol(new SymbolRef(plan.snapshotId(), alias.targetDomain()))
                    .known().orElseThrow();
            descriptor = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        }
        return descriptor;
    }

    int dimensions(CoordinateType type) {
        if (type.multiCoordinate() || type.dimensions() < 2) {
            throw unsupported("Only single two- and three-dimensional XTF coordinates are supported");
        }
        return type.dimensions();
    }

    record CurveEncoding(int dimensions, boolean straights, boolean arcs) { }

    CurveEncoding curveEncoding(GeometryType type) {
        if (type.lineAttributes().isPresent()) {
            throw unsupported("XTF LINEATTR is unsupported");
        }
        var domain = type.vertexDomain().flatMap(id -> plan.symbol(new SymbolRef(plan.snapshotId(), id)).known());
        if (domain.isEmpty() || !(domain.orElseThrow() instanceof DomainSymbol vertex)
                || !(type(new TypeRef(plan.snapshotId(), vertex.typeId())) instanceof CoordinateType coordinate)) {
            throw unsupported("XTF line type requires a concrete coordinate domain");
        }
        boolean straights = type.lineForms().isEmpty();
        boolean arcs = type.lineForms().isEmpty();
        for (var id : type.lineForms()) {
            var symbol = plan.symbol(new SymbolRef(plan.snapshotId(), id)).known().orElseThrow();
            if (!(symbol.origin() instanceof Origin.PredefinedOrigin origin)) {
                throw unsupported("Custom XTF line forms are unsupported");
            }
            switch (origin.key()) {
                case "INTERLIS.STRAIGHTS" -> straights = true;
                case "INTERLIS.ARCS" -> arcs = true;
                default -> throw unsupported("Unsupported XTF line form " + origin.key());
            }
        }
        return new CurveEncoding(dimensions(coordinate), straights, arcs);
    }

    private static Xtf23Exception unsupported(String message) {
        return new Xtf23Exception(Xtf23Exception.Reason.UNSUPPORTED, message, -1, -1, null);
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
        var hierarchy = new ArrayList<ObjectDescriptor>();
        hierarchy.add(descriptor);
        while (descriptor.baseType().isPresent()) {
            descriptor = plan.object(descriptor.baseType().orElseThrow()).orElseThrow();
            hierarchy.add(descriptor);
        }
        return hierarchy.reversed();
    }
}
