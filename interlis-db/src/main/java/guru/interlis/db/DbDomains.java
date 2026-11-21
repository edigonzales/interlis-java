package guru.interlis.db;

import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferEvent.DomainBinding;
import guru.interlis.transfer.TransferPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Resolves the generic domain selections shared by SQL and the transfer. */
final class DbDomains {
    private DbDomains() { }

    static Optional<TopicSymbol.GenericBinding> geometryBinding(TransferPlan plan, ObjectDescriptor object, AttributeSlot attr) {
        var type = plan.type(attr.declaredType()).known().orElseThrow();
        while (type instanceof DomainReferenceType alias) {
            var domain = domain(plan, alias.targetDomain());
            if (domain.generic()) {
                return Optional.of(binding(plan, object, domain.id()));
            }
            type = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        }
        if (type instanceof GeometryType geometry && geometry.vertexDomain().isPresent()) {
            var vertex = domain(plan, geometry.vertexDomain().orElseThrow());
            if (vertex.generic()) return Optional.of(binding(plan, object, vertex.id()));
        }
        return Optional.empty();
    }

    static boolean deferred(TopicDescriptor topic) {
        return topic.genericBindings().stream().anyMatch(binding -> binding.state() == TopicSymbol.BindingState.DEFERRED);
    }

    static boolean active(DbMapping.Column column, List<DomainBinding> bindings) {
        return column.domainBinding().isEmpty() || bindings.contains(column.domainBinding().orElseThrow());
    }

    static TopicSymbol.GenericBinding binding(TransferPlan plan, ObjectDescriptor object, SymbolId id) {
        return object.topic().flatMap(plan::topic).stream().flatMap(topic -> topic.genericBindings().stream())
                .filter(candidate -> candidate.genericDomain().equals(id)).findFirst()
                .orElseThrow(() -> DbMapping.unsupported("unbound generic domain in " + object.name().declared()));
    }

    static DomainSymbol domain(TransferPlan plan, SymbolId id) {
        return (DomainSymbol) plan.symbol(new SymbolRef(plan.snapshotId(), id)).known().orElseThrow();
    }

    static DomainBinding choice(TransferPlan plan, TopicSymbol.GenericBinding binding, SymbolId id) {
        return new DomainBinding(new SymbolRef(plan.snapshotId(), binding.genericDomain()), new SymbolRef(plan.snapshotId(), id));
    }

    static List<DomainBinding> read(TransferPlan plan, TopicDescriptor topic, String value, String bid) {
        var supplied = new ArrayList<DomainBinding>();
        if (value != null && !value.isBlank()) {
            if (value.length() > 1024) throw invalid(bid, "assignments exceed 1024 characters");
            var names = new HashMap<String, SymbolRef>();
            for (var binding : topic.genericBindings()) {
                var generic = new SymbolRef(plan.snapshotId(), binding.genericDomain());
                names.put(name(plan, generic), generic);
                for (var id : binding.alternatives()) {
                    var ref = new SymbolRef(plan.snapshotId(), id);
                    names.put(name(plan, ref), ref);
                }
            }
            for (String assignment : value.strip().split("[ \\t\\r\\n]+")) {
                int equals = assignment.indexOf('=');
                if (equals < 1) throw invalid(bid, assignment);
                var generic = names.get(assignment.substring(0, equals));
                var concrete = names.get(assignment.substring(equals + 1));
                if (generic == null || concrete == null) throw invalid(bid, assignment);
                supplied.add(new DomainBinding(generic, concrete));
            }
        }
        return resolve(plan, topic, supplied, bid);
    }

    static List<DomainBinding> resolve(TransferPlan plan, TopicDescriptor topic, List<DomainBinding> supplied, String bid) {
        var assignments = new HashMap<SymbolRef, SymbolRef>();
        for (var binding : supplied) {
            if (assignments.putIfAbsent(binding.generic(), binding.concrete()) != null) {
                throw invalid(bid, "duplicate assignment for " + name(plan, binding.generic()));
            }
        }
        var selected = new ArrayList<DomainBinding>();
        for (var binding : topic.genericBindings()) {
            var generic = new SymbolRef(plan.snapshotId(), binding.genericDomain());
            var concrete = assignments.remove(generic);
            if (concrete == null && binding.state() == TopicSymbol.BindingState.CONCRETE) {
                concrete = new SymbolRef(plan.snapshotId(), binding.alternatives().getFirst());
            }
            if (concrete == null || !binding.alternatives().contains(concrete.symbol())) {
                throw invalid(bid, "expected a permitted assignment for " + name(plan, generic));
            }
            selected.add(new DomainBinding(generic, concrete));
        }
        if (!assignments.isEmpty()) throw invalid(bid, "assignment for an unused domain");
        return List.copyOf(selected);
    }

    static String format(TransferPlan plan, List<DomainBinding> bindings, String bid) {
        String value = bindings.stream().map(b -> name(plan, b.generic()) + "=" + name(plan, b.concrete()))
                .collect(Collectors.joining(" "));
        if (value.length() > 1024) throw invalid(bid, "assignments exceed 1024 characters");
        return value;
    }

    private static String name(TransferPlan plan, SymbolRef ref) {
        return name(plan, plan.symbol(ref).known().orElseThrow());
    }

    private static String name(TransferPlan plan, Symbol symbol) {
        return symbol.owner().map(id -> name(plan, new SymbolRef(plan.snapshotId(), id)) + ".").orElse("")
                + symbol.name().value();
    }

    private static DbException invalid(String bid, String detail) {
        return new DbException("Invalid domain assignments for basket " + bid + ": " + detail);
    }
}
