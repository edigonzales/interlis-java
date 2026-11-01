package guru.interlis.repository;

/** Credential-free host cache observations; callbacks belong to one host invocation. */
public enum RepositoryCacheEvent {
    FRESH_HIT, DOWNLOAD, NOT_MODIFIED, OFFLINE_HIT, OFFLINE_MISS, CACHE_MISS, WRITE_UNAVAILABLE
}
