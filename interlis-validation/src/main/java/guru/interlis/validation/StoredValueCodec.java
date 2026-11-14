package guru.interlis.validation;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferIdentity.*;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Internal byte representation of a neutral value within one bound store. */
final class StoredValueCodec {
    private final SnapshotId snapshot;
    private final int maxBytes;

    StoredValueCodec(SnapshotId snapshot, int maxBytes) {
        this.snapshot = snapshot;
        if (maxBytes < 1) {
            throw new IllegalArgumentException("Value byte limit must be positive");
        }
        this.maxBytes = maxBytes;
    }

    byte[] encode(TransferValue value) {
        return encodeRecord(out -> write(out, value, 0));
    }

    byte[] encodeLink(RelationProjector.Link link) {
        return encodeRecord(out -> {
            out.writeLong(link.source().ordinal());
            symbol(out, link.association());
            optional(out, link.embeddedField(), field -> symbol(out, field));
            list(out, link.endpoints(), endpoint -> {
                symbol(out, endpoint.role());
                optional(out, endpoint.reference(), reference -> write(out, reference, 0));
                optional(out, endpoint.implicitTarget(), target -> out.writeLong(target.ordinal()));
            });
        });
    }

    RelationProjector.Link decodeLink(byte[] bytes) {
        return decodeRecord(bytes, in -> {
            var source = new ObjectHandle(in.readLong());
            var association = symbol(in);
            var field = optional(in, () -> symbol(in));
            var endpoints = list(in, () -> new RelationProjector.Endpoint(symbol(in),
                    optional(in, () -> (Reference) read(in, 0)),
                    optional(in, () -> new ObjectHandle(in.readLong()))));
            return new RelationProjector.Link(source, association, field, endpoints);
        });
    }

    byte[] encodeInput(InputDescriptor input, TransferEvent.StartTransfer header,
                       java.util.Map<String, QualifiedName> namespaces) {
        return encodeRecord(out -> {
            text(out, input.id().value());
            text(out, input.role().name());
            optional(out, header.source(), source -> text(out, source.value()));
            optional(out, header.sender(), sender -> text(out, sender));
            optional(out, header.comment(), comment -> text(out, comment));
            out.writeInt(namespaces.size());
            for (var entry : new java.util.TreeMap<>(namespaces).entrySet()) {
                text(out, entry.getKey());
                list(out, entry.getValue().segments(), segment -> text(out, segment));
            }
            list(out, header.models(), model -> {
                symbol(out, model.model());
                text(out, model.languageVersion().name());
                optional(out, model.modelVersion(), version -> text(out, version));
                optional(out, model.issuer(), issuer -> text(out, issuer));
            });
        });
    }

    StoredInput decodeInput(byte[] bytes) {
        return decodeRecord(bytes, in -> {
            var input = new InputDescriptor(new InputId(text(in)), InputRole.valueOf(text(in)));
            var source = optional(in, () -> new SourceId(text(in)));
            var sender = optional(in, () -> text(in));
            var comment = optional(in, () -> text(in));
            var namespaces = new LinkedHashMap<String, QualifiedName>();
            for (int n = length(in); n > 0; n--) {
                namespaces.put(text(in), new QualifiedName(list(in, () -> text(in))));
            }
            var models = list(in, () -> new TransferEvent.ModelDeclaration(symbol(in),
                    guru.interlis.model.LanguageVersion.valueOf(text(in)), optional(in, () -> text(in)),
                    optional(in, () -> text(in))));
            return new StoredInput(input, new TransferEvent.StartTransfer(source, sender, comment, models),
                    java.util.Map.copyOf(namespaces));
        });
    }

    record StoredInput(InputDescriptor descriptor, TransferEvent.StartTransfer header,
                       java.util.Map<String, QualifiedName> namespaces) { }

    byte[] encodeBasket(long input, TransferEvent.StartBasket start) {
        return encodeRecord(out -> {
            out.writeLong(input);
            basket(out, start.id());
            symbol(out, start.topic());
            list(out, start.additionalTopics(), topic -> symbol(out, topic));
            text(out, start.kind().name());
            text(out, start.consistency().name());
            optional(out, start.startState(), state -> text(out, state));
            optional(out, start.endState(), state -> text(out, state));
            optional(out, start.stableOid(), oid -> text(out, oid));
            list(out, start.domainBindings(), binding -> {
                symbol(out, binding.generic());
                symbol(out, binding.concrete());
            });
        });
    }

