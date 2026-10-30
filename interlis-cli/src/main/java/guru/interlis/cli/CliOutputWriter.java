package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Host-only atomic replacement, with no non-atomic fallback. */
public final class CliOutputWriter {
    @FunctionalInterface
    interface Move {
        void replace(Path temporary, Path target) throws IOException;
    }

    private final Move move;

    public CliOutputWriter() {
        this((temporary, target) -> Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    CliOutputWriter(Move move) { this.move = Objects.requireNonNull(move, "move"); }

    /**
     * Stages bytes beside the target and checks cancellation immediately before
     * the atomic replacement. A supplied original is checked byte-for-byte;
     * a changed file is preserved. This is an optimistic read window, not a
     * filesystem compare-and-swap transaction. The caller retains all arrays.
     */
    public void write(Path target, byte[] bytes, Optional<byte[]> original,
               CancellationToken cancellation) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(cancellation, "cancellation");
        target = target.toAbsolutePath().normalize();
        cancellation.throwIfCancellationRequested();
        if (Files.isSymbolicLink(target)) throw new IOException("symbolic output target is not replaceable");
        Path parent = target.getParent();
        if (parent == null) throw new IOException("target has no parent");
        Path temporary = Files.createTempFile(parent, ".interlis-output-", ".tmp");
        try {
            Files.write(temporary, bytes);
            var attributes = Files.getFileAttributeView(target, PosixFileAttributeView.class);
            if (attributes != null && Files.exists(target)) {
                Files.setPosixFilePermissions(temporary, attributes.readAttributes().permissions());
            }
            cancellation.throwIfCancellationRequested();
            if (original.isPresent() && !Arrays.equals(original.orElseThrow(), Files.readAllBytes(target))) {
                throw new IOException("source changed since it was read");
            }
            cancellation.throwIfCancellationRequested();
            move.replace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
