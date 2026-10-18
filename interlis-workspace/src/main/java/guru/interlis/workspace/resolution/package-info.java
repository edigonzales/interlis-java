/**
 * Resolver SPI and prepared import-closure models.
 *
 * <p>Resolution is explicit and deterministic. Missing, ambiguous, blocked and
 * stale targets remain distinguishable; the engine never fetches implicitly.
 * Prepared closures are immutable and carry their source/revision provenance.</p>
 */
package guru.interlis.workspace.resolution;
