/**
 * Pure, renderer-independent diagram projection.
 *
 * <p>Projection never mutates the analysis snapshot and produces deterministic
 * containers, nodes, members and edges in semantic order. Every emitted symbol
 * and source anchor keeps its snapshot and revision identity. A stale or mixed
 * selection is rejected; incomplete analysis is represented explicitly rather
 * than silently omitted.</p>
 */
package guru.interlis.projection;
