package guru.interlis.validation.mvstore;

import guru.interlis.execution.CancellationToken;
import guru.interlis.validation.DatasetOptions;
import guru.interlis.validation.DatasetValidator;
import guru.interlis.validation.InputDescriptor;
import guru.interlis.validation.InputId;
import guru.interlis.validation.InputRole;
import guru.interlis.validation.StorageArea;
import guru.interlis.validation.ValidationStreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import static guru.interlis.validation.mvstore.StorageParityTest.fixture;
import static guru.interlis.validation.mvstore.StorageParityTest.reader;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StorageOwnershipTest {
    @TempDir Path root;

    @Test
    void removesOnlyItsOwnDirectoryAndClosesOpenCursors() throws IOException {
        var foreign = Files.writeString(root.resolve("retain.txt"), "untouched");
        var first = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(1_000_000));
        try (first; var second = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(1_000_000))) {
            try (var cursor = first.table(StorageArea.METADATA).scan(new byte[0], Optional.empty())) {
                try (var children = Files.list(root)) {
                    var directories = children.filter(Files::isDirectory).toList();
                    assertEquals(2, directories.size());
                    if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
                        var permissions = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE);
                        for (var directory : directories) {
                            assertEquals(permissions, Files.getPosixFilePermissions(directory));
                        }
                    }
                }
                first.close();
                assertThrows(IllegalStateException.class, cursor::next);
                assertTrue(second.diskBytes() > 0);
                assertEquals("untouched", Files.readString(foreign));
            }
        }
        try (var children = Files.list(root)) {
            assertEquals(List.of(foreign), children.toList());
        }
    }

    @Test
    void refusesAReplacedFileAndNeverDeletesASymlinkTarget() throws IOException {
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"));
        var disk = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(1_000_000));
        Path directory;
        try (var children = Files.list(root)) {
            directory = children.findFirst().orElseThrow();
        }
        var file = directory.resolve("validation.mv");
        Files.move(file, root.resolve("retained.mv"));
        var foreign = Files.writeString(root.resolve("foreign.txt"), "other file");
        Files.createSymbolicLink(file, foreign);
        assertThrows(UncheckedIOException.class, disk::checkpoint);
        assertThrows(UncheckedIOException.class, disk::close);
        assertTrue(Files.isSymbolicLink(file));
        assertEquals("other file", Files.readString(foreign));
        assertTrue(Files.exists(root.resolve("retained.mv")));
    }

    @Test
    void rejectsAnAlteredRunAndSchemaHeaderBeforePublishingAReport() {
        var fixture = fixture();
        var plan = fixture.plan();
        var token = CancellationToken.none();
        try (var disk = MvStoreValidationStorage.open(root, MvStoreOptions.defaults(1_000_000));
             var run = DatasetValidator.session(plan, DatasetOptions.defaults(plan), disk);
             var reader = reader(fixture, "")) {
            run.readInput(new InputDescriptor(new InputId("p"), InputRole.PRIMARY), reader, token);
            disk.table(StorageArea.METADATA).put(new byte[]{0}, new byte[]{0});
            var failure = assertThrows(ValidationStreamException.class, () -> run.finish(token));
            assertTrue(failure.getMessage().contains("another run or schema revision"));
        }
    }
}