    StoredBasket decodeBasket(byte[] bytes) {
        return decodeRecord(bytes, in -> {
            long input = in.readLong();
            var id = basket(in);
            var topic = symbol(in);
            var additional = list(in, () -> symbol(in));
            var kind = TransferEvent.BasketKind.valueOf(text(in));
            var consistency = TransferEvent.Consistency.valueOf(text(in));
            var start = optional(in, () -> text(in));
            var end = optional(in, () -> text(in));
            var oid = optional(in, () -> text(in));
            var bindings = list(in, () -> new TransferEvent.DomainBinding(symbol(in), symbol(in)));
            return new StoredBasket(input, new TransferEvent.StartBasket(id, topic, additional, kind,
                    consistency, start, end, oid, bindings));
        });
    }

    record StoredBasket(long input, TransferEvent.StartBasket event) { }

    byte[] encodeObject(StoredObject stored) {
        return encodeRecord(out -> {
            out.writeLong(stored.input());
            out.writeLong(stored.basket());
            var event = stored.event();
            var id = event.object().identity();
            basket(out, id.currentBasket());
            optional(out, id.originalBasket(), value -> basket(out, value));
            optional(out, id.tid(), tid -> {
                text(out, tid.value());
                text(out, tid.origin().name());
            });
            optional(out, id.oid(), oid -> text(out, oid.value()));
            optional(out, id.source(), source -> text(out, source.value()));
            optional(out, event.operation(), operation -> text(out, operation.name()));
            text(out, event.consistency().name());
            write(out, event.object().data(), 0);
        });
    }

    StoredObject decodeObject(byte[] bytes) {
        return decodeRecord(bytes, in -> {
            long input = in.readLong();
            long basket = in.readLong();
            var current = basket(in);
            var original = optional(in, () -> basket(in));
            var tid = optional(in, () -> new TransferId(text(in), IdOrigin.valueOf(text(in))));
            var oid = optional(in, () -> new ObjectOid(text(in)));
            var source = optional(in, () -> new SourceId(text(in)));
            var operation = optional(in, () -> TransferEvent.Operation.valueOf(text(in)));
            var consistency = TransferEvent.Consistency.valueOf(text(in));
            var object = new TransferObject(new TransferIdentity(current, original, tid, oid, source),
                    (Structured) read(in, 0));
            return new StoredObject(input, basket, new TransferEvent.ObjectEvent(object, operation, consistency));
        });
    }

    record StoredObject(long input, long basket, TransferEvent.ObjectEvent event) { }

    private static void basket(DataOutputStream out, BasketId id) throws IOException {
        text(out, id.value());
        text(out, id.origin().name());
    }

    private static BasketId basket(DataInputStream in) throws IOException {
        return new BasketId(text(in), IdOrigin.valueOf(text(in)));
    }

