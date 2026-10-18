/**
 * Run identity, cooperative cancellation and observation contracts.
 *
 * <p>Runs have a stable id, an ordered event sequence and exactly one terminal
 * outcome. Cancellation and deadlines are typed values; observer failures never
 * replace a factual compiler result. The portable core owns no global current
 * run, listener or clock.</p>
 */
package guru.interlis.execution;
