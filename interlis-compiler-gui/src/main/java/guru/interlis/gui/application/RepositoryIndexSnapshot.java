package guru.interlis.gui.application;

import guru.interlis.repository.ContentDigest;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositoryModelMetadata;
import guru.interlis.repository.RepositorySource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One index load in original document order. Entry occurrences remain distinct. */
public record RepositoryIndexSnapshot(RepositorySource repository, State state, List<Entry> entries,
                                Optional<String> problem, Optional<String> provenance) {
    public enum State { LOADED, EMPTY, UNAVAILABLE, OFFLINE_UNAVAILABLE, INVALID }

    public RepositoryIndexSnapshot {
        Objects.requireNonNull(repository);
        Objects.requireNonNull(state);
        entries = List.copyOf(entries);
        Objects.requireNonNull(problem);
        Objects.requireNonNull(provenance);
    }

    /** Catalog generations distinguish selections across a reload even when bytes match. */
    public record EntryId(RepositoryBase repository, ContentDigest indexDigest, int occurrence, long generation) {
        public EntryId {
            Objects.requireNonNull(repository);
            Objects.requireNonNull(indexDigest);
        }
    }

    public record Entry(EntryId id, RepositoryModelMetadata metadata) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(metadata);
        }
    }
}
