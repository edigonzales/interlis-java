package guru.interlis.validation;

import guru.interlis.transfer.TransferValue;

sealed interface EvaluationResult {
    record Known(TransferValue value) implements EvaluationResult { }
    record KnownNumber(NumericSemantics.Number value) implements EvaluationResult { }
    record UndefinedByData(boolean comparison) implements EvaluationResult { }
    enum MissingContext implements EvaluationResult { POPULATION, OBJECT_TYPE, ATTRIBUTE_VALUE, PARAMETER, UNIT_CONVERSION }
    record BlockedByViolation(DataLocation cause) implements EvaluationResult { }
    enum Unsupported implements EvaluationResult { INSTANCE }
}
