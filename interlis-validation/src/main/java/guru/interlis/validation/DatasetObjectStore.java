package guru.interlis.validation;

import java.nio.ByteBuffer;
import java.util.Optional;

/** Maps object handles to independently owned neutral values. */
final class DatasetObjectStore {
    private final ValidationTable table;
    private final StoredValueCodec codec;

    DatasetObjectStore(ValidationStorage storage, StoredValueCodec codec) {
        this.table = storage.table(StorageArea.OBJECTS);
        this.codec = codec;
    }

    void add(ObjectHandle handle, BasketHandle basket, guru.interlis.transfer.TransferEvent.ObjectEvent event) {
        var value = new StoredValueCodec.StoredObject(basket.input(), basket.ordinal(), event);
        if (table.putIfAbsent(key(handle), codec.encodeObject(value)).isPresent()) {
            throw new ValidationStreamException("Dataset object handle was reused");
        }
    }

    StoredValueCodec.StoredObject get(ObjectHandle handle) {
        return table.get(key(handle)).map(codec::decodeObject)
                .orElseThrow(() -> new ValidationStreamException("Unknown dataset object handle"));
    }

    EntryCursor scan() {
        return table.scan(new byte[0], Optional.empty());
    }

    StoredValueCodec.StoredObject decode(EntryCursor.Entry entry) {
        return codec.decodeObject(entry.value());
    }

    static ObjectHandle handle(EntryCursor.Entry entry) {
        return new ObjectHandle(ByteBuffer.wrap(entry.key()).getLong());
    }

    private static byte[] key(ObjectHandle handle) {
        return ByteBuffer.allocate(Long.BYTES).putLong(handle.ordinal()).array();
    }
}
