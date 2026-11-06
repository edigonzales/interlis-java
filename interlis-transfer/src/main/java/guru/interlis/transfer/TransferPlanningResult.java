package guru.interlis.transfer;

import guru.interlis.model.QualifiedName;

import java.util.List;
import java.util.Objects;

/** Outcome of projecting a checked schema into the neutral transfer vocabulary. */
public sealed interface TransferPlanningResult
        permits TransferPlanningResult.Ready, TransferPlanningResult.Unsupported {

    /** A complete plan for the supported scope. */
    record Ready(TransferPlan plan) implements TransferPlanningResult {
        public Ready {
            Objects.requireNonNull(plan, "plan");
        }
    }

    /** An immutable, nonempty list of issues in the order discovered by the planner. */
    record Unsupported(List<Issue> issues) implements TransferPlanningResult {
        public Unsupported {
            issues = List.copyOf(issues);
            if (issues.isEmpty()) {
                throw new IllegalArgumentException("An unsupported projection must identify its issues");
            }
        }
    }

    /** One unsupported feature at a qualified declaration path in the checked schema. */
    record Issue(QualifiedName path, Feature feature) {
        public Issue {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(feature, "feature");
        }
    }

    /** Reasons a checked schema cannot be projected in full. */
    enum Feature {
        MISSING_MODEL_INFORMATION,
        ANONYMOUS_OBJECT,
        OBJECT_OUTSIDE_TOPIC,
        VIEW,
        GRAPHIC,
        DERIVED_ASSOCIATION,
        VALUE_TYPE,
        COORDINATE_DIMENSION,
        CUSTOM_LINE_FORM,
        LINE_ATTRIBUTES
    }
}
