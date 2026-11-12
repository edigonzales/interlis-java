package guru.interlis.validation;

import java.util.Optional;

/** A byte table ordered by unsigned lexicographical key comparison. */
public interface ValidationTable {
    Optional<byte[]> get(byte[] key);
    Optional<byte[]> putIfAbsent(byte[] key, byte[] value);
    void put(byte[] key, byte[] value);

    /** The cursor owns its results. Do not mutate this table while the cursor is open. */
    EntryCursor scan(byte[] inclusiveLower, Optional<byte[]> exclusiveUpper);
}
