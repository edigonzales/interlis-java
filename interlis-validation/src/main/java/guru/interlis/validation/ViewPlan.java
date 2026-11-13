package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.ViewSymbol;

import java.util.List;

/** Bound execution information for a virtual view in one schema. */
record ViewPlan(ViewSymbol view, ViewSymbol.Formation formation, List<ViewSymbol> inheritance,
                List<AttributeSymbol> attributes, List<ConstraintSymbol> constraints) {
    ViewPlan {
        inheritance = List.copyOf(inheritance);
        attributes = List.copyOf(attributes);
        constraints = List.copyOf(constraints);
    }
}
