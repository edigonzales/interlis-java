/**
 * Immutable diagnostic values, typed arguments, catalog and display projection.
 *
 * <p>Codes and message keys are stable data; rendered text is never the
 * diagnostic identity. Diagnostics carry UTF-16 half-open source ranges and
 * optional explicit cause/dependency ids. Catalog validation rejects unknown
 * arguments and phases. Presentation is a separate, immutable view and never
 * mutates the complete diagnostic list.</p>
 */
package guru.interlis.diagnostics;
