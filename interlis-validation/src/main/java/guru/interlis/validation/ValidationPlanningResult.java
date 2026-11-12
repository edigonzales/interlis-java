package guru.interlis.validation;

import java.util.List;
import java.util.Objects;

/** Result of projecting a checked schema into local validation rules. */
public sealed interface ValidationPlanningResult
        permits ValidationPlanningResult.Ready, ValidationPlanningResult.Rejected {

    /** The complete immutable projection, including rules outside its scope. */
    record Ready(ValidationPlan plan) implements ValidationPlanningResult {
        public Ready {
            Objects.requireNonNull(plan, "plan");
        }
    }

    /** Nonempty, immutable list of reasons the projection could not be built. */
    record Rejected(List<PlanningProblem> problems) implements ValidationPlanningResult {
        public Rejected {
            problems = List.copyOf(problems);
            if (problems.isEmpty()) {
                throw new IllegalArgumentException("Missing planning problem");
            }
        }
    }
}
