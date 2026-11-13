package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferValue;

import java.util.OptionalLong;

record EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit,
               OptionalLong objectCount, CancellationToken cancellation) {
    EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit,
                    OptionalLong count) {
        this(plan, current, partial, visit, count, CancellationToken.none());
    }

    EvaluationFrame(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit) {
        this(plan, current, partial, visit, OptionalLong.empty(), CancellationToken.none());
    }
}
