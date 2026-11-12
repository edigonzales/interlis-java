package guru.interlis.validation;

import java.util.Objects;

/** Non-null configuration for local checks, subjects, extent claims and budgets. */
public record DatasetOptions(ValidationOptions localOptions, SubjectSelection subjects,
                             ExtentDeclarations extents, DatasetLimits limits) {
    public DatasetOptions {
        Objects.requireNonNull(localOptions, "localOptions");
        Objects.requireNonNull(subjects, "subjects");
        Objects.requireNonNull(extents, "extents");
        Objects.requireNonNull(limits, "limits");
    }

    public static DatasetOptions defaults(ValidationPlan plan) {
        return new DatasetOptions(ValidationOptions.defaults(), SubjectSelection.fromRoots(plan),
                ExtentDeclarations.open(), DatasetLimits.defaults());
    }
}
