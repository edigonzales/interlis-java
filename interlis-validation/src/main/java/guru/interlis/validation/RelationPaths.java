package guru.interlis.validation;

import guru.interlis.model.SymbolId;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class RelationPaths {
    private static final byte TAG = 7;
    private final ValidationTable table;

    RelationPaths(ValidationTable table) {
        this.table = table;
    }

    void observe(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole,
                 ObjectHandle target, ObjectHandle link, Optional<BigInteger> position) {
        var prefix = prefix(from, sourceRole, targetRole);
        var key = ByteBuffer.allocate(prefix.length + Long.BYTES).put(prefix).putLong(link.ordinal()).array();
        byte[] order = position.map(BigInteger::toByteArray).orElseGet(() -> new byte[0]);
        table.put(key, ByteBuffer.allocate(Long.BYTES + order.length).putLong(target.ordinal()).put(order).array());
    }

    List<ObjectHandle> targets(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole, Runnable visit) {
        var prefix = prefix(from, sourceRole, targetRole);
        var upper = prefix.clone();
        upper[upper.length - 1] = 1;
        var handles = new ArrayList<Target>();
        try (var cursor = table.scan(prefix, Optional.of(upper))) {
            for (var item = cursor.next(); item.isPresent(); item = cursor.next()) {
                visit.run();
                var data = ByteBuffer.wrap(item.orElseThrow().value());
                var handle = new ObjectHandle(data.getLong());
                byte[] order = new byte[data.remaining()];
                data.get(order);
                handles.add(new Target(handle, order.length == 0 ? Optional.empty() : Optional.of(new BigInteger(order))));
            }
        }
        handles.sort(Comparator.comparing(target -> target.position().orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return handles.stream().map(Target::handle).toList();
    }

    void markUnresolved(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole) {
        table.put(unresolvedKey(from, sourceRole, targetRole), new byte[0]);
    }

    boolean hasUnresolved(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole) {
        return table.get(unresolvedKey(from, sourceRole, targetRole)).isPresent();
    }

    private static byte[] unresolvedKey(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole) {
        var key = prefix(from, sourceRole, targetRole);
        key[key.length - 1] = 1;
        return key;
    }

    List<ObjectHandle> firstTwo(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole, Runnable visit) {
        var prefix = prefix(from, sourceRole, targetRole);
        var upper = unresolvedKey(from, sourceRole, targetRole);
        var handles = new ArrayList<ObjectHandle>(2);
        try (var cursor = table.scan(prefix, Optional.of(upper))) {
            while (handles.size() < 2) {
                var item = cursor.next();
                if (item.isEmpty()) {
                    break;
                }
                visit.run();
                handles.add(new ObjectHandle(ByteBuffer.wrap(item.orElseThrow().value()).getLong()));
            }
        }
        return handles;
    }

    long count(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole, Runnable visit) {
        var prefix = prefix(from, sourceRole, targetRole);
        var upper = prefix.clone();
        upper[upper.length - 1] = 1;
        long count = 0;
        try (var cursor = table.scan(prefix, Optional.of(upper))) {
            for (var item = cursor.next(); item.isPresent(); item = cursor.next()) {
                visit.run();
                count = Math.incrementExact(count);
            }
        }
        return count;
    }

    private static byte[] prefix(ObjectHandle from, SymbolId sourceRole, SymbolId targetRole) {
        var source = sourceRole.value().getBytes(StandardCharsets.UTF_8);
        var target = targetRole.value().getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(2 + Long.BYTES + 2 * Integer.BYTES + source.length + target.length)
                .put(TAG).putLong(from.ordinal()).putInt(source.length).put(source)
                .putInt(target.length).put(target).put((byte) 0).array();
    }
    private record Target(ObjectHandle handle, Optional<BigInteger> position) { }
}
