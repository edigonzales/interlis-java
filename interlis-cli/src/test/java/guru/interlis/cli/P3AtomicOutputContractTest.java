package guru.interlis.cli;

import guru.interlis.execution.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class P3AtomicOutputContractTest {
    @TempDir Path directory;

    @Test void unsupportedAtomicMoveAndIoFailurePreserveOriginalAndCleanStaging() throws Exception {
        Path target = target();
        for (var failure : new IOException[]{new AtomicMoveNotSupportedException("stage", "target", "injected"),
                new IOException("injected write failure")}) {
            var writer = new CliOutputWriter((temporary, output) -> { throw failure; });
            assertSame(failure, assertThrows(IOException.class, () -> writer.write(target, bytes("new"),
                    Optional.of(bytes("original")), CancellationToken.none())));
            assertEquals("original", Files.readString(target));
            assertNoStaging();
        }
    }

    @Test void cancellationAfterStagingNeverCommitsTheNewBytes() throws Exception {
        Path target = target();
        var calls = new AtomicInteger();
        CancellationToken token = () -> calls.incrementAndGet() >= 2;
        assertThrows(OperationCancelledException.class, () -> new CliOutputWriter().write(target,
                bytes("new"), Optional.of(bytes("original")), token));
        assertEquals("original", Files.readString(target));
        assertNoStaging();
    }

    @Test void anExternalEditIsPreservedInsteadOfBeingOverwritten() throws Exception {
        Path target = target();
        Files.writeString(target, "external edit");
        assertThrows(IOException.class, () -> new CliOutputWriter().write(target, bytes("formatted"),
                Optional.of(bytes("original")), CancellationToken.none()));
        assertEquals("external edit", Files.readString(target));
        assertNoStaging();
    }

    @Test void successfulReplacementUsesTheTargetDirectoryAndPreservesPermissions() throws Exception {
        Path target = target();
        var view = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        var permissions = view == null ? null : view.readAttributes().permissions();
        new CliOutputWriter().write(target, bytes("formatted"), Optional.of(bytes("original")),
                CancellationToken.none());
        assertEquals("formatted", Files.readString(target));
        if (permissions != null) assertEquals(permissions, Files.getPosixFilePermissions(target));
        assertNoStaging();
    }

    @Test void aLaterBatchFailureListsOnlyTheFilesActuallyReplaced() throws Exception {
        Path first = directory.resolve("First.ili"), second = directory.resolve("Second.ili");
        String original = "INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = END F.";
        Files.writeString(first, original); Files.writeString(second, original);
        var moves = new AtomicInteger();
        var writer = new CliOutputWriter((temporary, target) -> {
            if (moves.incrementAndGet() == 2) throw new IOException("injected second move");
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
        var result = new DefaultCompilerCli(writer).run(List.of("format", "--write",
                first.toUri().toString(), second.toString()), environment(), CancellationToken.none());
        assertEquals(CliTermination.OPERATIONAL_FAILURE, result.termination());
        assertNotEquals(original, Files.readString(first));
        assertEquals(original, Files.readString(second));
        assertEquals(List.of(new CliExecutionResult.Artifact(CliExecutionResult.ArtifactKind.FORMATTED_SOURCE,
                first.toUri(), Files.size(first))), result.artifacts());
        try (var paths = Files.list(directory)) { assertEquals(2, paths.count()); }
    }

    private CliEnvironment environment() {
        return new CliEnvironment(directory, new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), Map.of());
    }

    @Test void anUnexpectedOutputDefectReturnsAFailureIdWithoutAStacktraceOrReplacement() throws Exception {
        Path file = directory.resolve("Defect.ili");
        String original = "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = END F.";
        Files.writeString(file, original);
        var result = new DefaultCompilerCli(new CliOutputWriter((temporary, target) -> {
            throw new IllegalStateException("private implementation detail");
        })).run(List.of("format", "--write", file.toString()), environment(), CancellationToken.none());
        assertEquals(CliTermination.INTERNAL_FAILURE, result.termination());
        assertTrue(result.output().startsWith("internal failure: cli-"));
        assertFalse(result.output().contains("private implementation detail"));
        assertTrue(result.artifacts().isEmpty());
        assertEquals(original, Files.readString(file));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    private Path target() throws IOException {
        Path path = directory.resolve("existing.ili");
        Files.writeString(path, "original");
        return path;
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private void assertNoStaging() throws IOException {
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
