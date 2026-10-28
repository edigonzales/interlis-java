package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Objects;

/** Closed typed object-path step. */
public sealed interface PathStep permits PathStep.InspectionStep, PathStep.ViewBaseStep, PathStep.ThisStep, PathStep.ParentStep,
        PathStep.AttributeStep, PathStep.IndexStep, PathStep.RoleStep, PathStep.AssociationAccessStep, PathStep.AssociationSourceStep, PathStep.UnresolvedStep {

    /**
     * Returns the {@code range} value.
     *
     * @return the value
     */
    SourceRange range();

    /** Context selected by a special inspection path element. */
    enum InspectionKind { PARENT, THISAREA, THATAREA }
    record InspectionStep(InspectionKind kind, SymbolId view, SymbolId target, SourceRange range) implements PathStep {
        public InspectionStep { Objects.requireNonNull(kind); Objects.requireNonNull(view); Objects.requireNonNull(target); Objects.requireNonNull(range); }
    }

    record ViewBaseStep(SymbolId base, SymbolId target, SourceRange range) implements PathStep {
        public ViewBaseStep { Objects.requireNonNull(base); Objects.requireNonNull(target); Objects.requireNonNull(range); }
    }

    /** Selects one element/axis of the preceding attribute. The result type is
     * snapshot-local; range includes the attribute and brackets. AttributeStep
     * separately retains the name-only range and reference target. */
    record IndexStep(PathIndex index, TypeId resultType, SourceRange range) implements PathStep {
        public IndexStep {
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(resultType, "resultType");
            Objects.requireNonNull(range, "range");
        }
    }

    /** An unbound source segment in an erroneous analysis, never a resolved THIS step. */
    record UnresolvedStep(String name, SourceRange range) implements PathStep {
        public UnresolvedStep {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");
        }
    }

    record ThisStep(SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public ThisStep {
            Objects.requireNonNull(range, "range");
        }
    }

    record ParentStep(SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public ParentStep {
            Objects.requireNonNull(range, "range");
        }
    }

    record AttributeStep(String name, SymbolId target, SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public AttributeStep {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Class-origin access via an association. All identities/types are snapshot-local.
     * sourceRole links the starting object, targetRole names the opposite role.
     * associationObject distinguishes \role from role. Cardinality describes this step;
     * resultType retains the resulting individual-object or object-set constraint. */
    record AssociationAccessStep(String name, SymbolId association, SymbolId sourceRole,
            SymbolId targetRole, boolean associationObject, Cardinality cardinality,
            TypeId resultType, SourceRange range) implements PathStep {
        public AssociationAccessStep {
            Objects.requireNonNull(name, "name"); Objects.requireNonNull(association, "association");
            Objects.requireNonNull(sourceRole, "sourceRole"); Objects.requireNonNull(targetRole, "targetRole");
            Objects.requireNonNull(cardinality, "cardinality"); Objects.requireNonNull(resultType, "resultType");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Outgoing-role selection of association instances (including role[Association]).
     * All identities are snapshot-local. Cardinality is per starting object;
     * resultType also retains a set inherited from earlier path steps. */
    record AssociationSourceStep(String name, SymbolId association, SymbolId sourceRole,
            Cardinality cardinality, TypeId resultType, SourceRange range) implements PathStep {
        public AssociationSourceStep {
            Objects.requireNonNull(name, "name"); Objects.requireNonNull(association, "association");
            Objects.requireNonNull(sourceRole, "sourceRole"); Objects.requireNonNull(cardinality, "cardinality");
            Objects.requireNonNull(resultType, "resultType"); Objects.requireNonNull(range, "range");
        }
    }

    record RoleStep(String name, SymbolId target, SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public RoleStep {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(range, "range");
        }
    }
}
