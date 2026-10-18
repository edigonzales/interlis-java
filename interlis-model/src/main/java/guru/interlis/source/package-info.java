/**
 * Immutable source identities, revisions, UTF-16 half-open ranges and line maps.
 *
 * <p>Ranges are half-open ({@code [startOffset, endOffset)}) and use UTF-16
 * code-unit offsets. A revision is part of the range identity; a range from one
 * revision must not be applied to another. Empty ranges are allowed at edit
 * boundaries. Null is never used to represent a missing range; callers use
 * {@link java.util.Optional} where absence is part of the contract.</p>
 */
package guru.interlis.source;
