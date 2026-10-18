/**
 * Read-only semantic queries bound to exactly one snapshot.
 *
 * <p>Every reference is snapshot-bound; foreign or missing references produce a
 * typed {@code QueryResult}, never a null or a cross-snapshot result. Member
 * order is the effective INTERLIS order and results are deterministic.</p>
 */
package guru.interlis.model.query;
