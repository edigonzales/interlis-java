package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MemoryStorageTest {
    @Test
    void ordersBytesAndOwnsStoredAndReturnedArrays() {
        try (var storage = new InMemoryValidationStorage(3, 6)) {
            var table = storage.table(StorageArea.OBJECTS);
            byte[] key = {(byte) 0xff};
            byte[] value = {42};
            table.put(key, value);
            key[0] = 0;
            value[0] = 0;
            table.put(new byte[]{0}, new byte[]{1});
            table.put(new byte[]{(byte) 0x80}, new byte[]{2});

            try (var cursor = table.scan(new byte[]{0}, Optional.of(new byte[]{(byte) 0xff}))) {
                assertEquals(0, cursor.next().orElseThrow().key()[0]);
                var entry = cursor.next().orElseThrow();
                assertEquals(128, Byte.toUnsignedInt(entry.key()[0]));
                entry.value()[0] = 0;
                assertEquals(2, table.get(new byte[]{(byte) 0x80}).orElseThrow()[0]);
                assertTrue(cursor.next().isEmpty());
                assertThrows(IllegalStateException.class, () -> table.put(new byte[]{0}, new byte[]{9}));
            }
            assertEquals(42, table.get(new byte[]{(byte) 0xff}).orElseThrow()[0]);
            assertEquals(new StorageUsage(3, 6), storage.usage());
        }
    }

    @Test
    void rejectsOverBudgetWritesWithoutReplacingExistingValues() {
        try (var storage = new InMemoryValidationStorage(1, 4)) {
            var table = storage.table(StorageArea.OBJECTS);
            byte[] key = {1};
            assertTrue(table.putIfAbsent(key, new byte[]{7, 8}).isEmpty());
            assertArrayEquals(new byte[]{7, 8}, table.putIfAbsent(key, new byte[]{9}).orElseThrow());
            assertThrows(ValidationLimitException.class, () -> table.put(key, new byte[4]));
            assertThrows(ValidationLimitException.class, () -> storage.table(StorageArea.LINKS).put(new byte[]{2}, new byte[0]));
            assertArrayEquals(new byte[]{7, 8}, table.get(key).orElseThrow());
            assertEquals(new StorageUsage(1, 3), storage.usage());
        }
    }
}
