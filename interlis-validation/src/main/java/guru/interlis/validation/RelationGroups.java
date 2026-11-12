package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolId;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Stores ordered counterpart tuples and their distinct observed targets in the backend. */
final class RelationGroups {
    private final ValidationTable table;

    RelationGroups(ValidationStorage storage) {
        this.table = storage.table(StorageArea.RULE_KEYS);
    }

    void observe(SymbolId role, List<ObjectHandle> counterparts, ObjectHandle target) {
        table.putIfAbsent(key(role, counterparts, target.ordinal()), new byte[0]);
    }

    void expect(SymbolId role, List<ObjectHandle> counterparts) {
        table.putIfAbsent(key(role, counterparts, 0), new byte[0]);
    }

    void groups(CancellationToken cancellation, Consumer<Group> consumer) {
        try (var cursor = table.scan(new byte[]{0}, Optional.of(new byte[]{1}))) {
            GroupKey current = null;
            long targets = 0;
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                cancellation.throwIfCancellationRequested();
                var key = ByteBuffer.wrap(next.orElseThrow().key());
                key.get();
                int length = key.getInt();
                var role = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    role.append(key.getChar());
                }
                int arity = key.getInt();
                var counterparts = new ArrayList<ObjectHandle>(arity);
                for (int i = 0; i < arity; i++) {
                    counterparts.add(new ObjectHandle(key.getLong()));
                }
                var group = new GroupKey(new SymbolId(role.toString()), List.copyOf(counterparts));
                if (!group.equals(current)) {
                    if (current != null) {
                        consumer.accept(new Group(current.role(), current.counterparts(), targets));
                    }
                    current = group;
                    targets = 0;
                }
                if (key.getLong() != 0) {
                    targets = Math.incrementExact(targets);
                }
            }
            if (current != null) {
                consumer.accept(new Group(current.role(), current.counterparts(), targets));
            }
        }
    }

    private static byte[] key(SymbolId role, List<ObjectHandle> counterparts, long target) {
        return tupleKey((byte) 0, role, counterparts, Long.BYTES).putLong(target).array();
    }

    static ByteBuffer tupleKey(byte tag, SymbolId role, List<ObjectHandle> counterparts, int suffixBytes) {
        String name = role.value();
        int size = Math.addExact(Math.addExact(1 + 2 * Integer.BYTES, suffixBytes),
                Math.addExact(Math.multiplyExact(Character.BYTES, name.length()),
                        Math.multiplyExact(Long.BYTES, counterparts.size())));
        var key = ByteBuffer.allocate(size).put(tag).putInt(name.length());
        for (int i = 0; i < name.length(); i++) {
            key.putChar(name.charAt(i));
        }
        key.putInt(counterparts.size());
        for (var counterpart : counterparts) {
            key.putLong(counterpart.ordinal());
        }
        return key;
    }

    private record GroupKey(SymbolId role, List<ObjectHandle> counterparts) { }

    record Group(SymbolId role, List<ObjectHandle> counterparts, long targets) { }
}
