package guru.interlis.validation;

import guru.interlis.model.ConstraintSymbol;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import java.util.List;

record ObjectValidationPlan(ObjectDescriptor descriptor, List<FieldRule> fields,
                            List<ConstraintSymbol> constraints) {
    ObjectValidationPlan {
        fields = List.copyOf(fields);
        constraints = List.copyOf(constraints);
    }

    record FieldRule(AttributeSlot slot, List<ValueRules> values) {
        FieldRule {
            values = List.copyOf(values);
        }
    }

    /** Rules for the value itself at depth zero and collection elements at higher depths. */
    record ValueRules(int depth, boolean required, List<ConstraintSymbol> constraints) {
        ValueRules {
            constraints = List.copyOf(constraints);
        }
    }
}
