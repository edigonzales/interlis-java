package guru.interlis.validation;

import java.util.Objects;

/** Non-null configuration for local checks, resource limits and the delivery scope. */
public record DatasetOptions(ValidationOptions localOptions, DatasetLimits limits, DatasetScope scope) {
    public DatasetOptions {
        Objects.requireNonNull(localOptions, "localOptions");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(scope, "scope");
    }

    /** Checks the supplied delivery with the standard resource limits. */
    public static DatasetOptions defaults() {
        return new DatasetOptions(ValidationOptions.defaults(), DatasetLimits.defaults(), DatasetScope.SUPPLIED_DATASET);
    }
}
