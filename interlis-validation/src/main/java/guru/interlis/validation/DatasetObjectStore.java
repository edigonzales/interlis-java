package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;

/** Maps object handles to independently owned neutral values. */
final class DatasetObjectStore {
    private final ValidationTable table;
    private final StoredValueCodec codec;

    DatasetObjectStore(ValidationStorage storage, StoredValueCodec codec) {
        this.table = storage.table(StorageArea.OBJECTS);
        this.codec = codec;
    }

    void add(ObjectHandle handle, BasketHandle basket, TransferEvent.ObjectEvent event, Set<SymbolRef> projection) {
        var value = new StoredValueCodec.StoredObject(basket.input(), basket.ordinal(), event);
        var bytes = projection == null ? codec.encodeObject(value) : codec.encodeProjection(value, projection);
        if (table.putIfAbsent(key(handle), bytes).isPresent()) {
            throw new ValidationStreamException("Dataset object handle was reused");
        }
    }

    StoredValueCodec.StoredObject get(ObjectHandle handle) {
        return table.get(key(handle)).map(codec::decodeObject)
                .orElseThrow(() -> new ValidationStreamException("Unknown dataset object handle"));
    }

    StoredValueCodec.StoredObjectHeader getHeader(ObjectHandle handle) {
        return table.get(key(handle)).map(codec::decodeObjectHeader)
                .orElseThrow(() -> new ValidationStreamException("Unknown dataset object handle"));
    }

    StoredValueCodec.StoredObject getRetained(ObjectHandle handle) {
        return table.get(key(handle)).map(codec::decodeRetainedObject)
                .orElseThrow(() -> new ValidationStreamException("Unknown dataset object handle"));
    }

    EntryCursor scan() {
        return table.scan(new byte[0], Optional.empty());
    }

    EntryCursor scan(ObjectHandle first, long exclusiveUpper) {
        return table.scan(key(first), Optional.of(ByteBuffer.allocate(Long.BYTES).putLong(exclusiveUpper).array()));
    }

    StoredValueCodec.StoredObject decodeRetained(EntryCursor.Entry entry) {
        return codec.decodeRetainedObject(entry.value());
    }

    StoredValueCodec.StoredObjectHeader decodeHeader(EntryCursor.Entry entry) {
        return codec.decodeObjectHeader(entry.value());
    }

    static ObjectHandle handle(EntryCursor.Entry entry) {
        return new ObjectHandle(ByteBuffer.wrap(entry.key()).getLong());
    }

    private static byte[] key(ObjectHandle handle) {
        return ByteBuffer.allocate(Long.BYTES).putLong(handle.ordinal()).array();
    }
}
