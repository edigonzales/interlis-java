package guru.interlis.validation;

import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.Set;

record ObjectValidationPlan(ObjectDescriptor descriptor, List<FieldRule> fields,
                            List<ConstraintSymbol> constraints) {
    ObjectValidationPlan {
        fields = List.copyOf(fields);
        constraints = List.copyOf(constraints);
    }

    record FieldRule(AttributeSlot slot, boolean derived, Optional<String> charset, List<ValueRules> values) {
        FieldRule {
            values = List.copyOf(values);
        }
    }

    /** Rules for the value itself at depth zero and collection elements at higher depths. */
    record ValueRules(int depth, TypeDescriptor type, Optional<Symbol> requiredDeclaration,
                      List<ConstraintSymbol> constraints, Set<List<String>> enumerationValues) {
        ValueRules {
            constraints = List.copyOf(constraints);
        }

        boolean required() {
            return requiredDeclaration.isPresent();
        }
    }
}
