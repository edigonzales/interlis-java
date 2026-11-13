package guru.interlis.validation.mvstore;

import guru.interlis.execution.CancellationToken;
import guru.interlis.validation.DatasetOptions;
import guru.interlis.validation.DatasetValidator;
import guru.interlis.validation.InputDescriptor;
import guru.interlis.validation.InputId;
import guru.interlis.validation.InputRole;
import guru.interlis.validation.StorageArea;
import guru.interlis.validation.ValidationLimitException;
import guru.interlis.validation.ValidationStorage;
import guru.interlis.validation.ValidationStreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.validation.mvstore.StorageParityTest.fixture;
import static guru.interlis.validation.mvstore.StorageParityTest.reader;
import static org.junit.jupiter.api.Assertions.*;

class DiskFailureTest {
    @TempDir Path root;

    @Test
    void rejectsAnActualFileGrowthPastTheQuotaAndPoisonsTheStore() throws IOException {
        long quota = 96 * 1024;
        try (var disk = MvStoreValidationStorage.open(root, new MvStoreOptions(10_000, 2_000_000, quota, 1, 1))) {
            var table = disk.table(StorageArea.OBJECTS);
            table.put(new byte[]{1}, new byte[]{2});
            disk.checkpoint();
            var error = assertThrows(ValidationLimitException.class, () -> {
                table.put(new byte[]{2}, new byte[256 * 1024]);
                disk.checkpoint();
            });
            assertEquals("MVStore temporary file quota exceeded", error.getMessage());
            assertSame(error, assertThrows(ValidationLimitException.class, () -> table.get(new byte[]{1})));
            try (var directories = Files.list(root)) {
                var file = directories.findFirst().orElseThrow().resolve("validation.mv");
                assertTrue(Files.size(file) <= quota);
            }
        }
        try (var directories = Files.list(root)) {
            assertEquals(0, directories.count());
        }
    }

    @Test
    void removesAnOwnedPartialStoreWhenTheInitialWriteExceedsQuota() throws IOException {
        var foreign = Files.writeString(root.resolve("host-owned.txt"), "keep");
        var options = new MvStoreOptions(10, 1_000, 1, 1, 1);
        var error = assertThrows(ValidationLimitException.class, () -> MvStoreValidationStorage.open(root, options));
        assertEquals("MVStore temporary file quota exceeded", error.getMessage());
        try (var files = Files.list(root)) {
            assertEquals(java.util.List.of(foreign), files.toList());
        }
    }

    @Test
    void preservesThePreviousValueWhenALogicalBudgetRejectsAReplacement() {
        try (var disk = MvStoreValidationStorage.open(root, new MvStoreOptions(1, 4, 1_000_000, 1, 1))) {
            var table = disk.table(StorageArea.RULE_KEYS);
            table.put(new byte[]{1}, new byte[]{3, 4});
            assertThrows(ValidationLimitException.class, () -> table.put(new byte[]{1}, new byte[4]));
            assertThrows(ValidationLimitException.class, () -> table.put(new byte[]{2}, new byte[]{5}));
            assertArrayEquals(new byte[]{3, 4}, table.get(new byte[]{1}).orElseThrow());
            assertEquals(1, disk.usage().entries());
            assertEquals(3, disk.usage().bytes());
        }
    }

    @Test
    void deliversAnUnobservedBackgroundWriteFailureAtClose() throws ReflectiveOperationException, IOException {
        var disk = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(1_000_000));
        try (disk) {
            var storeField = MvStoreValidationStorage.class.getDeclaredField("store");
            assertTrue(storeField.trySetAccessible());
            var store = (org.h2.mvstore.MVStore) storeField.get(disk);
            var writeFailure = new IOException("background write probe");
            store.backgroundExceptionHandler.uncaughtException(Thread.currentThread(), writeFailure);
            var reported = assertThrows(UncheckedIOException.class, disk::close);
            assertSame(writeFailure, reported.getCause().getCause());
        }
        try (var children = Files.list(root)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void aFinalCheckpointFailureMakesTheDatasetTerminalBeforeAReport() {
        var fixture = fixture();
        var plan = fixture.plan();
        var token = CancellationToken.none();
        try (var disk = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(1_000_000));
             var input = reader(fixture, "<Data i:bid='b'><Owner i:tid='o'/></Data>")) {
            var fault = new ValidationStorage() {
                private int checkpoints;

                @Override
                public guru.interlis.validation.ValidationTable table(StorageArea area) {
                    return disk.table(area);
                }

                @Override
                public void checkpoint() {
                    if (++checkpoints == 3) {
                        throw new UncheckedIOException("Final write failed", new IOException("probe"));
                    }
                    disk.checkpoint();
                }

                @Override
                public guru.interlis.validation.StorageUsage usage() {
                    return disk.usage();
                }

                @Override
                public void close() { }
            };
            try (var run = DatasetValidator.session(plan, DatasetOptions.defaults(), fault)) {
                run.readInput(new InputDescriptor(new InputId("p"), InputRole.PRIMARY), input, token);
                var failure = assertThrows(UncheckedIOException.class, () -> run.finish(token));
                assertEquals("Final write failed", failure.getMessage());
                assertThrows(ValidationStreamException.class, () -> run.finish(token));
            }
        }
    }
}
