package guru.interlis.validation;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/** Retains every occurrence of an identity in the selected storage backend. */
final class IdentityIndex {
    private final ValidationTable table;

    IdentityIndex(ValidationStorage storage) {
        table = storage.table(StorageArea.IDENTITIES);
    }

    void addTemporary(long input, String tid, ObjectHandle handle) {
        add(temporary(input, tid), handle);
    }

    void addStable(String namespace, String value, ObjectHandle handle) {
        add(stable(namespace, value), handle);
    }

    Lookup temporaryTarget(long input, String tid) {
        return lookup(temporary(input, tid));
    }

    Lookup stableTarget(String namespace, String value) {
        return lookup(stable(namespace, value));
    }

    void checkStableCollisions(guru.interlis.execution.CancellationToken cancellation,
                               java.util.function.BiConsumer<ObjectHandle, ObjectHandle> collision) {
        byte[] firstKey = null;
        ObjectHandle firstHandle = null;
        try (var entries = table.scan(new byte[]{1}, Optional.of(new byte[]{2}))) {
            for (var next = entries.next(); next.isPresent(); next = entries.next()) {
                cancellation.throwIfCancellationRequested();
                var entry = next.orElseThrow();
                byte[] key = entry.key();
                int prefixLength = key.length - Long.BYTES;
                var handle = suffix(entry, prefixLength);
                if (firstKey != null && Arrays.equals(firstKey, 0, firstKey.length - Long.BYTES, key, 0, prefixLength)) {
                    collision.accept(firstHandle, handle);
                } else {
                    firstKey = key;
                    firstHandle = handle;
                }
            }
        }
    }

    private void add(byte[] prefix, ObjectHandle handle) {
        var key = ByteBuffer.allocate(Math.addExact(prefix.length, Long.BYTES))
                .put(prefix).putLong(handle.ordinal()).array();
        table.putIfAbsent(key, new byte[0]);
    }

    private Lookup lookup(byte[] prefix) {
        try (var entries = table.scan(prefix, Optional.of(exclusiveUpper(prefix)))) {
            var first = entries.next();
            if (first.isEmpty()) {
                return Missing.INSTANCE;
            }
            var handle = suffix(first.orElseThrow(), prefix.length);
            return entries.next().<Lookup>map(second -> new Ambiguous(handle, suffix(second, prefix.length)))
                    .orElseGet(() -> new Found(handle));
        }
    }

    private static ObjectHandle suffix(EntryCursor.Entry entry, int offset) {
        return new ObjectHandle(ByteBuffer.wrap(entry.key(), offset, Long.BYTES).getLong());
    }

    private static byte[] exclusiveUpper(byte[] prefix) {
        byte[] next = prefix.clone();
        int last = next.length - 1;
        while (next[last] == (byte) 0xff) {
            last--;
        }
        next[last]++;
        return Arrays.copyOf(next, last + 1);
    }

    private static byte[] temporary(long input, String value) {
        var key = ByteBuffer.allocate(Math.addExact(1 + Long.BYTES, stringSize(value)));
        key.put((byte) 0).putLong(input);
        writeString(key, value);
        return key.array();
    }

    private static byte[] stable(String namespace, String value) {
        var key = ByteBuffer.allocate(Math.addExact(1, Math.addExact(stringSize(namespace), stringSize(value))));
        key.put((byte) 1);
        writeString(key, namespace);
        writeString(key, value);
        return key.array();
    }

    private static int stringSize(String value) {
        return Math.addExact(Integer.BYTES, Math.multiplyExact(Character.BYTES, value.length()));
    }

    private static void writeString(ByteBuffer key, String value) {
        key.putInt(value.length());
        for (int i = 0; i < value.length(); i++) {
            key.putChar(value.charAt(i));
        }
    }

    sealed interface Lookup permits Found, Missing, Ambiguous { }

    record Found(ObjectHandle handle) implements Lookup { }

    enum Missing implements Lookup { INSTANCE }

    record Ambiguous(ObjectHandle first, ObjectHandle second) implements Lookup { }
}
