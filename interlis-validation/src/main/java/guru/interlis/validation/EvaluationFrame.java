package guru.interlis.validation;

import guru.interlis.transfer.TransferValue;

import java.util.OptionalLong;

record EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit,
               OptionalLong objectCount) {
    EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit) {
        this(plan, current, partial, visit, OptionalLong.empty());
    }
}
