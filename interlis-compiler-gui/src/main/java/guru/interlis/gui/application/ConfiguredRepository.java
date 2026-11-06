package guru.interlis.gui.application;

import guru.interlis.repository.RepositorySource;

import java.util.Objects;

/** One explicitly configured repository. List order determines active priority. */
public record ConfiguredRepository(RepositorySource source, boolean enabled) {
    public ConfiguredRepository {
        Objects.requireNonNull(source);
    }
}
