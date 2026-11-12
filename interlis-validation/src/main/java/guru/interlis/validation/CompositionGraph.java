package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

final class CompositionGraph {
    private static final byte EDGE = 2;
    private static final byte VISIT = 3;
    private static final byte ACTIVE = 1;
    private static final byte DONE = 2;
    private final ValidationTable entries;
    private final ValidationTable visits;

    CompositionGraph(ValidationStorage storage) {
        entries = storage.table(StorageArea.RULE_KEYS);
        visits = storage.table(StorageArea.TEMPORARY);
    }

    void observe(ObjectHandle part, ObjectHandle owner, SymbolId role) {
        entries.putIfAbsent(ByteBuffer.allocate(1 + 2 * Long.BYTES).put(EDGE)
                .putLong(part.ordinal()).putLong(owner.ordinal()).array(), role.value().getBytes(StandardCharsets.UTF_8));
    }

    void check(CancellationToken cancellation, RelationFindingSink findings) {
        try (var cursor = entries.scan(new byte[]{EDGE}, Optional.of(new byte[]{VISIT}))) {
            ObjectHandle previousPart = null;
            ObjectHandle firstOwner = null;
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                cancellation.throwIfCancellationRequested();
                var edge = edge(next.orElseThrow());
                if (edge.part().equals(previousPart)) {
                    findings.report(edge.part(), List.of(), edge.role(), Optional.of(ValidationCode.COMPOSITION_MULTIPLE_OWNERS),
                            true, List.of(Long.toString(firstOwner.ordinal()), Long.toString(edge.owner().ordinal())));
                } else {
                    previousPart = edge.part();
                    firstOwner = edge.owner();
                }
            }
        }
        try (var cursor = entries.scan(new byte[]{EDGE}, Optional.of(new byte[]{VISIT}))) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                cancellation.throwIfCancellationRequested();
                var part = edge(next.orElseThrow()).part();
                if (state(part) == 0) {
                    visit(part, cancellation, findings);
                }
            }
        }
    }

    private void visit(ObjectHandle root, CancellationToken cancellation, RelationFindingSink findings) {
        var stack = new ArrayDeque<Frame>();
        stack.push(new Frame(root, key(EDGE, root)));
        mark(root, ACTIVE);
        while (!stack.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            var current = stack.peek();
            var upper = key(EDGE, new ObjectHandle(Math.incrementExact(current.part.ordinal())));
            Optional<EntryCursor.Entry> next;
            try (var cursor = entries.scan(current.lower, Optional.of(upper))) {
                next = cursor.next();
            }
            if (next.isEmpty()) {
                mark(current.part, DONE);
                stack.pop();
                continue;
            }
            var entry = next.orElseThrow();
            current.lower = java.util.Arrays.copyOf(entry.key(), entry.key().length + 1);
            var edge = edge(entry);
            switch (state(edge.owner())) {
                case ACTIVE -> findings.report(edge.part(), List.of(), edge.role(), Optional.of(ValidationCode.COMPOSITION_CYCLE),
                        true, List.of(Long.toString(edge.owner().ordinal())));
                case DONE -> { }
                default -> {
                    mark(edge.owner(), ACTIVE);
                    stack.push(new Frame(edge.owner(), key(EDGE, edge.owner())));
                }
            }
        }
    }

    private byte state(ObjectHandle part) {
        return visits.get(key(VISIT, part)).map(value -> value[0]).orElse((byte) 0);
    }

    private void mark(ObjectHandle part, byte state) {
        visits.put(key(VISIT, part), new byte[]{state});
    }

    private static byte[] key(byte tag, ObjectHandle part) {
        return ByteBuffer.allocate(1 + Long.BYTES).put(tag).putLong(part.ordinal()).array();
    }

    private static Edge edge(EntryCursor.Entry entry) {
        var key = ByteBuffer.wrap(entry.key());
        key.get();
        return new Edge(new ObjectHandle(key.getLong()), new ObjectHandle(key.getLong()),
                new SymbolId(new String(entry.value(), StandardCharsets.UTF_8)));
    }

    private record Edge(ObjectHandle part, ObjectHandle owner, SymbolId role) { }

    private static final class Frame {
        private final ObjectHandle part;
        private byte[] lower;

        private Frame(ObjectHandle part, byte[] lower) {
            this.part = part;
            this.lower = lower;
        }
    }
}
