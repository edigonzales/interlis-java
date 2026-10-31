/**
 * In-process CLI adapter.
 *
 * <p>The application returns a typed result and never calls `System.exit`;
 * only the process entry may do so. Diagnostics and operational output are
 * separated, and a failed run never presents an incomplete artifact as
 * success.</p>
 */
package guru.interlis.cli;
