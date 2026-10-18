package guru.interlis.workspace;

/** Machine-readable invalidation reason. */
public enum InvalidationReason {
    /** The {@code SOURCE_CONTENT_CHANGED} constant. */
    SOURCE_CONTENT_CHANGED,
    /** The {@code SOURCE_ADDED} constant. */
    SOURCE_ADDED,
    /** The {@code SOURCE_REMOVED} constant. */
    SOURCE_REMOVED,
    /** The {@code DEPENDENT_SOURCE_CHANGED} constant. */
    DEPENDENT_SOURCE_CHANGED,
    /** The {@code CONFIGURATION_CHANGED} constant. */
    CONFIGURATION_CHANGED,
    /** The {@code RESOLVER_CHANGED} constant. */
    RESOLVER_CHANGED,
    /** The {@code CACHE_LIMIT_CHANGED} constant. */
    CACHE_LIMIT_CHANGED,
    /** The {@code NEGATIVE_LOOKUP_INVALIDATED} constant. */
    NEGATIVE_LOOKUP_INVALIDATED
}
