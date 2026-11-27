package guru.interlis.validation.mvstore;

import guru.interlis.validation.StorageArea;
import guru.interlis.validation.StorageUsage;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationLimitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.mvstore.StorageParityTest.fixture;
import static guru.interlis.validation.mvstore.StorageParityTest.validate;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FilePoolValidationStorageTest {
    @TempDir Path root;

    @Test
    void matchesDatasetReportsAndLogicalStorageUsage() {
        var model = fixture();
        var data = """
                <Data i:bid="b1">
                  <Owner i:tid="o1"/>
                  <Item i:tid="i1"><code>same</code><owner i:ref="o1"/></Item>
                </Data>
                <Data i:bid="b2">
                  <Item i:tid="i2"><code>same</code><owner i:ref="absent"/></Item>
                </Data>
                """;
        var options = MvStoreOptions.defaults(64 * 1024 * 1024);
        try (var mvstore = MvStoreValidationStorage.open(root, options);
             var pool = FilePoolValidationStorage.open(root, options)) {
            var expected = validate(model, mvstore, data);
            var actual = validate(model, pool, data);
            assertEquals(expected, actual);
            assertEquals(List.of(ValidationCode.REFERENCE_TARGET_MISSING, ValidationCode.DATASET_UNIQUE_DUPLICATE),
                    actual.examples().stream().map(example -> example.code()).toList());
            assertEquals(mvstore.usage(), pool.usage());
        }
    }

    @Test
    void ownsValuesAndPreservesUnsignedRangesDuringOtherTableWrites() {
        try (var pool = FilePoolValidationStorage.open(root, MvStoreOptions.defaults(1024 * 1024))) {
            var table = pool.table(StorageArea.OBJECTS);
            byte[] key = {(byte) 0x80};
            byte[] value = {2};
            table.put(key, value);
            key[0] = 1;
            value[0] = 99;
            table.put(new byte[]{0}, new byte[]{1});
            table.put(new byte[]{(byte) 0xff}, new byte[]{3});
            byte[] lower = {0};
            byte[] upper = {(byte) 0xff};
            try (var cursor = table.scan(lower, Optional.of(upper))) {
                lower[0] = (byte) 0xff;
                upper[0] = 0;
                assertArrayEquals(new byte[]{0}, cursor.next().orElseThrow().key());
                assertThrows(IllegalStateException.class, () -> table.put(new byte[]{0}, new byte[]{9}));
                assertThrows(IllegalStateException.class, () -> table.putIfAbsent(new byte[]{1}, new byte[]{9}));
                var prior = table.putIfAbsent(new byte[]{(byte) 0x80}, new byte[]{9}).orElseThrow();
                assertArrayEquals(new byte[]{2}, prior);
                prior[0] = 99;
                pool.table(StorageArea.TEMPORARY).put(new byte[]{1}, new byte[]{7, 8});
                pool.checkpoint();
                var entry = cursor.next().orElseThrow();
                assertArrayEquals(new byte[]{(byte) 0x80}, entry.key());
                assertArrayEquals(new byte[]{2}, entry.value());
                entry.key()[0] = 0;
                entry.value()[0] = 99;
                assertTrue(cursor.next().isEmpty());
                assertTrue(cursor.next().isEmpty());
            }
            var copied = table.get(new byte[]{(byte) 0x80}).orElseThrow();
            assertArrayEquals(new byte[]{2}, copied);
            copied[0] = 99;
            assertArrayEquals(new byte[]{2}, table.get(new byte[]{(byte) 0x80}).orElseThrow());
            assertThrows(IllegalArgumentException.class, () -> table.scan(new byte[]{1}, Optional.of(new byte[]{0})));
            try (var cursor = table.scan(new byte[]{0}, Optional.of(new byte[]{0}))) {
                assertTrue(cursor.next().isEmpty());
            }
        }
    }

    @Test
    void limitsLiveBytesAndAppendLengthBeforeChangingTheIndex() throws IOException {
        try (var pool = FilePoolValidationStorage.open(root, new MvStoreOptions(2, 4, 4, 1, 1))) {
            var table = pool.table(StorageArea.OBJECTS);
            var key = new byte[]{1};
            table.put(key, new byte[]{2});
            table.put(key, new byte[]{3, 4});
            assertEquals(new StorageUsage(1, 3), pool.usage());
            assertThrows(ValidationLimitException.class, () -> table.put(key, new byte[4]));
            assertThrows(ValidationLimitException.class, () -> table.put(key, new byte[2]));
            assertArrayEquals(new byte[]{3, 4}, table.get(key).orElseThrow());
            table.put(new byte[]{2}, new byte[0]);
            assertArrayEquals(new byte[0], table.get(new byte[]{2}).orElseThrow());
            assertThrows(ValidationLimitException.class, () -> table.put(new byte[]{3}, new byte[0]));
            pool.checkpoint();
            assertEquals(3, Files.size(poolFile()));
            assertEquals(new StorageUsage(2, 4), pool.usage());
        }
    }

    @Test
    void releasesCursorsAndKeepsFilesInTheHostDirectory() throws IOException {
        var neighbor = Files.writeString(root.resolve("neighbor.txt"), "retained");
        var pool = FilePoolValidationStorage.open(root, MvStoreOptions.defaults(1024 * 1024));
        try (pool; var cursor = pool.table(StorageArea.OBJECTS).scan(new byte[0], Optional.empty())) {
            pool.close();
            assertThrows(IllegalStateException.class, cursor::next);
            assertThrows(IllegalStateException.class, pool::checkpoint);
            try (var children = Files.list(root)) {
                assertEquals(List.of(neighbor), children.toList());
            }
        }
    }

    @Test
    void rejectsAReplacedPathAndPreservesItsTarget() throws IOException {
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"));
        var pool = FilePoolValidationStorage.open(root, MvStoreOptions.defaults(1024 * 1024));
        try (pool) {
            var file = poolFile();
            var original = Files.move(file, root.resolve("original.pool"));
            var target = Files.writeString(root.resolve("target.txt"), "retained");
            Files.createSymbolicLink(file, target);
            assertThrows(UncheckedIOException.class, pool::checkpoint);
            assertThrows(UncheckedIOException.class, pool::close);
            assertTrue(Files.isSymbolicLink(file));
            assertTrue(Files.exists(original));
            assertEquals("retained", Files.readString(target));
        }
    }

    @Test
    void stopsAfterAnIncompleteRecordRead() throws IOException {
        try (var pool = FilePoolValidationStorage.open(root, MvStoreOptions.defaults(1024 * 1024))) {
            var table = pool.table(StorageArea.OBJECTS);
            var key = new byte[]{1};
            table.put(key, new byte[]{2, 3, 4});
            try (var channel = FileChannel.open(poolFile(), StandardOpenOption.WRITE)) {
                channel.truncate(1);
            }
            assertThrows(UncheckedIOException.class, () -> table.get(key));
            assertThrows(UncheckedIOException.class, pool::checkpoint);
        }
    }

    private Path poolFile() throws IOException {
        try (var children = Files.list(root)) {
            return children.filter(Files::isDirectory).findFirst().orElseThrow().resolve("validation.pool");
        }
    }
}
