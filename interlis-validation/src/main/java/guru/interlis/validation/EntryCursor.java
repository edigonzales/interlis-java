package guru.interlis.validation;

import java.util.Optional;

/** Forward scan of a byte table. A returned entry's arrays belong to the caller. */
public interface EntryCursor extends AutoCloseable {
    /** Returns the next entry, or empty once exhausted. */
    Optional<Entry> next();

    @Override
    void close();

    record Entry(byte[] key, byte[] value) { }
}
