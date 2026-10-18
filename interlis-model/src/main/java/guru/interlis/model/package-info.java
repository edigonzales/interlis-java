/**
 * Immutable semantic model, stable symbol identities and compiled-schema views.
 *
 * <p>All public model values are immutable. Symbol and type references are
 * snapshot-bound; mixing references from different snapshots is invalid and is
 * rejected at the query/projection boundary. Member order is the effective
 * INTERLIS order and is part of the contract. Optional properties are modelled
 * with {@link java.util.Optional}, never with null.</p>
 */
package guru.interlis.model;
