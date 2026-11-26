package guru.interlis.validation;

import guru.interlis.transfer.TransferValue;

import java.util.Optional;

sealed interface EvaluationResult {
    record Known(TransferValue value, Optional<DatasetPathContext> referenceOrigin,
                 Optional<GeometryEvidence> geometryEvidence) implements EvaluationResult {
        Known(TransferValue value) {
            this(value, Optional.empty());
        }

        Known(TransferValue value, Optional<DatasetPathContext> referenceOrigin) {
            this(value, referenceOrigin, Optional.empty());
        }
    }
    record KnownObjects(java.util.function.Supplier<java.util.List<ObjectHandle>> firstTwo,
                        java.util.function.LongSupplier size) implements EvaluationResult {
        KnownObjects(java.util.List<ObjectHandle> handles) {
            this(() -> handles, handles::size);
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
