/**
 * Transport-neutral JSON protocol for portable compiler sessions.
 *
 * <p>The bridge is synchronous, owns no global session state and returns one
 * envelope per request. Protocol errors, modelled rejections, cancellation and
 * internal failures use distinct status/error fields. Snapshot/schema handles
 * remain revision-bound.</p>
 */
package guru.interlis.bridge;
