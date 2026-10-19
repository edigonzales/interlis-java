package guru.interlis.repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Concurrent in-memory content store; every hit is re-verified. */
public final class InMemoryContentStore implements ContentStore {
    private final ConcurrentMap<ContentDigest, VerifiedContent> entries =
            new ConcurrentHashMap<>();

    @Override
    public Optional<VerifiedContent> lookup(ContentDigest digest) {
        VerifiedContent content = entries.get(digest);
        if (content == null) {
            return Optional.empty();
        }
        if (!digest.matches(content.bytes())) {
            entries.remove(digest, content);
            return Optional.empty();
        }
        return Optional.of(content);
    }

    @Override
    public void put(VerifiedContent content) {
        if (!content.digest().matches(content.bytes())) {
            throw new IllegalArgumentException("content digest does not match bytes");
        }
        entries.put(content.digest(), content);
    }
}
