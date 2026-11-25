package guru.interlis.explorer.gui;

import guru.interlis.explorer.DuckDbStore;
import guru.interlis.explorer.DuckDbStore.StoredObject;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Structured;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

record InspectorRelationships(Map<Reference, Long> targets, List<Association> associations, boolean limited) {
    private static final int RELATIONSHIP_LIMIT = 100;
    static final InspectorRelationships EMPTY = new InspectorRelationships(Map.of(), List.of(), false);

    record Association(StoredObject source, ObjectDescriptor type, Structured data,
                       RoleSlot embeddedOwner, Map<Reference, Long> targets) {}

    static InspectorRelationships read(TransferPlan plan, DuckDbStore store, StoredObject selected)
            throws SQLException {
        var selectedTargets = targets(store, selected.position());
        var sources = new TreeMap<Long, StoredObject>();
        sources.put(selected.position(), selected);
        var incoming = store.incomingRoleSources(selected.position(), RELATIONSHIP_LIMIT + 1);
        for (var source : incoming) {
            sources.put(source.position(), source);
        }
        var associations = new ArrayList<Association>();
        for (var source : sources.values()) {
            boolean own = source.position() == selected.position();
            var targets = own ? selectedTargets : targets(store, source.position());
            for (var association : associations(plan, source, targets)) {
                if (own || refersTo(association, selected.position())) {
                    if (associations.size() == RELATIONSHIP_LIMIT) {
                        return new InspectorRelationships(selectedTargets, List.copyOf(associations), true);
                    }
                    associations.add(association);
                }
            }
        }
        return new InspectorRelationships(selectedTargets, List.copyOf(associations),
                incoming.size() > RELATIONSHIP_LIMIT);
    }

    private static Map<Reference, Long> targets(DuckDbStore store, long source) throws SQLException {
        var result = new HashMap<Reference, Long>();
        for (var link : store.referencePositions(source)) {
            link.targetPosition().ifPresent(target -> result.put(link.reference(), target));
        }
        return Map.copyOf(result);
    }

    private static List<Association> associations(TransferPlan plan, StoredObject source, Map<Reference, Long> targets) {
        var data = source.object().data();
        var type = plan.object(data.type()).orElseThrow();
        if (type.kind() == SymbolKind.ASSOCIATION) {
            return List.of(new Association(source, type, data, null, targets));
        }

        var relations = new ArrayList<Association>();
        for (var field : data.values().entrySet()) {
            var declaration = plan.symbol(field.getKey()).known().orElseThrow();
            if (declaration.kind() != SymbolKind.ROLE) {
                continue;
            }
            if (field.getValue() instanceof Structured structure) {
                var association = plan.object(structure.type()).orElseThrow();
                // The payload carries the explicit end. Its other end is the containing object.
                relations.add(new Association(source, association, structure,
                        otherRole(association, structure), targets));
            } else if (field.getValue() instanceof Reference reference) {
                var owner = new SymbolRef(plan.snapshotId(), declaration.owner().orElseThrow());
                var association = plan.object(owner).orElseThrow();
                var structure = new TransferValue.Structured(owner, Map.of(field.getKey(), reference));
                relations.add(new Association(source, association, structure,
                        otherRole(association, structure), targets));
            }
        }
        return relations;
    }

    private static RoleSlot otherRole(ObjectDescriptor type, Structured payload) {
        return type.slots().stream().filter(RoleSlot.class::isInstance).map(RoleSlot.class::cast)
                .filter(role -> payload.value(role.symbol()).isEmpty()).findFirst().orElseThrow();
    }

    private static boolean refersTo(Association association, long target) {
        for (var slot : association.type().slots()) {
            if (slot instanceof RoleSlot && association.data().value(slot.symbol()).orElse(null) instanceof Reference reference
                    && Long.valueOf(target).equals(association.targets().get(reference))) {
                return true;
            }
        }
        return false;
    }
}
