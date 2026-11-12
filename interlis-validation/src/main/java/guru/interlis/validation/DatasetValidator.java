package guru.interlis.validation;

import java.util.Objects;

/** Entry point for dataset validation. */
public final class DatasetValidator {
    private DatasetValidator() { }

    /** Starts a single-caller run. The host owns the supplied empty storage. */
    public static DatasetValidationSession session(ValidationPlan plan, DatasetOptions options,
                                                   ValidationStorage storage) {
        Objects.requireNonNull(plan, "plan");
        if (plan.profile() != ValidationProfile.DATASET) {
            throw new IllegalArgumentException("Dataset validation requires a DATASET plan");
        }
        return new DatasetValidationSession(plan, Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(storage, "storage"));
    }
}
