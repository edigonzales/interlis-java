/**
 * Explicit host source loaders.
 *
 * <p>Loaders perform IO only and return immutable text plus byte provenance.
 * Timeouts, size limits, redirect limits, encoding failures, access denial and
 * offline policy are typed failures. The compiler core never performs IO.</p>
 */
package guru.interlis.repository;
