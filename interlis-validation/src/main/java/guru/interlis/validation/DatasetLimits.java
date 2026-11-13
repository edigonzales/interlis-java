package guru.interlis.validation;

/** Positive limits for one dataset run, independent of the backend's byte and entry limits. */
public record DatasetLimits(int maxInputs, long maxBaskets, long maxObjects, int maxRecordBytes,
                            long maxRelationWork, long maxConstraintWork, int maxKeyBytes) {
    public DatasetLimits {
        if (maxInputs < 1 || maxBaskets < 1 || maxObjects < 1 || maxRecordBytes < 1
                || maxRelationWork < 1 || maxConstraintWork < 1 || maxKeyBytes < 1) {
            throw new IllegalArgumentException("Dataset limits must be positive");
        }
    }

    /** Creates limits with the default relationship work budget. */
    public DatasetLimits(int maxInputs, long maxBaskets, long maxObjects, int maxRecordBytes) {
        this(maxInputs, maxBaskets, maxObjects, maxRecordBytes, 10_000_000);
    }

    /** Creates limits with the default constraint work and canonical key budgets. */
    public DatasetLimits(int maxInputs, long maxBaskets, long maxObjects, int maxRecordBytes,
                         long maxRelationWork) {
        this(maxInputs, maxBaskets, maxObjects, maxRecordBytes, maxRelationWork,
                10_000_000, 8 * 1024 * 1024);
    }

    public static DatasetLimits defaults() {
        return new DatasetLimits(1_024, 1_000_000, 10_000_000, 16 * 1024 * 1024);
    }
}
