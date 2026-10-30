package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Optional atomic digest-addressed JVM cache. IO failures/corrupt/oversize entries
 * are misses, never success proofs. Content identity is the verified digest;
 * lookup uses a cache URI because identical bytes can belong to several sources.
 */
public final class DirectoryContentStore implements ContentStore {
    private final Path directory;
    private final int maxEntryBytes;
    public DirectoryContentStore(Path directory) { this(directory, 16_777_216); }
    public DirectoryContentStore(Path directory, int maxEntryBytes) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (maxEntryBytes <= 0 || maxEntryBytes == Integer.MAX_VALUE) throw new IllegalArgumentException("cache limit must be positive");
        this.maxEntryBytes = maxEntryBytes;
    }
    @Override public Optional<VerifiedContent> lookup(ContentDigest digest) {
        Objects.requireNonNull(digest, "digest");
        Path file = directory.resolve(digest.value());
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(maxEntryBytes + 1);
            if (bytes.length > maxEntryBytes || !digest.matches(bytes)) return Optional.empty();
            return Optional.of(new VerifiedContent(new SourceId("cache:sha256:" + digest.value()), digest, bytes));
        } catch (IOException unavailable) { return Optional.empty(); }
    }
    @Override public void put(VerifiedContent content) {
        Objects.requireNonNull(content, "content");
        byte[] bytes = content.bytes();
        if (bytes.length > maxEntryBytes) return;
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".content-", ".tmp");
            Files.write(temporary, bytes);
            Path target = directory.resolve(content.digest().value());
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException fallback) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException unavailable) {
            // Cache availability never changes the correctness of an online load.
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* optional cache */ }
        }
    }
}
