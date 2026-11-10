package guru.interlis.xtf24;

import guru.interlis.model.DomainSymbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferEvent.DomainBinding;
import guru.interlis.transfer.TransferPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.xtf24.Xtf24Exception.Reason.INVALID_WIRE;

final class Xtf24Domains {
    private final TransferPlan plan;
    private final Xtf24Names names;

    Xtf24Domains(TransferPlan plan, Xtf24Names names) {
        this.plan = plan;
        this.names = names;
    }

    List<DomainBinding> parse(TopicDescriptor topic, String text) {
        Map<String, SymbolRef> names = new HashMap<>();
        for (var binding : topic.genericBindings()) {
            index(names, new SymbolRef(plan.snapshotId(), binding.genericDomain()));
            for (var alternative : binding.alternatives()) {
                index(names, new SymbolRef(plan.snapshotId(), alternative));
            }
        }
        var bindings = new ArrayList<DomainBinding>();
        if (!text.isBlank()) {
            for (String pair : text.trim().split("[ \\t\\r\\n]+")) {
                int equals = pair.indexOf('=');
                if (equals <= 0 || equals == pair.length() - 1) {
                    throw invalid();
                }
                SymbolRef generic = names.get(pair.substring(0, equals));
                SymbolRef concrete = names.get(pair.substring(equals + 1));
                if (generic == null || concrete == null) {
                    throw invalid();
                }
                bindings.add(new DomainBinding(generic, concrete));
            }
        }
        return List.copyOf(bindings);
    }

    String format(List<DomainBinding> bindings) {
        return bindings.stream().map(binding -> name(binding.generic()) + "=" + name(binding.concrete()))
                .collect(Collectors.joining(" "));
    }

    Map<SymbolRef, TypeRef> resolve(TopicDescriptor topic, List<DomainBinding> bindings) {
        Map<SymbolRef, SymbolRef> selected = new LinkedHashMap<>();
        for (var binding : bindings) {
            if (selected.putIfAbsent(binding.generic(), binding.concrete()) != null) {
                throw invalid();
            }
        }
        Map<SymbolRef, TypeRef> types = new HashMap<>();
        for (var usage : topic.genericBindings()) {
            var generic = new SymbolRef(plan.snapshotId(), usage.genericDomain());
            SymbolRef concrete = selected.remove(generic);
            if (concrete == null || !usage.alternatives().contains(concrete.symbol())
                    || !(plan.symbol(concrete).known().orElse(null) instanceof DomainSymbol domain)) {
                throw invalid();
            }
            types.put(generic, new TypeRef(concrete.snapshot(), domain.typeId()));
        }
        if (!selected.isEmpty()) {
            throw invalid();
        }
        return Map.copyOf(types);
    }

    private void index(Map<String, SymbolRef> names, SymbolRef ref) {
        SymbolRef prior = names.putIfAbsent(name(ref), ref);
        if (prior != null && !prior.equals(ref)) {
            throw invalid();
        }
    }

    private String name(SymbolRef ref) {
        return names.originalName(ref).toString();
    }

    private static Xtf24Exception invalid() {
        return Xtf24Wire.failure(INVALID_WIRE, "Expected explicit, permitted assignments for the basket's generic domains");
    }
}
