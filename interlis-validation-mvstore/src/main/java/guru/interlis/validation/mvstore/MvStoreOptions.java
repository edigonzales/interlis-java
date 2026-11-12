package guru.interlis.validation.mvstore;

/**
 * Positive limits for a disposable store. Logical bytes count keys and values.
 * Disk bytes cap the file's logical length. Cache and write buffer configure
 * MVStore in MiB and KiB respectively.
 */
public record MvStoreOptions(long maxEntries, long maxBytes, long maxDiskBytes,
                             int cacheMiB, int writeBufferKiB) {
    public MvStoreOptions {
        if (maxEntries < 1 || maxBytes < 1 || maxDiskBytes < 1 || cacheMiB < 1
                || writeBufferKiB < 1 || writeBufferKiB > Integer.MAX_VALUE / 1024) {
            throw new IllegalArgumentException("MVStore limits must be positive and representable");
        }
    }

    /** Default cache and write buffer for an explicitly supplied file quota. */
    public static MvStoreOptions defaults(long maxDiskBytes) {
        return new MvStoreOptions(50_000_000, maxDiskBytes, maxDiskBytes, 64, 1_024);
    }
}
