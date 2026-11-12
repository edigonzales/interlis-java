package guru.interlis.validation.mvstore;

import guru.interlis.validation.EntryCursor;
import guru.interlis.validation.StorageArea;
import guru.interlis.validation.StorageUsage;
import guru.interlis.validation.ValidationLimitException;
import guru.interlis.validation.ValidationStorage;
import guru.interlis.validation.ValidationTable;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.SFChunk;
import org.h2.mvstore.SingleFileStore;
import org.h2.mvstore.type.ByteArrayDataType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Disposable JVM byte store. Use it from a single caller and close it after the run. */
public final class MvStoreValidationStorage implements ValidationStorage {
    private static final String FILE_NAME = "validation.mv";
    private final Path directory;
    private final Path file;
    private final Object directoryKey;
    private final Object fileKey;
    private final MvStoreOptions options;
    private final MVStore store;
    private final QuotaFileStore fileStore;
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    private final Map<StorageArea, Table> tables = new EnumMap<>(StorageArea.class);
    private final Set<Scan> scans = new HashSet<>();
    private long entries;
    private long bytes;
    private boolean closed;
    private boolean failureDelivered;

    /**
     * Creates one private run directory beneath the existing host directory.
     * Both arguments are non-null. The store owns only its new directory and file.
     */
    public static MvStoreValidationStorage open(Path tempRoot, MvStoreOptions options) {
        return new MvStoreValidationStorage(Objects.requireNonNull(tempRoot, "tempRoot"),
                Objects.requireNonNull(options, "options"));
    }

    private MvStoreValidationStorage(Path tempRoot, MvStoreOptions options) {
        this.options = options;
        try {
            var root = tempRoot.toRealPath();
            if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
                var privateDirectory = PosixFilePermissions.asFileAttribute(Set.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
                directory = Files.createTempDirectory(root, "interlis-validation-", privateDirectory);
            } else {
                directory = Files.createTempDirectory(root, "interlis-validation-");
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot create the validation store directory", error);
        }
        file = directory.resolve(FILE_NAME);
        fileStore = new QuotaFileStore(options, failure);
        MVStore opened = null;
        try {
            directoryKey = attributes(directory).fileKey();
            fileStore.open(file.toString(), false, (char[]) null);
            fileKey = attributes(file).fileKey();
            opened = new MVStore.Builder().fileStore(fileStore)
                    .autoCommitDisabled().autoCommitBufferSize(options.writeBufferKiB())
                    .backgroundExceptionHandler((thread, cause) -> remember(cause))
                    .open();
            store = opened;
            for (var area : StorageArea.values()) {
                var builder = new MVMap.Builder<byte[], byte[]>()
                        .keyType(ByteArrayDataType.INSTANCE).valueType(ByteArrayDataType.INSTANCE);
                tables.put(area, new Table(store.openMap(area.name(), builder)));
            }
            checkpoint();
        } catch (IOException | RuntimeException error) {
            var original = remember(error);
            if (opened != null) {
                opened.closeImmediately();
            }
            try {
                fileStore.close();
            } catch (RuntimeException cleanup) {
                original.addSuppressed(cleanup);
            }
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(directory);
            } catch (IOException cleanup) {
                original.addSuppressed(cleanup);
            }
            throw original;
        }
    }

    @Override
    public ValidationTable table(StorageArea area) {
        requireOpen();
        return tables.get(Objects.requireNonNull(area, "area"));
    }

    @Override
    public void checkpoint() {
        requireOpen();
        verifyOwnedPaths();
        access(() -> {
            store.commit();
            store.sync();
            return null;
        });
    }

    @Override
    public StorageUsage usage() {
        requireOpen();
        return new StorageUsage(entries, bytes);
    }

    /** Actual logical file length in bytes at the time of the call. */
    public long diskBytes() {
        requireOpen();
        return verifyOwnedPaths().size();
    }

