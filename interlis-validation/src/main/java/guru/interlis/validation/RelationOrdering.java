package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.SymbolId;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class RelationOrdering {
    private static final byte TAG = 1;
    private final ValidationTable entries;

    RelationOrdering(ValidationStorage storage) {
        entries = storage.table(StorageArea.RULE_KEYS);
    }

    void observe(RoleSymbol role, List<ObjectHandle> counterparts, long basket, ObjectHandle source,
                 ObjectHandle occurrence, Optional<BigInteger> position) {
        byte[] digits = position.map(BigInteger::toByteArray).orElseGet(() -> new byte[0]);
        int size = Math.addExact(digits.length, 3 * Long.BYTES + Integer.BYTES);
        var key = RelationGroups.tupleKey(TAG, role.id(), counterparts, size)
                .putLong(basket).putInt(digits.length).put(digits).putLong(occurrence.ordinal())
                .putLong(source.ordinal());
        entries.put(key.array(), new byte[0]);
    }

    void check(CancellationToken cancellation, java.util.function.BiPredicate<Long, SymbolId> closed, RelationFindingSink findings) {
        try (var cursor = entries.scan(new byte[]{TAG}, Optional.of(new byte[]{TAG + 1}))) {
            byte[] current = null;
            BigInteger previous = BigInteger.ZERO;
            boolean complete = false;
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                cancellation.throwIfCancellationRequested();
                var bytes = next.orElseThrow().key();
                var key = ByteBuffer.wrap(bytes);
                key.get();
                int nameLength = key.getInt();
                var name = new StringBuilder(nameLength);
                for (int i = 0; i < nameLength; i++) {
                    name.append(key.getChar());
                }
                int arity = key.getInt();
                var counterparts = new ArrayList<ObjectHandle>(arity);
                for (int i = 0; i < arity; i++) {
                    counterparts.add(new ObjectHandle(key.getLong()));
                }
                long basket = key.getLong();
                var group = Arrays.copyOf(bytes, key.position());
                byte[] digits = new byte[key.getInt()];
                key.get(digits);
                key.getLong();
                var source = new ObjectHandle(key.getLong());
                var role = new SymbolId(name.toString());
                if (!Arrays.equals(group, current)) {
                    current = group;
                    previous = BigInteger.ZERO;
                    complete = closed.test(basket, role);
                }
                if (digits.length == 0) {
                    complete = false;
                    findings.report(source, counterparts, role, Optional.empty(), false, List.of());
                    continue;
                }
                var position = new BigInteger(digits);
                var expected = previous.add(BigInteger.ONE);
                if (position.equals(previous)) {
                    findings.report(source, counterparts, role, Optional.of(ValidationCode.ORDER_POSITION_DUPLICATE),
                            complete, List.of(position.toString()));
                } else if (complete && position.compareTo(expected) > 0) {
                    findings.report(source, counterparts, role, Optional.of(ValidationCode.ORDER_POSITION_GAP),
                            true, List.of(expected.toString(), position.toString()));
                } else {
                    findings.report(source, counterparts, role, Optional.empty(), complete, List.of());
                }
                previous = position;
            }
        }
    }

}
