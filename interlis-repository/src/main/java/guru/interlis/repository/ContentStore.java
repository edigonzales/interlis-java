package guru.interlis.repository;

import java.util.Optional;

/** Optional host cache; hits are returned only for digest-verified content. */
public interface ContentStore {
    /**
     * Looks up verified content.
     *
     * @param digest exact digest
     * @return verified content or empty
     */
    Optional<VerifiedContent> lookup(ContentDigest digest);

    /**
     * Atomically stores verified content.
     *
     * @param content digest-verified content
     */
    void put(VerifiedContent content);
}
