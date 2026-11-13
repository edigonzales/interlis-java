package guru.interlis.validation;

/** Extent of the delivery the caller asks the validator to assess. */
public enum DatasetScope {
    /** Checks the supplied delivery after all primary and reference inputs have reached EOF. */
    SUPPLIED_DATASET,
    /** Reports observed violations and leaves conclusions requiring missing data open. */
    PARTIAL_DATASET
}
