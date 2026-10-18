/**
 * LSP4J adapter over the workspace and language queries.
 *
 * <p>stdout carries LSP framing only. Requests use immutable analysis
 * snapshots, support cancellation and keep protocol, language and internal
 * errors distinguishable. The adapter contains no second binder or type
 * checker.</p>
 */
package guru.interlis.lsp;
