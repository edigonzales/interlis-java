/**
 * Public compiler request, analysis and compilation contracts.
 *
 * <p>Requests own their source/import/root collections and never expose mutable
 * caller lists. Analysis and compilation are cancellable through
 * {@link guru.interlis.execution.CancellationToken}. A successful
 * {@link guru.interlis.compiler.CompilationOutcome.Succeeded} exists only after
 * the closure gate has passed; missing implementation, rejected input,
 * cancellation and internal failures remain distinct outcomes. Public types do
 * not expose ANTLR or host/transport types.</p>
 */
package guru.interlis.compiler;
