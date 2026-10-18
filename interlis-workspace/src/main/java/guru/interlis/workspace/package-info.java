/**
 * In-memory workspace with revisioned, atomic source updates and analysis reuse.
 *
 * <p>A workspace is mutated by explicit updates under an expected revision.
 * Accepted snapshots are immutable and remain valid after later updates or
 * close. Mutating methods are not reentrant on one instance; independent
 * workspaces may run concurrently. Operational cancellation is reported as
 * {@link guru.interlis.execution.OperationCancelledException}, not as a modelled
 * language rejection.</p>
 */
package guru.interlis.workspace;
