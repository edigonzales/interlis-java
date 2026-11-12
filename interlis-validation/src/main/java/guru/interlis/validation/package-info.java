/**
 * Local INTERLIS 2.3 and 2.4 validation for decoded transfer events. A
 * {@link guru.interlis.validation.ValidationPlan} belongs to one compiled
 * schema revision and is safe to share across independent, single-caller
 * sessions. Public inputs and results are non-null; absence is explicit.
 *
 * <p>A completed report applies only to rules that ran. Coverage describes
 * declared exclusions and runtime gaps. Data findings carry a transfer
 * location and separate model provenance. Input failures, cancellation and
 * resource limits abort the run and do not produce a completed report.
 */
package guru.interlis.validation;
