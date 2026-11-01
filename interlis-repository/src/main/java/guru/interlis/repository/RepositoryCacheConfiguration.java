package guru.interlis.repository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Immutable JVM host policy. An empty directory disables all persistent cache IO. */
public record RepositoryCacheConfiguration(Optional<Path> directory) {
    public RepositoryCacheConfiguration {
        directory = Objects.requireNonNull(directory, "directory").map(p -> p.toAbsolutePath().normalize());
    }
    /** Does not create directories; the host supplies its home explicitly. */
    public static RepositoryCacheConfiguration defaults(Path home) { return at(Objects.requireNonNull(home).resolve(".interlis-cache")); }
    public static RepositoryCacheConfiguration at(Path directory) { return new RepositoryCacheConfiguration(Optional.of(directory)); }
    public static RepositoryCacheConfiguration disabled() { return new RepositoryCacheConfiguration(Optional.empty()); }
}
