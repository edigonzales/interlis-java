package guru.interlis.model;

/** Closed constraint definition hierarchy. */
public sealed interface ConstraintDefinition permits MandatoryConstraint,
        PlausibilityConstraint, UniquenessConstraint, ExistenceConstraint,
        SetConstraint, DomainConstraint {
}
