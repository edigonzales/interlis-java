package guru.interlis.validation;

import guru.interlis.transfer.TransferValue;

sealed interface EvaluationResult {
    record Known(TransferValue value) implements EvaluationResult { }
    record KnownObjects(java.util.List<ObjectHandle> handles) implements EvaluationResult {
        public KnownObjects {
            handles = java.util.List.copyOf(handles);
        }
    }
    record KnownNumber(NumericSemantics.Number value) implements EvaluationResult { }
    record UndefinedByData(boolean comparison, boolean nullPartner) implements EvaluationResult {
        UndefinedByData(boolean comparison) {
            this(comparison, false);
        }
    }
    enum MissingContext implements EvaluationResult { POPULATION, OBJECT_TYPE, ATTRIBUTE_VALUE, PARAMETER, UNIT_CONVERSION, DERIVATION_CYCLE, DATASET_REFERENCE, STRUCTURE_PARENT, VIEW_ROW }
    record BlockedByViolation(DataLocation cause) implements EvaluationResult { }
    enum Unsupported implements EvaluationResult { INSTANCE }
}
