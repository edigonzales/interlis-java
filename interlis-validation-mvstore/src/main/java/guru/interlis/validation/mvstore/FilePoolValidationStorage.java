package guru.interlis.validation.mvstore;

import guru.interlis.validation.EntryCursor;
import guru.interlis.validation.StorageArea;
import guru.interlis.validation.StorageUsage;
import guru.interlis.validation.ValidationLimitException;
import guru.interlis.validation.ValidationStorage;
import guru.interlis.validation.ValidationTable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Disposable single-caller byte store with ordered heap keys and append-only file values. */
public final class FilePoolValidationStorage implements ValidationStorage {
    private final Path directory;
    private final Path file;
    private final Object directoryKey;
    private final Object fileKey;
    private final FileChannel channel;
    private final MvStoreOptions options;
    private final Map<StorageArea, Table> tables = new EnumMap<>(StorageArea.class);
    private final Set<Scan> scans = new HashSet<>();
    private long entries;
    private long bytes;
    private long fileBytes;
    private boolean closed;
    private UncheckedIOException failure;

    /** Opens one private run directory beneath an existing host directory with the supplied storage quotas. */
    public static FilePoolValidationStorage open(Path tempRoot, MvStoreOptions options) {
        Objects.requireNonNull(options, "options");
        try {
            var root = tempRoot.toRealPath();
            var directory = Files.getFileStore(root).supportsFileAttributeView("posix")
                    ? Files.createTempDirectory(root, "interlis-validation-", PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")))
                    : Files.createTempDirectory(root, "interlis-validation-");
            var directoryKey = attributes(directory).fileKey();
            var file = directory.resolve("validation.pool");
            BasicFileAttributes createdFile = null;
            FileChannel channel = null;
            try {
                Files.createFile(file);
                createdFile = attributes(file);
                channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
                var result = new FilePoolValidationStorage(directory, file, directoryKey, createdFile.fileKey(),
                        channel, options);
                result.checkpoint();
                return result;
            } catch (IOException | RuntimeException failure) {
                try {
                    if (channel != null) {
                        channel.close();
                    }
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                try {
                    removeOwnedFiles(directory, directoryKey, file, createdFile == null ? null : createdFile.fileKey(),
                            createdFile != null);
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        } catch (IOException problem) {
            throw new UncheckedIOException("Cannot open the validation file pool", problem);
        }
    }

    private FilePoolValidationStorage(Path directory, Path file, Object directoryKey, Object fileKey,
                                      FileChannel channel, MvStoreOptions options) {
        this.directory = directory;
        this.file = file;
        this.directoryKey = directoryKey;
        this.fileKey = fileKey;
        this.channel = channel;
        this.options = options;
    }

    @Override
    public ValidationTable table(StorageArea area) {
        requireOpen();
        return tables.computeIfAbsent(Objects.requireNonNull(area, "area"), ignored -> new Table());
    }

    @Override
    public void checkpoint() {
        requireOpen();
        try {
            verifyDirectory(directory, directoryKey);
            verifyFile(file, fileKey);
            if (channel.size() != fileBytes) {
                throw new IOException("Validation file pool length changed");
            }
            channel.force(true);
        } catch (IOException problem) {
            throw fail(problem);
        }
    }

    @Override
    public StorageUsage usage() {
        requireOpen();
        return new StorageUsage(entries, bytes);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Validation storage is closed");
        }
        if (failure != null) {
            throw failure;
        }
    }

    private UncheckedIOException fail(IOException problem) {
        failure = new UncheckedIOException("Validation file pool failed", problem);
        return failure;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Set.copyOf(scans).forEach(Scan::close);
        tables.values().forEach(table -> table.values.clear());
        tables.clear();
        IOException failure = null;
        try {
            channel.close();
        } catch (IOException problem) {
            failure = problem;
        }
        try {
            removeOwnedFiles(directory, directoryKey, file, fileKey, true);
        } catch (IOException problem) {
            if (failure == null) {
                failure = problem;
            } else {
                failure.addSuppressed(problem);
            }
        }
        if (failure != null) {
            throw new UncheckedIOException("Cannot close the validation file pool", failure);
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void verifyDirectory(Path directory, Object key) throws IOException {
        var current = attributes(directory);
        if (!current.isDirectory() || !Objects.equals(current.fileKey(), key)) {
            throw new IOException("Validation file pool directory was replaced");
        }
    }

    private static void verifyFile(Path file, Object key) throws IOException {
        var current = attributes(file);
        if (!current.isRegularFile() || !Objects.equals(current.fileKey(), key)) {
            throw new IOException("Validation file pool was replaced");
        }
    }

    private static void removeOwnedFiles(Path directory, Object directoryKey, Path file, Object fileKey,
                                         boolean fileIdentified) throws IOException {
        verifyDirectory(directory, directoryKey);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!fileIdentified) {
                throw new IOException("Validation file pool ownership could not be determined");
            }
            verifyFile(file, fileKey);
            Files.delete(file);
        }
        Files.delete(directory);
    }

    private record Value(long offset, int length) { }

    private byte[] read(Value reference) {
        var data = new byte[reference.length()];
        var buffer = ByteBuffer.wrap(data);
        try {
            while (buffer.hasRemaining()) {
                if (channel.read(buffer, reference.offset() + buffer.position()) <= 0) {
                    throw new IOException("Cannot read a complete validation file pool record");
                }
            }
            return data;
        } catch (IOException problem) {
            throw fail(problem);
        }
    }

    private void append(byte[] data) {
        var buffer = ByteBuffer.wrap(data);
        try {
            while (buffer.hasRemaining()) {
                if (channel.write(buffer, fileBytes + buffer.position()) <= 0) {
                    throw new IOException("Cannot append a complete validation file pool record");
                }
            }
        } catch (IOException problem) {
            throw fail(problem);
        }
    }

    private final class Table implements ValidationTable {
        private final TreeMap<byte[], Value> values = new TreeMap<>(Arrays::compareUnsigned);
        private int openCursors;

        @Override
        public Optional<byte[]> get(byte[] key) {
            requireOpen();
            return Optional.ofNullable(values.get(key)).map(FilePoolValidationStorage.this::read);
        }

        @Override
        public Optional<byte[]> putIfAbsent(byte[] key, byte[] value) {
            requireOpen();
            Objects.requireNonNull(value, "value");
            var previous = values.get(key);
            if (previous != null) {
                return Optional.of(read(previous));
            }
            write(key, value, null);
            return Optional.empty();
        }

        @Override
        public void put(byte[] key, byte[] value) {
            requireOpen();
            Objects.requireNonNull(value, "value");
            write(key, value, values.get(key));
        }

        private void write(byte[] key, byte[] data, Value previous) {
            Objects.requireNonNull(key, "key");
            if (openCursors != 0) {
                throw new IllegalStateException("Cannot mutate a table during a scan");
            }
            long growth = previous == null ? (long) key.length + data.length : (long) data.length - previous.length();
            if (previous == null && entries == options.maxEntries() || growth > options.maxBytes() - bytes) {
                throw new ValidationLimitException("File pool logical validation storage budget exceeded");
            }
            if (data.length > options.maxDiskBytes() - fileBytes) {
                throw new ValidationLimitException("Validation file pool temporary file quota exceeded");
            }
            var reference = new Value(fileBytes, data.length);
            append(data);
            if (previous == null) {
                values.put(key.clone(), reference);
                entries++;
            } else {
                values.replace(key, reference);
            }
            fileBytes += data.length;
            bytes += growth;
        }

        @Override
        public EntryCursor scan(byte[] inclusiveLower, Optional<byte[]> exclusiveUpper) {
            requireOpen();
            var lower = inclusiveLower.clone();
            var range = exclusiveUpper.map(upper -> values.subMap(lower, true, upper.clone(), false))
                    .orElseGet(() -> values.tailMap(lower, true));
            var cursor = new Scan(this, range.entrySet().iterator());
            openCursors++;
            scans.add(cursor);
            return cursor;
        }
    }

    private final class Scan implements EntryCursor {
        private final Table table;
        private final Iterator<Map.Entry<byte[], Value>> iterator;
        private boolean closed;

        private Scan(Table table, Iterator<Map.Entry<byte[], Value>> iterator) {
            this.table = table;
            this.iterator = iterator;
        }

        @Override
        public Optional<Entry> next() {
            requireOpen();
            if (closed) {
                throw new IllegalStateException("Validation cursor is closed");
            }
            if (!iterator.hasNext()) {
                return Optional.empty();
            }
            var entry = iterator.next();
            return Optional.of(new Entry(entry.getKey().clone(), read(entry.getValue())));
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                scans.remove(this);
                table.openCursors--;
            }
        }
    }
}
