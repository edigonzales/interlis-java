package guru.interlis.execution;

/** Typed reason for a controlled cancellation. */
public enum CancellationReason {
    /** The {@code USER_REQUESTED} constant. */
    USER_REQUESTED,
    /** The {@code SUPERSEDED} constant. */
    SUPERSEDED,
    /** The {@code DEADLINE_EXCEEDED} constant. */
    DEADLINE_EXCEEDED,
    /** The {@code RESOURCE_LIMIT} constant. */
    RESOURCE_LIMIT,
    /** The {@code HOST_SHUTDOWN} constant. */
    HOST_SHUTDOWN
}
