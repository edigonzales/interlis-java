/**
 * Deterministic INTERLIS source formatting without host IO.
 *
 * <p>The formatter preserves comments, strings, Unicode and the semantic token
 * sequence. It rejects incomplete syntax instead of returning a partial
 * document. Hosts own file reading and writing.</p>
 */
package guru.interlis.format;
