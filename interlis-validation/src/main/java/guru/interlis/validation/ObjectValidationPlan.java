package guru.interlis.validation;

import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record ObjectValidationPlan(ObjectDescriptor descriptor, List<FieldRule> fields,
                            List<ConstraintSymbol> constraints, Map<SymbolId, FieldRule> pathFields) {
    ObjectValidationPlan {
        fields = List.copyOf(fields);
        constraints = List.copyOf(constraints);
        pathFields = Map.copyOf(pathFields);
    }

    FieldRule field(SymbolId target) {
        return pathFields.get(target);
    }

    record FieldRule(AttributeSlot slot, boolean derived, Optional<String> charset, List<ValueRules> values) {
        FieldRule {
            values = List.copyOf(values);
        }
    }

    /** Rules for the value itself at depth zero and collection elements at higher depths. */
    record ValueRules(int depth, TypeDescriptor type, Optional<SymbolId> genericDomain,
                      Optional<Symbol> requiredDeclaration,
                      List<ConstraintSymbol> constraints, Map<SymbolId, List<ConstraintSymbol>> concreteConstraints,
                      Map<SymbolId, List<ConstraintSymbol>> concreteUnsupported,
                      Set<List<String>> enumerationValues) {
        ValueRules {
            constraints = List.copyOf(constraints);
            concreteConstraints = Map.copyOf(concreteConstraints);
            concreteUnsupported = Map.copyOf(concreteUnsupported);
        }

        List<ConstraintSymbol> constraints(Optional<SymbolId> concreteDomain) {
            return concreteDomain.map(concreteConstraints::get).orElse(constraints);
        }

        boolean required() {
            return requiredDeclaration.isPresent();
        }
    }
}
