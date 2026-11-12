package guru.interlis.validation;

/** Positive limits for one dataset run, independent of the backend's byte and entry limits. */
public record DatasetLimits(int maxInputs, long maxBaskets, long maxObjects, int maxRecordBytes, int maxExtentClaims) {
    public DatasetLimits {
        if (maxInputs < 1 || maxBaskets < 1 || maxObjects < 1 || maxRecordBytes < 1 || maxExtentClaims < 1) {
            throw new IllegalArgumentException("Dataset limits must be positive");
        }
    }

    public static DatasetLimits defaults() {
        return new DatasetLimits(1_024, 1_000_000, 10_000_000, 16 * 1024 * 1024, 4_096);
    }
}
