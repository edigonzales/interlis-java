package guru.interlis.validation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Heap storage bounded by entry count and total key and value bytes. */
public final class InMemoryValidationStorage implements ValidationStorage {
    private final Map<StorageArea, Table> tables = new EnumMap<>(StorageArea.class);
    private final long maxEntries;
    private final long maxBytes;
    private long entries;
    private long bytes;
    private boolean closed;

    public InMemoryValidationStorage(long maxEntries, long maxBytes) {
        if (maxEntries < 1 || maxBytes < 1) {
            throw new IllegalArgumentException("Storage limits must be positive");
        }
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
    }

    @Override
    public ValidationTable table(StorageArea area) {
        requireOpen();
        return tables.computeIfAbsent(Objects.requireNonNull(area, "area"), ignored -> new Table());
    }

    @Override
    public void checkpoint() {
        requireOpen();
    }

    @Override
    public StorageUsage usage() {
        requireOpen();
        return new StorageUsage(entries, bytes);
    }

    @Override
    public void close() {
        closed = true;
        tables.values().forEach(table -> table.values.clear());
        tables.clear();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Validation storage is closed");
        }
    }

    private final class Table implements ValidationTable {
        private final TreeMap<byte[], byte[]> values = new TreeMap<>(Arrays::compareUnsigned);
        private int openCursors;

        @Override
        public Optional<byte[]> get(byte[] key) {
            requireOpen();
            return Optional.ofNullable(values.get(key)).map(byte[]::clone);
        }

        @Override
        public Optional<byte[]> putIfAbsent(byte[] key, byte[] value) {
            requireOpen();
            Objects.requireNonNull(value, "value");
            var previous = values.get(key);
            if (previous != null) {
                return Optional.of(previous.clone());
            }
            put(key, value);
            return Optional.empty();
        }

        @Override
        public void put(byte[] key, byte[] value) {
            requireOpen();
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (openCursors != 0) {
                throw new IllegalStateException("Cannot mutate a table during a scan");
            }
            var previous = values.get(key);
            long growth = previous == null ? (long) key.length + value.length : (long) value.length - previous.length;
            if (previous == null && entries == maxEntries || growth > maxBytes - bytes) {
                throw new ValidationLimitException("In-memory validation storage budget exceeded");
            }
            values.put(key.clone(), value.clone());
            if (previous == null) {
                entries++;
            }
            bytes += growth;
        }

        @Override
        public EntryCursor scan(byte[] inclusiveLower, Optional<byte[]> exclusiveUpper) {
            requireOpen();
            var lower = inclusiveLower.clone();
            var range = exclusiveUpper.map(upper -> values.subMap(lower, true, upper.clone(), false))
                    .orElseGet(() -> values.tailMap(lower, true));
            var iterator = range.entrySet().iterator();
            openCursors++;
            return new EntryCursor() {
                private boolean cursorClosed;

                @Override
                public Optional<Entry> next() {
                    requireOpen();
                    if (cursorClosed) {
                        throw new IllegalStateException("Validation cursor is closed");
                    }
                    if (!iterator.hasNext()) {
                        return Optional.empty();
                    }
                    var entry = iterator.next();
                    return Optional.of(new Entry(entry.getKey().clone(), entry.getValue().clone()));
                }

                @Override
                public void close() {
                    if (!cursorClosed) {
                        cursorClosed = true;
                        openCursors--;
                    }
                }
            };
        }
    }
}
