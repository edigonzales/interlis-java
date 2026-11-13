package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SymbolId;
import guru.interlis.model.ViewSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ViewPlanner {
    private ViewPlanner() { }

    static Map<SymbolId, ViewPlan> compile(SemanticGraph graph) {
        var plans = new LinkedHashMap<SymbolId, ViewPlan>();
        for (var symbol : graph.declarationsInOrder()) {
            if (!(symbol instanceof ViewSymbol view)) {
                continue;
            }
            var chain = new ArrayList<ViewSymbol>();
            for (var current = Optional.of(view); current.isPresent();) {
                var member = current.orElseThrow();
                chain.add(member);
                current = member.baseView().flatMap(graph::symbol).map(ViewSymbol.class::cast);
                if (chain.size() > 128) {
                    throw new ValidationLimitException("View inheritance exceeds the plan depth limit");
                }
            }
            var formation = chain.stream().flatMap(member -> member.formation().stream()).findFirst();
            if (formation.isEmpty()) {
                continue;
            }
            var attributes = graph.effectiveMembers(view.id()).stream()
                    .map(member -> graph.symbol(member.declaredSymbol()).orElseThrow())
                    .filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).toList();
            var constraints = chain.reversed().stream().flatMap(member -> member.constraints().stream())
                    .map(id -> (ConstraintSymbol) graph.symbol(id).orElseThrow()).toList();
            plans.put(view.id(), new ViewPlan(view, formation.orElseThrow(), List.copyOf(chain.reversed()),
                    attributes, constraints));
        }
        return java.util.Collections.unmodifiableMap(plans);
    }
}
