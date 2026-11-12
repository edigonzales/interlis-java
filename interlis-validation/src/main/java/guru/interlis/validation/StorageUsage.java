package guru.interlis.validation;

/** Number of retained entries and the accounted key and value bytes. */
public record StorageUsage(long entries, long bytes) {
    public StorageUsage {
        if (entries < 0 || bytes < 0) {
            throw new IllegalArgumentException("Storage usage must be non-negative");
        }
    }
}
