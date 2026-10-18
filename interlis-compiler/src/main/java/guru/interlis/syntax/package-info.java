/**
 * Own, recoverable syntax tree independent of ANTLR.
 *
 * <p>Snapshots are immutable and retain tokens, trivia, source ranges and
 * coverage. Syntax errors are diagnostics, not exceptions. Unsupported
 * constructs remain visible through coverage and are never silently turned into
 * a successful semantic node.</p>
 */
package guru.interlis.syntax;
