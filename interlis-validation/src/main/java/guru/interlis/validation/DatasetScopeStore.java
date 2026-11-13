package guru.interlis.validation;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Per-rule counters for delivered scopes, ordered by run-local basket ordinal. */
final class DatasetScopeStore {
    private static final byte TAG = 6;
    private final ValidationTable table;
    private final int rule;

    DatasetScopeStore(ValidationTable table, int rule) {
        this.table = table;
        this.rule = rule;
    }

    Optional<Scope> get(long ordinal) {
        return table.get(key(ordinal)).map(DatasetScopeStore::read);
    }

    void put(long ordinal, Scope scope) {
        var counts = scope.counts();
        var value = ByteBuffer.allocate(2 + 7 * Long.BYTES)
                .put((byte) scope.completeness().ordinal()).put((byte) (counts.selected ? 1 : 0))
                .putLong(counts.candidates).putLong(counts.evaluations).putLong(counts.satisfied).putLong(counts.violations)
                .putLong(counts.undecided).putLong(counts.unsupported).putLong(counts.unsupportedValues);
        table.put(key(ordinal), value.array());
    }

    void forScopes(BiConsumer<Long, Scope> consumer) {
        var upper = ByteBuffer.allocate(1 + Integer.BYTES).put(TAG).putInt(Math.incrementExact(rule)).array();
        try (var cursor = table.scan(key(0), Optional.of(upper))) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                var entry = next.orElseThrow();
                consumer.accept(ByteBuffer.wrap(entry.key()).getLong(1 + Integer.BYTES), read(entry.value()));
            }
        }
    }

    private byte[] key(long ordinal) {
        return ByteBuffer.allocate(1 + Integer.BYTES + Long.BYTES).put(TAG).putInt(rule).putLong(ordinal).array();
    }

    private static Scope read(byte[] bytes) {
        try {
            var value = ByteBuffer.wrap(bytes);
            var completeness = Completeness.values()[Byte.toUnsignedInt(value.get())];
            var counts = new DatasetScopeCounts();
            counts.selected = switch (value.get()) {
                case 0 -> false;
                case 1 -> true;
                default -> throw new ValidationStreamException("Invalid stored scope selection");
            };
            counts.candidates = value.getLong();
            counts.evaluations = value.getLong();
            counts.satisfied = value.getLong();
            counts.violations = value.getLong();
            counts.undecided = value.getLong();
            counts.unsupported = value.getLong();
            counts.unsupportedValues = value.getLong();
            if (value.hasRemaining() || counts.candidates < 0 || counts.evaluations < 0 || counts.violations < 0
                    || counts.satisfied < 0 || counts.undecided < 0 || counts.unsupported < 0 || counts.unsupportedValues < 0) {
                throw new ValidationStreamException("Invalid stored scope counters");
            }
            return new Scope(completeness, counts);
        } catch (java.nio.BufferUnderflowException | IndexOutOfBoundsException invalid) {
            throw new ValidationStreamException("Invalid stored scope record");
        }
    }

    enum Completeness { OPEN, CLOSED_BY_TRANSFER, CLOSED_DELIVERY }

    record Scope(Completeness completeness, DatasetScopeCounts counts) { }
}
