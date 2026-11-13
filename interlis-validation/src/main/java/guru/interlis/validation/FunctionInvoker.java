package guru.interlis.validation;

import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferValue;

import java.util.List;
import guru.interlis.execution.OperationCancelledException;

import static guru.interlis.validation.EvaluationResult.*;

final class FunctionInvoker {
    private FunctionInvoker() { }

    static EvaluationResult invoke(FunctionSymbol function, List<TransferValue> arguments, EvaluationFrame frame) {
        var plan = frame.plan();
        var canonical = new SymbolRef(plan.snapshotId(), plan.canonical(function.id()));
        var binding = plan.bindings().functions().get(canonical);
        if (binding == null) {
            return Unsupported.INSTANCE;
        }
        var signature = binding.signature();
        if (arguments.size() != signature.parameters().size()) {
            throw failure(canonical, "Function argument count differs from the bound signature", null);
        }
        for (int i = 0; i < arguments.size(); i++) {
            var formal = function.parameters().get(i);
            if (!RuntimeTypeChecks.accepts(plan, formal.typeId(), formal.mandatory(), arguments.get(i), frame.visit())) {
                throw failure(canonical, "Function argument " + (i + 1) + " violates the bound signature", null);
            }
        }
        var cancellation = frame.cancellation();
        var context = new FunctionContext(plan.bindings(), frame.visit());
        cancellation.throwIfCancellationRequested();
        TransferValue value;
        try {
            value = binding.implementation().evaluate(List.copyOf(arguments), context, cancellation);
        } catch (OperationCancelledException | ValidationLimitException technical) {
            throw technical;
        } catch (RuntimeException provider) {
            throw failure(canonical, "Function implementation threw an exception", provider);
        }
        cancellation.throwIfCancellationRequested();
        if (value == null || !RuntimeTypeChecks.accepts(plan, function.returnType(), function.returnMandatory(),
                value, frame.visit())) {
            throw failure(canonical, "Function result violates the bound signature", null);
        }
        return ExpressionEvaluator.value(value, false);
    }

    private static FunctionEvaluationException failure(SymbolRef function, String message, Throwable cause) {
        return new FunctionEvaluationException(function, message, cause);
    }
}