    private byte[] encodeRecord(Write<DataOutputStream> encode) {
        var bytes = new ByteArrayOutputStream() {
            @Override
            public synchronized void write(int value) {
                reserve(1);
                super.write(value);
            }

            @Override
            public synchronized void write(byte[] value, int offset, int length) {
                reserve(length);
                super.write(value, offset, length);
            }

            private void reserve(int length) {
                if (length > maxBytes - size()) {
                    throw new ValidationLimitException("Stored value exceeds the byte budget");
                }
            }
        };
        try (var out = new DataOutputStream(bytes)) {
            encode.accept(out);
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    TransferValue decode(byte[] bytes) {
        return decodeRecord(bytes, in -> read(in, 0));
    }

    private <T> T decodeRecord(byte[] bytes, ReadRecord<T> read) {
        if (bytes.length > maxBytes) {
            throw new ValidationLimitException("Stored value exceeds the byte budget");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var value = read.get(in);
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in stored value");
            }
            return value;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void write(DataOutputStream out, TransferValue value, int depth) throws IOException {
        depth(depth);
        switch (value) {
            case Undefined ignored -> out.writeByte(0);
            case Text v -> { out.writeByte(1); text(out, v.value()); }
            case BooleanValue v -> { out.writeByte(2); out.writeBoolean(v.value()); }
            case NumberValue v -> {
                out.writeByte(3);
                text(out, v.lexeme());
                bytes(out, v.exactValue().unscaledValue().toByteArray());
                out.writeInt(v.exactValue().scale());
            }
            case Enumeration v -> { out.writeByte(4); list(out, v.path(), x -> text(out, x)); }
            case Binary v -> { out.writeByte(5); bytes(out, v.bytes()); }
            case XmlFragment v -> {
                out.writeByte(6);
                text(out, v.xml());
                out.writeInt(v.namespaceBindings().size());
                for (var entry : v.namespaceBindings().entrySet()) {
                    text(out, entry.getKey());
                    text(out, entry.getValue());
                }
            }
            case Oid v -> { out.writeByte(7); text(out, v.lexeme()); }
            case ClassName v -> { out.writeByte(8); list(out, v.name().segments(), x -> text(out, x)); }
            case AttributePath v -> { out.writeByte(9); list(out, v.name().segments(), x -> text(out, x)); }
            case Structured v -> {
                out.writeByte(10);
                symbol(out, v.type());
                out.writeInt(v.values().size());
                for (var field : v.values().entrySet()) {
                    symbol(out, field.getKey());
                    write(out, field.getValue(), depth + 1);
                }
            }
            case Repeated v -> {
                out.writeByte(11);
                text(out, v.kind().name());
                list(out, v.values(), x -> write(out, x, depth + 1));
            }
            case Reference v -> {
                out.writeByte(12);
                out.writeBoolean(v.target() instanceof TransferId);
                text(out, v.target().value());
                if (v.target() instanceof TransferId tid) {
                    text(out, tid.origin().name());
                }
                optional(out, v.targetBasket(), x -> {
                    text(out, x.value());
                    text(out, x.origin().name());
                });
                out.writeBoolean(v.external());
                optional(out, v.orderPosition(), x -> bytes(out, x.toByteArray()));
            }
            case Coordinate v -> { out.writeByte(13); list(out, v.ordinates(), x -> write(out, x, depth + 1)); }
            case MultiCoordinate v -> { out.writeByte(14); list(out, v.coordinates(), x -> write(out, x, depth + 1)); }
            case Polyline v -> {
                out.writeByte(15);
                list(out, v.parts(), part -> {
                    write(out, part.start(), depth + 1);
                    list(out, part.segments(), segment -> {
                        out.writeBoolean(segment instanceof Arc);
                        write(out, segment.end(), depth + 1);
                        if (segment instanceof Arc arc) {
                            write(out, arc.support(), depth + 1);
                            optional(out, arc.radius(), radius -> {
                                text(out, radius.convention().name());
                                write(out, radius.sourceValue(), depth + 1);
                            });
                        }
                    });
                });
                optional(out, v.lineAttributes(), x -> write(out, x, depth + 1));
            }
            case MultiPolyline v -> { out.writeByte(16); list(out, v.lines(), x -> write(out, x, depth + 1)); }
            case Surface v -> {
                out.writeByte(17);
                writeRing(out, v.shell(), depth);
                list(out, v.holes(), ring -> writeRing(out, ring, depth));
            }
            case MultiSurface v -> { out.writeByte(18); list(out, v.surfaces(), x -> write(out, x, depth + 1)); }
        }
    }

    private TransferValue read(DataInputStream in, int depth) throws IOException {
        depth(depth);
        return switch (in.readUnsignedByte()) {
            case 0 -> Undefined.INSTANCE;
            case 1 -> new Text(text(in));
            case 2 -> new BooleanValue(in.readBoolean());
            case 3 -> new NumberValue(text(in), new BigDecimal(new BigInteger(bytes(in)), in.readInt()));
            case 4 -> new Enumeration(list(in, () -> text(in)));
            case 5 -> new Binary(bytes(in));
            case 6 -> {
                var xml = text(in);
                var namespaces = new LinkedHashMap<String, String>();
                for (int n = length(in); n > 0; n--) {
                    namespaces.put(text(in), text(in));
                }
                yield new XmlFragment(xml, namespaces);
            }
            case 7 -> new Oid(text(in));
            case 8 -> new ClassName(new QualifiedName(list(in, () -> text(in))));
            case 9 -> new AttributePath(new QualifiedName(list(in, () -> text(in))));
            case 10 -> {
                var type = symbol(in);
                var fields = new LinkedHashMap<SymbolRef, TransferValue>();
                for (int n = length(in); n > 0; n--) {
                    fields.put(symbol(in), read(in, depth + 1));
                }
                yield new Structured(type, fields);
            }
            case 11 -> new Repeated(CollectionKind.valueOf(text(in)), list(in, () -> read(in, depth + 1)));
            case 12 -> {
                boolean transferId = in.readBoolean();
                var key = text(in);
                ObjectKey target = transferId ? new TransferId(key, IdOrigin.valueOf(text(in))) : new ObjectOid(key);
                var basket = optional(in, () -> new BasketId(text(in), IdOrigin.valueOf(text(in))));
                yield new Reference(target, basket, in.readBoolean(), optional(in, () -> new BigInteger(bytes(in))));
            }
            case 13 -> new Coordinate(list(in, () -> (NumberValue) read(in, depth + 1)));
            case 14 -> new MultiCoordinate(list(in, () -> (Coordinate) read(in, depth + 1)));
            case 15 -> new Polyline(list(in, () -> {
                var start = (Coordinate) read(in, depth + 1);
                return new CurvePart(start, list(in, () -> {
                    boolean arc = in.readBoolean();
                    var end = (Coordinate) read(in, depth + 1);
                    return arc ? new Arc((Coordinate) read(in, depth + 1), end,
                            optional(in, () -> {
                                var convention = RadiusConvention.valueOf(text(in));
                                return new ArcRadius((NumberValue) read(in, depth + 1), convention);
                            })) : new Straight(end);
                }));
            }), optional(in, () -> (Structured) read(in, depth + 1)));
            case 16 -> new MultiPolyline(list(in, () -> (Polyline) read(in, depth + 1)));
            case 17 -> new Surface(readRing(in, depth), list(in, () -> readRing(in, depth)));
            case 18 -> new MultiSurface(list(in, () -> (Surface) read(in, depth + 1)));
            default -> throw new IOException("Unknown stored value tag");
        };
    }

    private void writeRing(DataOutputStream out, Ring ring, int depth) throws IOException {
        list(out, ring.lines(), line -> write(out, line, depth + 1));
    }

    private Ring readRing(DataInputStream in, int depth) throws IOException {
        return new Ring(list(in, () -> (Polyline) read(in, depth + 1)));
    }

    private void symbol(DataOutputStream out, SymbolRef ref) throws IOException {
        if (!snapshot.equals(ref.snapshot())) {
            throw new IllegalArgumentException("Stored symbol belongs to another snapshot");
        }
        text(out, ref.symbol().value());
    }

    private SymbolRef symbol(DataInputStream in) throws IOException {
        return new SymbolRef(snapshot, new SymbolId(text(in)));
    }

    private static void depth(int depth) {
        if (depth > 128) {
            throw new ValidationLimitException("Stored value exceeds the depth budget");
        }
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        out.writeInt(value.length());
        for (int i = 0; i < value.length(); i++) {
            out.writeChar(value.charAt(i));
        }
    }

    private static String text(DataInputStream in) throws IOException {
        int length = length(in);
        if (length > in.available() / 2) {
            throw new IOException("Truncated stored text");
        }
        var value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(in.readChar());
        }
        return value.toString();
    }

    private static void bytes(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] bytes(DataInputStream in) throws IOException {
        return in.readNBytes(length(in));
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > in.available()) {
            throw new IOException("Invalid stored length");
        }
        return n;
    }

    private static <T> void list(DataOutputStream out, List<T> values, Write<T> write) throws IOException {
        out.writeInt(values.size());
        for (var value : values) {
            write.accept(value);
        }
    }

    private static <T> List<T> list(DataInputStream in, Read<T> read) throws IOException {
        int n = length(in);
        var values = new ArrayList<T>();
        for (int i = 0; i < n; i++) {
            values.add(read.get());
        }
        return values;
    }

    private static <T> void optional(DataOutputStream out, Optional<T> value, Write<T> write) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            write.accept(value.orElseThrow());
        }
    }

    private static <T> Optional<T> optional(DataInputStream in, Read<T> read) throws IOException {
        return in.readBoolean() ? Optional.of(read.get()) : Optional.empty();
    }

    @FunctionalInterface
    private interface ReadRecord<T> { T get(DataInputStream in) throws IOException; }

    @FunctionalInterface
    private interface Read<T> { T get() throws IOException; }

    @FunctionalInterface
    private interface Write<T> { void accept(T value) throws IOException; }
}
