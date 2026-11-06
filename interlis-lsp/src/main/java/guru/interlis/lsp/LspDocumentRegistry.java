package guru.interlis.lsp;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Owner-queue document state. Entries are immutable and keyed by the exact client URI. */
final class LspDocumentRegistry {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long generation;

    sealed interface Entry permits Synchronized, ResyncRequired, RejectedOpen {
        SourceId sourceId();
        int lspVersion();

        /** Last accepted client text, if available. Resync text is never a query input. */
        default Optional<SourceText> acceptedSource() {
            return switch (this) {
                case Synchronized open -> Optional.of(open.source());
                case ResyncRequired resync -> resync.lastAccepted();
                case RejectedOpen ignored -> Optional.empty();
            };
        }

        /** A failed identity lookup alone does not give the client authority over a physical file. */
        default boolean hasClientAuthority() {
            return !(this instanceof ResyncRequired resync && resync.lastAccepted().isEmpty());
        }
    }

    record Synchronized(SourceId sourceId, int lspVersion, SourceText source,
                        Optional<AnalysisSnapshot> currentAnalysis) implements Entry {
        Synchronized {
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(currentAnalysis, "currentAnalysis");
            if (!sourceId.equals(source.id()) || currentAnalysis.isPresent()
                    && !source.revision().equals(currentAnalysis.orElseThrow().sourceRevisions().get(sourceId))) {
                throw new IllegalArgumentException("document source and analysis revisions differ");
            }
        }

        /** Returns the current analysis after the host has checked that it is available. */
        AnalysisSnapshot analysis() {
            return currentAnalysis.orElseThrow();
        }
    }

    record ResyncRequired(SourceId sourceId, int lspVersion,
                          Optional<SourceText> lastAccepted) implements Entry {
        ResyncRequired {
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(lastAccepted, "lastAccepted");
            if (lastAccepted.isPresent() && !sourceId.equals(lastAccepted.orElseThrow().id())) {
                throw new IllegalArgumentException("resync text belongs to another source");
            }
        }
    }

    record RejectedOpen(SourceId sourceId, int lspVersion) implements Entry {
        RejectedOpen {
            Objects.requireNonNull(sourceId, "sourceId");
        }
    }

    long generation() {
        return generation;
    }

    void advanceGeneration() {
        generation++;
    }

    /** Returns the exact client's state or null when it is closed. */
    Entry find(String uri) {
        return entries.get(uri);
    }

    /** Returns the exact client's synchronized state or null. */
    Synchronized synchronizedDocument(String uri) {
        return entries.get(uri) instanceof Synchronized open ? open : null;
    }

    boolean needsResync() {
        return entries.values().stream().anyMatch(entry -> !(entry instanceof Synchronized));
    }

    boolean hasSynchronizedAlias(String uri, SourceId sourceId) {
        return entries.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(uri)
                && entry.getValue() instanceof Synchronized open && open.sourceId().equals(sourceId));
    }

    boolean hasClientAuthority(SourceId sourceId) {
        return entries.values().stream().anyMatch(entry -> entry.hasClientAuthority() && entry.sourceId().equals(sourceId));
    }

    void forEachClientSource(BiConsumer<SourceId, String> consumer) {
        entries.forEach((uri, entry) -> {
            if (entry.hasClientAuthority()) consumer.accept(entry.sourceId(), uri);
        });
    }

    /** Immutable snapshot in client admission order, containing only synchronized buffers. */
    Map<String, Synchronized> synchronizedDocuments() {
        Map<String, Synchronized> snapshot = new LinkedHashMap<>();
        entries.forEach((uri, entry) -> {
            if (entry instanceof Synchronized open) snapshot.put(uri, open);
        });
        return Collections.unmodifiableMap(snapshot);
    }

    Optional<String> synchronize(String uri, int version, SourceText source) {
        return replace(uri, new Synchronized(source.id(), version, source, Optional.empty()));
    }

    Optional<String> reject(String uri, SourceId sourceId, int version) {
        var accepted = acceptedSource(sourceId);
        Entry rejected = accepted.isPresent()
                ? new ResyncRequired(sourceId, version, accepted)
                : new RejectedOpen(sourceId, version);
        return replace(uri, rejected);
    }

    Optional<String> requireResync(String uri, SourceId sourceId, int version) {
        return replace(uri, new ResyncRequired(sourceId, version, acceptedSource(sourceId)));
    }

    void observeRejectedVersion(String uri, int version) {
        Entry updated = switch (entries.get(uri)) {
            case ResyncRequired resync -> new ResyncRequired(resync.sourceId(), version, resync.lastAccepted());
            case RejectedOpen rejected -> new RejectedOpen(rejected.sourceId(), version);
            default -> throw new IllegalStateException("document does not require resynchronization");
        };
        entries.put(uri, updated);
    }

    void invalidateAnalysis() {
        entries.replaceAll((uri, entry) -> entry instanceof Synchronized open && open.currentAnalysis().isPresent()
                ? new Synchronized(open.sourceId(), open.lspVersion(), open.source(), Optional.empty())
                : entry);
    }

    Synchronized acceptAnalysis(String uri, Synchronized previous, AnalysisSnapshot analysis) {
        var current = new Synchronized(previous.sourceId(), previous.lspVersion(), previous.source(), Optional.of(analysis));
        if (!entries.replace(uri, previous, current)) {
            throw new IllegalStateException("document changed before accepting analysis");
        }
        return current;
    }

    void remove(String uri) {
        entries.remove(uri);
    }

    List<String> removeSources(Predicate<SourceId> predicate) {
        var removed = entries.entrySet().stream().filter(entry -> predicate.test(entry.getValue().sourceId()))
                .map(Map.Entry::getKey).toList();
        removed.forEach(entries::remove);
        return removed;
    }

    void clear() {
        entries.clear();
    }

    private Optional<SourceText> acceptedSource(SourceId sourceId) {
        return entries.values().stream().filter(entry -> entry.sourceId().equals(sourceId))
                .findFirst().flatMap(Entry::acceptedSource);
    }

    /** Replaces the client state and returns any superseded unsynchronized alias. */
    private Optional<String> replace(String uri, Entry entry) {
        var alias = entries.entrySet().stream().filter(previous -> !previous.getKey().equals(uri)
                && previous.getValue().sourceId().equals(entry.sourceId())).findFirst();
        if (alias.isPresent() && alias.orElseThrow().getValue() instanceof Synchronized) {
            throw new IllegalStateException("source already has a synchronized client");
        }
        var removed = alias.map(Map.Entry::getKey);
        removed.ifPresent(entries::remove);
        entries.put(uri, entry);
        return removed;
    }
}