    private BasicFileAttributes verifyOwnedPaths() {
        try {
            var currentDirectory = attributes(directory);
            if (!currentDirectory.isDirectory() || !Objects.equals(currentDirectory.fileKey(), directoryKey)) {
                throw new IOException("Validation store directory was replaced");
            }
            var currentFile = attributes(file);
            if (!currentFile.isRegularFile() || !Objects.equals(currentFile.fileKey(), fileKey)) {
                throw new IOException("Validation store file was replaced");
            }
            if (currentFile.size() > options.maxDiskBytes()) {
                throw deliver(new ValidationLimitException("MVStore temporary file quota exceeded"));
            }
            return currentFile;
        } catch (IOException error) {
            throw deliver(error);
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private RuntimeException remember(Throwable cause) {
        var problem = cause instanceof ValidationLimitException limit ? limit
                : cause instanceof UncheckedIOException io ? io
                : new UncheckedIOException("Validation MVStore failed", new IOException(cause));
        failure.compareAndSet(null, problem);
        return failure.get();
    }

    private RuntimeException deliver(Throwable cause) {
        failureDelivered = true;
        return remember(cause);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Validation storage is closed");
        }
        var problem = failure.get();
        if (problem != null) {
            failureDelivered = true;
            throw problem;
        }
    }

    private <T> T access(Supplier<T> action) {
        requireOpen();
        try {
            var result = action.get();
            requireOpen();
            return result;
        } catch (MVStoreException error) {
            throw deliver(error);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException cleanupFailure = null;
        for (var scan : Set.copyOf(scans)) {
            try {
                scan.close();
            } catch (RuntimeException error) {
                cleanupFailure = combine(cleanupFailure, error);
            }
        }
        try {
            store.closeImmediately();
            fileStore.close();
        } catch (RuntimeException error) {
            cleanupFailure = combine(cleanupFailure, error);
        }
        try {
            var owned = attributes(directory);
            if (!owned.isDirectory() || !Objects.equals(owned.fileKey(), directoryKey)) {
                throw new IOException("Validation store directory was replaced");
            }
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                var retained = attributes(file);
                if (!retained.isRegularFile() || !Objects.equals(retained.fileKey(), fileKey)) {
                    throw new IOException("Validation store file was replaced");
                }
                Files.delete(file);
            }
            Files.delete(directory);
        } catch (IOException error) {
            cleanupFailure = combine(cleanupFailure, new UncheckedIOException("Cannot remove validation run files", error));
        }
        if (!failureDelivered && failure.get() != null) {
            cleanupFailure = combine(failure.get(), cleanupFailure);
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private static RuntimeException combine(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        if (next != null && first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private final class Table implements ValidationTable {
        private final MVMap<byte[], byte[]> values;
        private int openCursors;

        private Table(MVMap<byte[], byte[]> values) {
            this.values = values;
        }

        @Override
        public Optional<byte[]> get(byte[] key) {
            return access(() -> Optional.ofNullable(values.get(key)).map(byte[]::clone));
        }

        @Override
        public Optional<byte[]> putIfAbsent(byte[] key, byte[] value) {
            Objects.requireNonNull(value, "value");
            var previous = get(key);
            if (previous.isEmpty()) {
                put(key, value);
            }
            return previous;
        }

        @Override
        public void put(byte[] key, byte[] value) {
            requireOpen();
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (openCursors != 0) {
                throw new IllegalStateException("Cannot mutate a table during a scan");
            }
            access(() -> {
                var previous = values.get(key);
                long growth = previous == null ? (long) key.length + value.length : (long) value.length - previous.length;
                if (previous == null && entries == options.maxEntries() || growth > options.maxBytes() - bytes) {
                    throw new ValidationLimitException("MVStore logical validation storage budget exceeded");
                }
                values.put(key.clone(), value.clone());
                bytes += growth;
                if (previous == null) {
                    entries++;
                }
                return null;
            });
        }

        @Override
        public EntryCursor scan(byte[] inclusiveLower, Optional<byte[]> exclusiveUpper) {
            var lower = inclusiveLower.clone();
            var upper = exclusiveUpper.map(byte[]::clone);
            return access(() -> {
                if (upper.isPresent() && Arrays.compareUnsigned(lower, upper.orElseThrow()) > 0) {
                    throw new IllegalArgumentException("Lower bound exceeds upper bound");
                }
                var pin = store.registerVersionUsage();
                try {
                    var result = new Scan(this, values.cursor(lower), upper, pin);
                    openCursors++;
                    scans.add(result);
                    return result;
                } catch (RuntimeException error) {
                    store.deregisterVersionUsage(pin);
                    throw error;
                }
            });
        }
    }

    private final class Scan implements EntryCursor {
        private final Table table;
        private final org.h2.mvstore.Cursor<byte[], byte[]> cursor;
        private final Optional<byte[]> upper;
        private final MVStore.TxCounter pin;
        private boolean exhausted;
        private boolean closed;

        private Scan(Table table, org.h2.mvstore.Cursor<byte[], byte[]> cursor, Optional<byte[]> upper,
                     MVStore.TxCounter pin) {
            this.table = table;
            this.cursor = cursor;
            this.upper = upper;
            this.pin = pin;
        }

        @Override
        public Optional<Entry> next() {
            if (closed) {
                throw new IllegalStateException("Validation cursor is closed");
            }
            return access(() -> {
                if (exhausted || !cursor.hasNext()) {
                    return Optional.empty();
                }
                var key = cursor.next();
                if (upper.isPresent() && Arrays.compareUnsigned(key, upper.orElseThrow()) >= 0) {
                    exhausted = true;
                    return Optional.empty();
                }
                return Optional.of(new Entry(key.clone(), cursor.getValue().clone()));
            });
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                scans.remove(this);
                table.openCursors--;
                store.deregisterVersionUsage(pin);
            }
        }
    }

    private static final class QuotaFileStore extends SingleFileStore {
        private final long quota;
        private final AtomicReference<RuntimeException> failure;

        private QuotaFileStore(MvStoreOptions options, AtomicReference<RuntimeException> failure) {
            super(Map.of());
            this.quota = options.maxDiskBytes();
            this.failure = failure;
            setCacheSize(options.cacheMiB());
        }

        @Override
        protected void writeFully(SFChunk chunk, long position, ByteBuffer source) {
            if (position < 0 || position > quota - source.remaining()) {
                var exceeded = new ValidationLimitException("MVStore temporary file quota exceeded");
                failure.compareAndSet(null, exceeded);
                throw exceeded;
            }
            super.writeFully(chunk, position, source);
        }
    }
}
